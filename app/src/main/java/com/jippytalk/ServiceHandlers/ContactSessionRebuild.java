package com.jippytalk.ServiceHandlers;

import android.content.Context;
import android.util.Log;

import com.jippytalk.TokenRefreshAPI;
import com.jippytalk.Database.ContactsDatabase.Repository.ContactsRepository;
import com.jippytalk.Database.MessagesDatabase.Repository.MessagesRepository;
import com.jippytalk.Encryption.DecryptionFailScenario;
import com.jippytalk.Extras;
import com.jippytalk.Managers.ChatManager;
import com.jippytalk.Managers.MessagesManager;
import com.jippytalk.Messages.Datahandlers.HandleOneTimePreKeyRetrieval;
import com.jippytalk.WebSocketConnection;
import com.jippytalk.UserDetailsRepository;

import org.java_websocket.client.WebSocketClient;
import org.json.JSONObject;

import java.util.UUID;
import java.util.concurrent.ExecutorService;

public class ContactSessionRebuild {

    private final Context                   context;
    private final WebSocketClient           webSocketClient;
    private final ContactsRepository        contactsRepository;
    private final MessagesRepository        messagesRepository;
    private final UserDetailsRepository     userDetailsRepository;
    private final TokenRefreshAPI           tokenRefreshAPI;
    private final DecryptionFailScenario    decryptionFailScenario;
    private final SendUnSyncedDataToServer  sendUnSyncedDataToServer;
    private final ExecutorService           executorService;

    public ContactSessionRebuild(Context context, WebSocketClient webSocketClient,
                                 ContactsRepository contactsRepository,
                                 MessagesRepository messagesRepository,
                                 UserDetailsRepository userDetailsRepository,
                                 TokenRefreshAPI tokenRefreshAPI,
                                 DecryptionFailScenario decryptionFailScenario,
                                 SendUnSyncedDataToServer sendUnSyncedDataToServer,
                                 ExecutorService executorService) {
        this.context                    =   context.getApplicationContext();
        this.webSocketClient            =   webSocketClient;
        this.contactsRepository         =   contactsRepository;
        this.messagesRepository         =   messagesRepository;
        this.userDetailsRepository      =   userDetailsRepository;
        this.tokenRefreshAPI            =   tokenRefreshAPI;
        this.decryptionFailScenario     =   decryptionFailScenario;
        this.sendUnSyncedDataToServer   =   sendUnSyncedDataToServer;
        this.executorService            =   executorService;
    }

    public void rebuildSessionAndReSendMessage(String response) {
        if (response == null) {
            Log.e(Extras.LOG_MESSAGE,"null response from server for invalid whisper message");
            return;
        }

        try {
            JSONObject  jsonObject  =   new JSONObject(response);
            String      messageId   =   jsonObject.getString("message_id");
            String      receiverId  =   jsonObject.getString("receiver_id");

            String systemMessageId  =   UUID.randomUUID().toString();
            messagesRepository.insertMessageToLocalStorageFromService(systemMessageId,
                    MessagesManager.SYSTEM_GIVEN_MESSAGE, receiverId, MessagesManager.CONTACT_KEYS_CHANGED,
                    MessagesManager.MESSAGE_DELIVERED, MessagesManager.NO_NEED_TO_PUSH_MESSAGE, System.currentTimeMillis(),
                    MessagesManager.DEFAULT_DELIVERED_TIMESTAMP,
                    MessagesManager.DEFAULT_READ_TIMESTAMP, MessagesManager.MESSAGE_NOT_STARRED, MessagesManager.MESSAGE_NOT_EDITED,
                    MessagesManager.SYSTEM_MESSAGE_TYPE, MessagesManager.DEFAULT_LATITUDE, MessagesManager.DEFAULT_LONGITUDE,
                    0, "", ChatManager.UNARCHIVE_CHAT, () -> {});

            messagesRepository.updateMessageAsSyncedWithServer(messageId, MessagesManager.MESSAGE_NOT_SYNCED_WITH_SERVER,
                    MessagesManager.NEED_MESSAGE_PUSH, isSuccess -> {

                    });

            decryptionFailScenario.retrieveContactSignalKeysOnKeysChange(receiverId, isSuccess -> {
                if (isSuccess) {
                    getContactDeviceIdToRebuildSession(receiverId);
                }
            });

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"null response from server for invalid whisper message");
        }
    }

    private void getContactDeviceIdToRebuildSession(String contactId) {
        executorService.execute(() -> {
            int contactDeviceId =   contactsRepository.getContactDeviceId(contactId);
            if (contactDeviceId != 0) {
                HandleOneTimePreKeyRetrieval handleOneTimePreKeyRetrieval   =   new HandleOneTimePreKeyRetrieval(context,
                userDetailsRepository, tokenRefreshAPI,
                (contactId1, deviceId) -> {
                    contactsRepository.getContactDetailsForMessagingActivity(contactId1);
                    sendUnSyncedDataToServer.getUnSyncedMessagesOfContact(contactId1, deviceId, webSocketClient);
                });
                handleOneTimePreKeyRetrieval.getContactOneTimePreKey(contactId);
            }
        });
    }
}
