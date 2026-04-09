package com.jippytalk.Database.User;

import android.content.Context;
import android.util.Log;


import com.jippytalk.Database.SqliteDatabaseHook;
import com.jippytalk.Extras;

import net.zetetic.database.DefaultDatabaseErrorHandler;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

public class UsersDatabase extends SQLiteOpenHelper {

    private final Context                   context;
    private static volatile UsersDatabase   usersDatabase;
    public static final String              DB_NAME                         =   "usersEncryptedDatabase.db";
    // public static final int                 DATABASE_VERSION                =   3;
    public static final int                 DATABASE_VERSION                =   4;
    private SQLiteDatabase                  readableDb;
    private SQLiteDatabase                  writableDb;
    private static final SQLiteDatabaseHook databaseHook                    =   new SqliteDatabaseHook();

    public static final String              IDENTITY_KEYS_TABLE             =   "identity_keys";
    public static final String              IDENTITY_KEYS_SQLITE_ID         =   "identity_keys_sqlite_id";
    public static final String              IDENTITY_KEY_PRIVATE            =   "identity_key_private";
    public static final String              IDENTITY_KEY_PUBLIC             =   "identity_key_public";

    public static final String              SIGNED_KEYS_TABLE               =   "signed_keys_table";
    public static final String              SIGNED_KEYS_SQLITE_ID           =   "signed_keys_sqlite_id";
    public static final String              SIGNED_PRE_KEY_ID               =   "signed_pre_key_id";
    public static final String              SIGNED_PRE_KEY_SIGNATURE        =   "signed_pre_key_signature";
    public static final String              SIGNED_PUBLIC_KEY               =   "signed_public_key";
    public static final String              SIGNED_PRIVATE_KEY              =   "signed_private_key";

    public static final String              ONE_TIME_PRE_KEYS_TABLE         =   "one_time_pre_keys_table";
    public static final String              ONE_TIME_PRE_KEY_SQLITE_ID      =   "pre_key_sqlite_id";
    public static final String              ONE_TIME_PRE_KEY_ID             =   "one_time_pre_key_id";
    public static final String              ONE_TIME_PUBLIC_PRE_KEY         =   "one_time_public_pre_key";
    public static final String              ONE_TIME_PRIVATE_PRE_KEY        =   "one_time_private_pre_key";
    public static final String              STALE_TIMESTAMP                 =   "stale_timestamp";

    public static final String              KYBER_PRE_KEYS_TABLE            =   "kyber_pre_keys_table";
    public static final String              KYBER_PRE_KEY_SQLITE_ID         =   "kyber_pre_key_sqlite_id";
    public static final String              KYBER_PRE_KEY_ID                =   "kyber_pre_key_id";
    public static final String              KYBER_SERIALIZED_KEY            =   "kyber_serialized_record";
    public static final String              KYBER_KEY_TIMESTAMP             =   "kyber_key_timestamp";
    public static final String              IS_KYBER_KEY_USED               =   "is_kyber_key_used";

    public UsersDatabase(Context context, byte[] password) {
        super(context.getApplicationContext(), DB_NAME, password, null, DATABASE_VERSION,
                1, new DefaultDatabaseErrorHandler(), databaseHook, true);
        this.context        =   context.getApplicationContext();
        setWriteAheadLoggingEnabled(true);
    }


    public static void initialize(Context context, byte[] password) {
        if (usersDatabase == null) {
            synchronized (UsersDatabase.class) {
                if (usersDatabase == null) {
                    usersDatabase = new UsersDatabase(context.getApplicationContext(), password);
                }
            }
        }
    }


    public static UsersDatabase getInstance(Context context) {
        if (usersDatabase == null) {
            Log.e(Extras.LOG_MESSAGE,"usersDatabase is null and not defined");
        }
        return usersDatabase;
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.rawExecSQL("PRAGMA kdf_iter = 1;");
        db.execSQL("PRAGMA page_size = 4096;");
        db.enableWriteAheadLogging();
    }

    @Override
    public void onCreate(SQLiteDatabase sqLiteDatabase) {

        sqLiteDatabase.execSQL("PRAGMA page_size = 16384;");

        String  identityKeysTable   =   "CREATE TABLE " + IDENTITY_KEYS_TABLE + "("
                +   IDENTITY_KEYS_SQLITE_ID +   " INTEGER PRIMARY KEY AUTOINCREMENT, "
                +   IDENTITY_KEY_PUBLIC     +   " TEXT UNIQUE, "
                +   IDENTITY_KEY_PRIVATE    +   " TEXT " + ")";

        String  signedPreKeysTable  =   "CREATE TABLE " + SIGNED_KEYS_TABLE + "("
                +   SIGNED_KEYS_SQLITE_ID       +   " INTEGER PRIMARY KEY AUTOINCREMENT, "
                +   SIGNED_PRE_KEY_ID           +   " INTEGER UNIQUE, "
                +   SIGNED_PRE_KEY_SIGNATURE    +   " TEXT, "
                +   SIGNED_PUBLIC_KEY           +   " TEXT, "
                +   SIGNED_PRIVATE_KEY          +   " TEXT " + ")";

        String  preKeysTable        =   "CREATE TABLE " + ONE_TIME_PRE_KEYS_TABLE + "("
                +   ONE_TIME_PRE_KEY_SQLITE_ID  +   " INTEGER PRIMARY KEY AUTOINCREMENT, "
                +   ONE_TIME_PRE_KEY_ID         +   " VARCHAR UNIQUE, "
                +   ONE_TIME_PUBLIC_PRE_KEY     +   " TEXT,  "
                +   ONE_TIME_PRIVATE_PRE_KEY    +   " TEXT,  "
                +   STALE_TIMESTAMP             +   " INTEGER DEFAULT 0 "+")";

        String kyberPreKeysTable    =   "CREATE TABLE " + KYBER_PRE_KEYS_TABLE + "("
                +   KYBER_PRE_KEY_SQLITE_ID     +   " INTEGER PRIMARY KEY AUTOINCREMENT, "
                +   KYBER_PRE_KEY_ID            +   " INTEGER UNIQUE, "
                +   KYBER_SERIALIZED_KEY        +   " BLOB, "
                +   KYBER_KEY_TIMESTAMP         +   " VARCHAR, "
                +   IS_KYBER_KEY_USED           +   " INTEGER DEFAULT 0 "+")";

        sqLiteDatabase.execSQL(identityKeysTable);
        sqLiteDatabase.execSQL(signedPreKeysTable);
        sqLiteDatabase.execSQL(preKeysTable);
        sqLiteDatabase.execSQL(kyberPreKeysTable);

        sqLiteDatabase.execSQL("CREATE INDEX idx_signed_pre_key_id ON " + SIGNED_KEYS_TABLE + "(" + SIGNED_PRE_KEY_ID + ")");
        sqLiteDatabase.execSQL("CREATE INDEX idx_one_time_pre_key_id ON " + ONE_TIME_PRE_KEYS_TABLE + "(" + ONE_TIME_PRE_KEY_ID + ")");
        sqLiteDatabase.execSQL("CREATE INDEX idx_kyber_pre_key_id ON " + KYBER_PRE_KEYS_TABLE + "(" + KYBER_PRE_KEY_ID + ")");

        logMessage("Users encrypted database created successfully");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

        Log.e(Extras.LOG_MESSAGE, "db version is " + oldVersion);
        try {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE " + KYBER_PRE_KEYS_TABLE +
                        " ADD COLUMN " + IS_KYBER_KEY_USED + " INTEGER DEFAULT 0");
                logMessage("Added IS_KYBER_KEY_USED column to kyber_pre_keys_table");
            }

            if (oldVersion < 3) {
                migrateIdentityKeys(db);
                migrateKyberPreKeysToUnique(db);
                migrateSignedPreKeysTable(db);
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error during database upgrade: " + e.getMessage());
        }
    }

    private void migrateIdentityKeys(SQLiteDatabase db) {
        db.execSQL("DELETE FROM " + IDENTITY_KEYS_TABLE + " " +
                "WHERE " + IDENTITY_KEYS_SQLITE_ID + " NOT IN (" +
                "SELECT MAX(" + IDENTITY_KEYS_SQLITE_ID + ") FROM " + IDENTITY_KEYS_TABLE + ")");

        String newIdentityTable = "CREATE TABLE identity_keys_new (" +
                IDENTITY_KEYS_SQLITE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                IDENTITY_KEY_PUBLIC + " TEXT UNIQUE, " +
                IDENTITY_KEY_PRIVATE + " TEXT" +
                ")";
        db.execSQL(newIdentityTable);
        db.execSQL("INSERT INTO identity_keys_new (" +
                IDENTITY_KEYS_SQLITE_ID + "," +
                IDENTITY_KEY_PUBLIC + "," +
                IDENTITY_KEY_PRIVATE +
                ") SELECT " +
                IDENTITY_KEYS_SQLITE_ID + "," +
                IDENTITY_KEY_PUBLIC + "," +
                IDENTITY_KEY_PRIVATE +
                " FROM " + IDENTITY_KEYS_TABLE);
        db.execSQL("DROP TABLE " + IDENTITY_KEYS_TABLE);
        db.execSQL("ALTER TABLE identity_keys_new RENAME TO " + IDENTITY_KEYS_TABLE);
        Log.e(Extras.LOG_MESSAGE, "Migrated identity_keys_table with UNIQUE public key");

    }

    private void migrateKyberPreKeysToUnique(SQLiteDatabase db) {
        // 1️⃣ Remove duplicates, keep latest
        db.execSQL("DELETE FROM " + KYBER_PRE_KEYS_TABLE + " " +
                "WHERE " + KYBER_PRE_KEY_SQLITE_ID + " NOT IN (" +
                "SELECT MAX(" + KYBER_PRE_KEY_SQLITE_ID + ") " +
                "FROM " + KYBER_PRE_KEYS_TABLE + " " +
                "GROUP BY " + KYBER_PRE_KEY_ID + ")");

        // 2️⃣ Create new table with UNIQUE
        String newKyberTable = "CREATE TABLE kyber_pre_keys_table_new (" +
                KYBER_PRE_KEY_SQLITE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                KYBER_PRE_KEY_ID + " INTEGER UNIQUE, " +
                KYBER_SERIALIZED_KEY + " BLOB, " +
                KYBER_KEY_TIMESTAMP + " VARCHAR, " +
                IS_KYBER_KEY_USED + " INTEGER DEFAULT 0" +
                ")";
        db.execSQL(newKyberTable);

        // 3️⃣ Copy data
        db.execSQL("INSERT INTO kyber_pre_keys_table_new (" +
                KYBER_PRE_KEY_SQLITE_ID + "," +
                KYBER_PRE_KEY_ID + "," +
                KYBER_SERIALIZED_KEY + "," +
                KYBER_KEY_TIMESTAMP + "," +
                IS_KYBER_KEY_USED +
                ") SELECT " +
                KYBER_PRE_KEY_SQLITE_ID + "," +
                KYBER_PRE_KEY_ID + "," +
                KYBER_SERIALIZED_KEY + "," +
                KYBER_KEY_TIMESTAMP + "," +
                IS_KYBER_KEY_USED +
                " FROM " + KYBER_PRE_KEYS_TABLE);

        // 4️⃣ Replace old table
        db.execSQL("DROP TABLE " + KYBER_PRE_KEYS_TABLE);
        db.execSQL("ALTER TABLE kyber_pre_keys_table_new RENAME TO " + KYBER_PRE_KEYS_TABLE);
        Log.e(Extras.LOG_MESSAGE, "Migrated kyber_pre_keys_table with UNIQUE kyber_pre_key_id key");
    }

    private void migrateSignedPreKeysTable(SQLiteDatabase db) {
        db.execSQL("DELETE FROM " + SIGNED_KEYS_TABLE + " " +
                "WHERE " + SIGNED_KEYS_SQLITE_ID + " NOT IN (" +
                "SELECT MAX(" + SIGNED_KEYS_SQLITE_ID + ") " +
                "FROM " + SIGNED_KEYS_TABLE + " " +
                "GROUP BY " + SIGNED_PRE_KEY_ID + ")");

        // Create new table with UNIQUE constraint
        String newSignedTable = "CREATE TABLE signed_keys_table_new (" +
                SIGNED_KEYS_SQLITE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                SIGNED_PRE_KEY_ID + " INTEGER UNIQUE, " +
                SIGNED_PRE_KEY_SIGNATURE + " TEXT, " +
                SIGNED_PUBLIC_KEY + " TEXT, " +
                SIGNED_PRIVATE_KEY + " TEXT" +
                ")";
        db.execSQL(newSignedTable);

        // Copy data
        db.execSQL("INSERT INTO signed_keys_table_new (" +
                SIGNED_KEYS_SQLITE_ID + "," +
                SIGNED_PRE_KEY_ID + "," +
                SIGNED_PRE_KEY_SIGNATURE + "," +
                SIGNED_PUBLIC_KEY + "," +
                SIGNED_PRIVATE_KEY +
                ") SELECT " +
                SIGNED_KEYS_SQLITE_ID + "," +
                SIGNED_PRE_KEY_ID + "," +
                SIGNED_PRE_KEY_SIGNATURE + "," +
                SIGNED_PUBLIC_KEY + "," +
                SIGNED_PRIVATE_KEY +
                " FROM " + SIGNED_KEYS_TABLE);

        // Replace old table
        db.execSQL("DROP TABLE " + SIGNED_KEYS_TABLE);
        db.execSQL("ALTER TABLE signed_keys_table_new RENAME TO " + SIGNED_KEYS_TABLE);
        Log.e(Extras.LOG_MESSAGE, "Migrated signed_keys_table to UNIQUE SIGNED_PRE_KEY_ID");
    }

    private void logMessage(String message) {
        Log.e(Extras.LOG_MESSAGE, message);
    }

    public synchronized SQLiteDatabase getReadableDb() {
        if (readableDb == null || !readableDb.isOpen()) {
            readableDb = getReadableDatabase(); // key derivation only once
        }
        return readableDb;
    }

    public synchronized SQLiteDatabase getWritableDb() {
        if (writableDb == null || !writableDb.isOpen()) {
            writableDb = getWritableDatabase();
        }
        return writableDb;
    }
}

