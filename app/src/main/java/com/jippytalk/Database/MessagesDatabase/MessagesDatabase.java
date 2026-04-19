package com.jippytalk.Database.MessagesDatabase;

import android.content.Context;
import android.util.Log;

import com.jippytalk.Database.SqliteDatabaseHook;
import com.jippytalk.Database.User.UsersDatabase;
import com.jippytalk.Extras;

import net.zetetic.database.DefaultDatabaseErrorHandler;
import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook;
import net.zetetic.database.sqlcipher.SQLiteOpenHelper;

public class MessagesDatabase extends SQLiteOpenHelper {


    private static volatile MessagesDatabase    messagesDatabase;
    public static final String                  DB_NAME             = "messages.db";
    public static final int                     DATABASE_VERSION    = 9;
    private SQLiteDatabase                      readableDb;
    private SQLiteDatabase                      writableDb;
    private static final SQLiteDatabaseHook     databaseHook                    =   new SqliteDatabaseHook();

    // --------------  Messages Table ----------

    public static final String                  MESSAGES_TABLE                  = "messages";

    public static final String                  SQLITE_MESSAGE_ID               = "sqlite_message_id";  // 0
    public static final String                  MESSAGE_ID                      = "message_id";   // 1
    public static final String                  MESSAGE_DIRECTION               = "message_direction";  // 2
    public static final String                  RECEIVER_ID                     = "receiver_id";  // 3
    public static final String                  MESSAGE                         = "message";  // 4
    public static final String                  MESSAGE_STATUS                  = "message_status";  // 5
    public static final String                  NEED_PUSH                       = "need_push";
    public static final String                  TIMESTAMP                       = "timestamp";  // 6
    public static final String                  RECEIVE_TIMESTAMP               = "receive_timestamp";  // 7
    public static final String                  READ_TIMESTAMP                  = "read_timestamp";  // 8
    public static final String                  STARRED                         = "starred_status";  // 9
    public static final String                  EDIT_STATUS                     = "edit_status";  // 10
    public static final String                  MESSAGE_TYPE                    = "message_type";  // 11
    public static final String                  LATITUDE                        = "latitude";  // 12
    public static final String                  LONGITUDE                       = "longitude";  // 13
    public static final String                  IS_REPLY                        = "is_reply"; //14
    public static final String                  REPLY_TO_MESSAGE_ID             = "reply_to"; //15

    // ----- v9: server-assigned room identifier -----
    // Stable UUID that the backend assigns to each 1-on-1 conversation
    // (and later to each group). Every REST message response and every WS
    // payload carries it. We store it so the chat screen can query by
    // room_id instead of inferring the conversation from receiver_id.
    public static final String                  ROOM_ID                         = "room_id";

    // ----- Media / attachment columns (added in v8) -----
    // Only metadata + on-device PATHS are stored — never the file bytes.
    // The actual files live in /files/sent/, /files/received/, or /cache/.

    public static final String                  FILE_NAME                       = "file_name";  // 16
    public static final String                  CONTENT_TYPE                    = "content_type";  // 17  image/video/audio/document
    public static final String                  CONTENT_SUBTYPE                 = "content_subtype";  // 18  pdf/jpg/mp4
    public static final String                  CAPTION                         = "caption";  // 19
    public static final String                  MEDIA_WIDTH                     = "media_width";  // 20
    public static final String                  MEDIA_HEIGHT                    = "media_height";  // 21
    public static final String                  MEDIA_DURATION                  = "media_duration";  // 22
    public static final String                  FILE_SIZE                       = "file_size";  // 23
    public static final String                  S3_KEY                          = "s3_key";  // 24
    public static final String                  S3_BUCKET                       = "s3_bucket";  // 25
    public static final String                  FILE_TRANSFER_ID                = "file_transfer_id";  // 26
    public static final String                  LOCAL_FILE_PATH                 = "local_file_path";  // 27  on-device plaintext path
    public static final String                  LOCAL_THUMBNAIL_PATH            = "local_thumbnail_path";  // 28  on-device plaintext thumb
    public static final String                  REMOTE_THUMBNAIL_URL            = "remote_thumbnail_url";  // 29  S3 thumb URL (encrypted bytes)
    public static final String                  ENCRYPTED_S3_URL                = "encrypted_s3_url";  // 30  S3 file URL (encrypted bytes)
    public static final String                  ENCRYPTION_KEY                  = "encryption_key";  // 31  per-message AES-256 key (Base64)
    public static final String                  ENCRYPTION_IV                   = "encryption_iv";  // 32  per-message AES IV (Base64)


    // ------------------  Chat List Table -----------

    public static final String                  CHAT_LIST_TABLE                 = "chat_list";

    public static final String                  SQLITE_CHAT_ID                  = "sqlite_chat_id";
    public static final String                  CHAT_ID                         = "chat_id";
    public static final String                  SORT_LAST_MESSAGE_TIME          = "sort_last_message_time";
    public static final String                  UNREAD_MESSAGES_COUNT           = "unread_messages_count";
    public static final String                  CHAT_ARCHIVE                    = "chat_archive";
    public static final String                  CHAT_LOCK                       = "chat_lock";
    public static final String                  IS_CHAT_LOCKED                  = "is_chat_locked";
    public static final String                  CHAT_LOCKED_TIME                = "chat_locked_time";
    public static final String                  CHAT_LAST_SCROLL_POSITION       = "chat_last_scroll_position";
    public static final String                  CHAT_READ_RECEIPTS              = "chat_read_receipts";
    public static final String                  CHAT_LAST_MESSAGE_ID_FK         = "chat_last_message_id";


    // ------------------- Scheduled Messages Table -----------------------------


    public static final String              SCHEDULED_MESSAGE_TABLE             = "scheduled_messages";

    public static final String              SCHEDULED_MESSAGE_SQLITE_ID         = "scheduled_message_sqlite_id";
    public static final String              SCHEDULED_MESSAGE_ID                = "scheduled_message_receiver_id";
    public static final String              SCHEDULED_MESSAGE_RECEIVER_ID       = "scheduled_message_id";
    public static final String              SCHEDULED_MESSAGE                   = "scheduled_message";
    public static final String              SCHEDULED_MESSAGE_TIMESTAMP         = "scheduled_message_timestamp";
    public static final String              SCHEDULED_MESSAGE_STATUS            = "scheduled_message_status";


    public MessagesDatabase(Context context, byte[] password) {
        super(context, DB_NAME, password, null, DATABASE_VERSION, 1,
                new DefaultDatabaseErrorHandler(), databaseHook, true);
    }

    public static void initialize(Context context, byte[] password) {
        if (messagesDatabase == null) {
            synchronized (UsersDatabase.class) {
                if (messagesDatabase == null) {
                    messagesDatabase = new MessagesDatabase(context.getApplicationContext(), password);
                }
            }
        }
    }

    public static synchronized MessagesDatabase getInstance(Context context) {
        if (messagesDatabase == null) {
            Log.e(Extras.LOG_MESSAGE,"messages database is null and not defined");
        }
        return messagesDatabase;
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

        String messagesTable = "CREATE TABLE " + MESSAGES_TABLE + "("
                + SQLITE_MESSAGE_ID     + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + MESSAGE_ID            + " VARCHAR UNIQUE, "
                + MESSAGE_DIRECTION     + " INTEGER , "
                + RECEIVER_ID           + " VARCHAR, "
                + MESSAGE               + " TEXT,"
                + MESSAGE_STATUS        + " INTEGER DEFAULT 0, "
                + NEED_PUSH             + " INTEGER DEFAULT 0, "
                + TIMESTAMP             + " DATETIME DEFAULT CURRENT_TIMESTAMP, "
                + RECEIVE_TIMESTAMP     + " INTEGER DEFAULT 0,"
                + READ_TIMESTAMP        + " INTEGER DEFAULT 0, "
                + STARRED               + " INTEGER DEFAULT 0, "
                + EDIT_STATUS           + " INTEGER DEFAULT 0 , "
                + MESSAGE_TYPE          + " INTEGER DEFAULT 0, "
                + LATITUDE              + " VARCHAR, "
                + LONGITUDE             + " VARCHAR, "
                + IS_REPLY              + " INTEGER DEFAULT 0, "
                + REPLY_TO_MESSAGE_ID   + " VARCHAR, "
                // ----- v8: media / attachment columns -----
                + FILE_NAME             + " TEXT, "
                + CONTENT_TYPE          + " TEXT, "
                + CONTENT_SUBTYPE       + " TEXT, "
                + CAPTION               + " TEXT, "
                + MEDIA_WIDTH           + " INTEGER DEFAULT 0, "
                + MEDIA_HEIGHT          + " INTEGER DEFAULT 0, "
                + MEDIA_DURATION        + " INTEGER DEFAULT 0, "
                + FILE_SIZE             + " INTEGER DEFAULT 0, "
                + S3_KEY                + " TEXT, "
                + S3_BUCKET             + " TEXT, "
                + FILE_TRANSFER_ID      + " TEXT, "
                + LOCAL_FILE_PATH       + " TEXT, "
                + LOCAL_THUMBNAIL_PATH  + " TEXT, "
                + REMOTE_THUMBNAIL_URL  + " TEXT, "
                + ENCRYPTED_S3_URL      + " TEXT, "
                + ENCRYPTION_KEY        + " TEXT, "
                + ENCRYPTION_IV         + " TEXT, "
                + ROOM_ID               + " TEXT DEFAULT ''"
                + ")";


        String chatListsTable = "CREATE TABLE " + CHAT_LIST_TABLE + "("
                + SQLITE_CHAT_ID            + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + CHAT_ID                   + " VARCHAR UNIQUE, "
                + SORT_LAST_MESSAGE_TIME    + " DATETIME DEFAULT CURRENT_TIMESTAMP , "
                + CHAT_ARCHIVE              + " INTEGER DEFAULT 0 ,"
                + UNREAD_MESSAGES_COUNT     + " INTEGER DEFAULT 0,"
                + CHAT_LOCK                 + " INTEGER DEFAULT 0, "
                + IS_CHAT_LOCKED            + " INTEGER DEFAULT 0 ,"
                + CHAT_LOCKED_TIME          + " INTEGER , "
                + CHAT_LAST_SCROLL_POSITION + " INTEGER ,"
                + CHAT_READ_RECEIPTS        + " INTEGER DEFAULT 0,"
                + CHAT_LAST_MESSAGE_ID_FK   + " INTEGER, "
                + ROOM_ID                   + " TEXT DEFAULT '', " +
                " FOREIGN KEY(" + CHAT_LAST_MESSAGE_ID_FK + ") REFERENCES " + MESSAGES_TABLE + "(" + SQLITE_MESSAGE_ID + ")" +
                "ON DELETE CASCADE "+")";


        sqLiteDatabase.execSQL(messagesTable);
        sqLiteDatabase.execSQL(chatListsTable);

        sqLiteDatabase.execSQL("CREATE INDEX idx_message_id ON " + MESSAGES_TABLE + "(" + MESSAGE_ID + ")");
        sqLiteDatabase.execSQL("CREATE INDEX idx_receiver_id ON " + MESSAGES_TABLE + "(" + RECEIVER_ID + ")");
        sqLiteDatabase.execSQL("CREATE INDEX idx_chat_id ON " + CHAT_LIST_TABLE + "(" + CHAT_ID + ")");
        // v9: allow fast lookups by server-assigned room_id
        sqLiteDatabase.execSQL("CREATE INDEX idx_msg_room_id ON " + MESSAGES_TABLE + "(" + ROOM_ID + ")");
        sqLiteDatabase.execSQL("CREATE INDEX idx_chat_room_id ON " + CHAT_LIST_TABLE + "(" + ROOM_ID + ")");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.e(Extras.LOG_MESSAGE,"messages db upgrade " + oldVersion + " -> " + newVersion);

        // v8: add media / attachment columns to the messages table.
        // ALTER TABLE ADD COLUMN is non-destructive — existing rows keep their
        // current values, new columns default to NULL / 0. Existing column
        // names are not touched.
        if (oldVersion < 8) {
            try {
                db.execSQL("ALTER TABLE " + MESSAGES_TABLE + " ADD COLUMN " + FILE_NAME            + " TEXT");
                db.execSQL("ALTER TABLE " + MESSAGES_TABLE + " ADD COLUMN " + CONTENT_TYPE         + " TEXT");
                db.execSQL("ALTER TABLE " + MESSAGES_TABLE + " ADD COLUMN " + CONTENT_SUBTYPE      + " TEXT");
                db.execSQL("ALTER TABLE " + MESSAGES_TABLE + " ADD COLUMN " + CAPTION              + " TEXT");
                db.execSQL("ALTER TABLE " + MESSAGES_TABLE + " ADD COLUMN " + MEDIA_WIDTH          + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + MESSAGES_TABLE + " ADD COLUMN " + MEDIA_HEIGHT         + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + MESSAGES_TABLE + " ADD COLUMN " + MEDIA_DURATION       + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + MESSAGES_TABLE + " ADD COLUMN " + FILE_SIZE            + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + MESSAGES_TABLE + " ADD COLUMN " + S3_KEY               + " TEXT");
                db.execSQL("ALTER TABLE " + MESSAGES_TABLE + " ADD COLUMN " + S3_BUCKET            + " TEXT");
                db.execSQL("ALTER TABLE " + MESSAGES_TABLE + " ADD COLUMN " + FILE_TRANSFER_ID     + " TEXT");
                db.execSQL("ALTER TABLE " + MESSAGES_TABLE + " ADD COLUMN " + LOCAL_FILE_PATH      + " TEXT");
                db.execSQL("ALTER TABLE " + MESSAGES_TABLE + " ADD COLUMN " + LOCAL_THUMBNAIL_PATH + " TEXT");
                db.execSQL("ALTER TABLE " + MESSAGES_TABLE + " ADD COLUMN " + REMOTE_THUMBNAIL_URL + " TEXT");
                db.execSQL("ALTER TABLE " + MESSAGES_TABLE + " ADD COLUMN " + ENCRYPTED_S3_URL     + " TEXT");
                db.execSQL("ALTER TABLE " + MESSAGES_TABLE + " ADD COLUMN " + ENCRYPTION_KEY       + " TEXT");
                db.execSQL("ALTER TABLE " + MESSAGES_TABLE + " ADD COLUMN " + ENCRYPTION_IV        + " TEXT");
                Log.e(Extras.LOG_MESSAGE,"messages db upgraded to v8 (media columns added)");
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"failed to add media columns " + e.getMessage());
            }
        }

        // v9: add the server-assigned room_id column to both messages and
        // chat_list tables, plus their lookup indices. Non-destructive —
        // existing rows get room_id = '' (empty string), to be filled in by
        // the backfill step after /api/rooms is fetched on app open.
        if (oldVersion < 9) {
            try {
                db.execSQL("ALTER TABLE " + MESSAGES_TABLE   + " ADD COLUMN " + ROOM_ID + " TEXT DEFAULT ''");
                db.execSQL("ALTER TABLE " + CHAT_LIST_TABLE  + " ADD COLUMN " + ROOM_ID + " TEXT DEFAULT ''");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_msg_room_id ON "
                        + MESSAGES_TABLE   + "(" + ROOM_ID + ")");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_chat_room_id ON "
                        + CHAT_LIST_TABLE  + "(" + ROOM_ID + ")");
                Log.e(Extras.LOG_MESSAGE, "messages db upgraded to v9 (room_id column added)");
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "failed to add room_id column " + e.getMessage());
            }
        }
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
