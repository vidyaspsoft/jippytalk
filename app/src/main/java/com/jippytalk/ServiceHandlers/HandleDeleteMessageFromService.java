package com.jippytalk.ServiceHandlers;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import com.jippytalk.WebSocketConnection;
import com.jippytalk.ServiceLocators.DatabaseServiceLocator;
import com.jippytalk.Common.UpdateLastMessageIdForContact;
import com.jippytalk.Database.MessagesDatabase.MessagesDatabaseDAO;
import com.jippytalk.Database.MessagesDatabase.Repository.MessagesRepository;
import com.jippytalk.Extras;
import com.jippytalk.MyApplication;

import org.java_websocket.client.WebSocketClient;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class HandleDeleteMessageFromService {

    private final Context                                           context;
    private String                                                  openedChatId;
    private DatabaseServiceLocator                                  databaseServiceLocator;
    private WebSocketConnection.MessageCallBacks                    messageCallBacks;
    private WebSocketConnection.ChatListCallBacks                   chatListCallBacks;
    private SendDeleteRequestToServer                               sendDeleteRequestToServer;
    private WebSocketClient                                         webSocketClient;
    private MessagesRepository                                      messagesRepository;
    private boolean                                                 isMessagingActivityVisible      =   false;
    private boolean                                                 isMainActivityVisible           =   false;
    private final ConcurrentHashMap<String, Pair<String, String>>   deleteContextMap                =   new ConcurrentHashMap<>();


    public void setMessageCallback(WebSocketConnection.MessageCallBacks callback) {
        this.messageCallBacks                   =   callback;
    }

    public void setChatListCallback(WebSocketConnection.ChatListCallBacks callback) {
        this.chatListCallBacks                  =   callback;
    }

    public void setMessagingActivityVisible(boolean isVisible) {
        this.isMessagingActivityVisible         =   isVisible;
    }

    public void setMainActivityVisible(boolean isVisible) {
        this.isMainActivityVisible              =   isVisible;
    }

    public void getOpenedChatId(String contactId) {
        this.openedChatId                       = contactId;
    }


    public HandleDeleteMessageFromService(Context context, WebSocketClient webSocketClient, MessagesRepository messagesRepository) {
        this.context                =   context.getApplicationContext();
        this.webSocketClient        =   webSocketClient;
        databaseServiceLocator      =   ((MyApplication) context.getApplicationContext()).getDatabaseServiceLocator();
        sendDeleteRequestToServer   =   new SendDeleteRequestToServer(webSocketClient);
        this.messagesRepository     =   messagesRepository;
    }

    public void deleteForEveryoneInserted(String message) {
        try {
            JSONObject  jsonObject      =   new JSONObject(message);
            String      messageId       =   jsonObject.getString("message_id");
            String      receiverId      =   jsonObject.getString("receiver_id");

            Log.e(Extras.LOG_MESSAGE,"delete data is " + message);

            if (messagesRepository != null) {
                messagesRepository.deleteMessage(messageId);
            } else {
                Log.e(Extras.LOG_MESSAGE,"Unable to get message id to delete at receiver side repo is null");
            }
        }
        catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to get message id to delete at receiver side "+e.getMessage());
        }
    }

    public void deleteReceivedMessageRequestedBySender(String message) {
        try {
            JSONObject  jsonObject      =   new JSONObject(message);
            String      messageId       =   jsonObject.getString("message_id");
            String      senderId        =   jsonObject.getString("sender_id");
            String      receiverId      =   jsonObject.getString("receiver_id");

            deleteContextMap.put(messageId, new Pair<>(senderId, receiverId));

            messagesRepository.deleteReceivedMessageRequestedFromService(messageId, isSuccess -> {
                messagesRepository.getMessagesForContact(senderId);
                sendDeleteRequestToServer.deleteMessageInServerAfterDeliveredAndSeen(messageId, senderId, receiverId);
                UpdateLastMessageIdForContact   updateLastMessageIdForContact   =
                        new UpdateLastMessageIdForContact(context.getApplicationContext());
                updateLastMessageIdForContact.updateContactLastMessageId(senderId);
            });

        } catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to get message id to delete at receiver side "+e.getMessage());
        }
    }
}
