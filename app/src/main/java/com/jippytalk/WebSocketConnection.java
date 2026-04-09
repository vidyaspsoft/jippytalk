package com.jippytalk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.jippytalk.ArchiveChat.Model.ArchiveListModel;
import com.jippytalk.Database.MessagesDatabase.Repository.MessagesRepository;
import com.jippytalk.Extras;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.MyApplication;
import com.jippytalk.ServiceHandlers.ContactSessionRebuild;
import com.jippytalk.ServiceHandlers.HandleInsertionsFromService;
import com.jippytalk.ServiceHandlers.Models.UnSyncedSeenMessagesModel;
import com.jippytalk.ServiceHandlers.SendDataToServer;
import com.jippytalk.ServiceHandlers.SendUnSyncedDataToServer;
import com.jippytalk.ServiceHandlers.SendUpdatesToServer;
import com.jippytalk.ServiceHandlers.HandleDeleteMessageFromService;
import com.jippytalk.ServiceHandlers.HandleUpdatesFromService;
import com.jippytalk.ServiceLocators.APIServiceLocator;
import com.jippytalk.ServiceLocators.AppServiceLocator;
import com.jippytalk.ServiceLocators.DatabaseServiceLocator;
import com.jippytalk.ServiceLocators.RepositoryServiceLocator;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebSocketConnection {

    private final Context                           context;
    private static volatile WebSocketConnection     webSocketConnection;
    private DatabaseServiceLocator                  databaseServiceLocator;
    private APIServiceLocator                       apiServiceLocator;
    private RepositoryServiceLocator                repositoryServiceLocator;
    private AppServiceLocator                       appServiceLocator;
    private String                                  senderId, userId, openedChatId, pendingContactId;
    private int                                     pendingDeviceId;
    private final SharedPreferences                 sharedPreferences;
    private WebSocketClient                         webSocketClient;
    private MessageCallBacks                        messageCallBacks;
    private ChatListCallBacks                       chatListCallBacks;
    private ArchiveChatListCallBacks                archiveChatListCallBacks;
    private UnknownChatListCallBacks                unknownChatListCallBacks;
    private MessagesRepository                      messagesRepository;
    private HandleInsertionsFromService             handleInsertionsFromService;
    private HandleUpdatesFromService handleUpdatesFromService;
    private HandleDeleteMessageFromService handleDeleteMessageFromService;
    private ContactSessionRebuild                   contactSessionRebuild;
    private SendUpdatesToServer                     sendUpdatesToServer;
    private SendUnSyncedDataToServer                sendUnSyncedDataToServer;
    private SendDataToServer                        sendDataToServer;
    private Runnable                                connectionTimeoutRunnable;
    private final Handler                           connectionTimeoutHandler                    =   new Handler(Looper.getMainLooper());
    private boolean                                 isMessagingActivityVisible                  =   false;
    private boolean                                 isMainActivityVisible                       =   false;
    private boolean                                 isArchiveChatsActivityVisible               =   false;
    private boolean                                 isMessageRequestActivityVisible             =   false;
    private volatile boolean                        isConnectingOrConnected                     =   false;
    public volatile static boolean                  isConnectedToSocket                         =   false;
    private final int                               MAX_RETRY_ATTEMPTS                          =   10;
    private int                                     retryCount                                  =   0;
    private long                                    retryDelayMillis                            =   1000;
    private final long                              MAX_RETRY_DELAY                             =   5000;
    private final Handler                           retryHandler                                =   new Handler(Looper.getMainLooper());
    private final ExecutorService                   queueMessagesExecutor                       =   Executors.newFixedThreadPool(4);
    private final ExecutorService                   websocketMessagesExecutor                   =   Executors.newSingleThreadExecutor();
    private final ExecutorService                   unSyncDataExecutor                          =   Executors.newFixedThreadPool(2);
    private final Queue<String>                     incomingMessagesQueue                       =   new ConcurrentLinkedQueue<>();
    private final Queue<String>                     messageStatusQueue                          =   new ConcurrentLinkedQueue<>();


    public WebSocketConnection(Context context, SharedPreferences sharedPreferences) {
        this.context                        =   context.getApplicationContext();
        this.sharedPreferences              =   sharedPreferences;
        userId                              =   sharedPreferences.getString(SharedPreferenceDetails.USERID,"");
    }

    public synchronized static WebSocketConnection getInstance(Context context, SharedPreferences sharedPreferences) {
        if (webSocketConnection == null) {
            synchronized (WebSocketConnection.class) {
                if (webSocketConnection == null) {
                    webSocketConnection =   new WebSocketConnection(context.getApplicationContext(), sharedPreferences);
                }
            }
        }
        return webSocketConnection;
    }

    public void setMessageCallback(MessageCallBacks callback) {
        this.messageCallBacks                   =   callback;

        if (handleUpdatesFromService != null) {
            handleUpdatesFromService.setMessageCallback(callback);
        }

        if (handleDeleteMessageFromService != null) {
            handleDeleteMessageFromService.setMessageCallback(callback);
        }

        if (handleInsertionsFromService != null) {
            handleInsertionsFromService.setMessageCallback(callback);
        }
    }

    public void setChatListCallback(ChatListCallBacks callback) {
        this.chatListCallBacks                  =   callback;

        if (handleUpdatesFromService != null) {
            handleUpdatesFromService.setChatListCallback(callback);
        }

        if (handleDeleteMessageFromService != null) {
            handleDeleteMessageFromService.setChatListCallback(callback);
        }

        if (handleInsertionsFromService != null) {
            handleInsertionsFromService.setChatListCallback(callback);
        }
    }

    public void setArchiveChatsListCallback(ArchiveChatListCallBacks callback) {
        this.archiveChatListCallBacks           =   callback;

        if (handleInsertionsFromService != null) {
            handleInsertionsFromService.setArchiveChatsListCallback(callback);
        }
    }

    public void setUnknownChatListCallBacks(UnknownChatListCallBacks callBacks) {
        this.unknownChatListCallBacks           =   callBacks;

        if (handleInsertionsFromService != null) {
            handleInsertionsFromService.setUnknownChatListCallBacks(callBacks);
        }
    }

    public void setMessagingActivityVisible(boolean isVisible) {
        this.isMessagingActivityVisible         =   isVisible;

        if (handleInsertionsFromService != null) {
            handleInsertionsFromService.setMessagingActivityVisible(isVisible);
        }

        if (handleUpdatesFromService != null) {
            handleUpdatesFromService.setMessagingActivityVisible(isVisible);
        }

        if (handleDeleteMessageFromService != null) {
            handleDeleteMessageFromService.setMessagingActivityVisible(isVisible);
        }
    }

    public void setMainActivityVisible(boolean isVisible) {
        this.isMainActivityVisible              =   isVisible;

        if (handleUpdatesFromService != null) {
            handleUpdatesFromService.setMainActivityVisible(isVisible);
        }

        if (handleDeleteMessageFromService != null) {
            handleDeleteMessageFromService.setMainActivityVisible(isVisible);
        }

        if (handleInsertionsFromService != null) {
            handleInsertionsFromService.setMainActivityVisible(isVisible);
        }
    }

    public void setArchiveChatsActivityVisible(boolean isVisible) {
        this.isArchiveChatsActivityVisible      =   isVisible;

        if (handleInsertionsFromService != null) {
            handleInsertionsFromService.setArchiveChatsActivityVisible(isArchiveChatsActivityVisible);
        }
    }

    public void setMessageRequestActivityVisible(boolean isVisible) {
        this.isMessageRequestActivityVisible    =   isVisible;

        if (handleInsertionsFromService != null) {
            handleInsertionsFromService.setMessageRequestActivityVisible(isVisible);
        }
    }

    public void getOpenedChatId(String contactId) {
        this.openedChatId                       = contactId;

        if (handleDeleteMessageFromService != null) {
            handleDeleteMessageFromService.getOpenedChatId(contactId);
        }

        if (handleUpdatesFromService != null) {
            handleUpdatesFromService.getOpenedChatId(contactId);
        }

        if (handleInsertionsFromService != null) {
            handleInsertionsFromService.getOpenedChatId(contactId);
        }
    }

    public void connectToWebSocket() {
        if (retryCount >= MAX_RETRY_ATTEMPTS) {
            Log.e(Extras.LOG_MESSAGE, "Max retry attempts reached.");
            return;
        }

        if (isConnectingOrConnected) {
            Log.e(Extras.LOG_MESSAGE, "WebSocket connection attempt already in progress or connected");
            return;
        }

        try {
            URI uri = new URI("wss://JippyTalk.com/chatserver");
            isConnectingOrConnected = true;
            webSocketClient = new WebSocketClient(uri) {
                @Override
                public void onOpen(ServerHandshake handShakedData) {
                    connectionTimeoutHandler.removeCallbacks(connectionTimeoutRunnable);  // 🔥 cancel timeout
                    initializeServicesAfterConnection();

                    Log.e(Extras.LOG_MESSAGE, "Successfully connected to the websocket server");
                    isConnectingOrConnected = false;
                    isConnectedToSocket = true;
                    retryCount = 0;
                    retryDelayMillis = 2000;

                    if (userId != null && !userId.isEmpty()) {
                        webSocketClient.send(userId);
                    } else {
                        userId = sharedPreferences.getString(SharedPreferenceDetails.USERID, "");
                        if (!userId.isEmpty()) webSocketClient.send(userId);
                    }

                    if (sendUnSyncedDataToServer != null) {
                        unSyncDataExecutor.execute(() -> {
                            sendUnSyncedDataToServer.sendUnSyncedDeliveredMessagesToServer();
                            sendUnSyncedDataToServer.sendUnSyncedSeenMessagesToServer();
                            sendUnSyncedDataToServer.sendUnSyncedSentMessagesOrLinksToServer();
                        });
                        if (pendingContactId != null && pendingDeviceId != -1) {
                            sendUnSyncedDataToServer.getUnSyncedMessagesOfContact(
                                    pendingContactId, pendingDeviceId, webSocketClient
                            );
                            pendingContactId = null;
                            pendingDeviceId = -1;
                        }
                    }
                }

                @Override
                public void onMessage(String message) {
                    handleIncomingMessageFromWebSocket(message);
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    Log.e(Extras.LOG_MESSAGE, "connection closed " + code + " " + reason);
                    isConnectingOrConnected = false;
                    isConnectedToSocket = false;
                    webSocketClient = null;

                    if (!MyApplication.isAppClosed && retryCount < MAX_RETRY_ATTEMPTS) {
                        scheduleRetry();
                    } else {
                        Log.e(Extras.LOG_MESSAGE,"App closed so no retries");
                    }
                }

                @Override
                public void onError(Exception ex) {
                    connectionTimeoutHandler.removeCallbacks(connectionTimeoutRunnable);
                    Log.e(Extras.LOG_MESSAGE, "WebSocket error: " + (ex.getMessage() != null ? ex.getMessage() : "unknown"));
                    isConnectingOrConnected = false;
                    isConnectedToSocket = false;
                    if (!MyApplication.isAppClosed && retryCount < MAX_RETRY_ATTEMPTS) {
                        scheduleRetry();
                    } else {
                        Log.e(Extras.LOG_MESSAGE,"App closed so no retries");
                    }
                }
            };
            webSocketClient.connect();
            startConnectionTimeoutWatchdog();
        } catch (URISyntaxException e) {
            if (!MyApplication.isAppClosed && retryCount < MAX_RETRY_ATTEMPTS) {
                scheduleRetry();
            } else {
                Log.e(Extras.LOG_MESSAGE,"App closed so no retries");
            }
            Log.e(Extras.LOG_MESSAGE, "URI error: " + e.getMessage());
        }
    }

    private void startConnectionTimeoutWatchdog() {
        connectionTimeoutRunnable = () -> {
            if (!isConnectedToSocket) {
                Log.e(Extras.LOG_MESSAGE, "WebSocket connect timeout. Forcing retry.");

                try {
                    webSocketClient.close();
                } catch (Exception ignored) {}

                isConnectingOrConnected = false;
                isConnectedToSocket = false;
                if (!MyApplication.isAppClosed && retryCount < MAX_RETRY_ATTEMPTS) {
                    scheduleRetry();
                } else {
                    Log.e(Extras.LOG_MESSAGE,"App closed so no retries");
                }
            }
        };
        connectionTimeoutHandler.postDelayed(connectionTimeoutRunnable, 5000);
    }

    public void disconnectWebSocket() {
        isConnectingOrConnected     =   false;
        isConnectedToSocket         =   false;
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.close();
            webSocketClient = null;
        }
    }

    private void scheduleRetry() {
        if (retryCount >= MAX_RETRY_ATTEMPTS) {
            Log.e(Extras.LOG_MESSAGE, "Max retry attempts reached. Will keep trying on next close/error.");
            return;
        }
        retryCount++;
        Log.e(Extras.LOG_MESSAGE, "Retrying WebSocket connection in " + retryDelayMillis + " ms. Attempt " + retryCount);
        retryHandler.postDelayed(this::connectToWebSocket, retryDelayMillis);
        retryDelayMillis = Math.min(retryDelayMillis * 2, MAX_RETRY_DELAY);
    }

    private void initializeServicesAfterConnection() {
        databaseServiceLocator          =   ((MyApplication) context.getApplicationContext()).getDatabaseServiceLocator();
        apiServiceLocator               =   ((MyApplication) context.getApplicationContext()).getAPIServiceLocator();
        repositoryServiceLocator        =   ((MyApplication) context.getApplicationContext()).getRepositoryServiceLocator();
        appServiceLocator               =   ((MyApplication) context.getApplicationContext()).getAppServiceLocator();
        repositoryServiceLocator        =   ((MyApplication) context.getApplicationContext()).getRepositoryServiceLocator();
        handleInsertionsFromService     =   new HandleInsertionsFromService(context.getApplicationContext(), webSocketClient,
                                            databaseServiceLocator, apiServiceLocator, repositoryServiceLocator, appServiceLocator);
        handleUpdatesFromService        =   new HandleUpdatesFromService(context.getApplicationContext(), webSocketClient);
        handleDeleteMessageFromService  =   new HandleDeleteMessageFromService(context.getApplicationContext(), webSocketClient,
                                            repositoryServiceLocator.getMessagesRepository());
        sendUpdatesToServer             =   new SendUpdatesToServer(context.getApplicationContext(), webSocketClient);
        sendDataToServer                =   new SendDataToServer(context.getApplicationContext());
        sendUnSyncedDataToServer        =   new SendUnSyncedDataToServer(context.getApplicationContext(), webSocketClient,
                                            databaseServiceLocator, repositoryServiceLocator, apiServiceLocator, appServiceLocator);
        contactSessionRebuild           =   new ContactSessionRebuild(context.getApplicationContext(),
                                            webSocketClient,
                                            repositoryServiceLocator.getContactsRepository(),
                                            repositoryServiceLocator.getMessagesRepository(),
                                            repositoryServiceLocator.getUserDetailsRepository(),
                                            apiServiceLocator.getTokenRefreshAPI(),
                                            apiServiceLocator.getDecryptionFailScenario(),
                                            sendUnSyncedDataToServer,
                                            repositoryServiceLocator.getAppLevelExecutorService());

        setMainActivityVisible(isMainActivityVisible);
        setMessagingActivityVisible(isMessagingActivityVisible);
        setArchiveChatsActivityVisible(isArchiveChatsActivityVisible);
        setMessageRequestActivityVisible(isMessageRequestActivityVisible);
        setMessageCallback(messageCallBacks);
        setChatListCallback(chatListCallBacks);
        setArchiveChatsListCallback(archiveChatListCallBacks);
        getOpenedChatId(openedChatId);
    }

    private void handleIncomingMessageFromWebSocket(String message) {
        websocketMessagesExecutor.execute(() -> {
            try {
                JSONObject jsonObject      =    new JSONObject(message);
                String      action          =   jsonObject.getString("status");
                Log.e(Extras.LOG_MESSAGE,"websocket status " + action);

                switch (action) {
                    case "new_message"                          ->  incomingMessagesQueue.add(message);
                    case "inserted",
                         "messageFailed",
                         "messageSyncedSuccessfully",
                         "deliveredMessageStatusInserted",
                         "seenMessageStatusInserted",
                         "sentMessageDelivered",
                         "messageSeen",
                         "messageDeliveredAndSeen",
                         "deleteMessageForReceiver",
                         "deleteForEveryOneInserted"                ->  messageStatusQueue.add(message);
                    case "typing"                                   ->  notifyContactIsTyping(message);
                    case "presence_update"                          ->  updateContactIsOnline(message);
                    case "user_online_status"                       ->  handleContactOnlineStatus(message);
                    case "invalidWhisperMessage"                    ->  contactSessionRebuild.rebuildSessionAndReSendMessage(message);
                    default                                         ->  Log.e(Extras.LOG_MESSAGE,"null action or empty action came from" +
                            "websocket");
                }
                processMessageQueues();
            } catch (JSONException e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to receive any message from the websocket server "+e.getMessage());
            }
        });
    }

    private void processMessageQueues() {

        queueMessagesExecutor.execute(() -> {
            String queueMessage;

            while ((queueMessage = incomingMessagesQueue.poll()) != null) {
                handleInsertionsFromService.handleMessageInsertion(queueMessage);
            }

            while ((queueMessage = messageStatusQueue.poll()) != null) {
                try {
                    JSONObject json = new JSONObject(queueMessage);
                    String status = json.getString("status");

                    switch (status) {
                        case "inserted", "messageSyncedSuccessfully"    ->  handleUpdatesFromService.
                                updateSentMessageAsSyncedWithServer(queueMessage);
                        case "messageFailed"                            ->  startWorkerToSendUnSyncedMessages();
                        case "sentMessageDelivered"                     ->  handleUpdatesFromService.
                                updateSentMessageAsDelivered(queueMessage);
                        case "messageSeen"                              ->  handleUpdatesFromService.
                                updateSentMessageAsSeen(queueMessage);
                        case "messageDeliveredAndSeen"                  ->  handleUpdatesFromService.
                                updateSentMessageAsDeliveredAndSeen(queueMessage);
                        case "deleteMessageForReceiver"                 ->  handleDeleteMessageFromService.
                                deleteReceivedMessageRequestedBySender(queueMessage);
                        case "deleteForEveryOneInserted"                ->  handleDeleteMessageFromService.
                                deleteForEveryoneInserted(queueMessage);
                        case "deliveredMessageStatusInserted"           ->  handleUpdatesFromService.
                                updateReceivedMessageAsDeliveredSuccessfully(queueMessage);
                        case "seenMessageStatusInserted"                ->  handleUpdatesFromService.
                                updateReceivedMessageAsSeenSuccessfully(queueMessage);
                        default                                         ->  Log.e(Extras.LOG_MESSAGE, "Unknown status update: " + status);
                    }
                } catch (JSONException e) {
                    Log.e(Extras.LOG_MESSAGE, "Invalid status JSON: " + e.getMessage());
                }
            }
        });
    }

    public void sendMessageToServer(String messageId, String senderId, String receiverId, String message,
                                    int signalMessageType, int messageType, int messageStatus, int isEdited,
                                    long latitude, long longitude, long sentTimestamp, long deliveredTimestamp,
                                    long readTimestamp, int isReply, String replyToMsgId) {

        if (webSocketClient != null && webSocketClient.isOpen() && sendDataToServer != null) {
            sendDataToServer.sendMessageToServer(
                    webSocketClient, messageId, senderId, receiverId, message,
                    signalMessageType, messageType, messageStatus, isEdited,
                    latitude,longitude, sentTimestamp, deliveredTimestamp,
                    readTimestamp, isReply, replyToMsgId);
        }
        else {
            Log.e(Extras.LOG_MESSAGE,"No websocket client opened");
        }
    }

    private void startWorkerToSendUnSyncedMessages() {

    }

    public void sendUnSyncedMessagesToServer(String contactId, int contactDeviceId) {
        if (webSocketClient != null && webSocketClient.isOpen() && sendUnSyncedDataToServer != null) {
            sendUnSyncedDataToServer.getUnSyncedMessagesOfContact(contactId, contactDeviceId, webSocketClient);
        }
    }

    public void senderIsTyping(String senderId, String receiverId, boolean isTyping, String method) {
        if (webSocketClient != null && webSocketClient.isOpen() && sendDataToServer != null) {
            sendDataToServer.senderIsTyping(webSocketClient, senderId, receiverId, isTyping, method);
        } else {
            Log.e(Extras.LOG_MESSAGE,"Unable to send typing status due to websocket null");
        }
    }

    public void sendUnsyncedSentMessagesToServer() {
        sendUnSyncedDataToServer.sendUnSyncedSentMessagesOrLinksToServer();
    }

    public void deleteMessageForEveryone(String messageId, String senderId, String receiverId, int messageStatus) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("message_id", messageId);
                jsonObject.put("sender_id", senderId);
                jsonObject.put("receiver_id", receiverId);
                jsonObject.put("message_status", messageStatus);
                jsonObject.put("method","deleteMessageForEveryone");

                webSocketClient.send(jsonObject.toString());
            }
            catch (JSONException e)
            {
                Log.e(Extras.LOG_MESSAGE,"Unable to send message to server "+e.getMessage());
            }
        }
        else
        {
            Log.e(Extras.LOG_MESSAGE,"No websocket client opened");
        }
    }


    // --------------- send updates to the received message as per the user screen -------------------------

    public void updateReceivedMessageAsDeliveredAndSeen(ArrayList<UnSyncedSeenMessagesModel> unSyncedSeenMessagesModelArrayList) {
        if (unSyncedSeenMessagesModelArrayList != null && sendUpdatesToServer != null
                && !unSyncedSeenMessagesModelArrayList.isEmpty()) {
            sendUpdatesToServer.updateReceivedMessageAsDeliveredAndSeen(unSyncedSeenMessagesModelArrayList);
        }
    }

    // ***************  Getting data from the websocket server starts here ****************************


    public void notifyContactIsTyping(String message) {
        try {
            JSONObject jsonObject   =   new JSONObject(message);
            senderId                =   jsonObject.getString("typer_id");
            boolean typing          =   jsonObject.getBoolean("typing_status");

            if (typing) {
                if (isMessagingActivityVisible && openedChatId.equals(senderId)) {
                    messageCallBacks.contactIdIsTyping(true);
                }
                else if (isMainActivityVisible) {
                    chatListCallBacks.updateChatIsTyping(senderId, true);
                }
                else if(!isMessagingActivityVisible && openedChatId == null || openedChatId.equals("0") || !openedChatId.equals(senderId)) {
                    Log.e(Extras.LOG_MESSAGE,"Someone is typing");
                }
            }
            else {
                if (isMessagingActivityVisible && openedChatId.equals(senderId)) {
                    messageCallBacks.contactIdIsTyping(false);
                }
                else if (isMainActivityVisible) {
                    chatListCallBacks.updateChatIsTyping(senderId, false);
                }
                else if(openedChatId == null || openedChatId.equals("0") || !openedChatId.equals(senderId)) {
                    Log.e(Extras.LOG_MESSAGE,"Someone stopped typing");
                }
                getContactOnlineStatus(senderId);
            }
        }
        catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to get message id to delete at receiver side "+e.getMessage());
        }
    }

    public void getContactOnlineStatus(String contactId) {
        try {
            JSONObject request = new JSONObject();
            request.put("method", "getContactOnlineStatus");
            request.put("requested_user_id", contactId); // the contact whose status we want

            if (webSocketClient != null && webSocketClient.isOpen()) {
                webSocketClient.send(request.toString());
                Log.d(Extras.LOG_MESSAGE, "Requested online status for contact: " + contactId);
            } else {
                Log.e(Extras.LOG_MESSAGE, "WebSocket client not connected");
            }
        } catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE, "Error forming online status request: " + e.getMessage());
        }
    }

    public void handleContactOnlineStatus(String message) {
        try {
            JSONObject  jsonObject      =   new JSONObject(message);
            String      contactId       =   jsonObject.getString("user_id"); // contact whose status this is
            int         onlineStatus    =   jsonObject.getInt("is_online");
            long        lastSeen        =   jsonObject.optLong("last_seen", 0);

            if (onlineStatus == 1 && isMessagingActivityVisible && openedChatId.equals(contactId) && messageCallBacks != null) {
                Log.d(Extras.LOG_MESSAGE, "Contact " + contactId + " is online ");
                messageCallBacks.updateUserIsOnlineOrOffline(1, lastSeen);
            } else if (onlineStatus == 0 && isMessagingActivityVisible && openedChatId.equals(contactId) && messageCallBacks != null ) {
                Log.d(Extras.LOG_MESSAGE, "Contact " + contactId + " is offline " + lastSeen);
                messageCallBacks.updateUserIsOnlineOrOffline(0, lastSeen);
            }
        } catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE, "Error parsing contact online status: " + e.getMessage());
        }
    }

    public void updateContactIsOnline(String message) {
        try {
            JSONObject  jsonObject          =   new JSONObject(message);
            senderId                        =   jsonObject.getString("user_id");
            int         onlineStatus        =   jsonObject.getInt("is_online");
            long        lastSeen            =   jsonObject.optLong("last_seen", 0);

            if (onlineStatus == 1) {
                if (isMessagingActivityVisible && openedChatId.equals(senderId) && messageCallBacks != null) {
                    messageCallBacks.updateUserIsOnlineOrOffline(1, lastSeen);
                }
                else if(!isMessagingActivityVisible && openedChatId == null || openedChatId.equals("0") || !openedChatId.equals(senderId)) {
                    Log.e("online","Someone is typing");
                }
            }
            else {
                if (isMessagingActivityVisible && openedChatId.equals(senderId) && messageCallBacks != null) {
                    messageCallBacks.updateUserIsOnlineOrOffline(0, lastSeen);
                }
                else if(openedChatId == null || openedChatId.equals("0") || !openedChatId.equals(senderId)) {
                    Log.e("online","Someone stopped typing");
                }
            }
        }
        catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to get message id to delete at receiver side " + e.getMessage());
        }
    }


    public interface MessageCallBacks {
        void contactIdIsTyping(boolean isTyping);
        void updateUserIsOnlineOrOffline(int isOnline, long lastVisit);
        void updateMessageStatus(String messageId, int messageStatus);
        void updateMessageStatusAsBatch(List<String> messageIds, int messageStatus);
    }

    public interface ChatListCallBacks {
        void insertUnknownContactChat();
        void updateChatIsTyping(String contactId, boolean isTyping);
        void updateContactIsOnlineLocally(String contactId ,boolean isOnline);
    }

    public interface ArchiveChatListCallBacks {

    }

    public interface UnknownChatListCallBacks {
        void insertUnknownContactChat(ArchiveListModel archiveListModel);
    }
}
