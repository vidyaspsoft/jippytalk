package com.jippytalk.Database.SessionDatabase;

import android.content.Context;
import android.util.Log;

import com.jippytalk.Database.SqliteDatabaseHook;
import com.jippytalk.Database.User.UsersDatabase;
import com.jippytalk.Extras;

import net.zetetic.database.DefaultDatabaseErrorHandler;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

public class SessionsDatabase extends SQLiteOpenHelper {

    private static volatile SessionsDatabase    sessionsDatabase;
    public static final String                  DATABASE_NAME                   =   "sessions.db";
    public static final int                     DATABASE_VERSION                =   1;
    private SQLiteDatabase                      readableDb;
    private SQLiteDatabase                      writableDb;
    private static final SQLiteDatabaseHook     databaseHook                    =   new SqliteDatabaseHook();

    public static final String                  SESSION_RECORDS_TABLE           =   "session_records";

    public static final String                  SESSION_SQLITE_ID               =   "session_sqlite_id";
    public static final String                  SESSION_CONTACT_ID              =   "session_contact_id";
    public static final String                  SESSION_DEVICE_ID               =   "session_device_id";
    public static final String                  SESSION_OBJECT                  =   "session_object";
    public static final String                  SESSION_LAST_UPDATED_TIME       =   "last_updated_timestamp";


    public SessionsDatabase(Context context, byte[] password) {
        super(context, DATABASE_NAME, password, null, DATABASE_VERSION, 1,
                new DefaultDatabaseErrorHandler(), databaseHook, true);
    }

    public static void initialize(Context context, byte[] password) {
        if (sessionsDatabase == null) {
            synchronized (UsersDatabase.class) {
                if (sessionsDatabase == null) {
                    sessionsDatabase = new SessionsDatabase(context.getApplicationContext(), password);
                }
            }
        }
    }

    public static synchronized SessionsDatabase getInstance(Context context) {
        if (sessionsDatabase == null) {
            Log.e(Extras.LOG_MESSAGE,"contacts database is null and not defined");
        }
        return sessionsDatabase;
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

        String sessionRecordsTable  =   "CREATE TABLE " + SESSION_RECORDS_TABLE + " ("
                + SESSION_SQLITE_ID                 +   " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + SESSION_CONTACT_ID                +   " VARCHAR NOT NULL, "
                + SESSION_DEVICE_ID                 +   " INTEGER NOT NULL, "
                + SESSION_OBJECT                    +   " BLOB NOT NULL, "
                + SESSION_LAST_UPDATED_TIME         +   " INTEGER, "
                + "UNIQUE(" + SESSION_CONTACT_ID    + ", " + SESSION_DEVICE_ID + ")" + ")";

        sqLiteDatabase.execSQL(sessionRecordsTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

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
