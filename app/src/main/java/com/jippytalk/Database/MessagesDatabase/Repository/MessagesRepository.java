package com.jippytalk.Database.MessagesDatabase.Repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;


import com.jippytalk.Database.ContactsDatabase.DAO.ContactsDatabaseDAO;
import com.jippytalk.Database.ContactsDatabase.Repository.ContactsRepository;
import com.jippytalk.Database.MessagesDatabase.Model.MarkMessagesAsDeliveredAndSeenModel;
import com.jippytalk.Database.MessagesDatabase.Model.MarkMessagesAsDeliveredModel;
import com.jippytalk.Database.MessagesDatabase.Model.MarkMessagesAsSeenModel;
import com.jippytalk.Managers.ChatManager;
import com.jippytalk.WebSocketConnection;
import com.jippytalk.ServiceLocators.DatabaseServiceLocator;
import com.jippytalk.Common.SingleLiveEvent;
import com.jippytalk.Database.MessagesDatabase.MessagesDatabaseDAO;
import com.jippytalk.Database.MessagesDatabase.MessagesDatabase;
import com.jippytalk.Extras;
import com.jippytalk.Managers.MessagesManager;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.MessageInfo.Model.MessageInfoModel;
import com.jippytalk.Messages.Model.MessageInsertionModel;
import com.jippytalk.Messages.Model.MessageModal;
import com.jippytalk.ServiceHandlers.Models.UnSyncedSeenMessagesModel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.function.Consumer;

public class MessagesRepository {

    private final Context                                               context;
    private static volatile MessagesRepository                          INSTANCE;
    private final DatabaseServiceLocator                                databaseServiceLocator;
    private final WebSocketConnection                                   webSocketConnection;
    private final MessagesDatabaseDAO                                   messagesDatabaseDAO;
    private final ContactsRepository                                    contactsRepository;
    private final ExecutorService                                       writeExecutor;
    private final ExecutorService                                       readExecutor;
    private final SharedPreferences                                     sharedPreferences;
    private final String                                                userId;
    private final MutableLiveData<ArrayList<MessageModal>>              messagesMutableData                 =   new MutableLiveData<>();
    private final MutableLiveData<ArrayList<MessageModal>>              messageStatusUpdateMutable          =   new MutableLiveData<>();
    private final MutableLiveData<ArrayList<UnSyncedSeenMessagesModel>> unSyncUnknownMessagesMutableData    =   new MutableLiveData<>();
    private final MutableLiveData<ArrayList<UnSyncedSeenMessagesModel>> unSyncSeenMessagesMutableData       =   new MutableLiveData<>();
    private final SingleLiveEvent<MessageInsertionModel>                messageInsertionModel               =   new SingleLiveEvent<>();
    private final MutableLiveData<ArrayList<MessageModal>>              searchMessagesMutableData           =   new MutableLiveData<>();
    private final MutableLiveData<ArrayList<MessageModal>>              starredMessagesMutableData          =   new MutableLiveData<>();
    private final MutableLiveData<ArrayList<MessageModal>>              sharedLinkMessagesMutableData       =   new MutableLiveData<>();
    private final SingleLiveEvent<Integer>                              deleteMessageStatusMutableData      =   new SingleLiveEvent<>();
    private final SingleLiveEvent<Integer>                              starredMessageStatusMutableData     =   new SingleLiveEvent<>();
    private final MutableLiveData<Long>                                 contactFirstMessageTimeMutable      =   new MutableLiveData<>();
    private final SingleLiveEvent<Boolean>                              acceptedContactChatMutable          =   new SingleLiveEvent<>();


    public LiveData<ArrayList<MessageModal>> getMessagesLiveData() {
        return messagesMutableData;
    }

    public LiveData<ArrayList<MessageModal>> getMessageStatusUpdateLiveData() {
        return messageStatusUpdateMutable;
    }

    public LiveData<ArrayList<MessageModal>> getStarredMessagesLiveData() {
        return starredMessagesMutableData;
    }

    public LiveData<ArrayList<MessageModal>> getSearchMessagesLiveData() {
        return searchMessagesMutableData;
    }

    public LiveData<ArrayList<MessageModal>> getSharedLinksLiveData() {
        return sharedLinkMessagesMutableData;
    }

    public LiveData<ArrayList<UnSyncedSeenMessagesModel>> getUnSyncUnKnownMessagesData() {
        return unSyncUnknownMessagesMutableData;
    }

    public LiveData<ArrayList<UnSyncedSeenMessagesModel>> getUnsyncSeenMessagesLiveData() {
        return unSyncSeenMessagesMutableData;
    }

    public LiveData<Integer> getDeleteMessageStatusLiveData() {
        return deleteMessageStatusMutableData;
    }

    public LiveData<Integer> getMessageStarredStatusLiveData() {
        return starredMessageStatusMutableData;
    }

    public LiveData<Long> getContactFirstMessageTimeLiveData() {
        return contactFirstMessageTimeMutable;
    }

    public LiveData<MessageInsertionModel> getMessageInsertionStatusLiveData() {
        return messageInsertionModel;
    }

    public LiveData<Boolean> getAcceptedContactChatLiveData() {
        return acceptedContactChatMutable;
    }


    public MessagesRepository(Context context, DatabaseServiceLocator databaseServiceLocator, ContactsRepository contactsRepository,
                              SharedPreferences sharedPreferences) {
        this.context                                =   context.getApplicationContext();
        this.databaseServiceLocator                 =   databaseServiceLocator;
        messagesDatabaseDAO                         =   databaseServiceLocator.getMessagesDatabaseDAO();
        writeExecutor                               =   databaseServiceLocator.getWriteExecutor();
        readExecutor                                =   databaseServiceLocator.getReadExecutor();
        this.contactsRepository                     =   contactsRepository;
        this.sharedPreferences                      =   sharedPreferences;
        webSocketConnection                         =   WebSocketConnection.getInstance(context, sharedPreferences);
        userId                                      =   sharedPreferences.getString(SharedPreferenceDetails.USERID, "");
    }

    public static MessagesRepository getInstance(Context context, DatabaseServiceLocator databaseServiceLocator,
                                                 ContactsRepository contactsRepository, SharedPreferences sharedPreferences) {
        if (INSTANCE == null) {
            synchronized (MessagesRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE    =   new MessagesRepository(context.getApplicationContext(), databaseServiceLocator,
                                    contactsRepository, sharedPreferences);
                }
            }
        }
        return INSTANCE;
    }



    public void insertMessageToLocalStorage(String messageId, int messageDirection, String receiverId,
                                            String message, int messageStatus, int needPush,
                                            long sentTimestamp, long receivedTimestamp, long readTimestamp,
                                            int isStarred, int editedStatus, int messageType,
                                            double latitude, double longitude, int isReply,
                                            String replyToMsgId, int chatArchive) {
        writeExecutor.execute(() -> {
            try {
                boolean inserted = messagesDatabaseDAO.insertMessage(
                        messageId, messageDirection, receiverId,
                        message, messageStatus, needPush,
                        sentTimestamp, receivedTimestamp, readTimestamp,
                        isStarred, editedStatus, messageType,
                        latitude, longitude, isReply,
                        replyToMsgId, chatArchive);
                if (inserted) {
                    messageInsertionModel.postValue(new MessageInsertionModel(
                            messageId, message, messageType,
                            sentTimestamp, isReply, replyToMsgId));
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "message insert failed error in repository " + e.getMessage(), e);
            }
        });
    }

    // Back-compat overload — delegates to the roomId version with empty string.
    public void insertMessageToLocalStorageFromService(String messageId, int messageDirection, String receiverId,
                                            String message, int messageStatus, int needPush, long sentTimestamp,
                                            long receivedTimestamp, long readTimestamp, int isStarred,
                                            int editedStatus, int messageType, double latitude,
                                            double longitude, int isReply, String replyToMsgId,
                                            int chatArchive, Runnable onComplete) {
        insertMessageToLocalStorageFromService(messageId, messageDirection, receiverId,
                message, messageStatus, needPush, sentTimestamp,
                receivedTimestamp, readTimestamp, isStarred,
                editedStatus, messageType, latitude,
                longitude, isReply, replyToMsgId,
                chatArchive, "", onComplete);
    }

    public void insertMessageToLocalStorageFromService(String messageId, int messageDirection, String receiverId,
                                            String message, int messageStatus, int needPush, long sentTimestamp,
                                            long receivedTimestamp, long readTimestamp, int isStarred,
                                            int editedStatus, int messageType, double latitude,
                                            double longitude, int isReply, String replyToMsgId,
                                            int chatArchive, String roomId, Runnable onComplete) {

        writeExecutor.execute(() -> {
            try {
                boolean inserted = messagesDatabaseDAO.insertMessage(
                        messageId, messageDirection, receiverId,
                        message, messageStatus, needPush,
                        sentTimestamp, receivedTimestamp, readTimestamp,
                        isStarred, editedStatus, messageType,
                        latitude, longitude, isReply,
                        replyToMsgId, chatArchive,
                        roomId != null ? roomId : "");

                if (inserted && onComplete != null) {
                    new Handler(Looper.getMainLooper()).post(onComplete);
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "message insert failed error in repository " + e.getMessage(), e);
            }
        });
    }

    /**
     * Inserts a media/file message row using the v8 column layout (no JSON blob).
     * Only file metadata + on-device PATHS are persisted — the file BYTES live
     * on disk under /files/sent/ or /files/received/. The DAO mirrors the chat
     * list sync behavior of insertMessage so the chat_list always reflects the
     * latest message for the contact.
     */
    public void insertMediaMessageToLocalStorageFromService(String messageId, int messageDirection, String receiverId,
                                            String message, int messageStatus, int needPush, long sentTimestamp,
                                            long receivedTimestamp, long readTimestamp, int isStarred,
                                            int editedStatus, int messageType, double latitude,
                                            double longitude, int isReply, String replyToMsgId,
                                            int chatArchive,
                                            String fileName, String contentType, String contentSubtype,
                                            String caption, int mediaWidth, int mediaHeight,
                                            long mediaDuration, long fileSize,
                                            String s3Key, String s3Bucket, String fileTransferId,
                                            String localFilePath, String localThumbnailPath,
                                            String remoteThumbnailUrl, String encryptedS3Url,
                                            String encryptionKey, String encryptionIv,
                                            String roomId,
                                            Runnable onComplete) {

        writeExecutor.execute(() -> {
            try {
                boolean inserted = messagesDatabaseDAO.insertMessageWithMedia(
                        messageId, messageDirection, receiverId,
                        message, messageStatus, needPush,
                        sentTimestamp, receivedTimestamp, readTimestamp,
                        isStarred, editedStatus, messageType,
                        latitude, longitude, isReply,
                        replyToMsgId, chatArchive,
                        fileName, contentType, contentSubtype,
                        caption, mediaWidth, mediaHeight,
                        mediaDuration, fileSize,
                        s3Key, s3Bucket, fileTransferId,
                        localFilePath, localThumbnailPath,
                        remoteThumbnailUrl, encryptedS3Url,
                        encryptionKey, encryptionIv,
                        roomId != null ? roomId : "");

                if (inserted && onComplete != null) {
                    new Handler(Looper.getMainLooper()).post(onComplete);
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "media message insert failed error in repository " + e.getMessage(), e);
            }
        });
    }

    /**
     * Updates LOCAL_FILE_PATH on a media row after the file is copied into the
     * sent/ folder (sender) or downloaded into the received/ folder (receiver).
     * Refreshes the message list so the adapter rebinds with the new path.
     */
    public void updateLocalFilePathAndRefresh(String messageId, String localFilePath, String contactId) {
        writeExecutor.execute(() -> {
            try {
                messagesDatabaseDAO.updateLocalFilePath(messageId, localFilePath);
                getMessagesForContact(contactId);
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "updateLocalFilePathAndRefresh: " + e.getMessage());
            }
        });
    }

    /**
     * Updates LOCAL_THUMBNAIL_PATH on a media row after the thumbnail is
     * generated (sender) or downloaded + decrypted (receiver). Refreshes the
     * message list so the adapter rebinds with the new path.
     */
    public void updateLocalThumbnailPathAndRefresh(String messageId, String localThumbnailPath, String contactId) {
        writeExecutor.execute(() -> {
            try {
                messagesDatabaseDAO.updateLocalThumbnailPath(messageId, localThumbnailPath);
                getMessagesForContact(contactId);
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "updateLocalThumbnailPathAndRefresh: " + e.getMessage());
            }
        });
    }

    /**
     * Updates S3_KEY / S3_BUCKET / ENCRYPTED_S3_URL on an outgoing media row
     * after a successful upload, then refreshes the message list.
     */
    public void updateMediaS3InfoAndRefresh(String messageId, String s3Key, String s3Bucket,
                                            String encryptedS3Url, String contactId) {
        writeExecutor.execute(() -> {
            try {
                messagesDatabaseDAO.updateMessageS3Info(messageId, s3Key, s3Bucket, encryptedS3Url);
                getMessagesForContact(contactId);
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "updateMediaS3InfoAndRefresh: " + e.getMessage());
            }
        });
    }

    // ------------- Database Insertions ends here --------------------------------------


    // ------------- Database Reads starts from here -----------------------------------

    public void getMessagesForContact(String contactId) {
        readExecutor.execute(() -> {
            String contactName      =   contactsRepository.getContactNameForRepliedMessages(contactId);
            ArrayList<MessageModal> messages = new ArrayList<>();
            try (Cursor cursor = messagesDatabaseDAO.getMessages(contactId)) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        String  repliedMessageText          = cursor.getString(cursor.getColumnIndexOrThrow("replied_message_text"));
                        int     repliedMessageDirection     = cursor.getInt(cursor.getColumnIndexOrThrow("replied_message_direction"));
                        MessageModal messageModal = new MessageModal(
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_ID)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_DIRECTION)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.RECEIVER_ID)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_STATUS)),
                                cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.TIMESTAMP)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.STARRED)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.EDIT_STATUS)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_TYPE)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.IS_REPLY)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.REPLY_TO_MESSAGE_ID)),
                                repliedMessageText,
                                repliedMessageDirection,
                                contactName
                        );
                        // v8: prefer real columns, fall back to legacy JSON blob
                        if (!populateMediaFieldsFromCursor(messageModal, cursor)) {
                            populateMediaFieldsFromJson(messageModal);
                        }
                        messages.add(messageModal);
                    }
                    while (cursor.moveToNext());
                    messagesMutableData.postValue(messages);
                } else {
                    Log.e(Extras.LOG_MESSAGE, "Error loading messages cursor null");
                    messagesMutableData.postValue(new ArrayList<>());
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Error loading messages " + e.getMessage());
                messagesMutableData.postValue(new ArrayList<>());
            }
        });
    }


    public void getMessagesForContactOnStatusUpdate(String contactId) {
        readExecutor.execute(() -> {
            String contactName      =   contactsRepository.getContactNameForRepliedMessages(contactId);
            ArrayList<MessageModal> messages = new ArrayList<>();
            try (Cursor cursor = messagesDatabaseDAO.getMessages(contactId)) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        String  repliedMessageText          = cursor.getString(cursor.getColumnIndexOrThrow("replied_message_text"));
                        int     repliedMessageDirection     = cursor.getInt(cursor.getColumnIndexOrThrow("replied_message_direction"));
                        MessageModal messageModal = new MessageModal(
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_ID)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_DIRECTION)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.RECEIVER_ID)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_STATUS)),
                                cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.TIMESTAMP)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.STARRED)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.EDIT_STATUS)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_TYPE)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.IS_REPLY)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.REPLY_TO_MESSAGE_ID)),
                                repliedMessageText,
                                repliedMessageDirection,
                                contactName
                        );
                        messages.add(messageModal);
                    }
                    while (cursor.moveToNext());
                    messageStatusUpdateMutable.postValue(messages);
                    Log.e(Extras.LOG_MESSAGE,"sending message status updates");
                } else {
                    Log.e(Extras.LOG_MESSAGE, "Error loading messages cursor null");
                    messageStatusUpdateMutable.postValue(new ArrayList<>());
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Error loading messages " + e.getMessage());
                messageStatusUpdateMutable.postValue(new ArrayList<>());
            }
        });
    }

    public void getStarredMessagesForContact(String contactId) {
        readExecutor.execute(() -> {
            String contactName      =   contactsRepository.getContactNameForRepliedMessages(contactId);
            ArrayList<MessageModal> messages = new ArrayList<>();
            try (Cursor cursor = messagesDatabaseDAO.getStarredMessages(contactId)) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        String  repliedMessageText          = cursor.getString(cursor.getColumnIndexOrThrow("replied_message_text"));
                        int     repliedMessageDirection     = cursor.getInt(cursor.getColumnIndexOrThrow("replied_message_direction"));
                        MessageModal messageModal = new MessageModal(
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_ID)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_DIRECTION)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.RECEIVER_ID)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_STATUS)),
                                cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.TIMESTAMP)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.STARRED)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.EDIT_STATUS)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_TYPE)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.IS_REPLY)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.REPLY_TO_MESSAGE_ID)),
                                repliedMessageText,
                                repliedMessageDirection,
                                contactName
                        );
                        messages.add(messageModal);
                    }
                    while (cursor.moveToNext());
                    starredMessagesMutableData.postValue(messages);
                } else {
                    Log.e(Extras.LOG_MESSAGE, "Error loading messages cursor null");
                    starredMessagesMutableData.postValue(new ArrayList<>());
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Error loading messages " + e.getMessage());
                starredMessagesMutableData.postValue(new ArrayList<>());
            }
        });
    }

    public void getSharedLinksOfTheContact(String contactId) {
        ArrayList<MessageModal> messageModalArrayList   =   new ArrayList<>();
        readExecutor.execute(() -> {
            String contactName      =   contactsRepository.getContactNameForRepliedMessages(contactId);
            try (Cursor cursor = messagesDatabaseDAO.getSharedLinksMessages(contactId))
            {
                if (cursor != null && cursor.moveToFirst())
                {
                    do {
                        String  repliedMessageText          = cursor.getString(cursor.getColumnIndexOrThrow("replied_message_text"));
                        int     repliedMessageDirection     = cursor.getInt(cursor.getColumnIndexOrThrow("replied_message_direction"));
                        MessageModal messageModal = new MessageModal(
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_ID)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_DIRECTION)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.RECEIVER_ID)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_STATUS)),
                                cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.TIMESTAMP)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.STARRED)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.EDIT_STATUS)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_TYPE)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.IS_REPLY)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.REPLY_TO_MESSAGE_ID)),
                                repliedMessageText,
                                repliedMessageDirection,
                                contactName
                        );
                        messageModalArrayList.add(messageModal);
                    }
                    while (cursor.moveToNext());
                    sharedLinkMessagesMutableData.postValue(messageModalArrayList);
                }
                else {
                    Log.e(Extras.LOG_MESSAGE,"Unable to display search messages with text and date error in repo ");
                    sharedLinkMessagesMutableData.postValue(new ArrayList<>());
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to display search messages with date and text error in repo " + e.getMessage());
                sharedLinkMessagesMutableData.postValue(new ArrayList<>());
            }
        });
    }

    public void getMessagesForContactOnSelectedDate(String contactId, long selectedDateTimestamp) {
        ArrayList<MessageModal> messageModalArrayList   =   new ArrayList<>();
        readExecutor.execute(() -> {
            String contactName      =   contactsRepository.getContactNameForRepliedMessages(contactId);
            try (Cursor cursor = messagesDatabaseDAO.getMessagesByDate(contactId, selectedDateTimestamp)) {

                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        String  repliedMessageText          = cursor.getString(cursor.getColumnIndexOrThrow("replied_message_text"));
                        int     repliedMessageDirection     = cursor.getInt(cursor.getColumnIndexOrThrow("replied_message_direction"));
                        MessageModal messageModal = new MessageModal(
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_ID)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_DIRECTION)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.RECEIVER_ID)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_STATUS)),
                                cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.TIMESTAMP)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.STARRED)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.EDIT_STATUS)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_TYPE)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.IS_REPLY)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.REPLY_TO_MESSAGE_ID)),
                                repliedMessageText,
                                repliedMessageDirection,
                                contactName
                        );
                        messageModalArrayList.add(messageModal);
                    }
                    while (cursor.moveToNext());
                    searchMessagesMutableData.postValue(messageModalArrayList);
                }
                else {
                    searchMessagesMutableData.postValue(new ArrayList<>());
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to display search messages error in repo " + e.getMessage());
                searchMessagesMutableData.postValue(new ArrayList<>());
            }
        });
    }

    public void getMessagesFromDateWithText(String contactId, long timestamp, String searchText) {
        ArrayList<MessageModal> messageModalArrayList   =   new ArrayList<>();
        readExecutor.execute(() -> {
            String contactName      =   contactsRepository.getContactNameForRepliedMessages(contactId);
            try (Cursor cursor = messagesDatabaseDAO.getMessagesWithTextOnDate(contactId, searchText, timestamp)) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        String  repliedMessageText          = cursor.getString(cursor.getColumnIndexOrThrow("replied_message_text"));
                        int     repliedMessageDirection     = cursor.getInt(cursor.getColumnIndexOrThrow("replied_message_direction"));
                        MessageModal messageModal = new MessageModal(
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_ID)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_DIRECTION)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.RECEIVER_ID)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_STATUS)),
                                cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.TIMESTAMP)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.STARRED)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.EDIT_STATUS)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_TYPE)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.IS_REPLY)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.REPLY_TO_MESSAGE_ID)),
                                repliedMessageText,
                                repliedMessageDirection,
                                contactName
                        );
                        messageModalArrayList.add(messageModal);
                    }
                    while (cursor.moveToNext());
                    searchMessagesMutableData.postValue(messageModalArrayList);
                }
                else {
                    Log.e(Extras.LOG_MESSAGE,"Unable to display search messages with text and date error in repo ");
                    searchMessagesMutableData.postValue(new ArrayList<>());
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to display search messages with date and text error in repo " + e.getMessage());
                searchMessagesMutableData.postValue(new ArrayList<>());
            }
        });
    }

    public void getContactSearchMessagesFromText(String contactId, String searchText) {
        ArrayList<MessageModal> messageModalArrayList   =   new ArrayList<>();
        readExecutor.execute(() -> {
            String contactName      =   contactsRepository.getContactNameForRepliedMessages(contactId);
            try (Cursor cursor = messagesDatabaseDAO.getMessagesByText(contactId, searchText)) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        String  repliedMessageText          = cursor.getString(cursor.getColumnIndexOrThrow("replied_message_text"));
                        int     repliedMessageDirection     = cursor.getInt(cursor.getColumnIndexOrThrow("replied_message_direction"));
                        String  repliedMessageSenderName    = "";
                        MessageModal messageModal = new MessageModal(
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_ID)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_DIRECTION)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.RECEIVER_ID)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_STATUS)),
                                cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.TIMESTAMP)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.STARRED)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.EDIT_STATUS)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_TYPE)),
                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.IS_REPLY)),
                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.REPLY_TO_MESSAGE_ID)),
                                repliedMessageText,
                                repliedMessageDirection,
                                contactName
                        );
                        messageModalArrayList.add(messageModal);
                    }
                    while (cursor.moveToNext());
                    searchMessagesMutableData.postValue(messageModalArrayList);
                }
                else {
                    Log.e(Extras.LOG_MESSAGE,"Unable to display search messages with text and date error in repo ");
                    searchMessagesMutableData.postValue(new ArrayList<>());
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to display search messages with date and text error in repo " + e.getMessage());
                searchMessagesMutableData.postValue(new ArrayList<>());
            }
        });
    }

    public List<MarkMessagesAsSeenModel> getDeliveredMessagesList(String receiverId) {
        List<MarkMessagesAsSeenModel> list = new ArrayList<>();
        try (Cursor cursor = messagesDatabaseDAO.getDeliveredMessages(receiverId)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String  messageId           =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_ID));
                    long    deliveredTimestamp  =   cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.RECEIVE_TIMESTAMP));
                    list.add(new MarkMessagesAsSeenModel(messageId, deliveredTimestamp));
                } while (cursor.moveToNext());
            }
        }
        return list;
    }

    public void getContactUnSyncSeenMessages(String contactId) {
        readExecutor.execute(() -> {
            List<MarkMessagesAsSeenModel>           messages        =   getDeliveredMessagesList(contactId);
            ArrayList<UnSyncedSeenMessagesModel>    unSyncedList    =   new ArrayList<>();
            long currentTime = System.currentTimeMillis();
            for (MarkMessagesAsSeenModel m : messages) {
                try {
                    updateMessageStatusAsSeen(m.getMessageId(), MessagesManager.MESSAGE_SEEN, currentTime,
                            MessagesManager.NEED_MESSAGE_PUSH);
                    unSyncedList.add(new UnSyncedSeenMessagesModel(
                            m.getMessageId(), contactId, userId, MessagesManager.MESSAGE_SEEN,
                            m.getDeliveredTimestamp(), currentTime));
                } catch (Exception e) {
                    Log.e(Extras.LOG_MESSAGE, "Failed to mark message seen for id " + m.getMessageId(), e);
                }
            }
            unSyncSeenMessagesMutableData.postValue(unSyncedList);
        });
    }

    public void getDeliveredMessagesForNotification(Consumer<List<MessageModal>> callback) {
        readExecutor.execute(() -> {
            ArrayList<MessageModal> messageModalArrayList =   new ArrayList<>();
            try (Cursor cursor  =   messagesDatabaseDAO.getAllDeliveredMessagesForNotification()) {

                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        String  messageId           =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_ID));
                        int     messageDirection    =   cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_DIRECTION));
                        String  receiverId          =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.RECEIVER_ID));
                        String  message             =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE));
                        int     messageStatus       =   cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_STATUS));
                        long    timestamp           =   cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.TIMESTAMP));
                        int     starredStatus       =   cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.STARRED));
                        int     editedStatus        =   cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.EDIT_STATUS));
                        int     messageType         =   cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_TYPE));
                        int     isReply             =   cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.IS_REPLY));
                        String  replyToMessageId    =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.REPLY_TO_MESSAGE_ID));

                        MessageModal msg = new MessageModal(
                                messageId,
                                messageDirection,
                                receiverId,
                                message,
                                messageStatus,
                                timestamp,
                                starredStatus,
                                editedStatus,
                                messageType,
                                isReply,
                                replyToMessageId,
                                "",
                                0,
                                ""
                        );
                        messageModalArrayList.add(msg);
                    }
                    while (cursor.moveToNext());
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE," unable to get delivered messages due to sql exception " + e.getMessage());
            }
            new Handler(Looper.getMainLooper()).post(() -> callback.accept(messageModalArrayList));
        });
    }

    public void getContactFirstMessageTimestamp(String contactId) {
        readExecutor.execute(() -> {
            try (Cursor cursor  =   messagesDatabaseDAO.getFirstMessageDetails(contactId)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int     messageDirection    =   cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_DIRECTION));
                    long    timestamp           =   cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.TIMESTAMP));
                    long    deliveredTimestamp  =   cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.RECEIVE_TIMESTAMP));

                    if (messageDirection    ==  MessagesManager.MESSAGE_OUTGOING) {
                        contactFirstMessageTimeMutable.postValue(timestamp);
                    } else {
                        contactFirstMessageTimeMutable.postValue(deliveredTimestamp);
                    }
                }
                else {
                    contactFirstMessageTimeMutable.postValue(MessagesManager.DEFAULT_DELIVERED_TIMESTAMP);
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to access first message details error in repo " + e.getMessage());
                contactFirstMessageTimeMutable.postValue(MessagesManager.DEFAULT_DELIVERED_TIMESTAMP);
            }

        });
    }

    public LiveData<MessageInfoModel> getMessageDetailsFromId(String messageId) {
        final MutableLiveData<MessageInfoModel> messageInfoModelMutableLiveData =   new MutableLiveData<>();
        readExecutor.execute(() -> {
            try (Cursor cursor = messagesDatabaseDAO.MessageTimeDetails(messageId)) {

                if (cursor != null && cursor.moveToFirst()) {
                    long    sentTime        =   cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.TIMESTAMP));
                    long    seenTime        =   cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.READ_TIMESTAMP));
                    long    deliveredTime   =   cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.RECEIVE_TIMESTAMP));
                    String  message         =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE));
                    int     messageStatus   =   cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_STATUS));
                    MessageInfoModel    messageInfoModel    =   new MessageInfoModel(message, messageStatus, sentTime,
                            deliveredTime, seenTime);

                    messageInfoModelMutableLiveData.postValue(messageInfoModel);
                }
                else {
                    Log.e(Extras.LOG_MESSAGE,"Unable to get message info ");
                    messageInfoModelMutableLiveData.postValue(null);
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to get message info " + e.getMessage());
                messageInfoModelMutableLiveData.postValue(null);
            }
        });
        return messageInfoModelMutableLiveData;
    }

    public boolean  checkChatExistsOrNotInRep(String contactId) {
        FutureTask<Boolean> getChatExistsTask   =   new FutureTask<>(() -> {
            try (Cursor cursor  =   messagesDatabaseDAO.checkChatExistsOrNot(contactId)) {
                return cursor != null && cursor.moveToFirst();
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Error checking for chat existence in message repo " + e.getMessage(), e);
            }
            return false;
        });

        readExecutor.execute(getChatExistsTask);

        try {
            return getChatExistsTask.get();
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Error checking for chat existence in message repo " + e.getMessage(), e);
        }
        return false;
    }


    // --------------- Database Read Operations Ends Here ---------------------------


    /**
     * Reads the message body (message column) for a single row. Used to merge
     * server-provided file metadata into the existing local JSON without losing
     * fields like local_file_path.
     */
    // --------------- Database Update Operations Starts here ------------------------------------

    public void updateMessageAsSyncedWithServer(String messageId, int messageStatus, int needPushStatus,
                                                MessageStatusUpdatesCallbacks messageStatusUpdatesCallbacks) {
        writeExecutor.execute(() -> {
            try {
                boolean update  =   messagesDatabaseDAO.updateMessageStatusAsSynced(messageId, messageStatus, needPushStatus);
                messageStatusUpdatesCallbacks.onUpdatingMessageStatus(update);
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to update message as synced with server error in repository " + e.getMessage(), e);
            }
        });
    }

    public void updateMessageAsDelivered(String messageId, int messageStatus, long deliveredTime,
                                         MessageStatusUpdatesCallbacks messageStatusUpdatesCallbacks) {
        writeExecutor.execute(() -> {
            try {
                boolean update =    messagesDatabaseDAO.updateMessageAsDeliveredLocally(messageId, messageStatus, deliveredTime);
                messageStatusUpdatesCallbacks.onUpdatingMessageStatus(update);
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to update message as delivered caught exception in repository 123 " +e.getMessage());
            }

        });
    }

    public void updateMessageAsDeliveredBatch(List<MarkMessagesAsDeliveredModel> markMessagesAsDeliveredModels,
                                         MessageStatusBatchUpdatesCallbacks messageStatusBatchUpdatesCallbacks) {
        writeExecutor.execute(() -> {
            try {
                List<String> successfulMessageIds   =   messagesDatabaseDAO.updateMessageAsDeliveredLocallyAsBatch(markMessagesAsDeliveredModels);
                messageStatusBatchUpdatesCallbacks.onUpdatingMessageStatusBatch(successfulMessageIds);
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to update message as delivered caught exception in repository 123 " +e.getMessage());
            }

        });
    }

    public void updateSentMessageAsDeliveredSuccessfully(String messageId, int messageStatus,
                                                         MessageStatusUpdatesCallbacks messageStatusUpdatesCallbacks) {
        writeExecutor.execute(() -> {
            try {
                boolean update =    messagesDatabaseDAO.updateMessageAStatus(messageId, messageStatus);
                messageStatusUpdatesCallbacks.onUpdatingMessageStatus(update);
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to update message as delivered caught exception" +
                        " in repository locally " +e.getMessage());
            }
        });
    }


    public void updateMessageStatusAsSeen(String messageId, int messageStatus, long seenTimestamp, int needPush) {
        writeExecutor.execute(() -> {
            try {
                messagesDatabaseDAO.updateMessageSeenStatus(messageId, messageStatus, seenTimestamp, needPush);
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to update message as seen caught exception in repository " +e.getMessage());
            }
        });
    }

    public void updateMessageAsDeliveredAndSeen(String messageId, int messageStatus, long deliveredTimestamp,
                                                long seenTimestamp,
                                                MessageStatusUpdatesCallbacks messageStatusUpdatesCallbacks) {
        writeExecutor.execute(() -> {
            try {
                Log.e(Extras.LOG_MESSAGE,"final status is " + messageStatus);
                boolean update  =   messagesDatabaseDAO.updateSentMessageAsSeenAndDeliveredLocally(messageId, messageStatus,
                        deliveredTimestamp, seenTimestamp);
                messageStatusUpdatesCallbacks.onUpdatingMessageStatus(update);
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to update message as delivered and seen error caught exception" +
                        " in repository " + e.getMessage());
            }
        });
    }

    public void updateMessageAsDeliveredAndSeenBatch(List<MarkMessagesAsDeliveredAndSeenModel> markMessagesAsDeliveredAndSeenModels,
                                                     MessageStatusBatchUpdatesCallbacks messageStatusBatchUpdatesCallbacks) {
        writeExecutor.execute(() -> {
            try {
                List<String> successfulMessageIds   =   messagesDatabaseDAO.
                                                    updateSentMessageAsSeenAndDeliveredLocallyBatch(markMessagesAsDeliveredAndSeenModels);
                messageStatusBatchUpdatesCallbacks.onUpdatingMessageStatusBatch(successfulMessageIds);
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to update message as delivered and seen error caught exception" +
                        " in repository " + e.getMessage());
            }
        });
    }

    public void updateReceivedMessagePushStatus(String messageId, int needPush) {
        writeExecutor.execute(() -> {
            try {
                boolean update =    messagesDatabaseDAO.updateMessageAsNoNeedPush(messageId, needPush);
                Log.e(Extras.LOG_MESSAGE,"received message updated as delivered successfully " + update);
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to update message as delivered caught exception" +
                        " in repository locally " +e.getMessage());
            }
        });
    }

    public void acceptContactChat(String contactId) {
        writeExecutor.execute(() -> {
            boolean updated     =   messagesDatabaseDAO.updateChatArchiveStatus(contactId, Extras.UNARCHIVE_CHAT);
            if (updated) {
                getContactUnSyncSeenMessages(contactId);
                acceptedContactChatMutable.postValue(true);
            }
            else {
                Log.e(Extras.LOG_MESSAGE,"unable to Accept Chat");
                acceptedContactChatMutable.postValue(false);
            }
        });
    }

    public void updateMessageStarStatus(String messageId, int starredStatus) {
        writeExecutor.execute(() -> {
            try {
                boolean update  =   messagesDatabaseDAO.updateMessageStarredStatus(messageId, starredStatus);
                if (update) {
                    starredMessageStatusMutableData.postValue(starredStatus);
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, " error caught exception during updating message starred status" +
                        " in repository " + e.getMessage());
            }
        });
    }

    public void updateUnreadCount(String contactId, Runnable onComplete) {
        writeExecutor.execute(() -> {
            try {
                messagesDatabaseDAO.updateContactUnreadCount(contactId);

                if (onComplete != null) {
                    new Handler(Looper.getMainLooper()).post(onComplete);
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "unable to update unread count error in repository " + e.getMessage());
            }
        });
    }

    public void updateLastMessagePositionForTheContact(String contactId, int lastPosition) {
        writeExecutor.execute(() -> {
            try {
                messagesDatabaseDAO.updateLastMessagePosition(contactId, lastPosition);
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "unable to update contact last position error in repository " + e.getMessage());
            }
        });
    }

    // ---------------- Check operations starts here ---------------------------------

    public void checkChatListId(String chatId, ServiceCallBacks serviceCallBacks) {
        readExecutor.execute(() -> {
            boolean exists = false;
            try {
                exists = messagesDatabaseDAO.checkChatListId(chatId);
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Error checking chat list ID: " + e.getMessage());
            }
            serviceCallBacks.onChatListCheck(exists);
        });
    }

    // ---------------- Database Update Operations Ends Here --------------------------------

    // ---------------- Database Delete Operations Starts Here  -----------------------------

    public void deleteMessage(String messageId) {
        writeExecutor.execute(() -> {
            try {
                boolean deleted = messagesDatabaseDAO.deleteMessage(messageId);
                if (deleted) {
                    deleteMessageStatusMutableData.postValue(MessagesManager.MESSAGE_DELETED_SUCCESSFULLY);
                }
                else {
                    deleteMessageStatusMutableData.postValue(MessagesManager.MESSAGE_DELETION_FAILED);
                }
                Log.e(Extras.LOG_MESSAGE,"delete status is " + deleted);
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to delete message caught exception in repository " + e.getMessage());
            }
        });
    }

    public void deleteReceivedMessageRequestedFromService(String messageId, MessageDeleteCallbacks messageDeleteCallbacks)
    {
        writeExecutor.execute(() -> {
            try {
                boolean deleted = messagesDatabaseDAO.deleteMessage(messageId);
                messageDeleteCallbacks.onDeletingMessage(deleted);
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to delete message caught exception in repository " + e.getMessage());
            }
        });
    }

    public void deleteAllMessagesOfContact(String contactId) {
        writeExecutor.execute(() -> {
            try {
                boolean deleted     = messagesDatabaseDAO.deleteMessages(contactId);
                if (deleted) {
                    deleteMessageStatusMutableData.postValue(MessagesManager.MESSAGE_DELETED_SUCCESSFULLY);
                }
                else {
                    deleteMessageStatusMutableData.postValue(MessagesManager.MESSAGE_DELETION_FAILED);
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to delete all message of contact caught exception in repository " + e.getMessage());
            }
        });
    }

    public void clearSearchMessagesLiveData() {
        searchMessagesMutableData.setValue(null); // or new ArrayList<>() if you prefer
    }

    public interface MessageStatusUpdatesCallbacks {
        void onUpdatingMessageStatus(boolean isSuccess);
    }

    public interface MessageStatusBatchUpdatesCallbacks {
        void onUpdatingMessageStatusBatch(List<String> messageIds);
    }

    public interface MessageDeleteCallbacks {
        void onDeletingMessage(boolean isSuccess);
    }

    public interface ServiceCallBacks {
        void onChatListCheck(boolean isPresent);
    }

    /**
     * For media messages (image/video/audio/document), the body stored in the
     * `message` column is a JSON blob containing the attachment metadata (file_name,
     * s3_key, content_type, etc.). This parses it and populates the MessageModal's
     * media fields so the adapter's ViewHolders can render them.
     *
     * For plain text messages this is a no-op.
     */
    /**
     * Reads media columns from the cursor into the MessageModal. Used for v8+
     * rows where media metadata lives in real columns (no JSON blob). Returns
     * true if any media column was actually populated — caller can use this to
     * decide whether the legacy populateMediaFieldsFromJson() fallback is needed
     * for older rows.
     */
    private boolean populateMediaFieldsFromCursor(MessageModal model, Cursor cursor) {
        int type = model.getMessageType();
        if (type != MessagesManager.IMAGE_MESSAGE
                && type != MessagesManager.VIDEO_MESSAGE
                && type != MessagesManager.AUDIO_MESSAGE
                && type != MessagesManager.DOCUMENT_MESSAGE) {
            return false;
        }
        try {
            // The new columns may not exist on legacy DB files (v < 8). The
            // ALTER TABLE in onUpgrade adds them, but be defensive.
            int idxFileName = cursor.getColumnIndex(MessagesDatabase.FILE_NAME);
            if (idxFileName < 0) return false;

            String fileName       = cursor.getString(idxFileName);
            String contentType    = cursor.getString(cursor.getColumnIndex(MessagesDatabase.CONTENT_TYPE));
            String contentSubtype = cursor.getString(cursor.getColumnIndex(MessagesDatabase.CONTENT_SUBTYPE));
            String caption        = cursor.getString(cursor.getColumnIndex(MessagesDatabase.CAPTION));
            int mediaWidth        = cursor.getInt(cursor.getColumnIndex(MessagesDatabase.MEDIA_WIDTH));
            int mediaHeight       = cursor.getInt(cursor.getColumnIndex(MessagesDatabase.MEDIA_HEIGHT));
            long mediaDuration    = cursor.getLong(cursor.getColumnIndex(MessagesDatabase.MEDIA_DURATION));
            long fileSize         = cursor.getLong(cursor.getColumnIndex(MessagesDatabase.FILE_SIZE));
            String s3Key          = cursor.getString(cursor.getColumnIndex(MessagesDatabase.S3_KEY));
            String s3Bucket       = cursor.getString(cursor.getColumnIndex(MessagesDatabase.S3_BUCKET));
            String fileTransferId = cursor.getString(cursor.getColumnIndex(MessagesDatabase.FILE_TRANSFER_ID));
            String localFilePath  = cursor.getString(cursor.getColumnIndex(MessagesDatabase.LOCAL_FILE_PATH));
            String localThumbPath = cursor.getString(cursor.getColumnIndex(MessagesDatabase.LOCAL_THUMBNAIL_PATH));
            String remoteThumbUrl = cursor.getString(cursor.getColumnIndex(MessagesDatabase.REMOTE_THUMBNAIL_URL));
            String encryptedS3Url = cursor.getString(cursor.getColumnIndex(MessagesDatabase.ENCRYPTED_S3_URL));
            String encryptionKey  = cursor.getString(cursor.getColumnIndex(MessagesDatabase.ENCRYPTION_KEY));
            String encryptionIv   = cursor.getString(cursor.getColumnIndex(MessagesDatabase.ENCRYPTION_IV));
            int    idxRoomId      = cursor.getColumnIndex(MessagesDatabase.ROOM_ID);
            String roomIdVal      = idxRoomId >= 0 ? cursor.getString(idxRoomId) : "";

            // If all media columns are empty/null, this is either a legacy row
            // (still using JSON in `message`) or an unknown state — let the
            // caller fall back to JSON parsing.
            boolean hasAnyMedia =
                    (fileName != null && !fileName.isEmpty())
                    || (s3Key != null && !s3Key.isEmpty())
                    || (localFilePath != null && !localFilePath.isEmpty());
            if (!hasAnyMedia) return false;

            model.setFileName(fileName != null ? fileName : "");
            model.setCaption(caption != null ? caption : "");
            model.setContentSubtype(contentSubtype != null ? contentSubtype : "");
            model.setMediaWidth(mediaWidth);
            model.setMediaHeight(mediaHeight);
            model.setMediaDuration(mediaDuration);
            model.setFileSize(fileSize);
            model.setS3Key(s3Key);
            model.setS3Bucket(s3Bucket);
            model.setFileTransferId(fileTransferId);
            model.setRemoteThumbnailUrl(remoteThumbUrl);
            model.setEncryptedS3Url(encryptedS3Url);
            model.setEncryptionKey(encryptionKey);
            model.setEncryptionIv(encryptionIv);
            model.setRoomId(roomIdVal);

            // Thumbnail resolution order:
            //   1. local_thumbnail_path  (sender's pre-generated PNG OR receiver's
            //      already-decrypted thumbnail saved by autoFetchEncryptedThumbnails)
            //   2. remote_thumbnail_url  (only safe when bytes are PLAINTEXT —
            //      legacy rows). When encryption_key is set the bytes are AES-GCM
            //      ciphertext and Glide cannot render them — leave thumbnailUri
            //      empty so the adapter shows the doc icon until the receiver-side
            //      auto-fetch decrypts it and persists local_thumbnail_path.
            if (localThumbPath != null && !localThumbPath.isEmpty()) {
                model.setThumbnailUri(localThumbPath);
            } else if (remoteThumbUrl != null && !remoteThumbUrl.isEmpty()
                    && (encryptionKey == null || encryptionKey.isEmpty())) {
                model.setThumbnailUri(remoteThumbUrl);
            } else {
                model.setThumbnailUri("");
            }

            // Prefer the sender's local plaintext copy; otherwise fall back to
            // s3_key as the remote identifier the adapter can act on.
            if (localFilePath != null && !localFilePath.isEmpty()) {
                model.setMediaUri(localFilePath);
            } else {
                model.setMediaUri(s3Key != null ? s3Key : "");
            }
            return true;
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "populateMediaFieldsFromCursor failed for "
                    + model.getMessageId() + ": " + e.getMessage());
            return false;
        }
    }

    private void populateMediaFieldsFromJson(MessageModal model) {
        int type = model.getMessageType();
        if (type != MessagesManager.IMAGE_MESSAGE
                && type != MessagesManager.VIDEO_MESSAGE
                && type != MessagesManager.AUDIO_MESSAGE
                && type != MessagesManager.DOCUMENT_MESSAGE) {
            return;
        }
        String body = model.getMessage();
        if (body == null || body.isEmpty() || body.charAt(0) != '{') {
            return;
        }
        try {
            org.json.JSONObject json = new org.json.JSONObject(body);
            model.setFileName(json.optString("file_name", ""));
            model.setCaption(json.optString("caption", ""));
            model.setContentSubtype(json.optString("content_subtype", ""));
            model.setMediaWidth(json.optInt("width", 0));
            model.setMediaHeight(json.optInt("height", 0));
            model.setMediaDuration(json.optLong("duration", 0));
            // Legacy fields → expose on the model so receiver-side auto-fetch
            // and download trigger don't have to reparse the JSON later.
            model.setS3Key(json.optString("s3_key", ""));
            model.setS3Bucket(json.optString("bucket", ""));
            model.setFileTransferId(json.optString("file_transfer_id", ""));
            model.setRemoteThumbnailUrl(json.optString("thumbnail", ""));
            model.setEncryptedS3Url(json.optString("encrypted_s3_url", ""));
            model.setEncryptionKey(json.optString("encryption_key", ""));
            model.setEncryptionIv(json.optString("encryption_iv", ""));
            // Thumbnail resolution order:
            //   1. local_thumbnail_path  → sender's pre-generated PNG, OR receiver's
            //      already-decrypted thumbnail saved by autoFetchEncryptedThumbnails
            //   2. remote `thumbnail` URL  → only safe when bytes are PLAINTEXT.
            //      If encryption_key is set, the bytes at the URL are AES-GCM
            //      ciphertext and Glide cannot render them — leave thumbnailUri
            //      empty so the adapter shows the doc icon until the receiver-side
            //      auto-fetch decrypts it and persists local_thumbnail_path.
            String localThumbPath = json.optString("local_thumbnail_path", "");
            if (!localThumbPath.isEmpty()) {
                model.setThumbnailUri(localThumbPath);
            } else {
                String remoteThumb = json.optString("thumbnail", "");
                String thumbEncKey = json.optString("encryption_key", "");
                if (!remoteThumb.isEmpty() && thumbEncKey.isEmpty()) {
                    model.setThumbnailUri(remoteThumb);  // legacy plaintext
                } else {
                    model.setThumbnailUri("");           // wait for local decrypt
                }
            }
            model.setFileSize(json.optLong("file_size", 0));

            // Prefer a local file path (sender's copy) if present; otherwise use s3_key
            // as the remote identifier. The adapter can decide what to show.
            String localPath = json.optString("local_file_path", "");
            if (!localPath.isEmpty()) {
                model.setMediaUri(localPath);
            } else {
                model.setMediaUri(json.optString("s3_key", ""));
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to parse media metadata for " + model.getMessageId() + ": " + e.getMessage());
        }
    }
}
