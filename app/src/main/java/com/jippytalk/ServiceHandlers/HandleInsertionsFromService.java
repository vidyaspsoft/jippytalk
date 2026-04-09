package com.jippytalk.ServiceHandlers;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.util.Base64;
import android.util.Log;


import com.jippytalk.Encryption.DecryptionFailScenario;
import com.jippytalk.FirebasePushNotifications.ArchivedChatNotifications;
import com.jippytalk.Managers.SessionManager;
import com.jippytalk.WebSocketConnection;
import com.jippytalk.ServiceLocators.APIServiceLocator;
import com.jippytalk.ServiceLocators.AppServiceLocator;
import com.jippytalk.ServiceLocators.DatabaseServiceLocator;
import com.jippytalk.CommonUtils;
import com.jippytalk.Database.ContactsDatabase.DAO.ContactsDatabaseDAO;
import com.jippytalk.Database.MessagesDatabase.MessagesDatabaseDAO;
import com.jippytalk.Database.MessagesDatabase.MessagesDatabase;
import com.jippytalk.Database.MessagesDatabase.Repository.ChatListRepository;
import com.jippytalk.Database.ContactsDatabase.Repository.ContactsRepository;
import com.jippytalk.Database.MessagesDatabase.Repository.MessagesRepository;
import com.jippytalk.Extras;
import com.jippytalk.Managers.AccountManager;
import com.jippytalk.Managers.ChatManager;
import com.jippytalk.Managers.ContactManager;
import com.jippytalk.Managers.MessagesManager;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.Encryption.MessageEncryptAndDecrypt;
import com.jippytalk.MyApplication;
import com.jippytalk.R;
import com.jippytalk.FirebasePushNotifications.MessageNotifications;
import com.jippytalk.ServiceHandlers.Models.UnSyncedDeliveredMessagesModel;
import com.jippytalk.ServiceHandlers.Models.UnSyncedSeenMessagesModel;
import com.jippytalk.ServiceLocators.RepositoryServiceLocator;

import org.java_websocket.client.WebSocketClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HandleInsertionsFromService {

    private final Context                                   context;
    private final DatabaseServiceLocator                    databaseServiceLocator;
    private final RepositoryServiceLocator                  repositoryServiceLocator;
    private final APIServiceLocator                         apiServiceLocator;
    private final AppServiceLocator                         appServiceLocator;
    private final ContactsDatabaseDAO                       contactsDatabaseDAO;
    private final MessagesDatabaseDAO                       messagesDatabaseDAO;
    private WebSocketConnection.MessageCallBacks            messageCallBacks;
    private WebSocketConnection.ChatListCallBacks           chatListCallBacks;
    private WebSocketConnection.ArchiveChatListCallBacks    archiveChatListCallBacks;
    private WebSocketConnection.UnknownChatListCallBacks    unknownChatListCallBacks;
    private final SharedPreferences                         sharedPreferences;
    private long                                            deliveredTimestamp, readTimestamp;
    private String                                          openedChatId;
    private int                                             archiveStatusFinal;
    private final String                                    userId;
    private final DecryptionFailScenario                    decryptionFailScenario;
    private final MessagesRepository                        messagesRepository;
    private final ChatListRepository                        chatListRepository;
    private final ContactsRepository                        contactsRepository;
    private final MessageEncryptAndDecrypt                  messageEncryptAndDecrypt;
    private final MessageNotifications                      messageNotifications;
    private final WebSocketClient                           webSocketClient;
    private final HandleDecryptionExceptions handleDecryptionExceptions;
    private final SendUpdatesToServer sendUpdatesToServer;
    private static final Object                             CHAT_LIST_LOCK                  =   new Object();
    private boolean                                         isMessagingActivityVisible      =   false;
    private boolean                                         isMainActivityVisible           =   false;
    private boolean                                         isArchiveChatsActivityVisible   =   false;
    private boolean                                         isMessageRequestActivityVisible =   false;
    private final ExecutorService                           executorService                 =   Executors.newSingleThreadExecutor();


    public void setMessageCallback(WebSocketConnection.MessageCallBacks callback) {
        this.messageCallBacks                   =   callback;
    }

    public void setChatListCallback(WebSocketConnection.ChatListCallBacks callback) {
        this.chatListCallBacks                  =   callback;
    }

    public void setArchiveChatsListCallback(WebSocketConnection.ArchiveChatListCallBacks callback) {
        this.archiveChatListCallBacks           =   callback;
    }

    public void setUnknownChatListCallBacks(WebSocketConnection.UnknownChatListCallBacks callBacks) {
        this.unknownChatListCallBacks           =   callBacks;
    }

    public void setMessagingActivityVisible(boolean isVisible) {
        this.isMessagingActivityVisible         =   isVisible;
    }

    public void setMainActivityVisible(boolean isVisible) {
        this.isMainActivityVisible              =   isVisible;
    }

    public void setArchiveChatsActivityVisible(boolean isVisible) {
        this.isArchiveChatsActivityVisible      =   isVisible;
    }

    public void setMessageRequestActivityVisible(boolean isVisible) {
        this.isMessageRequestActivityVisible    =   isVisible;
    }

    public void getOpenedChatId(String contactId) {
        this.openedChatId                       = contactId;
    }


    public HandleInsertionsFromService(Context context, WebSocketClient  webSocketClient, DatabaseServiceLocator databaseServiceLocator,
                                       APIServiceLocator apiServiceLocator, RepositoryServiceLocator repositoryServiceLocator,
                                       AppServiceLocator appServiceLocator) {
        this.context                    =   context;
        this.webSocketClient            =   webSocketClient;
        this.databaseServiceLocator     =   databaseServiceLocator;
        this.apiServiceLocator          =   apiServiceLocator;
        this.repositoryServiceLocator   =   repositoryServiceLocator;
        this.appServiceLocator          =   appServiceLocator;
        contactsDatabaseDAO             =   databaseServiceLocator.getContactsDatabaseDAO();
        messagesDatabaseDAO             =   databaseServiceLocator.getMessagesDatabaseDAO();
        sendUpdatesToServer             =   new SendUpdatesToServer(context.getApplicationContext(), webSocketClient);
        handleDecryptionExceptions      =   new HandleDecryptionExceptions(webSocketClient,
                                            apiServiceLocator.getApiLevelExecutorService());
        sharedPreferences               =   repositoryServiceLocator.getSharedPreferences();
        userId                          =   sharedPreferences.getString(SharedPreferenceDetails.USERID,"");
        chatListRepository              =   repositoryServiceLocator.getChatListRepository();
        messagesRepository              =   repositoryServiceLocator.getMessagesRepository();
        contactsRepository              =   repositoryServiceLocator.getContactsRepository();
        decryptionFailScenario          =   apiServiceLocator.getDecryptionFailScenario();
        messageEncryptAndDecrypt        =   MessageEncryptAndDecrypt.getInstance(context.getApplicationContext(), appServiceLocator,
                                            decryptionFailScenario);
        messageNotifications            =   new MessageNotifications(context.getApplicationContext(), messagesRepository,
                                            contactsRepository);
    }

    public void handleMessageInsertion(String response) {
        executorService.execute(() -> {
            try {
                JSONObject  jsonObject                  =   new JSONObject(response);
                String      messageId                   =   jsonObject.getString("message_id");
                String      senderId                    =   jsonObject.getString("sender_id");
                String      receiverId                  =   jsonObject.getString("receiver_id");
                String      message                     =   jsonObject.getString("message");
                int         messageType                 =   jsonObject.getInt("message_type");
                int         signalMessageType           =   jsonObject.getInt("signalMessageType");
                int         messageEditedStatus         =   jsonObject.getInt("edited_status");
                long        latitude                    =   jsonObject.getLong("latitude");
                long        longitude                   =   jsonObject.getLong("longitude");
                long        sentTimestamp               =   jsonObject.getLong("sent_timestamp");
                int         deviceId                    =   jsonObject.getInt("device_id");
                String      contactPhone                =   jsonObject.getString("phone");
                String      contactAbout                =   jsonObject.getString("about");
                String      profilePic                  =   jsonObject.getString("profilePic");
                long        createdOn                   =   jsonObject.getLong("created_on");
                int         isReply                     =   jsonObject.getInt("is_reply");
                String      replyToMsgId                =   jsonObject.getString("reply_to_message_id");

                Log.e(Extras.LOG_MESSAGE, response);

                byte[]  decodedMessage      =   Base64.decode(message, Base64.NO_WRAP);

                if (messagesRepository != null) {

                    messageEncryptAndDecrypt.decryptMessage(senderId, deviceId, decodedMessage, signalMessageType,
                            false, MessagesManager.MAX_DECRYPTION_TRIES,
                            new MessageEncryptAndDecrypt.MessageDecryptionCallBack() {
                        @Override
                        public void onMessageDecryptSuccessful(String decryptedMessage, boolean keysChanged) {
                            int     dndOption                   =   sharedPreferences.getInt(
                                                                    SharedPreferenceDetails.USER_STATUS_OPTION,
                                                                    AccountManager.FREE);
                            boolean allUsersReadReceipts        =   sharedPreferences.getBoolean(
                                                                    SharedPreferenceDetails.MESSAGES_READ_RECEIPTS,
                                                                    true);
                            boolean msgNotificationsStatus      =   sharedPreferences.getBoolean(
                                                                    SharedPreferenceDetails.MESSAGES_NOTIFICATIONS_SWITCH,
                                                                    true);
                            boolean msgRequestNotifications     =   sharedPreferences.getBoolean(
                                                                    SharedPreferenceDetails.MESSAGES_RQST_NOTIFICATIONS_SWITCH,
                                                                    true);
                            int     contactMeOption             =   sharedPreferences.getInt(
                                                                    SharedPreferenceDetails.CONTACT_ME_PRIVACY_OPTION,
                                                                    MessagesManager.ONLY_MY_CONTACTS);
                            long    currentTime                 =   System.currentTimeMillis();
                            int     archiveStatus               =   ChatManager.UNARCHIVE_CHAT;
                            int     messageStatus               =   MessagesManager.MESSAGE_DELIVERED_LOCALLY;
                            int     contactReadReceiptStatus    =   ChatManager.READ_RECEIPTS_ON;
                            int     needPush                    =   MessagesManager.NEED_MESSAGE_PUSH;
                            Log.e(Extras.LOG_MESSAGE, " decrypted text is " + decryptedMessage +" " + sentTimestamp);

                            synchronized (CHAT_LIST_LOCK) {
                                boolean chatListCheck       =   messagesDatabaseDAO.checkChatListId(senderId);
                                if (chatListCheck) {
                                    try (Cursor cursor  =   messagesDatabaseDAO.getContactMessagePrivacyDetailsFromChatList(senderId)) {
                                        if (cursor != null && cursor.moveToFirst()) {
                                            contactReadReceiptStatus    =   cursor.getInt(cursor.getColumnIndexOrThrow(
                                                    MessagesDatabase.CHAT_READ_RECEIPTS));
                                            archiveStatus               =   cursor.getInt(cursor.getColumnIndexOrThrow(
                                                    MessagesDatabase.CHAT_ARCHIVE));
                                        }
                                    }
                                    catch (Exception e) {
                                        Log.e(Extras.LOG_MESSAGE,"Unable to get contact read receipt status " + e.getMessage());
                                    }
                                }
                                else {
                                    switch (contactMeOption) {
                                        case MessagesManager.EVERYONE_CAN_CONTACT_ME -> archiveStatus = ChatManager.UNARCHIVE_CHAT;
                                        case MessagesManager.ONLY_MY_CONTACTS -> {
                                            if (isContact(contactPhone)) {
                                                archiveStatus = ChatManager.UNARCHIVE_CHAT;
                                            } else {
                                                archiveStatus = ChatManager.UNKNOWN_CHAT;
                                            }
                                        }
                                    }
                                    switch (archiveStatus) {
                                        case ChatManager.UNARCHIVE_CHAT -> {
                                            if (isMainActivityVisible && chatListCallBacks != null) {
                                                chatListRepository.getAllNormalChats();
                                            }
                                        }
                                        case ChatManager.UNKNOWN_CHAT -> {
                                            if (isMainActivityVisible && chatListCallBacks != null) {
                                                chatListCallBacks.insertUnknownContactChat();
                                            }
                                        }
                                    }
                                }
                            }

                            archiveStatusFinal  =   archiveStatus;

                            if (isContact(contactPhone)) {
                                Log.e(Extras.LOG_MESSAGE, "is contact");
                                contactsRepository.updateContactAsUserInCaseOfSyncFail(senderId, contactPhone, deviceId,
                                        contactAbout, createdOn, profilePic);
                            } else {
                                Log.e(Extras.LOG_MESSAGE, "not contact");
                                contactsRepository.insertNonContactUsers(0, senderId, ContactManager.IS_APP_USER,
                                        ContactManager.NOT_A_CONTACT, "", contactPhone, contactAbout,
                                        createdOn, CommonUtils.sha256Hash(contactPhone),
                                        ContactManager.CONTACT_NOT_FAVOURITE, profilePic, deviceId);
                            }

                            boolean isChatScreenVisible = isMessagingActivityVisible && openedChatId != null
                                    && openedChatId.equals(senderId);

                            Log.e(Extras.LOG_MESSAGE, "read receipts " + contactReadReceiptStatus);

                            switch (archiveStatus) {
                                case ChatManager.UNARCHIVE_CHAT, ChatManager.ARCHIVE_CHAT -> {
                                    if (isChatScreenVisible && allUsersReadReceipts &&
                                            contactReadReceiptStatus == ChatManager.READ_RECEIPTS_ON) {
                                        messageStatus       =   MessagesManager.MESSAGE_SEEN;
                                        readTimestamp       =   currentTime;
                                        deliveredTimestamp  =   currentTime;
                                    } else {
                                        messageStatus       =   MessagesManager.MESSAGE_DELIVERED;
                                        readTimestamp       =   MessagesManager.DEFAULT_READ_TIMESTAMP;
                                        deliveredTimestamp  =   currentTime;
                                    }
                                }
                                case ChatManager.UNKNOWN_CHAT -> {
                                    messageStatus       =   MessagesManager.MESSAGE_DELIVERED;
                                    readTimestamp       =   MessagesManager.DEFAULT_READ_TIMESTAMP;
                                    deliveredTimestamp  =   currentTime;
                                    needPush            =   MessagesManager.NO_NEED_TO_PUSH_MESSAGE;
                                }
                            }

                            Log.e(Extras.LOG_MESSAGE, "message status " + messageStatus);

                            int finalArchiveStatus              =   archiveStatus;
                            int finalContactReadReceiptStatus   =   contactReadReceiptStatus;
                            int finalMessageStatus              =   messageStatus;

                            if (keysChanged) {
                                messagesRepository.insertMessageToLocalStorageFromService(UUID.randomUUID().toString(),
                                        MessagesManager.SYSTEM_GIVEN_MESSAGE, senderId, MessagesManager.CONTACT_KEYS_CHANGED,
                                        MessagesManager.MESSAGE_DELIVERED, MessagesManager.NO_NEED_TO_PUSH_MESSAGE,
                                        System.currentTimeMillis(), deliveredTimestamp,
                                        readTimestamp, MessagesManager.MESSAGE_NOT_STARRED, MessagesManager.MESSAGE_NOT_EDITED,
                                        MessagesManager.SYSTEM_MESSAGE_TYPE, latitude, longitude,
                                        isReply, replyToMsgId, archiveStatus, () -> {});
                            }

                            messagesRepository.insertMessageToLocalStorageFromService(messageId, MessagesManager.MESSAGE_INCOMING,
                                    senderId, decryptedMessage, finalMessageStatus,
                                    needPush, System.currentTimeMillis(), deliveredTimestamp,
                                    readTimestamp, MessagesManager.MESSAGE_NOT_STARRED, MessagesManager.MESSAGE_NOT_EDITED,
                                    messageType, latitude, longitude,
                                    isReply, replyToMsgId, archiveStatus, () -> {

                                switch (finalArchiveStatus) {
                                    case ChatManager.UNARCHIVE_CHAT -> {
                                        if (messageCallBacks != null && isMessagingActivityVisible &&
                                                openedChatId != null && openedChatId.equals(senderId)) {
                                            messagesRepository.getMessagesForContact(senderId);
                                            playReceiveMessageSound();

                                            boolean allowReadReceipt = allUsersReadReceipts && finalContactReadReceiptStatus == ChatManager.READ_RECEIPTS_ON;

                                            if (isChatScreenVisible) {
                                                if (allowReadReceipt) {
                                                    sendUnSyncedSeenMessagesToServer(messageId, senderId, MessagesManager.MESSAGE_SEEN,
                                                            deliveredTimestamp, readTimestamp);
                                                } else {
                                                    sendUnSyncedDeliveredMessagesToServer(messageId, senderId, MessagesManager.MESSAGE_DELIVERED,
                                                            deliveredTimestamp);
                                                }
                                            }
                                        }
                                        else if (isMainActivityVisible && chatListCallBacks != null) {
                                            messagesRepository.updateUnreadCount(senderId, chatListRepository::getAllNormalChats);
                                            sendUnSyncedDeliveredMessagesToServer(messageId, senderId, MessagesManager.MESSAGE_DELIVERED,
                                                    deliveredTimestamp);
                                        }
                                        else {
                                            messagesRepository.updateUnreadCount(senderId, () -> {});
                                            sendUnSyncedDeliveredMessagesToServer(messageId, senderId,
                                                    MessagesManager.MESSAGE_DELIVERED, deliveredTimestamp);
                                        }
                                    }
                                    case ChatManager.ARCHIVE_CHAT -> {
                                        if (messageCallBacks != null && isMessagingActivityVisible &&
                                                openedChatId != null && openedChatId.equals(senderId)) {
                                            messagesRepository.getMessagesForContact(senderId);
                                            playReceiveMessageSound();
                                            sendUnSyncedSeenMessagesToServer(messageId, senderId,
                                                    MessagesManager.MESSAGE_SEEN, deliveredTimestamp, readTimestamp);
                                        }
                                        else if (isArchiveChatsActivityVisible && archiveChatListCallBacks != null) {
                                            messagesRepository.updateUnreadCount(senderId, chatListRepository::getAllArchivedChats);
                                            sendUnSyncedDeliveredMessagesToServer(messageId, senderId, MessagesManager.MESSAGE_DELIVERED,
                                                    deliveredTimestamp);
                                        } else if (isMainActivityVisible && chatListCallBacks != null) {
                                            messagesRepository.updateUnreadCount(senderId, chatListRepository::checkForUnreadCountInArchiveChatsInDb);
                                            sendUnSyncedDeliveredMessagesToServer(messageId, senderId, MessagesManager.MESSAGE_DELIVERED,
                                                    deliveredTimestamp);
                                        } else {
                                            messagesRepository.updateUnreadCount(senderId, () -> {});
                                            sendUnSyncedDeliveredMessagesToServer(messageId, senderId,
                                                    MessagesManager.MESSAGE_DELIVERED, deliveredTimestamp);
                                        }
                                    }
                                    case ChatManager.UNKNOWN_CHAT -> {
                                        if (messageCallBacks != null && isMessagingActivityVisible &&
                                                openedChatId != null && openedChatId.equals(senderId)) {
                                            messagesRepository.getMessagesForContact(senderId);
                                            playReceiveMessageSound();
                                            sendUnSyncedDeliveredMessagesToServer(messageId, senderId,
                                                    MessagesManager.UNKNOWN_NUMBER_MESSAGE, MessagesManager.DEFAULT_DELIVERED_TIMESTAMP);
                                        }
                                        else if (isMessageRequestActivityVisible && unknownChatListCallBacks != null) {
                                            sendUnSyncedDeliveredMessagesToServer(messageId, senderId,
                                                    MessagesManager.UNKNOWN_NUMBER_MESSAGE, MessagesManager.DEFAULT_DELIVERED_TIMESTAMP);
                                            messagesRepository.updateUnreadCount(senderId, chatListRepository::getAllMessageRequestChats);
                                        }
                                        else {
                                            sendUnSyncedDeliveredMessagesToServer(messageId, senderId,
                                                    MessagesManager.UNKNOWN_NUMBER_MESSAGE, MessagesManager.DEFAULT_DELIVERED_TIMESTAMP);
                                            messagesRepository.updateUnreadCount(senderId, () -> {});
                                            chatListRepository.getAllMessageRequestChatsCount();
                                        }
                                    }
                                }

                                switch (finalArchiveStatus) {
                                    case ChatManager.UNARCHIVE_CHAT -> {
                                        if (openedChatId != null && !openedChatId.equals(senderId) && messageNotifications != null
                                        && dndOption == AccountManager.FREE && msgNotificationsStatus) {
                                            messageNotifications.showNotificationWithReply();
                                        }
                                    }
                                    case ChatManager.ARCHIVE_CHAT -> {
                                        if (dndOption == AccountManager.FREE && msgNotificationsStatus) {
                                            new ArchivedChatNotifications().showArchivedChatsNotification(context.getApplicationContext());
                                        }
                                    }
                                    case ChatManager.UNKNOWN_CHAT -> {
                                        if ( dndOption == AccountManager.FREE && msgRequestNotifications) {
                                            new ArchivedChatNotifications().showMessageRequestsNotification(context.getApplicationContext());
                                        }
                                    }
                                }
                            });
                        }

                        @Override
                        public void onDecryptionFail(int exceptionStatus) {
                            if (exceptionStatus == SessionManager.INVALID_MESSAGE_EXCEPTION) {
                                handleDecryptionExceptions.handleInvalidMessageException(
                                        messageId, senderId, userId, MessagesManager.INVALID_MESSAGE_DECRYPTION
                                );
                            }
                        }
                    });
                }
            }
            catch (JSONException e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to insert new message " + e.getMessage(), e);
            }
        });
    }

    public boolean isContact(String contactPhoneNumber) {
        return contactsDatabaseDAO.checkIsContactOrNot(contactPhoneNumber);
    }

    public void playReceiveMessageSound() {
        MediaPlayer music = MediaPlayer.create(context.getApplicationContext(), R.raw.message_receive_sound);
        music.start();
    }

    private void sendUnSyncedDeliveredMessagesToServer(String messageId, String senderId, int messageStatus,
                                                       long deliveredTimestamp) {
        ArrayList<UnSyncedDeliveredMessagesModel>   unSyncedDeliveredMessagesModelArrayList =   new ArrayList<>();
        unSyncedDeliveredMessagesModelArrayList.add(new UnSyncedDeliveredMessagesModel(
                messageId, senderId, userId, messageStatus, deliveredTimestamp));
        Log.e(Extras.LOG_MESSAGE,"message status is " + messageStatus);
        sendUpdatesToServer.updateReceivedMessageAsDelivered(unSyncedDeliveredMessagesModelArrayList);
    }

    private void sendUnSyncedSeenMessagesToServer(String messageId, String senderId, int messageStatus,
                                                  long deliveredTimestamp, long seenTimestamp) {
        ArrayList<UnSyncedSeenMessagesModel>    unSyncedSeenMessagesModelArrayList
                =   new ArrayList<>();
        unSyncedSeenMessagesModelArrayList.add(new UnSyncedSeenMessagesModel(
                messageId, senderId, userId, messageStatus,
                deliveredTimestamp, seenTimestamp
        ));
        sendUpdatesToServer.updateReceivedMessageAsDeliveredAndSeen(
                unSyncedSeenMessagesModelArrayList);
    }
}
