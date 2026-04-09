package com.jippytalk;

import android.app.Application;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
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

                // 2️⃣ Create service locators *before* DB initialization to prevent null access

                // 3️⃣ Initialize databases synchronously on background thread

                if (dbPassword != null) {
                    System.loadLibrary("sqlcipher");
                    Log.i(Extras.LOG_MESSAGE, "Initializing databases...");
                    UsersDatabase.initialize(this, dbPassword);
                    UsersDatabase.getInstance(this).getReadableDb();
                    ContactsDatabase.initialize(this, dbPassword);
                    ContactsDatabase.getInstance(this).getReadableDb();
                    MessagesDatabase.initialize(this, dbPassword);
                    MessagesDatabase.getInstance(this).getReadableDb();
                    SessionsDatabase.initialize(this, dbPassword);
                    SessionsDatabase.getInstance(this).getReadableDb();
                    Log.i(Extras.LOG_MESSAGE, "All databases initialized successfully.");
                }

                databaseServiceLocator      =   new DatabaseServiceLocator(getApplicationContext());
                repositoryServiceLocator    =   new RepositoryServiceLocator(getApplicationContext(), databaseServiceLocator);
                apiServiceLocator           =   new APIServiceLocator(getApplicationContext(), repositoryServiceLocator);
                appServiceLocator           =   new AppServiceLocator(getApplicationContext(), databaseServiceLocator,
                                                repositoryServiceLocator, apiServiceLocator);
                apiServiceLocator.setAppServiceLocator(appServiceLocator);

                // 4️⃣ Verify DBs respond
                MessagesDatabase.getInstance(getApplicationContext()).getReadableDb().rawQuery("SELECT 1", null).close();
                ContactsDatabase.getInstance(getApplicationContext()).getReadableDatabase().rawQuery("SELECT 1", null).close();

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

    private byte[] getOrCreateDatabaseKey() {
        SharedPreferences   prefs       =   getSharedPreferences(SharedPreferenceDetails.SHARED_PREFERENCE_NAME, MODE_PRIVATE);
        String              storedKey   =   prefs.getString(SharedPreferenceDetails.DATABASE_PASSWORD, null);

        try {
            if (storedKey != null && !storedKey.isEmpty()) {
                // Decrypt existing key
                byte[] encryptedBytes = Base64.decode(storedKey, Base64.NO_WRAP);
                return KeyStoreHelper.decrypt(encryptedBytes); // returns raw bytes
            }

            //  Generate 256-bit (32 bytes) random key
            byte[] randomKey = new byte[32];
            new SecureRandom().nextBytes(randomKey);

            //  Encrypt key with Keystore AES
            byte[] encryptedKey = KeyStoreHelper.encrypt(randomKey);

            // Store encrypted key in SharedPreferences
            prefs.edit().putString(SharedPreferenceDetails.DATABASE_PASSWORD,
                            Base64.encodeToString(encryptedKey, Base64.NO_WRAP))
                    .apply();

            return randomKey; // raw key for SQLCipher
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to get/create DB key", e);
            return null;
        }
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