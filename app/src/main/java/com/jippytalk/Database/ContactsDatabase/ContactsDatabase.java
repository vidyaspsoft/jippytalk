package com.jippytalk.Database.ContactsDatabase;

import android.content.Context;
import android.util.Log;

import com.jippytalk.Database.SqliteDatabaseHook;
import com.jippytalk.Database.User.UsersDatabase;
import com.jippytalk.Extras;

import net.zetetic.database.DefaultDatabaseErrorHandler;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;


public class ContactsDatabase extends SQLiteOpenHelper {

    private static volatile ContactsDatabase    contactsDatabase;
    public static final String                  DB_NAME             =   "contacts.db";
    public static final int                     DATABASE_VERSION    =   2;
    private static final SQLiteDatabaseHook     databaseHook        =   new SqliteDatabaseHook();
    private SQLiteDatabase                      readableDb;
    private SQLiteDatabase                      writableDb;


    // ------------------ Contacts Table ---------------

    public static final String                  CONTACTS_TABLE                  =   "user_contacts";
    public static final String                  SQLITE_ID                       =   "sqlite_user_id";
    public static final String                  RAW_CONTACT_ID                  =   "raw_contact_id";
    public static final String                  CONTACT_USER_ID                 =   "contact_user_id";
    public static final String                  IS_APP_USER                     =   "is_app_user";
    public static final String                  IS_CONTACT                      =   "is_contact";
    public static final String                  CONTACT_NAME                    =   "contact_name";
    public static final String                  CONTACT_PHONE                   =   "phone";
    public static final String                  CONTACT_PHONE_HASH              =   "contact_phone_hash";
    public static final String                  CONTACT_ABOUT                   =   "about";
    public static final String                  DATE_JOINED                     =   "date_joined";
    public static final String                  IS_FAVOURITE                    =   "is_favourite";
    public static final String                  PROFILE_PIC                     =   "profile_pic";


    public static final String                  CONTACT_KEYS_TABLE              =   "contacts_keys_table";
    public static final String                  CONTACT_KEYS_SQLITE_ID          =   "contact_keys_sqlite_id";
    public static final String                  KEYS_CONTACT_USER_ID            =   "keys_contact_user_id";
    public static final String                  CONTACT_REGISTRATION_ID         =   "contact_registration_id";
    public static final String                  CONTACT_DEVICE_ID               =   "contact_device_id";
    public static final String                  CONTACT_PUBLIC_KEY              =   "contact_public_key";
    public static final String                  CONTACT_SIGNED_KEY_ID           =   "contact_signed_key_id";
    public static final String                  CONTACT_SIGNED_PRE_PUBLIC_KEY   =   "contact_signed_pre_public_key";
    public static final String                  CONTACT_SIGNATURE               =   "contact_signature";
    public static final String                  CONTACT_KEYS_LAST_UPDATED       =   "contact_keys_last_updated";

    // -------------------  Blocked Users Table ----------------------

    public static final String                  BLOCKED_USERS_TABLE             =   "blocked_contacts";
    public static final String                  BLOCKED_USER_ID                 =   "blocked_user_id";
    public static final String                  BLOCKED_PHONE_NUMBER            =   "blocked_phone_number";
    public static final String                  BLOCKED_TIMESTAMP               =   "blocked_timestamp";



    public ContactsDatabase(Context context, byte[] password) {
        super(context, DB_NAME, password, null, DATABASE_VERSION, 1,
                new DefaultDatabaseErrorHandler(), databaseHook, true);
    }

    public static void initialize(Context context, byte[] password) {
        if (contactsDatabase == null) {
            synchronized (UsersDatabase.class) {
                if (contactsDatabase == null) {
                    contactsDatabase = new ContactsDatabase(context.getApplicationContext(), password);
                }
            }
        }
    }


    public static synchronized ContactsDatabase getInstance(Context context) {
        if (contactsDatabase == null) {
            Log.e(Extras.LOG_MESSAGE,"contacts database is null and not defined");
        }
        return contactsDatabase;
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

        String contactsTable        =   "CREATE TABLE " + CONTACTS_TABLE + "("
                +   SQLITE_ID               +   " INTEGER PRIMARY KEY AUTOINCREMENT,"
                +   RAW_CONTACT_ID          +   " INTEGER(20), "
                +   CONTACT_USER_ID         +   " VARCHAR ,"
                +   IS_APP_USER             +   " INTEGER DEFAULT 0,"
                +   IS_CONTACT              +   " INTEGER DEFAULT 0,"
                +   CONTACT_NAME            +   " VARCHAR(20),"
                +   CONTACT_PHONE           +   " VARCHAR(12) UNIQUE, "
                +   CONTACT_PHONE_HASH      +   " VARCHAR UNIQUE, "
                +   CONTACT_ABOUT           +   " VARCHAR,"
                +   DATE_JOINED             +   " INTEGER,"
                +   IS_FAVOURITE            +   " INTEGER DEFAULT 0, "
                +   PROFILE_PIC             +   " VARCHAR "+")";

        String contactKeysTable     =   "CREATE TABLE " + CONTACT_KEYS_TABLE + "("
                +   CONTACT_KEYS_SQLITE_ID          +   " INTEGER PRIMARY KEY AUTOINCREMENT, "
                +   KEYS_CONTACT_USER_ID            +   " VARCHAR, "
                +   CONTACT_REGISTRATION_ID         +   " INTEGER(20), "
                +   CONTACT_DEVICE_ID               +   " INTEGER(20), "
                +   CONTACT_PUBLIC_KEY              +   " TEXT, "
                +   CONTACT_SIGNED_KEY_ID           +   " INTEGER, "
                +   CONTACT_SIGNED_PRE_PUBLIC_KEY   +   " TEXT, "
                +   CONTACT_SIGNATURE               +   " TEXT, "
                +   CONTACT_KEYS_LAST_UPDATED       +   " INTEGER, "
                + "UNIQUE (" + KEYS_CONTACT_USER_ID + ", " + CONTACT_REGISTRATION_ID + ", " + CONTACT_DEVICE_ID + ")"
                + ")";

        String blockedTable        =    "CREATE TABLE "+ BLOCKED_USERS_TABLE + "("
                +   BLOCKED_USER_ID       +     " VARCHAR UNIQUE, "
                +   BLOCKED_PHONE_NUMBER  +     " VARCHAR(12) UNIQUE,"
                +   BLOCKED_TIMESTAMP     +     " INTEGER "+")";

        sqLiteDatabase.execSQL(contactsTable);
        sqLiteDatabase.execSQL(contactKeysTable);
        sqLiteDatabase.execSQL(blockedTable);

        sqLiteDatabase.execSQL("CREATE INDEX IF NOT EXISTS idx_contact_user_id ON " + CONTACTS_TABLE + "(" + CONTACT_USER_ID + ")");
        sqLiteDatabase.execSQL("CREATE INDEX IF NOT EXISTS idx_contact_phone ON " + CONTACTS_TABLE + "(" + CONTACT_PHONE + ")");
        sqLiteDatabase.execSQL("CREATE INDEX IF NOT EXISTS idx_keys_contact_user_id ON " + CONTACT_KEYS_TABLE + "(" + KEYS_CONTACT_USER_ID + ")");
        sqLiteDatabase.execSQL("CREATE INDEX IF NOT EXISTS idx_blocked_user_id ON " + BLOCKED_USERS_TABLE + "(" + BLOCKED_USER_ID + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {

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
