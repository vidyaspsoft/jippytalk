package com.jippytalk.Database.MessagesDatabase;


import static com.jippytalk.Database.MessagesDatabase.MessagesDatabase.CHAT_LIST_TABLE;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.util.Log;

import com.jippytalk.Database.MessagesDatabase.Model.MarkMessagesAsDeliveredAndSeenModel;
import com.jippytalk.Database.MessagesDatabase.Model.MarkMessagesAsDeliveredModel;
import com.jippytalk.Extras;
import com.jippytalk.Managers.MessagesManager;

import net.zetetic.database.sqlcipher.SQLiteDatabase;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class MessagesDatabaseDAO {

    private final MessagesDatabase messagesDatabase;

    public MessagesDatabaseDAO(MessagesDatabase messagesDatabase) {
        this.messagesDatabase = messagesDatabase;
    }


    // Messages Table Functions Start ----------------

    /**
     * Reads the message body for a single row by message_id.
     * @return the message column value, or null if not found
     */
    public String getMessageBodyById(String messageId) {
        try {
            SQLiteDatabase db = messagesDatabase.getReadableDb();
            try (android.database.Cursor cursor = db.rawQuery(
                    "SELECT " + MessagesDatabase.MESSAGE + " FROM " + MessagesDatabase.MESSAGES_TABLE
                            + " WHERE " + MessagesDatabase.MESSAGE_ID + " = ?",
                    new String[]{messageId})) {
                if (cursor != null && cursor.moveToFirst()) {
                    return cursor.getString(0);
                }
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "getMessageBodyById failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * Updates the `message` (body) column for a single row. Used to patch the
     * attachment metadata JSON with local_file_path once a download completes.
     *
     * @return true if exactly one row was updated
     */
    public boolean updateMessageContent(String messageId, String newMessageBody) {
        try {
            SQLiteDatabase db = messagesDatabase.getWritableDb();
            ContentValues cv = new ContentValues();
            cv.put(MessagesDatabase.MESSAGE, newMessageBody);
            int rows = db.update(
                    MessagesDatabase.MESSAGES_TABLE,
                    cv,
                    MessagesDatabase.MESSAGE_ID + " = ?",
                    new String[]{messageId});
            return rows > 0;
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "updateMessageContent failed: " + e.getMessage());
            return false;
        }
    }

    // Back-compat overload — callers that haven't been updated to pass room_id
    // get an empty-string default. Once every call site is updated, this can
    // be removed. Functionally identical to the roomId overload below.
    public boolean insertMessage(String messageId, int messageDirection, String receiverId,
                                 String message, int messageStatus, int needPush,
                                 long timestamp, long receiveTimestamp, long readTimestamp,
                                 int starredStatus, int editedStatus, int messageType,
                                 double latitude, double longitude, int isReply,
                                 String replyToMessageId, int chatArchive) {
        return insertMessage(messageId, messageDirection, receiverId,
                message, messageStatus, needPush,
                timestamp, receiveTimestamp, readTimestamp,
                starredStatus, editedStatus, messageType,
                latitude, longitude, isReply,
                replyToMessageId, chatArchive, "");
    }

    public boolean insertMessage(String messageId, int messageDirection, String receiverId,
                                 String message, int messageStatus, int needPush,
                                 long timestamp, long receiveTimestamp, long readTimestamp,
                                 int starredStatus, int editedStatus, int messageType,
                                 double latitude, double longitude, int isReply,
                                 String replyToMessageId, int chatArchive,
                                 String roomId) {
        boolean isSuccess = false;
        try  {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues values = new ContentValues();
                values.put(MessagesDatabase.MESSAGE_ID, messageId);
                values.put(MessagesDatabase.MESSAGE_DIRECTION, messageDirection);
                values.put(MessagesDatabase.RECEIVER_ID, receiverId);
                values.put(MessagesDatabase.MESSAGE, message);
                values.put(MessagesDatabase.MESSAGE_STATUS, messageStatus);
                values.put(MessagesDatabase.NEED_PUSH, needPush);
                values.put(MessagesDatabase.TIMESTAMP, timestamp);
                values.put(MessagesDatabase.RECEIVE_TIMESTAMP, receiveTimestamp);
                values.put(MessagesDatabase.READ_TIMESTAMP, readTimestamp);
                values.put(MessagesDatabase.EDIT_STATUS, editedStatus);
                values.put(MessagesDatabase.STARRED, starredStatus);
                values.put(MessagesDatabase.MESSAGE_TYPE, messageType);
                values.put(MessagesDatabase.LATITUDE, latitude);
                values.put(MessagesDatabase.LONGITUDE, longitude);
                values.put(MessagesDatabase.IS_REPLY, isReply);
                values.put(MessagesDatabase.REPLY_TO_MESSAGE_ID, replyToMessageId);
                values.put(MessagesDatabase.ROOM_ID, roomId != null ? roomId : "");

                // Use CONFLICT_IGNORE so duplicate message_ids (e.g. server echoes our own
                // sent messages back via fetchMessages) silently skip instead of throwing.
                long result = sqLiteDatabase.insertWithOnConflict(
                        MessagesDatabase.MESSAGES_TABLE, null, values,
                        SQLiteDatabase.CONFLICT_IGNORE);

                if (result != -1) {
                    try {
                        long finalTimeStamp;
                        if (messageDirection == MessagesManager.MESSAGE_INCOMING) {
                            finalTimeStamp  =   receiveTimestamp;
                        } else {
                            finalTimeStamp  =   timestamp;
                        }
                        ContentValues contentValues   = new ContentValues();
                        contentValues.put(MessagesDatabase.CHAT_ID, receiverId);
                        contentValues.put(MessagesDatabase.CHAT_LAST_MESSAGE_ID_FK, result);
                        contentValues.put(MessagesDatabase.SORT_LAST_MESSAGE_TIME, finalTimeStamp);
                        contentValues.put(MessagesDatabase.CHAT_ARCHIVE, chatArchive);
                        if (roomId != null && !roomId.isEmpty()) {
                            contentValues.put(MessagesDatabase.ROOM_ID, roomId);
                        }

                        if (messageType == MessagesManager.SYSTEM_MESSAGE_TYPE) {
                            sqLiteDatabase.setTransactionSuccessful();
                            isSuccess   =   true;
                            return isSuccess;
                        }

                        long chatInsertResult = sqLiteDatabase.insertWithOnConflict(
                                CHAT_LIST_TABLE,
                                null,
                                contentValues,
                                SQLiteDatabase.CONFLICT_IGNORE
                        );

                        if (chatInsertResult == -1) {
                            // Already exists → update only necessary columns
                            ContentValues updateValues   =   new ContentValues();
                            updateValues.put(MessagesDatabase.CHAT_LAST_MESSAGE_ID_FK, result);
                            updateValues.put(MessagesDatabase.SORT_LAST_MESSAGE_TIME, finalTimeStamp);
                            if (roomId != null && !roomId.isEmpty()) {
                                updateValues.put(MessagesDatabase.ROOM_ID, roomId);
                            }

                            long update  =   sqLiteDatabase.update(CHAT_LIST_TABLE, updateValues,
                                    MessagesDatabase.CHAT_ID + " =?", new String[]{receiverId});
                            if (update > 0) {
                                sqLiteDatabase.setTransactionSuccessful();
                                isSuccess = true;
                            }
                        } else {
                            sqLiteDatabase.setTransactionSuccessful();
                            isSuccess = true;
                        }
                    }
                    catch (Exception e) {
                        Log.e(Extras.LOG_MESSAGE, "Unable to insert chat list caught exception " + e.getMessage());
                    }
                } else {
                    // With CONFLICT_IGNORE, result == -1 means a row with the
                    // same UNIQUE message_id already exists and was silently
                    // skipped — NOT an error. This is the normal path when we
                    // re-fetch REST history for a chat we already opened before.
                    Log.i(Extras.LOG_MESSAGE, "Skipped duplicate message row: " + messageId);
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE, "Unexpected error during insertMessage caught sql exception: " + e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Unexpected error during insertMessage: " + e.getMessage());
        }

        return isSuccess;
    }


    // ---------- Media Message Insert (v8) ----------

    /**
     * Inserts a file/media message row with all media metadata fields populated
     * as real columns (no JSON blob). The media file BYTES are NOT stored — only
     * paths to the on-device plaintext copies live in LOCAL_FILE_PATH /
     * LOCAL_THUMBNAIL_PATH. Mirrors the chat_list sync behavior of insertMessage
     * so the chat list always reflects the latest message for the contact.
     */
    public boolean insertMessageWithMedia(String messageId, int messageDirection, String receiverId,
                                          String message, int messageStatus, int needPush,
                                          long timestamp, long receiveTimestamp, long readTimestamp,
                                          int starredStatus, int editedStatus, int messageType,
                                          double latitude, double longitude, int isReply,
                                          String replyToMessageId, int chatArchive,
                                          // media columns
                                          String fileName, String contentType, String contentSubtype,
                                          String caption, int mediaWidth, int mediaHeight,
                                          long mediaDuration, long fileSize,
                                          String s3Key, String s3Bucket, String fileTransferId,
                                          String localFilePath, String localThumbnailPath,
                                          String remoteThumbnailUrl, String encryptedS3Url,
                                          String encryptionKey, String encryptionIv,
                                          String roomId) {
        boolean isSuccess = false;
        try  {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues values = new ContentValues();
                values.put(MessagesDatabase.MESSAGE_ID, messageId);
                values.put(MessagesDatabase.MESSAGE_DIRECTION, messageDirection);
                values.put(MessagesDatabase.RECEIVER_ID, receiverId);
                values.put(MessagesDatabase.MESSAGE, message);
                values.put(MessagesDatabase.MESSAGE_STATUS, messageStatus);
                values.put(MessagesDatabase.NEED_PUSH, needPush);
                values.put(MessagesDatabase.TIMESTAMP, timestamp);
                values.put(MessagesDatabase.RECEIVE_TIMESTAMP, receiveTimestamp);
                values.put(MessagesDatabase.READ_TIMESTAMP, readTimestamp);
                values.put(MessagesDatabase.EDIT_STATUS, editedStatus);
                values.put(MessagesDatabase.STARRED, starredStatus);
                values.put(MessagesDatabase.MESSAGE_TYPE, messageType);
                values.put(MessagesDatabase.LATITUDE, latitude);
                values.put(MessagesDatabase.LONGITUDE, longitude);
                values.put(MessagesDatabase.IS_REPLY, isReply);
                values.put(MessagesDatabase.REPLY_TO_MESSAGE_ID, replyToMessageId);

                // Media columns — file BYTES are never stored, only paths.
                values.put(MessagesDatabase.FILE_NAME,            fileName            != null ? fileName            : "");
                values.put(MessagesDatabase.CONTENT_TYPE,         contentType         != null ? contentType         : "");
                values.put(MessagesDatabase.CONTENT_SUBTYPE,      contentSubtype      != null ? contentSubtype      : "");
                values.put(MessagesDatabase.CAPTION,              caption             != null ? caption             : "");
                values.put(MessagesDatabase.MEDIA_WIDTH,          mediaWidth);
                values.put(MessagesDatabase.MEDIA_HEIGHT,         mediaHeight);
                values.put(MessagesDatabase.MEDIA_DURATION,       mediaDuration);
                values.put(MessagesDatabase.FILE_SIZE,            fileSize);
                values.put(MessagesDatabase.S3_KEY,               s3Key               != null ? s3Key               : "");
                values.put(MessagesDatabase.S3_BUCKET,            s3Bucket            != null ? s3Bucket            : "");
                values.put(MessagesDatabase.FILE_TRANSFER_ID,     fileTransferId      != null ? fileTransferId      : "");
                values.put(MessagesDatabase.LOCAL_FILE_PATH,      localFilePath       != null ? localFilePath       : "");
                values.put(MessagesDatabase.LOCAL_THUMBNAIL_PATH, localThumbnailPath  != null ? localThumbnailPath  : "");
                values.put(MessagesDatabase.REMOTE_THUMBNAIL_URL, remoteThumbnailUrl  != null ? remoteThumbnailUrl  : "");
                values.put(MessagesDatabase.ENCRYPTED_S3_URL,     encryptedS3Url      != null ? encryptedS3Url      : "");
                values.put(MessagesDatabase.ENCRYPTION_KEY,       encryptionKey       != null ? encryptionKey       : "");
                values.put(MessagesDatabase.ENCRYPTION_IV,        encryptionIv        != null ? encryptionIv        : "");
                values.put(MessagesDatabase.ROOM_ID,              roomId              != null ? roomId              : "");

                long result = sqLiteDatabase.insertWithOnConflict(
                        MessagesDatabase.MESSAGES_TABLE, null, values,
                        SQLiteDatabase.CONFLICT_IGNORE);

                if (result != -1) {
                    try {
                        long finalTimeStamp;
                        if (messageDirection == MessagesManager.MESSAGE_INCOMING) {
                            finalTimeStamp  =   receiveTimestamp;
                        } else {
                            finalTimeStamp  =   timestamp;
                        }
                        ContentValues contentValues   = new ContentValues();
                        contentValues.put(MessagesDatabase.CHAT_ID, receiverId);
                        contentValues.put(MessagesDatabase.CHAT_LAST_MESSAGE_ID_FK, result);
                        contentValues.put(MessagesDatabase.SORT_LAST_MESSAGE_TIME, finalTimeStamp);
                        contentValues.put(MessagesDatabase.CHAT_ARCHIVE, chatArchive);
                        if (roomId != null && !roomId.isEmpty()) {
                            contentValues.put(MessagesDatabase.ROOM_ID, roomId);
                        }

                        long chatInsertResult = sqLiteDatabase.insertWithOnConflict(
                                CHAT_LIST_TABLE,
                                null,
                                contentValues,
                                SQLiteDatabase.CONFLICT_IGNORE
                        );

                        if (chatInsertResult == -1) {
                            // Already exists → update only necessary columns
                            ContentValues updateValues   =   new ContentValues();
                            updateValues.put(MessagesDatabase.CHAT_LAST_MESSAGE_ID_FK, result);
                            updateValues.put(MessagesDatabase.SORT_LAST_MESSAGE_TIME, finalTimeStamp);
                            if (roomId != null && !roomId.isEmpty()) {
                                updateValues.put(MessagesDatabase.ROOM_ID, roomId);
                            }

                            long update  =   sqLiteDatabase.update(CHAT_LIST_TABLE, updateValues,
                                    MessagesDatabase.CHAT_ID + " =?", new String[]{receiverId});
                            if (update > 0) {
                                sqLiteDatabase.setTransactionSuccessful();
                                isSuccess = true;
                            }
                        } else {
                            sqLiteDatabase.setTransactionSuccessful();
                            isSuccess = true;
                        }
                    }
                    catch (Exception e) {
                        Log.e(Extras.LOG_MESSAGE, "Unable to insert chat list (media) caught exception " + e.getMessage());
                    }
                } else {
                    // result == -1 with CONFLICT_IGNORE = row already exists
                    // (same message_id UNIQUE constraint hit). Normal path
                    // when REST history re-fetches a message we already stored.
                    Log.i(Extras.LOG_MESSAGE, "Skipped duplicate media row: " + messageId);
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE, "Unexpected error during insertMessageWithMedia caught sql exception: " + e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Unexpected error during insertMessageWithMedia: " + e.getMessage());
        }

        return isSuccess;
    }


    // ---------- Media Path Updaters (v8) ----------

    /**
     * Sets LOCAL_FILE_PATH on a message row after the file is copied/downloaded.
     * Only this single column is touched — does not affect message text or any
     * other fields.
     */
    public boolean updateLocalFilePath(String messageId, String localFilePath) {
        boolean isSuccess = false;
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getWritableDb();
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MessagesDatabase.LOCAL_FILE_PATH, localFilePath != null ? localFilePath : "");
                int rowAffected = sqLiteDatabase.update(MessagesDatabase.MESSAGES_TABLE,
                        contentValues,
                        MessagesDatabase.MESSAGE_ID + "=?", new String[]{messageId});
                if (rowAffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                } else {
                    Log.e(Extras.LOG_MESSAGE, "updateLocalFilePath: no row matched for " + messageId);
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "updateLocalFilePath inner: " + e.getMessage());
            } finally {
                sqLiteDatabase.endTransaction();
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "updateLocalFilePath outer: " + e.getMessage());
        }
        return isSuccess;
    }

    /**
     * Sets LOCAL_THUMBNAIL_PATH on a message row after a thumbnail is generated
     * (sender) or downloaded + decrypted (receiver).
     */
    public boolean updateLocalThumbnailPath(String messageId, String localThumbnailPath) {
        boolean isSuccess = false;
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getWritableDb();
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MessagesDatabase.LOCAL_THUMBNAIL_PATH, localThumbnailPath != null ? localThumbnailPath : "");
                int rowAffected = sqLiteDatabase.update(MessagesDatabase.MESSAGES_TABLE,
                        contentValues,
                        MessagesDatabase.MESSAGE_ID + "=?", new String[]{messageId});
                if (rowAffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                } else {
                    Log.e(Extras.LOG_MESSAGE, "updateLocalThumbnailPath: no row matched for " + messageId);
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "updateLocalThumbnailPath inner: " + e.getMessage());
            } finally {
                sqLiteDatabase.endTransaction();
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "updateLocalThumbnailPath outer: " + e.getMessage());
        }
        return isSuccess;
    }

    /**
     * Sets REMOTE_THUMBNAIL_URL on a message row after the encrypted thumbnail
     * has been uploaded to S3 (sender side).
     */
    public boolean updateRemoteThumbnailUrl(String messageId, String remoteThumbnailUrl) {
        boolean isSuccess = false;
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getWritableDb();
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MessagesDatabase.REMOTE_THUMBNAIL_URL,
                        remoteThumbnailUrl != null ? remoteThumbnailUrl : "");
                int rowAffected = sqLiteDatabase.update(MessagesDatabase.MESSAGES_TABLE,
                        contentValues,
                        MessagesDatabase.MESSAGE_ID + "=?", new String[]{messageId});
                if (rowAffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "updateRemoteThumbnailUrl inner: " + e.getMessage());
            } finally {
                sqLiteDatabase.endTransaction();
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "updateRemoteThumbnailUrl outer: " + e.getMessage());
        }
        return isSuccess;
    }

    /**
     * Sets the S3 key + bucket on an outgoing media row after a successful upload.
     * Used so the chat list / repository always reflects the latest sync state
     * without rewriting the message column.
     */
    public boolean updateMessageS3Info(String messageId, String s3Key, String s3Bucket, String encryptedS3Url) {
        boolean isSuccess = false;
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getWritableDb();
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MessagesDatabase.S3_KEY,           s3Key           != null ? s3Key           : "");
                contentValues.put(MessagesDatabase.S3_BUCKET,        s3Bucket        != null ? s3Bucket        : "");
                contentValues.put(MessagesDatabase.ENCRYPTED_S3_URL, encryptedS3Url  != null ? encryptedS3Url  : "");
                int rowAffected = sqLiteDatabase.update(MessagesDatabase.MESSAGES_TABLE,
                        contentValues,
                        MessagesDatabase.MESSAGE_ID + "=?", new String[]{messageId});
                if (rowAffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "updateMessageS3Info inner: " + e.getMessage());
            } finally {
                sqLiteDatabase.endTransaction();
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "updateMessageS3Info outer: " + e.getMessage());
        }
        return isSuccess;
    }


    // ---------- REST history UPSERT helpers (v8) ----------

    /**
     * Updates server-authoritative fields on an EXISTING text message row,
     * without touching device-local fields. Called after INSERT OR IGNORE
     * returns a duplicate — the row already exists from a previous REST
     * fetch / WS delivery, but the server may now have newer values for:
     *
     *   - message_status   (delivered / seen may have flipped)
     *   - read_timestamp   (recipient may have read it since last fetch)
     *   - message          (may have been edited by the sender)
     *
     * PRESERVED (never touched here): local_file_path, local_thumbnail_path,
     * starred_status, edit_status, need_push, and everything else.
     */
    public boolean updateTextMessageServerFields(String messageId,
                                                 String message,
                                                 int messageStatus,
                                                 long readTimestamp) {
        boolean isSuccess = false;
        try {
            SQLiteDatabase db = messagesDatabase.getWritableDb();
            try {
                db.beginTransaction();
                ContentValues cv = new ContentValues();
                cv.put(MessagesDatabase.MESSAGE_STATUS, messageStatus);
                if (readTimestamp > 0) {
                    cv.put(MessagesDatabase.READ_TIMESTAMP, readTimestamp);
                }
                if (message != null && !message.isEmpty()) {
                    cv.put(MessagesDatabase.MESSAGE, message);
                }
                int rows = db.update(MessagesDatabase.MESSAGES_TABLE, cv,
                        MessagesDatabase.MESSAGE_ID + "=?", new String[]{messageId});
                if (rows > 0) {
                    db.setTransactionSuccessful();
                    isSuccess = true;
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "updateTextMessageServerFields inner: " + e.getMessage());
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "updateTextMessageServerFields outer: " + e.getMessage());
        }
        return isSuccess;
    }

    /**
     * Updates ONLY server-side state changes on an EXISTING media message
     * row. Deliberately conservative — touches just:
     *
     *   - message_status   (delivered / seen may have flipped)
     *   - read_timestamp   (recipient may have read it since last fetch)
     *   - message / caption (only if the sender edited it and new text is
     *                        non-empty; otherwise preserved)
     *
     * PRESERVED (NEVER overwritten) — includes everything media-related:
     *   - local_file_path, local_thumbnail_path  (device-local)
     *   - encrypted_s3_url, remote_thumbnail_url (URLs we already have)
     *   - s3_key, s3_bucket, file_transfer_id    (stable identifiers)
     *   - encryption_key, encryption_iv          (per-message crypto material)
     *   - starred_status, edit_status, need_push (local flags)
     *
     * Rationale: once the device has processed a message (downloaded the
     * file, decrypted the thumbnail, saved presigned URLs), those local
     * references should never be clobbered by a re-fetch of REST history.
     * The server's role is only to tell us when the tick-status changed.
     */
    public boolean updateMediaMessageServerFields(String messageId,
                                                  String message,
                                                  int messageStatus,
                                                  long readTimestamp,
                                                  String caption) {
        boolean isSuccess = false;
        try {
            SQLiteDatabase db = messagesDatabase.getWritableDb();
            try {
                db.beginTransaction();
                ContentValues cv = new ContentValues();
                cv.put(MessagesDatabase.MESSAGE_STATUS, messageStatus);
                if (readTimestamp > 0) {
                    cv.put(MessagesDatabase.READ_TIMESTAMP, readTimestamp);
                }
                if (message != null && !message.isEmpty()) {
                    cv.put(MessagesDatabase.MESSAGE, message);
                }
                if (caption != null && !caption.isEmpty()) {
                    cv.put(MessagesDatabase.CAPTION, caption);
                }
                // Intentionally NOT touched (preserved on every REST re-fetch):
                //   LOCAL_FILE_PATH, LOCAL_THUMBNAIL_PATH,
                //   ENCRYPTED_S3_URL, REMOTE_THUMBNAIL_URL,
                //   S3_KEY, S3_BUCKET, FILE_TRANSFER_ID,
                //   ENCRYPTION_KEY, ENCRYPTION_IV,
                //   STARRED, EDIT_STATUS, NEED_PUSH

                int rows = db.update(MessagesDatabase.MESSAGES_TABLE, cv,
                        MessagesDatabase.MESSAGE_ID + "=?", new String[]{messageId});
                if (rows > 0) {
                    db.setTransactionSuccessful();
                    isSuccess = true;
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "updateMediaMessageServerFields inner: " + e.getMessage());
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "updateMediaMessageServerFields outer: " + e.getMessage());
        }
        return isSuccess;
    }


    public void updateLastMessagePosition(String contactId, int lastPosition) {
        SQLiteDatabase  sqLiteDatabase      =   messagesDatabase.getWritableDb();
        try {
            sqLiteDatabase.beginTransaction();
            ContentValues   contentValues   =   new ContentValues();
            contentValues.put(MessagesDatabase.CHAT_LAST_SCROLL_POSITION, lastPosition);
            int rowAffected =   sqLiteDatabase.update(CHAT_LIST_TABLE, contentValues,
                    MessagesDatabase.CHAT_ID + "=?", new String[]{contactId});

            if (rowAffected > 0) {
                sqLiteDatabase.setTransactionSuccessful();
                Log.e(Extras.LOG_MESSAGE,"success");
            } else {
                Log.e(Extras.LOG_MESSAGE,"Unable to insert last message position to this contact else");
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to insert last message position to this contact " + e.getMessage());
        }
        finally {
            sqLiteDatabase.endTransaction();
        }
    }

    public Cursor getMessages(String receiver_id) {
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery(
                    "SELECT m.*, r.message AS replied_message_text, r.message_direction AS replied_message_direction " +
                            "FROM " + MessagesDatabase.MESSAGES_TABLE + " m " +
                            "LEFT JOIN " + MessagesDatabase.MESSAGES_TABLE + " r " +
                            "ON m.reply_to = r.message_id " +
                            "WHERE m.receiver_id = ? " +
                            "ORDER BY m.sqlite_message_id ASC",
                    new String[] {receiver_id}
            );
        } catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE, "unable to get messages from database issue in DAO: " + e.getMessage());
        }
        return null;
    }

    /**
     * v9: fetches every row belonging to a server-assigned room_id. Used by
     * the chat screen when it has the roomId from /api/rooms. Falls back to
     * receiver_id matching for rows that don't have room_id populated yet
     * (e.g. legacy rows inserted before v9 migration ran or before backfill
     * completed). Pass empty string for receiver_id to disable the fallback.
     */
    public Cursor getMessagesByRoomId(String roomId, String receiverIdFallback) {
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getReadableDb();
            String fallback = receiverIdFallback != null ? receiverIdFallback : "";
            return sqLiteDatabase.rawQuery(
                    "SELECT m.*, r.message AS replied_message_text, r.message_direction AS replied_message_direction " +
                            "FROM " + MessagesDatabase.MESSAGES_TABLE + " m " +
                            "LEFT JOIN " + MessagesDatabase.MESSAGES_TABLE + " r " +
                            "ON m.reply_to = r.message_id " +
                            "WHERE m." + MessagesDatabase.ROOM_ID + " = ? " +
                            "   OR ((m." + MessagesDatabase.ROOM_ID + " IS NULL OR m." + MessagesDatabase.ROOM_ID + " = '') " +
                            "       AND m.receiver_id = ? AND ? <> '') " +
                            "ORDER BY m.sqlite_message_id ASC",
                    new String[] { roomId, fallback, fallback }
            );
        } catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE, "getMessagesByRoomId failed: " + e.getMessage());
        }
        return null;
    }

    /**
     * v9 backfill: fills in room_id for legacy rows that were inserted before
     * v9 was deployed (or before /api/rooms provided the mapping).
     *
     * For every row in `messages` AND `chat_list` where receiver_id = chatPartnerId
     * AND room_id is empty, sets room_id = the supplied value. Safe to call
     * repeatedly — only touches rows where room_id is still blank.
     */
    public int backfillRoomIdForContact(String roomId, String chatPartnerId) {
        if (roomId == null || roomId.isEmpty()
                || chatPartnerId == null || chatPartnerId.isEmpty()) {
            return 0;
        }
        int totalUpdated = 0;
        try {
            SQLiteDatabase db = messagesDatabase.getWritableDb();
            try {
                db.beginTransaction();

                ContentValues cv = new ContentValues();
                cv.put(MessagesDatabase.ROOM_ID, roomId);

                int m = db.update(MessagesDatabase.MESSAGES_TABLE, cv,
                        MessagesDatabase.RECEIVER_ID + "=? AND ("
                                + MessagesDatabase.ROOM_ID + " IS NULL OR "
                                + MessagesDatabase.ROOM_ID + " = '')",
                        new String[]{chatPartnerId});

                int c = db.update(CHAT_LIST_TABLE, cv,
                        MessagesDatabase.CHAT_ID + "=? AND ("
                                + MessagesDatabase.ROOM_ID + " IS NULL OR "
                                + MessagesDatabase.ROOM_ID + " = '')",
                        new String[]{chatPartnerId});

                totalUpdated = m + c;
                db.setTransactionSuccessful();

                if (totalUpdated > 0) {
                    Log.i(Extras.LOG_MESSAGE, "backfillRoomIdForContact: "
                            + chatPartnerId + " → " + roomId
                            + " (messages=" + m + ", chat_list=" + c + ")");
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "backfillRoomIdForContact inner: " + e.getMessage());
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "backfillRoomIdForContact outer: " + e.getMessage());
        }
        return totalUpdated;
    }

    public Cursor getMessagesPaginationForContact(String receiver_id, int limit, int offset) {
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery(
                    "SELECT m.*, r.message AS replied_message_text, r.message_direction AS replied_message_direction " +
                            "FROM " + MessagesDatabase.MESSAGES_TABLE + " m " +
                            "LEFT JOIN " + MessagesDatabase.MESSAGES_TABLE + " r " +
                            "ON m.reply_to = r.message_id " +
                            "WHERE m.receiver_id = ? " +
                            "ORDER BY m.sqlite_message_id DESC " +     // latest first
                            "LIMIT ? OFFSET ?",                         // for pagination
                    new String[] { receiver_id, String.valueOf(limit), String.valueOf(offset) }
            );
        } catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE, "unable to get messages from database issue in DAO: " + e.getMessage());
        }
        return null;
    }

    public Cursor getDeliveredMessages(String receiver_id) {
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery(
                    "SELECT * FROM " + MessagesDatabase.MESSAGES_TABLE + " WHERE receiver_id = ? " +
                            " AND " + MessagesDatabase.MESSAGE_DIRECTION + " = ? " +
                            " AND (" + MessagesDatabase.MESSAGE_STATUS + " = ? " +
                            " OR " + MessagesDatabase.MESSAGE_STATUS + " = ? " +
                            " OR " + MessagesDatabase.MESSAGE_STATUS + " = ? " +
                            " OR " + MessagesDatabase.MESSAGE_STATUS + " = ? " +
                            " OR " + MessagesDatabase.MESSAGE_STATUS + " = ?) ",
                    new String[]{
                            receiver_id,
                            "1",   // MESSAGE_DIRECTION
                            "3",   // existing status
                            "13",  // existing status
                            "7",   // new status
                            "8"    // new status
                    }
            );
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "unable to get messages for notification from database issue in DAO file: " + e.getMessage());
        }
        return null;
    }

    public Cursor getAllDeliveredMessagesForNotification() {
        try {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery(
                    "SELECT * FROM " + MessagesDatabase.MESSAGES_TABLE +
                            " WHERE " + MessagesDatabase.MESSAGE_DIRECTION + " = ? AND " +
                            MessagesDatabase.MESSAGE_STATUS + " IN (?,?,?) " + "ORDER BY " + MessagesDatabase.RECEIVE_TIMESTAMP + " DESC",
                    new String[] {"1", "3", "13", "7"});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "unable to get messages for notification from database issue in DAO file: " + e.getMessage());
        }
        return null;
    }

    public Cursor getUnknownNumberUnSyncedMessages(String contactId) {
        try
        {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT " + MessagesDatabase.MESSAGE_ID + " FROM " + MessagesDatabase.MESSAGES_TABLE +
                            " WHERE receiver_id = ? AND " + MessagesDatabase.MESSAGE_DIRECTION + " =? AND "
                            + MessagesDatabase.MESSAGE_STATUS + " =?",
                    new String[] {contactId, "1", "5"});
        }
        catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE, "unable to get messages for notification from database issue in DAO file: " + e.getMessage());
        }
        return null;
    }

    public Cursor getUnSyncedDeliveredMessages() {
        try {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT * FROM " + MessagesDatabase.MESSAGES_TABLE +
                    " WHERE " + MessagesDatabase.MESSAGE_DIRECTION + " =?  AND "+ MessagesDatabase.MESSAGE_STATUS + " =? " +
                            "AND " + MessagesDatabase.NEED_PUSH +  "=?",
                    new String[]{"1","3", "1"});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "unable to get messages for notification from database issue in DAO file: " + e.getMessage());
        }
        return null;
    }

    public Cursor getUnSyncedSeenMessages() {
        try {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT * FROM " + MessagesDatabase.MESSAGES_TABLE +
                    " WHERE " + MessagesDatabase.MESSAGE_DIRECTION + " =?  AND "
                    + MessagesDatabase.MESSAGE_STATUS + " =? AND " + MessagesDatabase.NEED_PUSH + " =?",new String[]{"1", "2", "1"});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "unable to get messages for notification from database issue in DAO file: " + e.getMessage());
        }
        return null;
    }

    public Cursor getUnSyncedSentMessagesOrLinks() {
        try {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT * FROM " + MessagesDatabase.MESSAGES_TABLE +
                    " WHERE " + MessagesDatabase.MESSAGE_DIRECTION + " =?  AND "+ MessagesDatabase.MESSAGE_STATUS + " =?   AND (" + MessagesDatabase.MESSAGE_TYPE + " = ? OR "
                    + MessagesDatabase.MESSAGE_TYPE + " =? )",new String[]{"0","0","0","1"});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "unable to get messages for notification from database issue in DAO file: " + e.getMessage());
        }
        return null;
    }

    // update operations starts here ----------------------------

    public void updateContactUnreadCount(String contactId)
    {
        try {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
            sqLiteDatabase.execSQL("UPDATE " + CHAT_LIST_TABLE + " SET " +
                    MessagesDatabase.UNREAD_MESSAGES_COUNT + "=" + MessagesDatabase.UNREAD_MESSAGES_COUNT  + " + 1 WHERE " +
                            MessagesDatabase.CHAT_ID +
                            " = ?",
                    new Object[] {contactId});
        }
        catch (SQLException e){
            Log.e(Extras.LOG_MESSAGE, "Error incrementing contact unread count error in db file : " + e.getMessage());
        }
    }

    public void resetUnreadCount(String contactId) {
        try {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
            sqLiteDatabase.execSQL("UPDATE " + CHAT_LIST_TABLE + " SET " +
                            MessagesDatabase.UNREAD_MESSAGES_COUNT + "= ? WHERE " +
                            MessagesDatabase.CHAT_ID +
                            " = ?",
                    new Object[] {"0", contactId});
        }
        catch (SQLException e){
            Log.e(Extras.LOG_MESSAGE, "Error incrementing contact unread count error in db file : " + e.getMessage());
        }
    }



    public boolean updateMessageAStatus(String message_id, int message_status) {
        boolean isSuccess = false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try {
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MessagesDatabase.MESSAGE_STATUS, message_status);
                contentValues.put(MessagesDatabase.NEED_PUSH, MessagesManager.NO_NEED_TO_PUSH_MESSAGE);
                int rowsEffected = sqLiteDatabase.update(MessagesDatabase.MESSAGES_TABLE, contentValues,
                        MessagesDatabase.MESSAGE_ID + "=?", new String[]{message_id});

                if (rowsEffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"Failed to update message seen status " +e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error occurred when accessing the database: " + e.getMessage());
        }
        return isSuccess;
    }

    public boolean updateMessageStarredStatus(String message_id, int starred_status)
    {
        boolean isSuccess = false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try
        {
            sqLiteDatabase.beginTransaction();
            try {
                ContentValues contentValues = new ContentValues();
                contentValues.put(MessagesDatabase.STARRED,starred_status);
                int rowsEffected = sqLiteDatabase.update(MessagesDatabase.MESSAGES_TABLE,contentValues,
                        MessagesDatabase.MESSAGE_ID + "=?",new String[]{message_id});

                if (rowsEffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                }
                else {
                    Log.e(Extras.LOG_MESSAGE,"Failed to update message as starred");
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"Failed to update message as starred "+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database "+e.getMessage());
        }
        return isSuccess;
    }

    public boolean updateMessageAsNoNeedPush(String messageId , int needPush) {
        boolean isSuccess = false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try
        {
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MessagesDatabase.NEED_PUSH, needPush);
                int rowsEffected = sqLiteDatabase.update(MessagesDatabase.MESSAGES_TABLE, contentValues,
                        MessagesDatabase.MESSAGE_ID + "= ?",new String[] {messageId});

                if (rowsEffected > 0)
                {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"Failed to update message status with it's id "+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database "+e.getMessage());
        }
        return isSuccess;
    }

    public boolean updateMessageStatusAsSynced(String messageId , int messageStatus, int needPush)
    {
        boolean isSuccess = false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try
        {
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MessagesDatabase.MESSAGE_STATUS, messageStatus);
                contentValues.put(MessagesDatabase.NEED_PUSH, needPush);
                int rowsEffected = sqLiteDatabase.update(MessagesDatabase.MESSAGES_TABLE, contentValues,
                        MessagesDatabase.MESSAGE_ID + "= ?",new String[] {messageId});

                if (rowsEffected > 0)
                {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"Failed to update message status with it's id "+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database "+e.getMessage());
        }
        return isSuccess;
    }

    public boolean updateMessageSeenStatus(String message_id , int status, long read_timestamp, int needPush) {
        boolean isSuccess = false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try {
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MessagesDatabase.MESSAGE_STATUS,status);
                contentValues.put(MessagesDatabase.READ_TIMESTAMP,read_timestamp);
                contentValues.put(MessagesDatabase.NEED_PUSH, needPush);
                int rowsEffected = sqLiteDatabase.update(MessagesDatabase.MESSAGES_TABLE, contentValues,
                        MessagesDatabase.MESSAGE_ID + "=?", new String[]{message_id});

                if (rowsEffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"Failed to update message seen status"+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database "+e.getMessage());
        }
        return isSuccess;
    }

    public boolean updateMessageAsDeliveredLocally(String message_id , int status, long receive_timestamp) {
        boolean isSuccess = false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try {
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MessagesDatabase.MESSAGE_STATUS, status);
                contentValues.put(MessagesDatabase.RECEIVE_TIMESTAMP, receive_timestamp);
                int rowsEffected = sqLiteDatabase.update(MessagesDatabase.MESSAGES_TABLE, contentValues,
                        MessagesDatabase.MESSAGE_ID + "=?", new String[]{message_id});

                if (rowsEffected > 0)
                {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"Failed to update message seen status"+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database "+e.getMessage());
        }
        return isSuccess;
    }

    public List<String> updateMessageAsDeliveredLocallyAsBatch(List<MarkMessagesAsDeliveredModel> markMessagesAsDeliveredModels) {
        List<String> successfulMessageIds = new ArrayList<>();
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try {
            sqLiteDatabase.beginTransaction();
            for (MarkMessagesAsDeliveredModel markMessagesAsDeliveredModel : markMessagesAsDeliveredModels) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(MessagesDatabase.MESSAGE_STATUS, MessagesManager.MESSAGE_DELIVERED);
                contentValues.put(MessagesDatabase.RECEIVE_TIMESTAMP, markMessagesAsDeliveredModel.getDeliveredTimestamp());
                int rowsEffected = sqLiteDatabase.update(MessagesDatabase.MESSAGES_TABLE, contentValues,
                        MessagesDatabase.MESSAGE_ID + "=?", new String[]{markMessagesAsDeliveredModel.getMessageId()});
                if (rowsEffected > 0) {
                    successfulMessageIds.add(markMessagesAsDeliveredModel.getMessageId());
                }
            }
            if (!successfulMessageIds.isEmpty()) {
                sqLiteDatabase.setTransactionSuccessful();
            } else {
                Log.e(Extras.LOG_MESSAGE,"unable to update message as delivered locally " + successfulMessageIds);
            }
        }
        catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE,"Failed to update message seen status "+e.getMessage());
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database "+e.getMessage());
        }
        finally {
            sqLiteDatabase.endTransaction();
        }
        return successfulMessageIds;
    }

    public List<String> updateSentMessageAsSeenAndDeliveredLocallyBatch(List<MarkMessagesAsDeliveredAndSeenModel>
                                                                           markMessagesAsDeliveredAndSeenModels) {
        List<String> successfulMessageIds = new ArrayList<>();
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try {
            sqLiteDatabase.beginTransaction();
            for (MarkMessagesAsDeliveredAndSeenModel markMessagesAsDeliveredAndSeenModel : markMessagesAsDeliveredAndSeenModels) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(MessagesDatabase.MESSAGE_STATUS, MessagesManager.MESSAGE_SEEN);
                contentValues.put(MessagesDatabase.READ_TIMESTAMP, markMessagesAsDeliveredAndSeenModel.getReadTimestamp());
                contentValues.put(MessagesDatabase.RECEIVE_TIMESTAMP, markMessagesAsDeliveredAndSeenModel.getDeliveredTimestamp());
                int rowsEffected = sqLiteDatabase.update(MessagesDatabase.MESSAGES_TABLE, contentValues,
                        MessagesDatabase.MESSAGE_ID + "=?", new String[]{markMessagesAsDeliveredAndSeenModel.getMessageId()});
                if (rowsEffected > 0) {
                    successfulMessageIds.add(markMessagesAsDeliveredAndSeenModel.getMessageId());
                }
            }
            if (!successfulMessageIds.isEmpty()) {
                sqLiteDatabase.setTransactionSuccessful();
            }
        }
        catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE,"unable to update message as delivered and seen status locally " + e.getMessage());
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database " + e.getMessage());
        }
        finally {
            sqLiteDatabase.endTransaction();
        }
        return successfulMessageIds;
    }

    public boolean updateSentMessageAsSeenAndDeliveredLocally(String message_id, int status,
                                                              long received_timestamp, long read_timestamp)
    {
        boolean isSuccess = false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try
        {
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MessagesDatabase.MESSAGE_STATUS, status);
                contentValues.put(MessagesDatabase.READ_TIMESTAMP, read_timestamp);
                contentValues.put(MessagesDatabase.RECEIVE_TIMESTAMP,received_timestamp);
                int rowsEffected = sqLiteDatabase.update(MessagesDatabase.MESSAGES_TABLE, contentValues,
                        MessagesDatabase.MESSAGE_ID + "=?", new String[]{message_id});
                if (rowsEffected > 0)
                {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"unable to update message as delivered and seen status locally " + e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database " + e.getMessage());
        }
        return isSuccess;
    }

    // Messages Table Functions Ends ----------------------



    // Chat List Table Functions Start --------------



    // Inserting into ChatList ----------


    // Retrieve Chat List from sqlite ...


    public Cursor getChatList() {
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getReadableDb();
            String query = "SELECT " +
                    "c.chat_id, c.sort_last_message_time, c.chat_archive, " +
                    "c.chat_lock, c.is_chat_locked, c.chat_locked_time, c.chat_last_scroll_position, " +
                    "c.chat_read_receipts, c.unread_messages_count, " + // moved from user_contacts to chat_list
                    "m.message, m.message_type, m.timestamp, m.receive_timestamp, m.message_direction, m.message_status " +
                    "FROM chat_list c " +
                    "LEFT JOIN messages m ON c.chat_last_message_id = m.sqlite_message_id " +
                    "WHERE c.chat_archive = ? " +
                    "ORDER BY c.sort_last_message_time DESC";
            return sqLiteDatabase.rawQuery(query, new String[] {"0"});
        } catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get chat list caught sql exception " + e.getMessage());
        }
        return null;
    }

    public Cursor getRecentChats() {
        try {
            SQLiteDatabase db = messagesDatabase.getReadableDb();
            String query = "SELECT chat_id FROM chat_list ORDER BY sort_last_message_time DESC LIMIT 5";
            return db.rawQuery(query, null);
        } catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get recent chats: " + e.getMessage());
        }
        return null;
    }

    public Cursor getArchiveChatList() {
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getReadableDb();
            String query = "SELECT " +
                    "c.chat_id, c.sort_last_message_time, c.chat_archive, " +
                    "c.chat_lock, c.is_chat_locked, c.chat_locked_time, c.chat_last_scroll_position, " +
                    "c.chat_read_receipts, c.unread_messages_count, " + // moved from user_contacts to chat_list
                    "m.message, m.message_type, m.timestamp, m.receive_timestamp, m.message_direction, m.message_status " +
                    "FROM chat_list c " +
                    "LEFT JOIN messages m ON c.chat_last_message_id = m.sqlite_message_id " +
                    "WHERE c.chat_archive = ? " +
                    "ORDER BY c.sort_last_message_time DESC";
            return sqLiteDatabase.rawQuery(query, new String[]{"1"});
        } catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get chat list caught sql exception " + e.getMessage());
        }
        return null;
    }


    public Cursor getMessageRequestChatList() {
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getReadableDb();
            String query = "SELECT " +
                    "c.chat_id, c.sort_last_message_time, c.chat_archive, " +
                    "c.chat_lock, c.is_chat_locked, c.chat_locked_time, c.chat_last_scroll_position, " +
                    "c.chat_read_receipts, c.unread_messages_count, " + // moved from user_contacts to chat_list
                    "m.message, m.message_type, m.timestamp, m.receive_timestamp, m.message_direction, m.message_status " +
                    "FROM chat_list c " +
                    "LEFT JOIN messages m ON c.chat_last_message_id = m.sqlite_message_id " +
                    "WHERE c.chat_archive = ? " +
                    "ORDER BY c.sort_last_message_time DESC";
            return sqLiteDatabase.rawQuery(query, new String[]{"2"});
        } catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get chat list caught sql exception " + e.getMessage());
        }
        return null;
    }

    public boolean updateLastMessageForTheContact(String contactId, String lastMessageId) {
        boolean         isSuccess       =   false;
        SQLiteDatabase  sqLiteDatabase  =   messagesDatabase.getWritableDb();
        try {
            sqLiteDatabase.beginTransaction();
            ContentValues   contentValues   =   new ContentValues();
            contentValues.put(MessagesDatabase.CHAT_LAST_MESSAGE_ID_FK, lastMessageId);

            int rowsEffected    =   sqLiteDatabase.update(CHAT_LIST_TABLE, contentValues, MessagesDatabase.CHAT_ID + "=?",
                                        new String[] {contactId});
            if (rowsEffected > 0) {
                sqLiteDatabase.setTransactionSuccessful();
                isSuccess   =   true;
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to updateLastMessage Id for the contact caught exception " + e.getMessage());
        }
        finally {
            sqLiteDatabase.endTransaction();
        }
        return isSuccess;
    }

    public boolean updateChatLockedStatusAndTime(String contact_id,int is_chat_locked,long chat_locked_time) {
        boolean isSuccess = false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try {
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues contentValues    = new ContentValues();
                contentValues.put(MessagesDatabase.IS_CHAT_LOCKED,is_chat_locked);
                contentValues.put(MessagesDatabase.CHAT_LOCKED_TIME,chat_locked_time);
                int rowsEffected = sqLiteDatabase.update(CHAT_LIST_TABLE,contentValues, MessagesDatabase.CHAT_ID + "=?", new String[]{contact_id});
                if (rowsEffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                }
                else {
                    Log.e(Extras.LOG_MESSAGE,"unable to update chat locked status and time");
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"unable to update chat lock and time "+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database "+e.getMessage());
        }
        return isSuccess;
    }

    public boolean updateChatReadReceipts(String contact_id, int read_receipts) {
        boolean isSuccess = false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try {
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues contentValues    = new ContentValues();
                contentValues.put(MessagesDatabase.CHAT_READ_RECEIPTS,read_receipts);
                int rowsEffected = sqLiteDatabase.update(CHAT_LIST_TABLE,contentValues, MessagesDatabase.CHAT_ID + "=?", new String[]{contact_id});
                if (rowsEffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                }
                else {
                    Log.e(Extras.LOG_MESSAGE,"unable to update read receipts error logged in DAO file");
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"unable to update read receipts error logged in DAO file"+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database "+e.getMessage());
        }
        return isSuccess;
    }

    public Cursor getLockedChatDetails(String contactId) {

        try {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT "+ MessagesDatabase.CHAT_LOCK + "," + MessagesDatabase.IS_CHAT_LOCKED + "," + MessagesDatabase.CHAT_LOCKED_TIME + " FROM "+ CHAT_LIST_TABLE+" WHERE "+ MessagesDatabase.CHAT_ID+" =? ",new String[] {contactId});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get from the chat list error logged in DAO file: " + e.getMessage());
        }
        return null;
    }

    public Cursor getDetailsFromChatList(String contact_id) {
        try {
            SQLiteDatabase  sqLiteDatabase   =   messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT * FROM " + CHAT_LIST_TABLE + " WHERE " +
                    MessagesDatabase.CHAT_ID + " =? ", new String[]{contact_id});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get from the chat list error logged in DAO file: " + e.getMessage());
        }
        return null;
    }

    public Cursor getChatLockAndReadReceiptsFromDatabase(String contactId) {
        try {
            SQLiteDatabase  sqLiteDatabase   =   messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT " + MessagesDatabase.CHAT_READ_RECEIPTS + ", " + MessagesDatabase.CHAT_LOCK +
                    " FROM " + CHAT_LIST_TABLE + " WHERE " +
                    MessagesDatabase.CHAT_ID + " =? ", new String[]{contactId});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get from the chat list error logged in DAO file: " + e.getMessage());
        }
        return null;
    }

    public Cursor getContactMessagePrivacyDetailsFromChatList(String contact_id) {
        try {
            SQLiteDatabase  sqLiteDatabase   =   messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT " + MessagesDatabase.CHAT_READ_RECEIPTS + " ," +
                    MessagesDatabase.CHAT_ARCHIVE + " FROM " + CHAT_LIST_TABLE + " WHERE " +
                    MessagesDatabase.CHAT_ID + " =? ", new String[]{contact_id});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get from the chat list error logged in DAO file: " + e.getMessage());
        }
        return null;
    }

    public Cursor getLastMessageDetailsFromId(String last_message_id)
    {
        try
        {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT * FROM " + MessagesDatabase.MESSAGES_TABLE + " WHERE " +
                    MessagesDatabase.SQLITE_MESSAGE_ID + "=?",new String[]{last_message_id});
        }
        catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE, "Chat list Insertion failed, result: " + e.getMessage());
        }
        return null;
    }


    // Get Archived Chat List ...

    public Cursor getLastMessageId(String receiver_id)
    {
        try
        {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT " + MessagesDatabase.SQLITE_MESSAGE_ID + " FROM " + MessagesDatabase.MESSAGES_TABLE +
                            " WHERE receiver_id = ?  ORDER BY sqlite_message_id DESC LIMIT 1 ",
                    new String[]{receiver_id});
        }
        catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE, "Chat list Insertion failed, result: " + e.getMessage());
        }
        return null;
    }

    public Cursor getMessageDetailsFromId(String message_id) {
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT * FROM " + MessagesDatabase.MESSAGES_TABLE +
                            " WHERE message_id = ?",
                    new String[]{message_id});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get the message details from it's ID error logged in DAO file : " + e.getMessage());
        }
        return null;
    }

    public Cursor getMessageDetailsAndChatArchiveFromId(String messageId, String chatId) {
        try {
            SQLiteDatabase db = messagesDatabase.getReadableDb();

            String query =
                    "SELECT m." + MessagesDatabase.MESSAGE + ", " +
                            "c.chat_archive " +
                            "FROM " + MessagesDatabase.MESSAGES_TABLE + " m " +
                            "LEFT JOIN " + MessagesDatabase.CHAT_LIST_TABLE + " c " +
                            "c.chat_id = ? AND " +
                            "WHERE m.message_id = ?";

            return db.rawQuery(query, new String[]{chatId, messageId});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,
                    "Unable to get message + chat archive. Error: " + e.getMessage());
            return null;
        }
    }



    // update records in Chat List Table  ...

    public boolean updateChatArchiveStatus(String chat_id ,int status) {
        boolean isSuccess = false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try
        {
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MessagesDatabase.CHAT_ARCHIVE,status);
                int rowsEffected = sqLiteDatabase.update(CHAT_LIST_TABLE, contentValues, MessagesDatabase.CHAT_ID + "=?",new String[]{chat_id});
                if (rowsEffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                }
                else {
                    Log.e(Extras.LOG_MESSAGE,"unable to update archive status error logged in DAO file");
                    sqLiteDatabase.endTransaction();
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"unable to update archive status error logged in DAO file"+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database "+e.getMessage());
        }
        return isSuccess;
    }

    public boolean updateMessageAsEdited(String message_id,String message,int message_status,int edited_status) {
        boolean isSuccess = false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try
        {
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MessagesDatabase.MESSAGE,message);
                contentValues.put(MessagesDatabase.MESSAGE_STATUS,message_status);
                contentValues.put(MessagesDatabase.EDIT_STATUS,edited_status);
                int rowsEffected = sqLiteDatabase.update(MessagesDatabase.MESSAGES_TABLE, contentValues, MessagesDatabase.MESSAGE_ID + "=?",new String[]{message_id});
                if (rowsEffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                }
                else {
                    Log.e(Extras.LOG_MESSAGE,"unable to update the message error logged in DAO file");
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"unable to update the message error logged in DAO file"+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database "+e.getMessage());
        }
        return isSuccess;
    }

    public boolean updateChatLockStatus(String chat_id ,int status)
    {
        boolean isSuccess = false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try
        {
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MessagesDatabase.CHAT_LOCK, status);
                int rowsEffected = sqLiteDatabase.update(CHAT_LIST_TABLE, contentValues,
                        MessagesDatabase.CHAT_ID + "=?" ,new String[]{chat_id});
                if (rowsEffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                }
                else {
                    Log.e(Extras.LOG_MESSAGE,"unable to update chat lock status error logged in DAO file");
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"unable to update chat lock  error logged in DAO file"+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database "+e.getMessage());
        }
        return isSuccess;
    }

    // check for duplicate record in chat list table ...

    public boolean checkChatListId(String id) {
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getReadableDb();
            String query = "SELECT chat_id FROM " + CHAT_LIST_TABLE + " WHERE " + MessagesDatabase.CHAT_ID + "=?";
            try (Cursor cursor = sqLiteDatabase.rawQuery(query, new String[]{id})) {
                return cursor.moveToFirst(); // returns true if at least one row is found
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to check the chat list ID: " + e.getMessage());
        }
        return false;
    }

    public Cursor checkChatExistsOrNot(String id) {
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getReadableDb();
            String query = "SELECT chat_id FROM " + CHAT_LIST_TABLE + " WHERE " + MessagesDatabase.CHAT_ID + "=?";
            return sqLiteDatabase.rawQuery(query, new String[]{id});
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to check the chat list ID: " + e.getMessage());
        }
        return null;
    }

    public boolean checkArchivedOrNot(String contactId) {
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getReadableDb();
            String query = "SELECT chat_id FROM " + CHAT_LIST_TABLE + " WHERE " + MessagesDatabase.CHAT_ID + "=? AND " +
                    MessagesDatabase.CHAT_ARCHIVE + " =?";
            try (Cursor cursor = sqLiteDatabase.rawQuery(query, new String[]{contactId, "1"})) {
                return cursor.moveToFirst();
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to check the chat list ID: " + e.getMessage());
        }
        return false;
    }

    public Cursor anyUnreadCountsInArchiveChats() {
        try {
            SQLiteDatabase  sqLiteDatabase  =   messagesDatabase.getReadableDb();
            String          query           =   "SELECT 1 FROM " + MessagesDatabase.CHAT_LIST_TABLE + " WHERE "
                    + MessagesDatabase.CHAT_ARCHIVE + " = 1 AND " + MessagesDatabase.UNREAD_MESSAGES_COUNT + " > 0 LIMIT 1";
            return sqLiteDatabase.rawQuery(query, null);
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to check the chat list ID: " + e.getMessage());
        }
        return null;
    }

    // Chat List Table Functions End  --------------


    // insert into deleted messages table



    public boolean checkMessageDuplicate(String id_post) {
        Cursor cursor = null;
        try {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getReadableDb();

            String Query = "SELECT * FROM " + MessagesDatabase.MESSAGES_TABLE +" WHERE "+ MessagesDatabase.MESSAGE_ID + "=?" ;
             cursor = sqLiteDatabase.rawQuery(Query, new String[]{id_post});
            if(cursor.getCount() <= 0){
                cursor.close();
                return false;
            }
            cursor.close();
            return true;
        }
        catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE,"Unable to check message duplicate error logged in DAO file "+e.getMessage());
            return false;
        }
    }

    public boolean deleteMessage(String message_id) {
        boolean isDeleted = false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try
        {
            try {
                sqLiteDatabase.beginTransaction();
                int rowsEffected = sqLiteDatabase.delete(MessagesDatabase.MESSAGES_TABLE,"message_id = ?",new String[]{message_id});
                if (rowsEffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isDeleted = true;
                }
                else {
                    Log.e(Extras.LOG_MESSAGE,"unable to delete the message");
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"unable to delete the message "+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database "+e.getMessage());
        }
        return isDeleted;
    }

    // Account Deletion ------------

    public boolean deleteAccount() {
        boolean isDeleted   =   false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try  {

            try {
                sqLiteDatabase.beginTransaction();
                sqLiteDatabase.delete(MessagesDatabase.MESSAGES_TABLE,null,null);
                sqLiteDatabase.delete(CHAT_LIST_TABLE, null, null);
                sqLiteDatabase.setTransactionSuccessful();
                isDeleted   =   true;

            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"unable to delete the the messages and chat lists "+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"unable to delete the the messages and chat lists "+e.getMessage());
        }
        return isDeleted;
    }

    public boolean deleteChatAndMessages(String contactId) {
        boolean isDeleted = false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try {

            try {
                sqLiteDatabase.beginTransaction();

                int chatRows    =   sqLiteDatabase.delete(CHAT_LIST_TABLE, MessagesDatabase.CHAT_ID + "=?",
                                    new String[]{contactId});
                int messageRows =   sqLiteDatabase.delete(MessagesDatabase.MESSAGES_TABLE,
                            MessagesDatabase.RECEIVER_ID + "=?", new String[]{contactId});

                if (chatRows > 0 || messageRows > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isDeleted = true;
                } else {
                    Log.e(Extras.LOG_MESSAGE, "Unable to delete chat or messages");
                }
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error deleting chat or messages: " + e.getMessage());
        }

        return isDeleted;
    }

    public boolean deleteMessages(String contactId) {
        boolean isDeleted = false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try
        {
            try {
                sqLiteDatabase.beginTransaction();
                int rowsEffected = sqLiteDatabase.delete(MessagesDatabase.MESSAGES_TABLE, MessagesDatabase.RECEIVER_ID + "=? ",new String[]{contactId});
                if (rowsEffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isDeleted = true;
                }
                else {
                    Log.e(Extras.LOG_MESSAGE,"unable to delete the message");
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"unable to delete the message "+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database "+e.getMessage());
        }
        return isDeleted;
    }

    // First Message details of an particular user with us ...

    public Cursor getFirstMessageDetails(String contactId) {
        try {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT " + MessagesDatabase.TIMESTAMP + ", " + MessagesDatabase.RECEIVE_TIMESTAMP +
                    ", " + MessagesDatabase.MESSAGE_DIRECTION + " FROM " + MessagesDatabase.MESSAGES_TABLE +
                    " WHERE receiver_id = ? ORDER BY sqlite_message_id ASC ",new String[] {contactId});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to get first message details error logged in DAO file "+e.getMessage());
        }
        return null;
    }

    public Cursor getUnSyncedMessagesOfContact(String contactId) {
        try {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT * FROM " + MessagesDatabase.MESSAGES_TABLE + " WHERE "
                    + MessagesDatabase.MESSAGE_STATUS + " = ? AND " + MessagesDatabase.RECEIVER_ID + " =?", new String[]{"0", contactId});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to get UnsyncedMessages error logged in DAO file "+e.getMessage());
        }
        return null;
    }

    // message time details ...

    public Cursor MessageTimeDetails(String id) {
        try {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery(" SELECT "+ MessagesDatabase.TIMESTAMP + "," + MessagesDatabase.RECEIVE_TIMESTAMP + "," +
                    MessagesDatabase.READ_TIMESTAMP + "," + MessagesDatabase.MESSAGE + "," + MessagesDatabase.MESSAGE_STATUS +
                    " FROM " + MessagesDatabase.MESSAGES_TABLE + " WHERE " + MessagesDatabase.MESSAGE_ID + "=?",new String[]{id});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to get message time details error logged in DAO file "+e.getMessage());
        }
        return null;
    }

    public Cursor getStarredMessages(String receiver_id) {
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getReadableDb();

            String query = "SELECT m.*, " +
                    "r.message AS replied_message_text, " +
                    "r.message_direction AS replied_message_direction " +
                    "FROM " + MessagesDatabase.MESSAGES_TABLE + " m " +
                    "LEFT JOIN " + MessagesDatabase.MESSAGES_TABLE + " r " +
                    "ON m.reply_to = r.message_id " +
                    "WHERE m.receiver_id = ? " +
                    "AND m." + MessagesDatabase.STARRED + " = ? " +
                    "ORDER BY m.sqlite_message_id ASC";

            String[] selectionArgs = new String[]{
                    receiver_id,
                    String.valueOf(MessagesManager.MESSAGE_IS_STARRED)
            };

            return sqLiteDatabase.rawQuery(query, selectionArgs);

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get starred messages error logged in DAO: " + e.getMessage());
        }

        return null;
    }

    // shared link messages

    public Cursor getSharedLinksMessages(String receiver_id) {
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getReadableDb();

            String query = "SELECT m.*, " +
                    "r.message AS replied_message_text, " +
                    "r.message_direction AS replied_message_direction " +
                    "FROM " + MessagesDatabase.MESSAGES_TABLE + " m " +
                    "LEFT JOIN " + MessagesDatabase.MESSAGES_TABLE + " r " +
                    "ON m.reply_to = r.message_id " +
                    "WHERE m.receiver_id = ? " +
                    "AND m." + MessagesDatabase.MESSAGE_TYPE + " = ? " +
                    "ORDER BY m.sqlite_message_id ASC";

            String[] selectionArgs = new String[]{
                    receiver_id,
                    String.valueOf(MessagesManager.LINK_MESSAGE)
            };

            return sqLiteDatabase.rawQuery(query, selectionArgs);

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get shared links messages: " + e.getMessage());
        }

        return null;
    }


    // get messages by date start

    public Cursor getMessagesByDate(String contact_id, long selectedDateTimestamp) {
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getReadableDb();
            long startOfDay = getStartOfDay(selectedDateTimestamp);
            long endOfDay = getEndOfDay(selectedDateTimestamp);

            String query = "SELECT m.*, " +
                    "r.message AS replied_message_text, " +
                    "r.message_direction AS replied_message_direction " +
                    "FROM " + MessagesDatabase.MESSAGES_TABLE + " m " +
                    "LEFT JOIN " + MessagesDatabase.MESSAGES_TABLE + " r " +
                    "ON m.reply_to = r.message_id " +
                    "WHERE m.receiver_id = ? " +
                    "AND m." + MessagesDatabase.TIMESTAMP + " BETWEEN ? AND ? " +
                    "ORDER BY m.sqlite_message_id ASC";

            return sqLiteDatabase.rawQuery(query, new String[]{contact_id, String.valueOf(startOfDay), String.valueOf(endOfDay)});

        } catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get messages by date from database: " + e.getMessage());
        }
        return null;
    }



    private long getStartOfDay(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getEndOfDay(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    public Cursor getMessagesByText(String contact_id, String searchText) {
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getReadableDb();
            String query = "SELECT m.*, " +
                    "r.message AS replied_message_text, " +
                    "r.message_direction AS replied_message_direction " +
                    "FROM " + MessagesDatabase.MESSAGES_TABLE + " m " +
                    "LEFT JOIN " + MessagesDatabase.MESSAGES_TABLE + " r " +
                    "ON m.reply_to = r.message_id " +
                    "WHERE m.receiver_id = ? " +
                    "AND m." + MessagesDatabase.MESSAGE + " LIKE ? " +
                    "ORDER BY m.sqlite_message_id ASC";

            String[] selectionArgs = new String[]{
                    contact_id,
                    "%" + searchText + "%"  // wildcard match
            };
            return sqLiteDatabase.rawQuery(query, selectionArgs);

        } catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get messages by text: " + e.getMessage());
        }

        return null;
    }

    public Cursor getChatsByContactIds(ArrayList<String> contactIds) {
        if (contactIds == null || contactIds.isEmpty()) {
            return null;
        }

        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getReadableDb();

            // Build placeholders for IN clause
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < contactIds.size(); i++) {
                placeholders.append("?");
                if (i < contactIds.size() - 1) {
                    placeholders.append(",");
                }
            }

            String query = "SELECT " +
                    "c.chat_id, c.sort_last_message_time, c.chat_archive, " +
                    "c.chat_lock, c.is_chat_locked, c.chat_locked_time, c.chat_last_scroll_position, " +
                    "c.chat_read_receipts, c.unread_messages_count, " + // moved from user_contacts to chat_list
                    "m.message, m.message_type, m.timestamp, m.receive_timestamp, m.message_direction, m.message_status " +
                    "FROM chat_list c " +
                    "LEFT JOIN messages m ON c.chat_last_message_id = m.sqlite_message_id " +
                    "WHERE c.chat_id IN (" + placeholders + ") " +
                    "AND c.chat_archive = ? " +
                    "ORDER BY c.sort_last_message_time DESC";

            String[] selectionArgs = new String[contactIds.size() + 1];
            for (int i = 0; i < contactIds.size(); i++) {
                selectionArgs[i] = contactIds.get(i);
            }
            selectionArgs[contactIds.size()] = "0";
            return sqLiteDatabase.rawQuery(query, selectionArgs);
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Unbale to display searched chats error in dao " + e.getMessage());
            return null;
        }
    }

    //  get messages by date end

    // get messages with text on a particular date start -----------

    public Cursor getMessagesWithTextOnDate(String contact_id, String searchText, long selectedDateTimestamp) {
        try {
            SQLiteDatabase sqLiteDatabase = messagesDatabase.getReadableDb();

            long startOfDay = getStartOfDay(selectedDateTimestamp);
            long endOfDay = getEndOfDay(selectedDateTimestamp);

            String query = "SELECT m.*, " +
                    "r.message AS replied_message_text, " +
                    "r.message_direction AS replied_message_direction " +
                    "FROM " + MessagesDatabase.MESSAGES_TABLE + " m " +
                    "LEFT JOIN " + MessagesDatabase.MESSAGES_TABLE + " r " +
                    "ON m.reply_to = r.message_id " +
                    "WHERE m.receiver_id = ? " +
                    "AND m." + MessagesDatabase.TIMESTAMP + " BETWEEN ? AND ? " +
                    "AND m." + MessagesDatabase.MESSAGE + " LIKE ? " +
                    "ORDER BY m.sqlite_message_id ASC";

            String[] selectionArgs = new String[]{
                    contact_id,
                    String.valueOf(startOfDay),
                    String.valueOf(endOfDay),
                    "%" + searchText + "%"   // wildcard match
            };

            return sqLiteDatabase.rawQuery(query, selectionArgs);

        } catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get messages with text on date: " + e.getMessage());
        }

        return null;
    }


    // Archived status records ...


    public Cursor getArchiveStatus(String chat_id) {
        try {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT chat_archive FROM " + CHAT_LIST_TABLE + " WHERE " + MessagesDatabase.CHAT_ID + "=?"
                    ,new String[]{chat_id});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to get archive status error logged in DAO file "+e.getMessage());
        }
        return null;
    }

    //  --------------------------------- Scheduled Messages Table code Starts Here ----------------------------------------------

    public boolean insertScheduleMessage(String messageId, String receiverId, String message,
                                         long scheduledTimestamp, int messageStatus) {
        boolean isSuccess = false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try
        {
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues values = new ContentValues();
                values.put(MessagesDatabase.SCHEDULED_MESSAGE_ID, messageId);
                values.put(MessagesDatabase.SCHEDULED_MESSAGE_RECEIVER_ID, receiverId);
                values.put(MessagesDatabase.SCHEDULED_MESSAGE, message);
                values.put(MessagesDatabase.SCHEDULED_MESSAGE_TIMESTAMP, scheduledTimestamp);
                values.put(MessagesDatabase.SCHEDULED_MESSAGE_STATUS, messageStatus);

                long result = sqLiteDatabase.insert(MessagesDatabase.SCHEDULED_MESSAGE_TABLE, null, values);

                if (result != -1) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                } else {
                    Log.e(Extras.LOG_MESSAGE, "Insertion failed into scheduled message , result: " + result);
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE, "Error inserting schedule message into database: " + e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error accessing the database: " + e.getMessage());
        }
        return isSuccess;
    }

    public Cursor getScheduledMessages(String receiver_id) {
        try {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT * FROM " + MessagesDatabase.SCHEDULED_MESSAGE_TABLE + " WHERE " + MessagesDatabase.SCHEDULED_MESSAGE_RECEIVER_ID + "= ? ORDER BY "+ MessagesDatabase.SCHEDULED_MESSAGE_SQLITE_ID +" ASC ",
                    new String[]{receiver_id});
        }
        catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE, "unable to get scheduled messages from database issue in DAO file: " + e.getMessage());
        }
        return null;
    }

    public Cursor getScheduledMessageDetailsFromId(String message_id)
    {
        try
        {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT * FROM " + MessagesDatabase.SCHEDULED_MESSAGE_TABLE +
                            " WHERE " + MessagesDatabase.SCHEDULED_MESSAGE_ID + "= ?",
                    new String[]{message_id});
        }
        catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE, "Unable to get the scheduled message details from it's ID error logged in DAO file : " + e.getMessage());
        }
        return null;
    }

    public Cursor getScheduledMessagesCountOfContact(String contactId)
    {
        try
        {
            SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getReadableDb();
             return sqLiteDatabase.rawQuery("SELECT COUNT(*) FROM " + MessagesDatabase.SCHEDULED_MESSAGE_TABLE + " WHERE "+ MessagesDatabase.SCHEDULED_MESSAGE_RECEIVER_ID + " = ? "
                    ,new String[]{contactId});
        }
        catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE, "Unable to retrieve the count of scheduled messages for a single contact, result: " + e.getMessage());
        }
        return null;
    }

    public boolean deleteScheduledMessage(String message_id) {

        boolean isDeleted = false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try
        {
            try {
                sqLiteDatabase.beginTransaction();
                int rowsEffected = sqLiteDatabase.delete(MessagesDatabase.SCHEDULED_MESSAGE_TABLE, MessagesDatabase.SCHEDULED_MESSAGE_ID + " =?",new String[]{message_id});
                if (rowsEffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isDeleted = true;
                }
                else {
                    Log.e(Extras.LOG_MESSAGE,"unable to delete scheduled message error logged in DAO file");
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"unable to delete scheduled message error logged in DAO file "+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database "+e.getMessage());
        }
        return isDeleted;
    }

    public boolean updateScheduledMessageText(String message_id ,String message)
    {
        boolean isSuccess = false;
        SQLiteDatabase sqLiteDatabase   =   messagesDatabase.getWritableDb();
        try
        {
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues contentValues = new ContentValues();
                contentValues.put(MessagesDatabase.SCHEDULED_MESSAGE,message);
                int rowsEffected = sqLiteDatabase.update(MessagesDatabase.SCHEDULED_MESSAGE_TABLE, contentValues, MessagesDatabase.SCHEDULED_MESSAGE_ID + "=?",new String[]{message_id});
                if (rowsEffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                }
                else {
                    Log.e(Extras.LOG_MESSAGE,"unable to update the scheduled message error logged in DAO file");
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"unable to update the scheduled message error logged in DAO file "+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database "+e.getMessage());
        }
        return isSuccess;
    }
}
