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

    public boolean insertMessage(String messageId, int messageDirection, String receiverId,
                                 String message, int messageStatus, int needPush,
                                 long timestamp, long receiveTimestamp, long readTimestamp,
                                 int starredStatus, int editedStatus, int messageType,
                                 double latitude, double longitude, int isReply,
                                 String replyToMessageId, int chatArchive) {
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

                long result = sqLiteDatabase.insert(MessagesDatabase.MESSAGES_TABLE, null, values);

                Log.e(Extras.LOG_MESSAGE, "values are " + values);

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
                    Log.e(Extras.LOG_MESSAGE, "Insertion failed, result: " + result);
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
