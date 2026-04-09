package com.jippytalk.ServiceHandlers;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.util.Log;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.jippytalk.Encryption.DecryptionFailScenario;
import com.jippytalk.Messages.MessagingActivity;
import com.jippytalk.ServiceLocators.APIServiceLocator;
import com.jippytalk.ServiceLocators.AppServiceLocator;
import com.jippytalk.ServiceLocators.DatabaseServiceLocator;
import com.jippytalk.Database.ContactsDatabase.ContactsDatabase;
import com.jippytalk.Database.ContactsDatabase.DAO.ContactsDatabaseDAO;
import com.jippytalk.Database.MessagesDatabase.MessagesDatabaseDAO;
import com.jippytalk.Database.MessagesDatabase.MessagesDatabase;
import com.jippytalk.Encryption.MessageEncryptAndDecrypt;
import com.jippytalk.Extras;
import com.jippytalk.Managers.MessagesManager;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.MyApplication;
import com.jippytalk.ServiceHandlers.Models.UnSyncedDeliveredMessagesModel;
import com.jippytalk.ServiceHandlers.Models.UnSyncedSeenMessagesModel;
import com.jippytalk.ServiceLocators.RepositoryServiceLocator;
import com.jippytalk.Sessions.SessionCreationWorkManager;

import org.java_websocket.client.WebSocketClient;

import java.util.ArrayList;

public class SendUnSyncedDataToServer {

    private final Context               context;
    private final WebSocketClient       webSocketClient;
    private final MessagesDatabaseDAO   messagesDatabaseDAO;
    private final ContactsDatabaseDAO   contactsDatabaseDAO;
    private final SharedPreferences     sharedPreferences;
    private String                      userId, finalUserId;
    private SendUpdatesToServer         sendUpdatesToServer;
    private MessageEncryptAndDecrypt    messageEncryptAndDecrypt;
    private SendDataToServer            sendDataToServer;
    private DatabaseServiceLocator      databaseServiceLocator;
    private AppServiceLocator           appServiceLocator;
    private APIServiceLocator           apiServiceLocator;
    private RepositoryServiceLocator    repositoryServiceLocator;
    private DecryptionFailScenario      decryptionFailScenario;


    public SendUnSyncedDataToServer(Context context, WebSocketClient webSocketClient, DatabaseServiceLocator databaseServiceLocator,
                                    RepositoryServiceLocator repositoryServiceLocator,
                                    APIServiceLocator apiServiceLocator, AppServiceLocator appServiceLocator) {
        this.context                            =   context;
        this.webSocketClient                    =   webSocketClient;
        this.apiServiceLocator                  =   apiServiceLocator;
        this.appServiceLocator                  =   appServiceLocator;
        this.databaseServiceLocator             =   databaseServiceLocator;
        this.repositoryServiceLocator           =   repositoryServiceLocator;
        decryptionFailScenario                  =   apiServiceLocator.getDecryptionFailScenario();
        messagesDatabaseDAO                     =   databaseServiceLocator.getMessagesDatabaseDAO();
        contactsDatabaseDAO                     =   databaseServiceLocator.getContactsDatabaseDAO();
        messageEncryptAndDecrypt                =   MessageEncryptAndDecrypt.getInstance(context.getApplicationContext(),
                                                    appServiceLocator, decryptionFailScenario);
        sharedPreferences                       =   repositoryServiceLocator.getSharedPreferences();
        userId                                  =   sharedPreferences.getString(SharedPreferenceDetails.USERID, null);
        sendUpdatesToServer                     =   new SendUpdatesToServer(context, webSocketClient);
        sendDataToServer                        =   new SendDataToServer(context.getApplicationContext());
    }

    public void sendUnSyncedDeliveredMessagesToServer() {
        ArrayList<UnSyncedDeliveredMessagesModel> unSyncedDeliveredMessagesModelArrayList = new ArrayList<>();
        try (Cursor cursor  =   messagesDatabaseDAO.getUnSyncedDeliveredMessages()) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String messageId           =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_ID));
                    String senderId            =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.RECEIVER_ID));
                    String receiverId          =   userId;
                    long receivedTimestamp     =   cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.RECEIVE_TIMESTAMP));

                    unSyncedDeliveredMessagesModelArrayList.add(new UnSyncedDeliveredMessagesModel(
                            messageId, senderId, receiverId, MessagesManager.MESSAGE_DELIVERED, receivedTimestamp));
                }
                while (cursor.moveToNext());
                sendUpdatesToServer.updateReceivedMessageAsDelivered(unSyncedDeliveredMessagesModelArrayList);
            }
            else {
                Log.e(Extras.LOG_MESSAGE,"failed to send unSynced delivered Messages to the server due to cursor null in service class");
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"failed to send unSynced delivered Messages to the server due to cursor null in service class "+e.getMessage());
        }
    }

    public void sendUnSyncedSeenMessagesToServer() {
        ArrayList<UnSyncedSeenMessagesModel> unSyncedSeenMessagesModelArrayList =   new ArrayList<>();
        try (Cursor cursor  =   messagesDatabaseDAO.getUnSyncedSeenMessages()) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String  messageId            =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_ID));
                    String  senderId             =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.RECEIVER_ID));
                    String  receiverId           =   userId;
                    long    receivedTimestamp    =   cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.RECEIVE_TIMESTAMP));
                    long    readTimestamp        =   cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.READ_TIMESTAMP));

                    Log.e(Extras.LOG_MESSAGE,"sending seen message is " +
                            cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE)));

                    unSyncedSeenMessagesModelArrayList.add(new UnSyncedSeenMessagesModel(
                            messageId, senderId, receiverId, MessagesManager.MESSAGE_SEEN,
                            receivedTimestamp, readTimestamp
                    ));
                }
                while (cursor.moveToNext());
                sendUpdatesToServer.updateReceivedMessageAsDeliveredAndSeen(unSyncedSeenMessagesModelArrayList);
            }
            else {
                Log.e(Extras.LOG_MESSAGE,"failed to send unSynced Seen Messages to the server due to cursor null in service class");
            }
        }
        catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE,"failed to send unSynced Seen Messages to the server due to cursor null in service class "+e.getMessage());
        }
    }

    public void sendUnSyncedSentMessagesOrLinksToServer() {
        try (Cursor cursor  =   messagesDatabaseDAO.getUnSyncedSentMessagesOrLinks()) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String  messageId           =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_ID));
                    String  receiverId          =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.RECEIVER_ID));
                    String  message             =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE));
                    int     messageType         =   cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_TYPE));
                    int     editedStatus        =   cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.EDIT_STATUS));
                    long    latitude            =   cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.LATITUDE));
                    long    longitude           =   cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.LONGITUDE));
                    long    sentTimestamp       =   cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.TIMESTAMP));
                    int     reply               =   cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.IS_REPLY));
                    String  replyMessageId      =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.REPLY_TO_MESSAGE_ID));

                    try (Cursor cursor1 =   contactsDatabaseDAO.getContactDeviceId(receiverId)) {
                        if (cursor1 != null && cursor1.moveToFirst()) {
                            int contactDeviceId         =   cursor1.getInt(cursor1.getColumnIndexOrThrow
                                                            (ContactsDatabase.CONTACT_DEVICE_ID));

                            messageEncryptAndDecrypt.encryptMessage(receiverId, contactDeviceId, message,
                                    new MessageEncryptAndDecrypt.MessageEncryptionCallBacks() {
                                @Override
                                public void onMessageEncryptSuccessful(String encryptedMessage, int signalMessageType) {
                                    sendDataToServer.sendMessageToServer(
                                            webSocketClient,
                                            messageId,
                                            userId,
                                            receiverId,
                                            encryptedMessage,
                                            signalMessageType,
                                            messageType,
                                            MessagesManager.MESSAGE_SYNCED_WITH_SERVER,
                                            editedStatus,
                                            latitude,
                                            longitude,
                                            sentTimestamp,
                                            MessagesManager.DEFAULT_DELIVERED_TIMESTAMP,
                                            MessagesManager.DEFAULT_READ_TIMESTAMP,
                                            reply,
                                            replyMessageId);
                                    Log.e(Extras.LOG_MESSAGE, "sentMessage is " + receiverId + " " + contactDeviceId + " " + message);
                                }

                                @Override
                                public void onMessageEncryptionFail(int exceptionStatus) {
                                    startWorkManagerToCreateSessionForContact(receiverId);
                                }
                            });
                        }
                    }
                }
                while (cursor.moveToNext());
            }
            else {
                Log.e(Extras.LOG_MESSAGE,"failed to send unSynced Messages/Links to the server due to cursor null in service class");
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"failed to send unSynced Messages/Links to the server due to cursor null in service class "+e.getMessage());
        }
    }

    public void getUnSyncedMessagesOfContact(String contactId, int contactDeviceId, WebSocketClient webSocketClient) {
        try (Cursor cursor  =   messagesDatabaseDAO.getUnSyncedMessagesOfContact(contactId)) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    String  messageId           =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_ID));
                    String  receiverId          =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.RECEIVER_ID));
                    String  message             =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE));
                    int     messageType         =   cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_TYPE));
                    long    sentTimestamp       =   cursor.getLong(cursor.getColumnIndexOrThrow(MessagesDatabase.TIMESTAMP));
                    int     isReply             =   cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.IS_REPLY));
                    String  replyToMessageId    =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.REPLY_TO_MESSAGE_ID));

                    messageEncryptAndDecrypt.encryptMessage(receiverId, contactDeviceId, message
                            , new MessageEncryptAndDecrypt.MessageEncryptionCallBacks() {
                        @Override
                        public void onMessageEncryptSuccessful(String encryptedMessage, int signalMessageType) {
                            sendDataToServer.sendMessageToServer(webSocketClient, messageId, userId, receiverId, encryptedMessage,
                                    signalMessageType, messageType, MessagesManager.MESSAGE_SYNCED_WITH_SERVER,
                                    MessagesManager.MESSAGE_NOT_EDITED, MessagesManager.DEFAULT_LATITUDE,
                                    MessagesManager.DEFAULT_LONGITUDE, sentTimestamp, MessagesManager.DEFAULT_DELIVERED_TIMESTAMP,
                                    MessagesManager.DEFAULT_READ_TIMESTAMP, isReply, replyToMessageId);
                        }

                        @Override
                        public void onMessageEncryptionFail(int exceptionStatus) {
                            startWorkManagerToCreateSessionForContact(contactId);
                        }
                    });
                }
                while (cursor.moveToNext());
            }
        }
    }

    public void startWorkManagerToCreateSessionForContact(String contactId) {
        Data inputData = new Data.Builder()
                .putString("contactId", contactId).build();
        WorkRequest uploadWorkRequest = new OneTimeWorkRequest.Builder(SessionCreationWorkManager.class)
                .setInputData(inputData).build();
        WorkManager.getInstance(context.getApplicationContext()).enqueue(uploadWorkRequest);
        Log.e(Extras.LOG_MESSAGE, "session not exists");
    }
}
