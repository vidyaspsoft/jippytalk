package com.jippytalk.ServiceHandlers;

import android.content.Context;
import android.media.MediaPlayer;
import android.util.Log;
import android.util.Pair;

import com.jippytalk.Database.MessagesDatabase.Model.MarkMessagesAsDeliveredAndSeenModel;
import com.jippytalk.Database.MessagesDatabase.Model.MarkMessagesAsDeliveredModel;
import com.jippytalk.R;
import com.jippytalk.ServiceHandlers.Models.DeleteMessageDataInServerModel;
import com.jippytalk.ServiceLocators.DatabaseServiceLocator;
import com.jippytalk.Database.ContactsDatabase.ContactsDatabase;
import com.jippytalk.Database.ContactsDatabase.DAO.ContactsDatabaseDAO;
import com.jippytalk.Database.MessagesDatabase.MessagesDatabaseDAO;
import com.jippytalk.Database.MessagesDatabase.MessagesDatabase;
import com.jippytalk.Database.MessagesDatabase.Repository.ChatListRepository;
import com.jippytalk.Database.MessagesDatabase.Repository.MessagesRepository;
import com.jippytalk.Extras;
import com.jippytalk.Managers.MessagesManager;
import com.jippytalk.MyApplication;
import com.jippytalk.ServiceLocators.RepositoryServiceLocator;
import com.jippytalk.WebSocketConnection;

import org.java_websocket.client.WebSocketClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class HandleUpdatesFromService {

    private final Context                                           context;
    private final DatabaseServiceLocator                            databaseServiceLocator;
    private final RepositoryServiceLocator                          repositoryServiceLocator;
    private final WebSocketClient                                   webSocketClient;
    private final ContactsDatabase                                  contactsDatabase;
    private final ContactsDatabaseDAO                               contactsDatabaseDAO;
    private final MessagesDatabase                                  messagesDatabase;
    private final MessagesDatabaseDAO                               messagesDatabaseDAO;
    private WebSocketConnection.MessageCallBacks                    messageCallBacks;
    private WebSocketConnection.ChatListCallBacks                   chatListCallBacks;
    private String                                                  openedChatId;
    private final ChatListRepository                                chatListRepository;
    private MessagesRepository                                      messagesRepository;
    private final SendDeleteRequestToServer sendDeleteRequestToServer;
    private boolean                                                 isMessagingActivityVisible  =   false;
    private boolean                                                 isMainActivityVisible       =   false;
    private final ConcurrentHashMap<String, Pair<String, String>>   updatesContactId            =   new ConcurrentHashMap<>();


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
        this.openedChatId                       =   contactId;
    }



    public HandleUpdatesFromService(Context context, WebSocketClient webSocketClient) {
        this.context                =   context;
        this.webSocketClient        =   webSocketClient;
        databaseServiceLocator      =   ((MyApplication) context.getApplicationContext()).getDatabaseServiceLocator();
        repositoryServiceLocator    =   ((MyApplication) context.getApplicationContext()).getRepositoryServiceLocator();
        contactsDatabase            =   databaseServiceLocator.getContactsDatabase();
        contactsDatabaseDAO         =   databaseServiceLocator.getContactsDatabaseDAO();
        messagesDatabase            =   databaseServiceLocator.getSqlite();
        messagesDatabaseDAO         =   databaseServiceLocator.getMessagesDatabaseDAO();
        sendDeleteRequestToServer   =   new SendDeleteRequestToServer(webSocketClient);
        chatListRepository          =   repositoryServiceLocator.getChatListRepository();
        messagesRepository          =   repositoryServiceLocator.getMessagesRepository();
    }

    public void updateSentMessageAsSyncedWithServer(String response) {
        try {
            JSONObject jsonObject       =   new JSONObject(response);
            String messageId            =   jsonObject.getString("message_id");
            String contactId            =   jsonObject.getString("receiver_id");
            String result               =   jsonObject.getString("status");
            if (result.equals("messageSyncedSuccessfully")) {
                messagesRepository.updateMessageAsSyncedWithServer(messageId, MessagesManager.MESSAGE_SYNCED_WITH_SERVER,
                        MessagesManager.NO_NEED_TO_PUSH_MESSAGE, isSuccess -> {
                    if (isSuccess) {
                        if (openedChatId == null) {
                            Log.e(Extras.LOG_MESSAGE,"openChatId is null " + openedChatId);
                            chatListRepository.getAllNormalChats();
                            return;
                        }

                        if (!isMessagingActivityVisible) {
                            Log.e(Extras.LOG_MESSAGE,"isMessagingActivityVisible is false");
                            chatListRepository.getAllNormalChats();
                            return;
                        }

                        if (messageCallBacks == null) {
                            Log.e(Extras.LOG_MESSAGE,"messageCallBacks is null");
                            chatListRepository.getAllNormalChats();
                        }

                        if (isMessagingActivityVisible && openedChatId.equals(contactId) && messageCallBacks != null) {
                            messageCallBacks.updateMessageStatus(messageId, MessagesManager.MESSAGE_SYNCED_WITH_SERVER);
                            playSound();
                        }

                        if (isMainActivityVisible) {
                            Log.e(Extras.LOG_MESSAGE,"isMainActivityVisible is true");
                            chatListRepository.getAllNormalChats();
                            return;
                        }

                    }
                });
            }
            else if (result.equals("failed")) {
                Log.e(Extras.LOG_MESSAGE,"unable to update message as synced due to server error logged in service class");
            }
        }
        catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to update the message status due to exception logged in service class "+e.getMessage());
        }
    }

    public void updateSentMessageAsDelivered(String response) {
        try {
            JSONObject                              jsonObject              =   new JSONObject(response);
            JSONArray                               jsonArray               =   jsonObject.getJSONArray("data");
            List<MarkMessagesAsDeliveredModel>      messagesToUpdate        =   new ArrayList<>();
            List<DeleteMessageDataInServerModel>    deleteData              =   new ArrayList<>();
            String                                  contactId               =   "";

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject  messageObject      =    jsonArray.getJSONObject(i);
                String      messageId           =   messageObject.getString("message_id");
                String      senderId            =   messageObject.getString("sender_id");
                String      receiverId          =   messageObject.getString("receiver_id");
                int         messageStatus       =   messageObject.getInt("message_status");
                long        receivedTimestamp   =   messageObject.getLong("received_timestamp");
                contactId                       =   receiverId;
                updatesContactId.put(messageId, new Pair<>(senderId, receiverId));
                messagesToUpdate.add(new MarkMessagesAsDeliveredModel(messageId, receivedTimestamp));
                deleteData.add(new DeleteMessageDataInServerModel(messageId, senderId, receiverId));
            }
            Log.e(Extras.LOG_MESSAGE,"activity visibles are " + isMessagingActivityVisible + " " + isMainActivityVisible);
            String finalContactId = contactId;
            messagesRepository.updateMessageAsDeliveredBatch(messagesToUpdate, messageIds -> {
                if (isMessagingActivityVisible && openedChatId.equals(finalContactId) && messageCallBacks != null) {
                    Log.e(Extras.LOG_MESSAGE,"contact id is " + finalContactId + " and opened id " + openedChatId);
                    messageCallBacks.updateMessageStatusAsBatch(messageIds, MessagesManager.MESSAGE_DELIVERED);
                }
                else if (isMainActivityVisible) {
                    chatListRepository.getAllNormalChats();
                }
                sendDeleteRequestToServer.deleteMessageDataInServer(deleteData);
            });
        }
        catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE,"unable to get changes response from the server "+e.getMessage());
        }
    }

    public void updateSentMessageAsDeliveredAndSeen(String response) {
        try {
            JSONObject                                  jsonObject              =   new JSONObject(response);
            JSONArray                                   jsonArray               =   jsonObject.getJSONArray("data");
            List<MarkMessagesAsDeliveredAndSeenModel>   messagesToUpdate        =   new ArrayList<>();
            List<DeleteMessageDataInServerModel>        deleteData              =   new ArrayList<>();
            String                                      contactId               =   "";

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject  messagesObject              =   jsonArray.getJSONObject(i);
                String      messageId                   =   messagesObject.getString("message_id");
                int         messageStatus               =   messagesObject.getInt("message_status");
                long        receivedTimestamp           =   messagesObject.getLong("delivered_timestamp");
                long        readTimestamp               =   messagesObject.getLong("read_timestamp");
                String      senderId                    =   messagesObject.getString("sender_id");
                String      receiverId                  =   messagesObject.getString("receiver_id");
                contactId                               =   receiverId;

                updatesContactId.put(messageId, new Pair<>(senderId, receiverId));
                messagesToUpdate.add(new MarkMessagesAsDeliveredAndSeenModel(messageId, receivedTimestamp, readTimestamp));
                deleteData.add(new DeleteMessageDataInServerModel(messageId, senderId, receiverId));
            }

            Log.e(Extras.LOG_MESSAGE,"activity visibles are " + isMessagingActivityVisible + " " + isMainActivityVisible);
            String finalContactId = contactId;
            messagesRepository.updateMessageAsDeliveredAndSeenBatch(messagesToUpdate, messageIds -> {
                if (!messageIds.isEmpty()) {
                    if (isMessagingActivityVisible && openedChatId.equals(finalContactId) && messageCallBacks != null) {
                        messageCallBacks.updateMessageStatusAsBatch(messageIds, MessagesManager.MESSAGE_SEEN);
                    }
                    else if (isMainActivityVisible) {
                        chatListRepository.getAllNormalChats();
                    }
                    sendDeleteRequestToServer.deleteMessageDataInServer(deleteData);
                }
            });
        }
        catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE,"unable to get changes response from the server "+e.getMessage());
        }
    }

    public void updateSentMessageAsSeen(String response) {
        try {
            JSONObject  jsonObject          =   new JSONObject(response);
            String      messageId           =   jsonObject.getString("message_id");
            int         messageStatus       =   jsonObject.getInt("message_status");
            long        readTimestamp       =   jsonObject.getLong("read_timestamp");
            String      senderId            =   jsonObject.getString("sender_id");
            String      receiverId          =   jsonObject.getString("receiver_id");

            updatesContactId.put(messageId, new Pair<>(senderId, receiverId));
            messagesRepository.updateMessageStatusAsSeen(messageId, messageStatus, readTimestamp, MessagesManager.NO_NEED_TO_PUSH_MESSAGE);

        }
        catch (JSONException e)
        {
            Log.e(Extras.LOG_MESSAGE,"unable to get changes response from the server "+e.getMessage());
        }
    }

    public void updateReceivedMessageAsDeliveredSuccessfully(String response) {
        try {
            JSONObject  jsonObject      =   new JSONObject(response);
            String      messageId       =   jsonObject.getString("message_id");
            messagesRepository.updateReceivedMessagePushStatus(messageId, MessagesManager.NO_NEED_TO_PUSH_MESSAGE);
        } catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to get message id to delete at receiver side "+e.getMessage());
        }
    }

    public void updateReceivedMessageAsSeenSuccessfully(String response) {
        try {
            JSONObject jsonObject = new JSONObject(response);
            JSONArray jsonArray = jsonObject.getJSONArray("data"); // expecting {"data":[{"message_id":"..."}, ...]}

            List<String> messageIdsToUpdate = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject messageObj = jsonArray.getJSONObject(i);
                String messageId = messageObj.getString("message_id");
                messagesRepository.updateReceivedMessagePushStatus(
                        messageId,
                        MessagesManager.NO_NEED_TO_PUSH_MESSAGE
                );
            }
        } catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get message ids to delete at receiver side: " + e.getMessage());
        }
    }

    public void playSound() {
        MediaPlayer music = MediaPlayer.create(context.getApplicationContext(), R.raw.message_sent_sound);
        try {
            if (music != null) {
                music.start();
                music.setOnCompletionListener(MediaPlayer::release);
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"unable to play sound "+e.getMessage());
        }
    }
}
