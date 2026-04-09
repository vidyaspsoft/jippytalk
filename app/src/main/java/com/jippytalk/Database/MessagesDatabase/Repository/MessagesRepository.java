package com.jippytalk.Database.MessagesDatabase.Repository;

import static android.view.View.VISIBLE;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;


import com.jippytalk.Database.ContactsDatabase.DAO.ContactsDatabaseDAO;
import com.jippytalk.Database.ContactsDatabase.Repository.ContactsRepository;
import com.jippytalk.Database.MessagesDatabase.Model.MarkMessagesAsDeliveredAndSeenModel;
import com.jippytalk.Database.MessagesDatabase.Model.MarkMessagesAsDeliveredModel;
import com.jippytalk.Database.MessagesDatabase.Model.MarkMessagesAsSeenModel;
import com.jippytalk.Managers.ChatManager;
import com.jippytalk.MessagesForward.Model.MessageForwardChatsListModel;
import com.jippytalk.WebSocketConnection;
import com.jippytalk.ServiceLocators.AppServiceLocator;
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

    public void insertMessageToLocalStorageFromService(String messageId, int messageDirection, String receiverId,
                                            String message, int messageStatus, int needPush, long sentTimestamp,
                                            long receivedTimestamp, long readTimestamp, int isStarred,
                                            int editedStatus, int messageType, double latitude,
                                            double longitude, int isReply, String replyToMsgId,
                                            int chatArchive, Runnable onComplete) {

        writeExecutor.execute(() -> {
            try {
                boolean inserted = messagesDatabaseDAO.insertMessage(
                        messageId, messageDirection, receiverId,
                        message, messageStatus, needPush,
                        sentTimestamp, receivedTimestamp, readTimestamp,
                        isStarred, editedStatus, messageType,
                        latitude, longitude, isReply,
                        replyToMsgId, chatArchive);

                if (inserted && onComplete != null) {
                    new Handler(Looper.getMainLooper()).post(onComplete);
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "message insert failed error in repository " + e.getMessage(), e);
            }
        });
    }

    // ------------- Database Insertions ends here --------------------------------------


    // ------------- Database Reads starts from here -----------------------------------

//    public void getMessagesForContactChatScreen(String contactId, int limit, int offset) {
//        readExecutor.execute(() -> {
//            String contactName      =   contactsRepository.getContactNameForRepliedMessages(contactId);
//            ArrayList<MessageModal> messages = new ArrayList<>();
//            try (Cursor cursor = messagesDatabaseDAO.getMessagesPaginationForContact(contactId, limit, offset)) {
//                if (cursor != null && cursor.moveToFirst()) {
//                    do {
//                        String  repliedMessageText          = cursor.getString(cursor.getColumnIndexOrThrow("replied_message_text"));
//                        int     repliedMessageDirection     = cursor.getInt(cursor.getColumnIndexOrThrow("replied_message_direction"));
//                        MessageModal messageModal = new MessageModal(
//                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_ID)),
//                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_DIRECTION)),
//                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.RECEIVER_ID)),
//                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE)),
//                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_STATUS)),
//                                cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.TIMESTAMP)),
//                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.STARRED)),
//                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.EDIT_STATUS)),
//                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_TYPE)),
//                                cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.IS_REPLY)),
//                                cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.REPLY_TO_MESSAGE_ID)),
//                                repliedMessageText,
//                                repliedMessageDirection,
//                                contactName
//                        );
//                        messages.add(messageModal);
//                    }
//                    while (cursor.moveToNext());
//                    List<MessageModal> currentList = messagesMutableData.getValue();
//                    if (currentList == null || offset == 0) {
//                        messagesMutableData.postValue(messages);
//                    } else {
//                        ArrayList<MessageModal> updatedList = new ArrayList<>(messages);
//                        updatedList.addAll(currentList);
//                        messagesMutableData.postValue(updatedList);
//                    }
//                } else {
//                    Log.e(Extras.LOG_MESSAGE, "Error loading messages cursor null");
//                    messagesMutableData.postValue(new ArrayList<>());
//                }
//            } catch (Exception e) {
//                Log.e(Extras.LOG_MESSAGE, "Error loading messages " + e.getMessage());
//                messagesMutableData.postValue(new ArrayList<>());
//            }
//        });
//    }

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
}
