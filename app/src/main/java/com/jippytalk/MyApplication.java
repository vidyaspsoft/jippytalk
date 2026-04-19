package com.jippytalk;

import android.app.Application;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteException;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ProcessLifecycleOwner;
// NOTE: do NOT import BuildConfig here. Android Studio's auto-import will
// replace it with androidx.multidex.BuildConfig (where DEBUG is always false),
// silently breaking debugExportMessagesDb(). The code below uses the
// fully-qualified name com.jippytalk.BuildConfig.DEBUG instead.

import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.jippytalk.BroadCastReceivers.ScreenOnOffChecker;
import com.jippytalk.CryptoKeys.KeyStoreGeneration;
import com.jippytalk.CryptoKeys.KeyStoreHelper;
import com.jippytalk.Database.ContactsDatabase.ContactsDatabase;
import com.jippytalk.Database.MessagesDatabase.MessagesDatabase;
import com.jippytalk.Database.SessionDatabase.SessionsDatabase;
import com.jippytalk.Database.User.UsersDatabase;
import com.jippytalk.Encryption.MessageEncryptAndDecrypt;
import com.jippytalk.Managers.AccountManager;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.WebSocketConnection;
import com.jippytalk.ServiceLocators.APIServiceLocator;
import com.jippytalk.ServiceLocators.AppServiceLocator;
import com.jippytalk.ServiceLocators.DatabaseServiceLocator;
import com.jippytalk.ServiceLocators.RepositoryServiceLocator;

import java.security.SecureRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyApplication extends Application {

    private static MyApplication            myApplication;
    public DatabaseServiceLocator           databaseServiceLocator;
    public RepositoryServiceLocator         repositoryServiceLocator;
    public APIServiceLocator                apiServiceLocator;
    public AppServiceLocator                appServiceLocator;
    private SharedPreferences               sharedPreferences;
    private ScreenOnOffChecker              screenOnOffChecker;
    private int                             registrationProcess;
    public static boolean                   isAppClosed         =   true;
    private final ExecutorService           executorService     =   Executors.newSingleThreadExecutor();
    private final MutableLiveData<Boolean>  isInitialized       =   new MutableLiveData<>(false);
    private final CountDownLatch            initLatch           =   new CountDownLatch(1);

    public static MyApplication getInstance() {
        return myApplication;
    }

    public LiveData<Boolean> getInitializationState() {
        return isInitialized;
    }

    public CountDownLatch getInitLatch() { return initLatch; }

    @Override
    public void onCreate() {
        super.onCreate();
        myApplication   =   this;

        executorService.execute(() -> {
            // 1️⃣ Generate key and password first
            try {
                KeyStoreGeneration.generateAESKeyIfNecessary();

                byte[] dbPassword   =   getOrCreateDatabaseKey();

                // 2️⃣ If the key had to be regenerated, the existing .db files
                // on disk are encrypted with the OLD key and are permanently
                // unreadable. Wipe them now so init creates fresh files with
                // the new key. Without this we get:
                //   SQLiteNotADatabaseException: hmac check failed for pgno=1
                if (dbKeyRegeneratedThisRun) {
                    Log.e(Extras.LOG_MESSAGE,
                            "⚠ DB key was regenerated — wiping stale encrypted DB files");
                    wipeStaleEncryptedDbFiles();
                }

                // 3️⃣ Initialize databases synchronously on background thread.
                //
                // Wrapper: open each DB with a retry-on-key-mismatch safety net.
                // If the .db file on disk was encrypted with a key that no
                // longer exists (for example: the Keystore AES entry was
                // invalidated in a previous run and we minted a fresh key),
                // the initial getReadableDb() call throws
                // SQLiteNotADatabaseException: "hmac check failed". When that
                // happens we wipe THAT specific DB file + its WAL/SHM sidecars
                // and retry once. Subsequent runs will find clean files.

                if (dbPassword != null) {
                    System.loadLibrary("sqlcipher");
                    Log.i(Extras.LOG_MESSAGE, "Initializing databases...");

                    final byte[] pwd = dbPassword;
                    openDbWithRecovery("usersEncryptedDatabase.db", () -> {
                        UsersDatabase.initialize(this, pwd);
                        UsersDatabase.getInstance(this).getReadableDb();
                    });
                    openDbWithRecovery("contacts.db", () -> {
                        ContactsDatabase.initialize(this, pwd);
                        ContactsDatabase.getInstance(this).getReadableDb();
                    });
                    openDbWithRecovery("messages.db", () -> {
                        MessagesDatabase.initialize(this, pwd);
                        MessagesDatabase.getInstance(this).getReadableDb();
                    });
                    openDbWithRecovery("sessions.db", () -> {
                        SessionsDatabase.initialize(this, pwd);
                        SessionsDatabase.getInstance(this).getReadableDb();
                    });
                    Log.i(Extras.LOG_MESSAGE, "All databases initialized successfully.");
                } else {
                    // getOrCreateDatabaseKey() failed (KeyStore edge-case on
                    // fresh install, secure-lockscreen reset, encrypted-
                    // storage-upgrade, etc.). Logging loudly so the user
                    // sees WHY the app can't talk to its DBs. The verification
                    // queries + ServiceLocators below are guarded against
                    // this case so we don't crash — but the app will not
                    // function correctly until the key can be regenerated.
                    Log.e(Extras.LOG_MESSAGE,
                            "FATAL: dbPassword is null — database initialization "
                            + "skipped. Check getOrCreateDatabaseKey() / KeyStore state. "
                            + "Possible remedies: reinstall the app, or verify "
                            + "KeyStoreHelper.decrypt() is returning valid bytes.");
                }

                databaseServiceLocator      =   new DatabaseServiceLocator(getApplicationContext());
                repositoryServiceLocator    =   new RepositoryServiceLocator(getApplicationContext(), databaseServiceLocator);
                apiServiceLocator           =   new APIServiceLocator(getApplicationContext(), repositoryServiceLocator);
                appServiceLocator           =   new AppServiceLocator(getApplicationContext(), databaseServiceLocator,
                                                repositoryServiceLocator, apiServiceLocator);
                apiServiceLocator.setAppServiceLocator(appServiceLocator);

                // 4️⃣ Verify DBs respond — but only if they were initialized.
                // If dbPassword was null above, getInstance() returns null and
                // calling getReadableDb() on null throws NPE. Guard defensively
                // so the app can surface the error without a hard crash.
                MessagesDatabase mdb = MessagesDatabase.getInstance(getApplicationContext());
                ContactsDatabase cdb = ContactsDatabase.getInstance(getApplicationContext());
                if (mdb != null) {
                    try (android.database.Cursor c = mdb.getReadableDb()
                            .rawQuery("SELECT 1", null)) {
                        // auto-close
                    } catch (Exception e) {
                        Log.e(Extras.LOG_MESSAGE,
                                "messages DB verification query failed: " + e.getMessage());
                    }
                } else {
                    Log.e(Extras.LOG_MESSAGE,
                            "Skipping messages DB verification — MessagesDatabase not initialized");
                }
                if (cdb != null) {
                    try (android.database.Cursor c = cdb.getReadableDatabase()
                            .rawQuery("SELECT 1", null)) {
                        // auto-close
                    } catch (Exception e) {
                        Log.e(Extras.LOG_MESSAGE,
                                "contacts DB verification query failed: " + e.getMessage());
                    }
                } else {
                    Log.e(Extras.LOG_MESSAGE,
                            "Skipping contacts DB verification — ContactsDatabase not initialized");
                }

                // 5️⃣ Apply theme and set crashlytics info
                sharedPreferences   =   repositoryServiceLocator.getSharedPreferences();
                String userId       =   sharedPreferences.getString(SharedPreferenceDetails.USERID, "");
                try {
                    FirebaseCrashlytics.getInstance().setUserId(userId);
                } catch (Exception e) {
                    Log.e(Extras.LOG_MESSAGE, "Firebase not initialized yet, skipping crashlytics " + e.getMessage());
                }
                applyTheme(sharedPreferences);

                // 6️⃣ Notify ready
                isInitialized.postValue(true);

                new Handler(Looper.getMainLooper()).post(() -> {
                    ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
                        @Override
                        public void onStop(@NonNull LifecycleOwner owner) {
                            DefaultLifecycleObserver.super.onStop(owner);
                            if (appServiceLocator != null && appServiceLocator.getWebSocketConnection() != null
                                    && WebSocketConnection.isConnectedToSocket) {
                                appServiceLocator.getWebSocketConnection().disconnectWebSocket();
                            }
                            isAppClosed =   true;
                            Log.e(Extras.LOG_MESSAGE,"unable to disconnect to socket due to null ");
                        }

                        @Override
                        public void onStart(@NonNull LifecycleOwner owner) {
                            DefaultLifecycleObserver.super.onStart(owner);
                            isAppClosed =   false;
                            registrationProcess = sharedPreferences.getInt(
                                    SharedPreferenceDetails.REGISTRATION_PROGRESS,
                                    AccountManager.INITIAL_SCREENS
                            );

                            if (appServiceLocator == null) {
                                Log.e(Extras.LOG_MESSAGE, "appServiceLocator is null");
                                return;
                            }

                            WebSocketConnection socket = appServiceLocator.getWebSocketConnection();
                            if (socket == null) {
                                Log.e(Extras.LOG_MESSAGE, "WebSocketConnection is null");
                                return;
                            }

                            if (WebSocketConnection.isConnectedToSocket) {
                                Log.e(Extras.LOG_MESSAGE, "Socket already connected");
                                return;
                            }

                            if (registrationProcess == AccountManager.REGISTRATION_DONE) {
                                socket.connectToWebSocket();
                            }
                        }

                        @Override
                        public void onPause(@NonNull LifecycleOwner owner) {
                            DefaultLifecycleObserver.super.onPause(owner);
                            isAppClosed =   true;
                            Log.e(Extras.LOG_MESSAGE,"on pause application");
                        }

                        @Override
                        public void onDestroy(@NonNull LifecycleOwner owner) {
                            DefaultLifecycleObserver.super.onDestroy(owner);
                            Log.e(Extras.LOG_MESSAGE,"on destroy application");
                        }
                    });
                });

                initLatch.countDown();
                Log.i(Extras.LOG_MESSAGE, "Application fully initialized.");
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Initialization failed", e);
                isInitialized.postValue(false);
                initLatch.countDown();
            }
        });

        screenOnOffChecker  =   new ScreenOnOffChecker(new ScreenOnOffChecker.ScreenListener() {
            @Override
            public void onScreenOff() {
                if (appServiceLocator != null && appServiceLocator.getWebSocketConnection() != null
                        && WebSocketConnection.isConnectedToSocket) {
                    appServiceLocator.getWebSocketConnection().disconnectWebSocket();
                }
            }

            @Override
            public void onScreenOn() {
                if (appServiceLocator != null && appServiceLocator.getWebSocketConnection() != null
                        && !WebSocketConnection.isConnectedToSocket && !isAppClosed) {
                    appServiceLocator.getWebSocketConnection().connectToWebSocket();
                } else {
                    Log.e(Extras.LOG_MESSAGE,"unable to connect to socket due to null ");
                }
            }

            @Override
            public void onUserPresent() {
                if (appServiceLocator != null && appServiceLocator.getWebSocketConnection() != null
                        && !WebSocketConnection.isConnectedToSocket && !isAppClosed) {
                    appServiceLocator.getWebSocketConnection().connectToWebSocket();
                } else {
                    Log.e(Extras.LOG_MESSAGE,"unable to connect to socket due to null ");
                }
            }
        });
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenOnOffChecker, filter);


    }

    private void applyTheme(SharedPreferences sharedPreferences) {
        new Handler(Looper.getMainLooper()).post(() -> {
            int appTheme = sharedPreferences.getInt(SharedPreferenceDetails.APP_THEME, AccountManager.SYSTEM_DEFAULT_THEME);
            switch (appTheme) {
                case AccountManager.SYSTEM_DEFAULT_THEME ->
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                case AccountManager.NIGHT_THEME ->
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                case AccountManager.LIGHT_THEME ->
                        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });
    }

    /**
     * Flipped to true by getOrCreateDatabaseKey() when the stored encrypted
     * key was unrecoverable (KeyStore AES key invalidated / missing / decrypt
     * threw) and a BRAND-NEW random key had to be minted. When true, any
     * pre-existing .db files on disk were encrypted with the OLD key and are
     * now permanently unreadable — onCreate() wipes them before init so the
     * app can start fresh with the new key.
     */
    private volatile boolean dbKeyRegeneratedThisRun = false;

    private byte[] getOrCreateDatabaseKey() {
        SharedPreferences   prefs       =   getSharedPreferences(SharedPreferenceDetails.SHARED_PREFERENCE_NAME, MODE_PRIVATE);
        String              storedKey   =   prefs.getString(SharedPreferenceDetails.DATABASE_PASSWORD, null);

        // ------ Path 1: decrypt an existing stored key ------
        if (storedKey != null && !storedKey.isEmpty()) {
            try {
                byte[] encryptedBytes = Base64.decode(storedKey, Base64.NO_WRAP);
                byte[] decrypted      = KeyStoreHelper.decrypt(encryptedBytes);
                if (decrypted == null || decrypted.length == 0) {
                    Log.e(Extras.LOG_MESSAGE,
                            "getOrCreateDatabaseKey: KeyStoreHelper.decrypt returned "
                            + "null/empty for stored key (length " + encryptedBytes.length
                            + " bytes). KeyStore AES key may have been invalidated. "
                            + "Regenerating key — local DBs will be wiped.");
                    dbKeyRegeneratedThisRun = true;
                    // fall through to regenerate
                } else {
                    return decrypted;  // happy path — existing key restored
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,
                        "getOrCreateDatabaseKey: decrypt of stored key failed — "
                        + e.getClass().getSimpleName() + ": " + e.getMessage()
                        + ". Regenerating key — local DBs will be wiped.", e);
                dbKeyRegeneratedThisRun = true;
                // fall through to regenerate
            }
        } else {
            Log.i(Extras.LOG_MESSAGE,
                    "getOrCreateDatabaseKey: no stored key — first run, generating");
            // First-run: no existing DB files to worry about, leave flag false
        }

        // ------ Path 2: generate a fresh key ------
        try {
            byte[] randomKey = new byte[32];
            new SecureRandom().nextBytes(randomKey);

            byte[] encryptedKey = KeyStoreHelper.encrypt(randomKey);
            if (encryptedKey == null || encryptedKey.length == 0) {
                Log.e(Extras.LOG_MESSAGE,
                        "getOrCreateDatabaseKey: KeyStoreHelper.encrypt returned "
                        + "null/empty — cannot persist the new key. "
                        + "Has KeyStoreGeneration.generateAESKeyIfNecessary() run?");
                return null;
            }

            prefs.edit().putString(SharedPreferenceDetails.DATABASE_PASSWORD,
                            Base64.encodeToString(encryptedKey, Base64.NO_WRAP))
                    .apply();

            Log.i(Extras.LOG_MESSAGE,
                    "getOrCreateDatabaseKey: new 32-byte key generated and persisted"
                    + (dbKeyRegeneratedThisRun
                        ? " (REGENERATION — stale encrypted DBs will be wiped)"
                        : ""));
            return randomKey;
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,
                    "getOrCreateDatabaseKey: key generation failed — "
                    + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * Opens a SQLCipher database with a one-shot self-heal on key mismatch.
     *
     * If the open throws SQLiteNotADatabaseException (SQLite code 26 / HMAC
     * check failure), the file on disk was encrypted with a key that no
     * longer exists — impossible to read. We delete THAT database's files
     * (main + WAL + SHM + journal) and run the opener again. The second call
     * will create a fresh empty file with the current key.
     *
     * Any other exception is logged and re-thrown so we don't silently mask
     * unrelated failures.
     */
    private void openDbWithRecovery(String dbFileName, Runnable opener) {
        try {
            opener.run();
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root != root.getCause()) {
                root = root.getCause();
            }
            boolean isKeyMismatch =
                    root instanceof SQLiteException
                    || (root.getMessage() != null
                        && (root.getMessage().contains("file is not a database")
                            || root.getMessage().contains("hmac check failed")));

            if (!isKeyMismatch) {
                Log.e(Extras.LOG_MESSAGE,
                        "openDbWithRecovery: " + dbFileName + " failed with a NON-key error — "
                        + root.getClass().getSimpleName() + ": " + root.getMessage(), e);
                throw new RuntimeException(e);
            }

            Log.e(Extras.LOG_MESSAGE,
                    "openDbWithRecovery: " + dbFileName + " is encrypted with a "
                    + "different key than we have now (" + root.getClass().getSimpleName()
                    + "). Wiping and recreating.");

            // Delete this DB's files + sidecars
            java.io.File base = getDatabasePath(dbFileName);
            for (String suffix : new String[]{"", "-wal", "-shm", "-journal"}) {
                java.io.File f = suffix.isEmpty()
                        ? base
                        : new java.io.File(base.getAbsolutePath() + suffix);
                if (f.exists()) {
                    boolean ok = f.delete();
                    Log.e(Extras.LOG_MESSAGE,
                            "openDbWithRecovery: delete " + f.getName()
                            + " → " + (ok ? "ok" : "FAILED"));
                }
            }

            // Retry once — the file is now gone, so initialize() will create
            // a fresh empty DB with the current key.
            try {
                opener.run();
                Log.i(Extras.LOG_MESSAGE,
                        "openDbWithRecovery: " + dbFileName + " recovered — "
                        + "fresh empty DB created with current key");
            } catch (Exception retry) {
                Log.e(Extras.LOG_MESSAGE,
                        "openDbWithRecovery: " + dbFileName + " still failed after wipe — "
                        + retry.getMessage(), retry);
                throw new RuntimeException(retry);
            }
        }
    }

    /**
     * Deletes the four SQLCipher DB files + their WAL/SHM/journal sidecars
     * from the app's internal /databases/ folder. Called when
     * dbKeyRegeneratedThisRun is true — the files on disk were encrypted with
     * the previous key and are permanently unreadable with the new one.
     *
     * ⚠ THIS PERMANENTLY DELETES all local chat history, contacts, Signal
     * sessions, and user profile from this device. The app will start with
     * empty databases. Data previously synced to the server (messages,
     * contacts) will be re-fetched on next open; data that was ONLY local
     * (Signal session state, draft messages) is lost.
     */
    private void wipeStaleEncryptedDbFiles() {
        String[] dbNames = {
                "messages.db",
                "usersEncryptedDatabase.db",
                "contacts.db",
                "sessions.db",
                "messages_plain.db"   // also wipe the debug plaintext mirror
        };
        String[] sidecars = {"", "-wal", "-shm", "-journal"};
        int deleted = 0;
        for (String name : dbNames) {
            java.io.File base = getDatabasePath(name);
            for (String suffix : sidecars) {
                java.io.File f = suffix.isEmpty()
                        ? base
                        : new java.io.File(base.getAbsolutePath() + suffix);
                if (f.exists()) {
                    if (f.delete()) {
                        deleted++;
                        Log.e(Extras.LOG_MESSAGE,
                                "wipeStaleEncryptedDbFiles: deleted " + f.getName());
                    } else {
                        Log.e(Extras.LOG_MESSAGE,
                                "wipeStaleEncryptedDbFiles: FAILED to delete " + f.getName());
                    }
                }
            }
        }
        Log.i(Extras.LOG_MESSAGE,
                "wipeStaleEncryptedDbFiles: " + deleted + " files removed. "
                + "Databases will be recreated empty on next open.");
    }

    // DEBUG: keep a live handle to the plaintext export so Android Studio's
    // Database Inspector can discover it. The Inspector only shows databases
    // that are currently OPEN via android.database.sqlite.SQLiteDatabase —
    // the framework class. JippyTalk's real databases use
    // net.zetetic.database.sqlcipher.SQLiteDatabase (SQLCipher) which is a
    // different, unrelated class hierarchy → the Inspector cannot see them
    // AT ALL. The workaround is to re-open the plaintext export below using
    // the framework class and hold the handle alive in a static field.
    private static android.database.sqlite.SQLiteDatabase debugPlainDbHandle;
    private static android.database.sqlite.SQLiteOpenHelper debugPlainDbHelper;

    /**
     * DEBUG ONLY: Exports an unencrypted, plaintext copy of messages.db and
     * opens it via framework SQLite so it shows up in Android Studio's
     * Database Inspector.
     *
     * Output path:
     *   /data/data/com.jippytalk/databases/messages_plain.db
     *
     * Flow:
     *   1. Force a WAL checkpoint so pending writes sitting in messages.db-wal
     *      are merged into the main .db file before we export.
     *   2. Close any previously-opened plaintext DB handle (to release the
     *      file lock from a prior export run).
     *   3. Delete the old plaintext copy + its -wal / -shm sidecars.
     *   4. ATTACH a fresh plaintext DB to the SQLCipher connection with an
     *      empty key and run SELECT sqlcipher_export('plain') — this copies
     *      every row of every table into the attached file.
     *   5. DETACH. The attached file is now a plain SQLite database.
     *   6. Re-open it via android.database.sqlite.SQLiteOpenHelper (the
     *      FRAMEWORK class) and keep the handle alive. This is what makes
     *      Database Inspector's auto-discovery pick it up.
     *
     * Gated on com.jippytalk.BuildConfig.DEBUG — a no-op in release builds.
     * The class is referenced with its FULLY QUALIFIED name on purpose:
     * Android Studio's auto-import tends to replace a bare "BuildConfig"
     * with androidx.multidex.BuildConfig, where DEBUG is always false.
     */
    public void debugExportMessagesDb() {
        // Release-build safety: the plaintext DB mirror is a data-leak risk
        // in production, so we hard-bail in non-debug builds. In debug builds
        // BuildConfig.DEBUG == true, so this early-return is skipped and the
        // export runs normally. Do NOT remove this line.
    //    if (!BuildConfig.DEBUG) return;
        executorService.execute(() -> {
            try {
                java.io.File outFile = getDatabasePath("messages_plain.db");
                java.io.File outDir  = outFile.getParentFile();
                if (outDir != null && !outDir.exists()) {
                    outDir.mkdirs();
                }

                MessagesDatabase db = MessagesDatabase.getInstance(this);
                if (db == null) return;
                net.zetetic.database.sqlcipher.SQLiteDatabase src = db.getWritableDb();

                // Force a WAL checkpoint so pending writes in messages.db-wal
                // are merged into the main file before we export.
                try { src.rawExecSQL("PRAGMA wal_checkpoint(FULL)"); } catch (Exception ignored) {}

                // SQLCipher Android uses an internal connection pool. Sequential
                // rawExecSQL calls can land on different connections, and schema
                // attachments (ATTACH DATABASE ... AS plain) are connection-local
                // — so ATTACH on connection A followed by sqlcipher_export that
                // routes to connection B fails with "unknown database plain".
                //
                // Workaround: retry up to 3 times. Each attempt deletes the
                // target file, attaches + probes + exports + verifies, and only
                // considers success when the output file has >= 2 tables and is
                // non-empty. In practice, one of 3 attempts usually hits the
                // same connection for both calls.
                final String escapedPath = outFile.getAbsolutePath().replace("'", "''");
                boolean exportOk      =   false;
                int     successAttempt =  0;
                long    exportedRows   =  -1L;

                for (int attempt = 1; attempt <= 3 && !exportOk; attempt++) {
                    // Close any existing framework handle before we modify the file.
                    closeDebugPlainDbHandle();

                    // Wipe target file + SQLite sidecars — ATTACH below will
                    // create a fresh empty DB.
                    deleteDbFile(outFile);
                    deleteDbFile(new java.io.File(outFile.getAbsolutePath() + "-wal"));
                    deleteDbFile(new java.io.File(outFile.getAbsolutePath() + "-shm"));
                    deleteDbFile(new java.io.File(outFile.getAbsolutePath() + "-journal"));

                    // Best-effort DETACH of any stale 'plain' from a prior run.
                    try { src.rawExecSQL("DETACH DATABASE plain"); } catch (Exception ignored) {}

                    try {
                        // ATTACH the empty plaintext file. rawExecSQL does NOT
                        // throw on aborted statements (SQLite "statement aborts"
                        // only logs) — we verify via probe.
                        src.rawExecSQL("ATTACH DATABASE '" + escapedPath + "' AS plain KEY ''");

                        // Probe to confirm this specific connection actually
                        // has 'plain' attached.
                        boolean attachedOk = false;
                        try (android.database.Cursor probe = src.rawQuery(
                                "SELECT 1 FROM plain.sqlite_master LIMIT 1", null)) {
                            attachedOk = (probe != null);
                        } catch (Exception ignored) {}

                        if (!attachedOk) {
                            try { src.rawExecSQL("DETACH DATABASE plain"); } catch (Exception ignored) {}
                            try { Thread.sleep(30); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                            continue;
                        }

                        // Export — use rawQuery to force the SELECT to actually
                        // evaluate and have a chance of staying on the same
                        // connection via cursor scoping.
                        try (android.database.Cursor c = src.rawQuery(
                                "SELECT sqlcipher_export('plain')", null)) {
                            if (c != null) c.moveToFirst();
                        } catch (Exception ignored) {}

                        // Verify: at least the 'messages' + 'chat_list' tables
                        // should now exist in 'plain', and the file should be
                        // non-empty on disk.
                        int tableCount = 0;
                        try (android.database.Cursor c = src.rawQuery(
                                "SELECT COUNT(*) FROM plain.sqlite_master WHERE type='table'", null)) {
                            if (c != null && c.moveToFirst()) tableCount = c.getInt(0);
                        } catch (Exception ignored) {}

                        long rowCount = -1L;
                        if (tableCount >= 2) {
                            try (android.database.Cursor c = src.rawQuery(
                                    "SELECT COUNT(*) FROM plain.messages", null)) {
                                if (c != null && c.moveToFirst()) rowCount = c.getLong(0);
                            } catch (Exception ignored) {}
                        }

                        // Must DETACH before checking on-disk size — unattached
                        // WAL pages aren't flushed otherwise.
                        try { src.rawExecSQL("DETACH DATABASE plain"); } catch (Exception ignored) {}

                        if (tableCount >= 2 && rowCount >= 0
                                && outFile.exists() && outFile.length() > 0) {
                            exportOk       = true;
                            successAttempt = attempt;
                            exportedRows   = rowCount;
                        } else {
                            // Retry
                            try { Thread.sleep(30); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                        }
                    } catch (Exception e) {
                        try { src.rawExecSQL("DETACH DATABASE plain"); } catch (Exception ignored) {}
                        Log.w(Extras.LOG_MESSAGE,
                                "debugExportMessagesDb attempt " + attempt
                                + " failed: " + e.getMessage());
                        try { Thread.sleep(30); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                }

                if (!exportOk) {
                    // All 3 attempts failed — keep the old (possibly stale)
                    // framework handle if it was valid, otherwise just bail.
                    Log.w(Extras.LOG_MESSAGE,
                            "debugExportMessagesDb: all 3 attempts failed — "
                            + "Inspector will show stale data (app is fine)");
                    return;
                }

                // Re-open the plaintext file via FRAMEWORK SQLite so Database
                // Inspector can see it.
                debugPlainDbHelper = new android.database.sqlite.SQLiteOpenHelper(
                        MyApplication.this, "messages_plain.db", null, 1) {
                    @Override public void onCreate(android.database.sqlite.SQLiteDatabase db) {}
                    @Override public void onUpgrade(android.database.sqlite.SQLiteDatabase db,
                                                    int oldVersion, int newVersion) {}
                };
                debugPlainDbHandle = debugPlainDbHelper.getReadableDatabase();

                long sizeKb = outFile.length() / 1024;
                Log.i(Extras.LOG_MESSAGE,
                        "debugExportMessagesDb: success"
                        + " (rows=" + exportedRows
                        + ", size=" + sizeKb + " KB"
                        + (successAttempt > 1 ? ", attempt=" + successAttempt : "")
                        + "). Database Inspector → 'messages_plain.db'");
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,
                        "debugExportMessagesDb failed: " + e.getMessage(), e);
            }
        });
    }

    // ==================================================================
    //  Media post-upload pipeline (shared between MessagingActivity and
    //  MediaUploadWorker so both paths produce identical DB state + WS
    //  messages). The activity calls this from its onUploadComplete
    //  listener; the worker calls it from its doWork() after the upload
    //  succeeds. Both produce: thumbnail gen → thumb upload → WS send →
    //  DB rows updated with local paths + S3 info.
    // ==================================================================

    /**
     * Runs the post-upload sequence for a media message that has just finished
     * uploading to S3 — generates a thumbnail, encrypts+uploads it, sends the
     * WebSocket `file` message, and persists the S3 key/bucket + local file
     * path + local thumbnail path onto the DB row.
     *
     * Safe to call from any thread. All heavy work happens on an internal
     * executor so the caller doesn't block. If the WebSocket is disconnected
     * when this runs, the WS layer queues the send via pendingOutboundSends
     * and flushes on next reconnect — no explicit retry needed here.
     *
     * Preserves every behavior that MessagingActivity.onUploadComplete had:
     * thumbnail rendering is main-thread-safe, encryption uses per-message
     * AES-GCM keys on the AttachmentModel, local file and thumbnail paths
     * go into the dedicated v8 columns so the adapter can load them via
     * Glide async.
     */
    public void performMediaPostUploadPipeline(String messageId, String s3Key,
                                               String bucket,
                                               com.jippytalk.Messages.Attachment.Model.AttachmentModel model) {
        if (messageId == null || model == null) {
            Log.e(Extras.LOG_MESSAGE, "performMediaPostUploadPipeline: null input — skipping");
            return;
        }
        final long fileSize;
        long computedSize = 0;
        if (model.getLocalFilePath() != null) {
            java.io.File localFile = new java.io.File(model.getLocalFilePath());
            if (localFile.exists()) computedSize = localFile.length();
        }
        fileSize = computedSize;

        executorService.execute(() -> {
            try {
                String thumbnailS3Key = "";
                // Reuse the sender-side local thumbnail if the activity (or a
                // prior worker run) already generated one. Avoids regenerating
                // on every retry and keeps sender's chat preview in sync with
                // what the receiver will decrypt from S3.
                java.io.File thumbFile = readExistingLocalThumbnail(messageId);
                if (thumbFile == null) {
                    thumbFile = com.jippytalk.Messages.Attachment.ThumbnailGenerator
                            .generateThumbnail(MyApplication.this,
                                    model.getLocalFilePath(), model.getContentType());
                    if (thumbFile != null && thumbFile.exists()) {
                        // Sender's local plaintext thumb — adapter loads via Glide
                        persistThumbnailPathOnRowInternal(messageId,
                                thumbFile.getAbsolutePath());
                    }
                }
                if (thumbFile != null && thumbFile.exists()) {
                    // Upload encrypted thumbnail for the receiver
                    thumbnailS3Key = uploadThumbnailSyncInternal(thumbFile, model.getName(),
                            model.getEncryptionKey(), model.getEncryptionIv());
                    Log.e(Extras.LOG_MESSAGE, "Thumbnail uploaded: " + thumbnailS3Key);
                }
                model.setThumbnail(thumbnailS3Key);

                // Send WS file message. If socket is down,
                // WebSocketConnection.pendingOutboundSends queues it and
                // flushes on reconnect — survives offline.
                final String fThumbS3Key = thumbnailS3Key;
                WebSocketConnection ws = appServiceLocator != null
                        ? appServiceLocator.getWebSocketConnection() : null;
                if (ws != null) {
                    String contactId = model.getMedia() != null
                            ? "" : "";
                    // The receiver ID is carried on the model via its caller —
                    // for the worker/activity we need to read it from the DB
                    // row since AttachmentModel doesn't own it. The DAO's
                    // getMessageDetailsFromId gives us the receiver_id column.
                    String receiverId = readReceiverIdForMessage(messageId);
                    if (receiverId != null && !receiverId.isEmpty()) {
                        ws.sendFileMessage(
                                messageId,
                                receiverId,
                                "",   // ciphertext (encryption happens inside sendFileMessage)
                                "",   // encrypted_s3_url
                                s3Key,
                                model.getName(),
                                fileSize,
                                model.getContentType(),
                                model.getContentSubtype(),
                                model.getCaption(),
                                model.getWidth(),
                                model.getHeight(),
                                model.getDuration(),
                                model.getThumbnail(),
                                bucket != null ? bucket : model.getBucket(),
                                model.getEncryptionKey(),
                                model.getEncryptionIv()
                        );
                    } else {
                        Log.e(Extras.LOG_MESSAGE,
                                "performMediaPostUploadPipeline: no receiver_id for "
                                + messageId + " — WS send skipped");
                    }
                } else {
                    Log.e(Extras.LOG_MESSAGE,
                            "performMediaPostUploadPipeline: WS not initialized — send deferred");
                }

                if (model.getLocalFilePath() != null && !model.getLocalFilePath().isEmpty()) {
                    persistLocalFilePathOnRowInternal(messageId, model.getLocalFilePath());
                }
                persistMediaUploadResultOnRowInternal(messageId, s3Key, bucket, fThumbS3Key);
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,
                        "performMediaPostUploadPipeline failed: " + e.getMessage(), e);
            }
        });
    }

    /** Reads the receiver_id column for a given message id. */
    private String readReceiverIdForMessage(String messageId) {
        try {
            com.jippytalk.Database.MessagesDatabase.MessagesDatabaseDAO dao =
                    databaseServiceLocator.getMessagesDatabaseDAO();
            try (android.database.Cursor c = dao.getMessageDetailsFromId(messageId)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(
                            com.jippytalk.Database.MessagesDatabase.MessagesDatabase.RECEIVER_ID);
                    if (idx >= 0) return c.getString(idx);
                }
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "readReceiverIdForMessage: " + e.getMessage());
        }
        return null;
    }

    /**
     * Returns the existing local thumbnail file for a message if one has
     * already been generated (e.g. by the activity's early-thumbnail path).
     * Returns null if no path is stored OR the file no longer exists.
     */
    private java.io.File readExistingLocalThumbnail(String messageId) {
        try {
            com.jippytalk.Database.MessagesDatabase.MessagesDatabaseDAO dao =
                    databaseServiceLocator.getMessagesDatabaseDAO();
            try (android.database.Cursor c = dao.getMessageDetailsFromId(messageId)) {
                if (c != null && c.moveToFirst()) {
                    int idx = c.getColumnIndex(
                            com.jippytalk.Database.MessagesDatabase.MessagesDatabase
                                    .LOCAL_THUMBNAIL_PATH);
                    if (idx >= 0) {
                        String path = c.getString(idx);
                        if (path != null && !path.isEmpty()) {
                            java.io.File f = new java.io.File(path);
                            if (f.exists() && f.length() > 0) return f;
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "readExistingLocalThumbnail: " + e.getMessage());
        }
        return null;
    }

    /** Background-thread-safe version of MessagingActivity.persistThumbnailPathOnRow. */
    private void persistThumbnailPathOnRowInternal(String messageId, String path) {
        try {
            databaseServiceLocator.getMessagesDatabaseDAO()
                    .updateLocalThumbnailPath(messageId, path);
            Log.e(Extras.LOG_MESSAGE, "persistThumbnailPath: updated " + messageId + " → " + path);
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "persistThumbnailPath internal: " + e.getMessage());
        }
    }

    /** Background-thread-safe version of MessagingActivity.persistLocalFilePathOnRow. */
    private void persistLocalFilePathOnRowInternal(String messageId, String path) {
        try {
            databaseServiceLocator.getMessagesDatabaseDAO()
                    .updateLocalFilePath(messageId, path);
            Log.e(Extras.LOG_MESSAGE, "persistLocalFilePath: updated " + messageId + " → " + path);
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "persistLocalFilePath internal: " + e.getMessage());
        }
    }

    /** Background-thread-safe version of MessagingActivity.persistMediaUploadResultOnRow. */
    private void persistMediaUploadResultOnRowInternal(String messageId, String s3Key,
                                                       String bucket, String thumbnailS3Key) {
        try {
            com.jippytalk.Database.MessagesDatabase.MessagesDatabaseDAO dao =
                    databaseServiceLocator.getMessagesDatabaseDAO();
            String bucketName = bucket != null && !bucket.isEmpty()
                    ? bucket : com.jippytalk.API.S3_BUCKET;
            String fileUrl = s3Key != null && !s3Key.isEmpty()
                    ? "https://" + bucketName + ".s3."
                            + com.jippytalk.API.S3_REGION + ".amazonaws.com/" + s3Key
                    : "";
            dao.updateMessageS3Info(messageId, s3Key != null ? s3Key : "", bucketName, fileUrl);
            if (thumbnailS3Key != null && !thumbnailS3Key.isEmpty()) {
                String thumbUrl = "https://" + bucketName + ".s3."
                        + com.jippytalk.API.S3_REGION + ".amazonaws.com/" + thumbnailS3Key;
                dao.updateRemoteThumbnailUrl(messageId, thumbUrl);
            }
            Log.e(Extras.LOG_MESSAGE, "persistMediaUploadResult: " + messageId
                    + " s3_key=" + s3Key + " thumbKey=" + thumbnailS3Key);
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "persistMediaUploadResult internal: " + e.getMessage());
        }
    }

    /**
     * Background-thread thumbnail upload — same logic as
     * MessagingActivity.uploadThumbnailSync but uses the app-level shared
     * preferences for the JWT token. Returns the S3 key on success, empty on failure.
     */
    private String uploadThumbnailSyncInternal(java.io.File thumbFile, String originalName,
                                               String b64Key, String b64Iv) {
        java.io.File encThumbFile = null;
        try {
            String jwtToken = getSharedPreferences(
                    com.jippytalk.Managers.SharedPreferenceDetails.SHARED_PREFERENCE_NAME,
                    MODE_PRIVATE)
                    .getString(com.jippytalk.Managers.SharedPreferenceDetails.JWT_TOKEN, "");
            String thumbName = "thumb_" + System.currentTimeMillis()
                    + "_" + (originalName != null ? originalName : "file") + ".png";

            java.io.File uploadFile = thumbFile;
            if (b64Key != null && !b64Key.isEmpty() && b64Iv != null && !b64Iv.isEmpty()) {
                encThumbFile = new java.io.File(getCacheDir(),
                        "enc_thumb_" + System.currentTimeMillis() + ".bin");
                try (java.io.FileInputStream pin = new java.io.FileInputStream(thumbFile);
                     java.io.FileOutputStream cout = new java.io.FileOutputStream(encThumbFile)) {
                    com.jippytalk.Encryption.MessageCryptoHelper.encryptStream(
                            pin, cout, b64Key, b64Iv);
                }
                uploadFile = encThumbFile;
                Log.e(Extras.LOG_MESSAGE, "Thumbnail encrypted: plaintext=" + thumbFile.length()
                        + " ciphertext=" + encThumbFile.length());
            }
            long uploadSize = uploadFile.length();

            java.net.URL presignUrl = new java.net.URL(com.jippytalk.API.FILES_PRESIGN);
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) presignUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + jwtToken);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);

            org.json.JSONObject body = new org.json.JSONObject();
            body.put("file_name", thumbName);
            body.put("file_size", uploadSize);
            try (java.io.OutputStreamWriter w = new java.io.OutputStreamWriter(conn.getOutputStream())) {
                w.write(body.toString()); w.flush();
            }
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) { conn.disconnect(); return ""; }

            StringBuilder resp = new StringBuilder();
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(conn.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) resp.append(line);
            }
            conn.disconnect();

            org.json.JSONObject presignResp = new org.json.JSONObject(resp.toString());
            String uploadUrl = presignResp.optString("upload_url",
                    presignResp.optString("presigned_url", ""));
            String thumbS3Key = presignResp.optString("s3_key", "");
            if (uploadUrl.isEmpty()) return "";

            java.net.HttpURLConnection s3Conn =
                    (java.net.HttpURLConnection) new java.net.URL(uploadUrl).openConnection();
            s3Conn.setRequestMethod("PUT");
            s3Conn.setDoOutput(true);
            s3Conn.setRequestProperty("Content-Type",
                    encThumbFile != null ? "application/octet-stream" : "image/png");
            s3Conn.setFixedLengthStreamingMode(uploadSize);
            s3Conn.setConnectTimeout(5000);
            s3Conn.setReadTimeout(30000);
            try (java.io.FileInputStream fis = new java.io.FileInputStream(uploadFile);
                 java.io.OutputStream os = s3Conn.getOutputStream()) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = fis.read(buf)) != -1) os.write(buf, 0, read);
                os.flush();
            }
            int s3Status = s3Conn.getResponseCode();
            s3Conn.disconnect();
            return s3Status == 200 ? thumbS3Key : "";
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Thumb upload failed (worker path): " + e.getMessage());
            return "";
        } finally {
            if (encThumbFile != null && encThumbFile.exists()) {
                try { encThumbFile.delete(); } catch (Exception ignored) {}
            }
        }
    }

    private static void closeDebugPlainDbHandle() {
        try {
            if (debugPlainDbHandle != null && debugPlainDbHandle.isOpen()) {
                debugPlainDbHandle.close();
            }
        } catch (Exception ignored) {}
        try {
            if (debugPlainDbHelper != null) {
                debugPlainDbHelper.close();
            }
        } catch (Exception ignored) {}
        debugPlainDbHandle = null;
        debugPlainDbHelper = null;
    }

    private static void deleteDbFile(java.io.File f) {
        try {
            if (f != null && f.exists()) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        } catch (Exception ignored) {}
    }

    public synchronized DatabaseServiceLocator getDatabaseServiceLocator() {
        return databaseServiceLocator;
    }

    public synchronized RepositoryServiceLocator getRepositoryServiceLocator() {
        return repositoryServiceLocator;
    }

    public synchronized APIServiceLocator getAPIServiceLocator() {
        return apiServiceLocator;
    }

    public synchronized AppServiceLocator getAppServiceLocator() {
        return appServiceLocator;
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        if (screenOnOffChecker != null) {
            unregisterReceiver(screenOnOffChecker);
        }
    }
}