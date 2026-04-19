package com.jippytalk;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.jippytalk.ArchiveChat.Model.ArchiveListModel;
import com.jippytalk.Database.MessagesDatabase.Repository.MessagesRepository;
import com.jippytalk.Managers.MessagesManager;
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
import org.json.JSONArray;
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
    private ConnectionStateListener                 connectionStateListener;
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

    // Outbound sends that arrived while the socket was disconnected. Flushed on
    // the next successful connection open. Used to ride through short reconnect
    // windows — e.g. when a document picker interaction briefly pauses the
    // activity and the socket closes + reopens in under a second.
    private final Queue<Runnable>                   pendingOutboundSends                        =   new ConcurrentLinkedQueue<>();

    /** Flushes any sends queued while the socket was offline. Called on connection open. */
    private void flushPendingOutboundSends() {
        Runnable task;
        int count = 0;
        while ((task = pendingOutboundSends.poll()) != null) {
            try {
                task.run();
                count++;
            } catch (Exception e) {
                Log.e(Extras.LOG_SOCKET_SEND, "[PENDING] send task failed: " + e.getMessage());
            }
        }
        if (count > 0) {
            Log.e(Extras.LOG_SOCKET_SEND, "[PENDING] flushed " + count + " queued sends");
        }
    }


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

    /**
     * Sets a one-shot or persistent connection state listener.
     * Fired on successful connection, connection failure, and disconnection.
     * Call with null to unregister.
     *
     * @param listener the listener to receive connection events
     */
    public void setConnectionStateListener(ConnectionStateListener listener) {
        this.connectionStateListener            =   listener;
        // If already connected, fire immediately so late subscribers get notified
        if (listener != null && isConnectedToSocket) {
            listener.onConnected();
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

    /**
     * Forces a reconnect even if max retries were exhausted. Called when there
     * are pending outbound sends that need the socket. Resets the retry counter
     * so connectToWebSocket() doesn't bail out.
     */
    public void ensureConnected() {
        // Already open — nothing to do
        if (webSocketClient != null && webSocketClient.isOpen()) return;

        // Already connecting — don't start a second connection
        if (isConnectingOrConnected) {
            Log.e(Extras.LOG_MESSAGE, "ensureConnected: connection already in progress, skipping");
            return;
        }

        // Reset retry counter so connectToWebSocket() doesn't bail
        retryCount          =   0;
        retryDelayMillis    =   2000;
        Log.e(Extras.LOG_MESSAGE, "ensureConnected: resetting retries and reconnecting");
        connectToWebSocket();
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

        String  jwtToken    =   sharedPreferences.getString(SharedPreferenceDetails.JWT_TOKEN, "");
        if (jwtToken.isEmpty()) {
            Log.e(Extras.LOG_MESSAGE, "Cannot connect WebSocket: no JWT token available");
            return;
        }

        try {
            URI uri = new URI(API.WS_URL + "?token=" + jwtToken);
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

                    // Auth is handled via token in the WebSocket URL query param
                    userId  =   sharedPreferences.getString(SharedPreferenceDetails.USERID, "");

                    // Flush any outbound sends that arrived while the socket was
                    // disconnected (e.g. during a document picker pause/resume).
                    flushPendingOutboundSends();

                    if (sendUnSyncedDataToServer != null) {
                        unSyncDataExecutor.execute(() -> {
                            try {
                                sendUnSyncedDataToServer.sendUnSyncedDeliveredMessagesToServer();
                                sendUnSyncedDataToServer.sendUnSyncedSeenMessagesToServer();
                                sendUnSyncedDataToServer.sendUnSyncedSentMessagesOrLinksToServer();
                            } catch (Exception e) {
                                Log.e(Extras.LOG_MESSAGE, "Error sending unsynced data: " + e.getMessage());
                            }
                        });
                        if (pendingContactId != null && pendingDeviceId != -1) {
                            try {
                                sendUnSyncedDataToServer.getUnSyncedMessagesOfContact(
                                        pendingContactId, pendingDeviceId, webSocketClient
                                );
                            } catch (Exception e) {
                                Log.e(Extras.LOG_MESSAGE, "Error sending unsynced contact messages: " + e.getMessage());
                            }
                            pendingContactId = null;
                            pendingDeviceId = -1;
                        }
                    }

                    // Notify connection state listener on the main thread
                    if (connectionStateListener != null) {
                        final ConnectionStateListener listener = connectionStateListener;
                        new Handler(Looper.getMainLooper()).post(listener::onConnected);
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

                    if (connectionStateListener != null) {
                        final ConnectionStateListener listener = connectionStateListener;
                        new Handler(Looper.getMainLooper()).post(listener::onDisconnected);
                    }

                    if (!MyApplication.isAppClosed && retryCount < MAX_RETRY_ATTEMPTS) {
                        scheduleRetry();
                    } else {
                        Log.e(Extras.LOG_MESSAGE,"App closed so no retries");
                    }
                }

                @Override
                public void onError(Exception ex) {
                    connectionTimeoutHandler.removeCallbacks(connectionTimeoutRunnable);
                    final String errorMessage = ex.getMessage() != null ? ex.getMessage() : "unknown";
                    Log.e(Extras.LOG_MESSAGE, "WebSocket error: " + errorMessage);
                    isConnectingOrConnected = false;
                    isConnectedToSocket = false;

                    if (connectionStateListener != null) {
                        final ConnectionStateListener listener = connectionStateListener;
                        new Handler(Looper.getMainLooper()).post(() -> listener.onConnectionFailed(errorMessage));
                    }

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
        messagesRepository              =   repositoryServiceLocator.getMessagesRepository();
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
            // Log every incoming message
            Log.e(Extras.LOG_SOCKET_RECV, "════════════════════ RECV ════════════════════");
            Log.e(Extras.LOG_SOCKET_RECV, "Raw JSON : " + message);
            Log.e(Extras.LOG_SOCKET_RECV, "═══════════════════════════════════════════════");

            try {
                JSONObject jsonObject      =    new JSONObject(message);

                // Check if this uses the new protocol envelope format {type, payload}
                if (jsonObject.has("type")) {
                    handleNewProtocolMessage(jsonObject);
                    return;
                }

                // Legacy protocol handling (status/method)
                String      action          =   jsonObject.optString("status", "");
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

    /**
     * Handles incoming WebSocket messages that use the new protocol envelope format:
     *   { "type": "<event>", "payload": { ... } }
     *
     * Routes the message to the appropriate handler based on the event type.
     */
    private void handleNewProtocolMessage(JSONObject jsonObject) {
        try {
            String      type        =   jsonObject.getString("type");
            JSONObject  payload     =   jsonObject.optJSONObject("payload");

            Log.e(Extras.LOG_SOCKET_RECV, "Event type : " + type);
            if (payload != null) {
                Log.e(Extras.LOG_SOCKET_RECV, "Payload    : " + payload.toString());
            }

            switch (type) {
                case "new_message" -> {
                    // Incoming real-time message (text or file) sent to the receiver.
                    // Contains full payload — insert into local DB and ack back to server.
                    if (payload != null) {
                        handleIncomingNewMessage(payload, true);
                    }
                }
                case "ack_callback" -> {
                    // Server's confirmation that our `ack` was processed. Contains the
                    // full message data as a bonus. DO NOT send another ack — that would
                    // create an infinite loop (server answers every ack with ack_callback).
                    // Insert is still attempted in case new_message was missed; duplicates
                    // are silently dropped by the DAO's CONFLICT_IGNORE.
                    if (payload != null) {
                        Log.e(Extras.LOG_SOCKET_RECV, "[ACK_CALLBACK] " + payload);
                        handleIncomingNewMessage(payload, false);
                    }
                }
                case "delivery_status" -> {
                    if (payload != null) {
                        handleDeliveryStatus(payload);
                    }
                }
                case "message", "file" -> {
                    // Legacy event names — kept for backwards compatibility while the
                    // backend rolls out the new_message event. Same handling as new_message.
                    if (payload != null) {
                        handleIncomingNewMessage(payload, true);
                    }
                }
                case "message_deleted" -> {
                    if (payload != null) {
                        String  msgId       =   payload.optString("message_id", "");
                        String  roomId      =   payload.optString("room_id", "");
                        Log.e(Extras.LOG_SOCKET_RECV, "[MESSAGE_DELETED] id=" + msgId + " room=" + roomId);
                        // The server message_id is not the same as our local UUID — but if we
                        // correlate by the pending queue (or in the future by client_message_id),
                        // we can match. For now, try both the server id and our local ids.
                        handleRemoteMessageDeletion(msgId);
                    }
                }
                case "messages_read" -> {
                    // Receiver read our messages → update each to SEEN (✓✓ blue)
                    if (payload != null) {
                        String      roomId          =   payload.optString("room_id", "");
                        String      readerId        =   payload.optString("reader_id", "");
                        JSONArray   clientMsgIds    =   payload.optJSONArray("client_message_ids");
                        JSONArray   serverMsgIds    =   payload.optJSONArray("message_ids");
                        // Prefer client_message_ids for local DB lookup
                        JSONArray   idsToUse        =   clientMsgIds != null && clientMsgIds.length() > 0
                                                        ? clientMsgIds : serverMsgIds;
                        Log.e(Extras.LOG_SOCKET_RECV, "[MESSAGES_READ] room=" + roomId + " reader=" + readerId
                                + " ids=" + (idsToUse != null ? idsToUse.toString() : "[]"));
                        if (idsToUse != null) {
                            for (int i = 0; i < idsToUse.length(); i++) {
                                String readMsgId = idsToUse.optString(i, "");
                                if (!readMsgId.isEmpty()) {
                                    markMessageAsSeen(readMsgId);
                                }
                            }
                        }
                    }
                }
                case "file_deleted" -> {
                    if (payload != null) {
                        String  fileTransferId  =   payload.optString("file_transfer_id", "");
                        String  status          =   payload.optString("status", "");
                        Log.e(Extras.LOG_SOCKET_RECV, "[FILE_DELETED] id=" + fileTransferId + " status=" + status);
                    }
                }
                case "typing" -> {
                    if (payload != null) {
                        String  typerId     =   payload.optString("typer_id", "");
                        boolean isTyping    =   payload.optBoolean("typing_status", false);
                        Log.e(Extras.LOG_SOCKET_RECV, "[TYPING] from=" + typerId + " typing=" + isTyping);
                    }
                }
                case "presence_update", "user_online_status" -> {
                    if (payload != null) {
                        String  userId      =   payload.optString("user_id", "");
                        int     online      =   payload.optInt("is_online", 0);
                        Log.e(Extras.LOG_SOCKET_RECV, "[PRESENCE] user=" + userId + " online=" + online);
                    }
                }
                case "error" -> {
                    String  error       =   payload != null ? payload.optString("error", "") : "";
                    Log.e(Extras.LOG_SOCKET_RECV, "[ERROR] " + error);
                }
                default -> Log.e(Extras.LOG_SOCKET_RECV, "[UNKNOWN] type=" + type);
            }
        } catch (JSONException e) {
            Log.e(Extras.LOG_SOCKET_RECV, "Failed to parse new protocol message: " + e.getMessage());
        }
    }

    /**
     * Parses a new_message / ack_callback / legacy message payload and inserts the
     * incoming message into the local DB. Handles both text and file messages:
     *
     *   Text payload: { id, room_id, sender_id, sender_username, receiver_id,
     *                   receiver_username, ciphertext, message_type: "text", delivered, created_at }
     *
     *   File payload: Text fields + { file_transfer_id, encrypted_s3_url, s3_key,
     *                   file_name, file_size, content_type, content_subtype, caption,
     *                   width, height, duration, thumbnail }
     *
     * While Signal encryption is disabled, `ciphertext` is treated as plaintext.
     * After insertion, optionally acks the message back to the server and notifies
     * observers (the messaging activity re-queries the DB via its LiveData observer
     * chain).
     *
     * @param payload   the WS payload JSON
     * @param shouldAck true only for `new_message` / legacy `message`-`file` events.
     *                  Must be false for `ack_callback` — acking the server's ack
     *                  confirmation creates an infinite loop.
     */
    private void handleIncomingNewMessage(JSONObject payload, boolean shouldAck) {
        try {
            String  messageId       =   payload.optString("id", payload.optString("message_id", ""));
            String  clientMsgId     =   payload.optString("client_message_id", "");
            String  senderId        =   payload.optString("sender_id", "");
            String  receiverId      =   payload.optString("receiver_id", "");
            String  rawCiphertext   =   payload.optString("ciphertext", "");
            String  encryptionKey   =   payload.optString("encryption_key", "");
            String  encryptionIv    =   payload.optString("encryption_iv", "");
            String  messageType     =   payload.optString("message_type", "text");
            String  createdAt       =   payload.optString("created_at", "");
            String  roomId          =   payload.optString("room_id", "");
            boolean delivered       =   payload.optBoolean("delivered", false);

            // Decrypt ciphertext if encryption_key + iv are present
            String  ciphertext;
            if (!encryptionKey.isEmpty() && !encryptionIv.isEmpty()) {
                String decrypted = com.jippytalk.Encryption.MessageCryptoHelper.decrypt(
                        rawCiphertext, encryptionKey, encryptionIv);
                ciphertext = decrypted != null ? decrypted : rawCiphertext;
                Log.e(Extras.LOG_SOCKET_RECV, "[INCOMING] decrypted="
                        + (decrypted != null ? "YES" : "FAILED (using raw)"));
            } else {
                // No encryption fields — plaintext (legacy or unencrypted message)
                ciphertext = rawCiphertext;
            }

            // For file messages: all fields come from top-level payload keys.
            // ciphertext = encrypted caption only (not metadata).
            String  fileTransferId  =   payload.optString("file_transfer_id", "");
            String  s3Key           =   payload.optString("s3_key", "");
            String  fileName        =   payload.optString("file_name", "");
            String  contentType     =   payload.optString("content_type", "");
            String  contentSubtype  =   payload.optString("content_subtype", "");

            Log.e(Extras.LOG_SOCKET_RECV, "[INCOMING] from=" + senderId
                    + " id=" + messageId + " clientId=" + clientMsgId
                    + " type=" + messageType + " file=" + fileName);

            if (messageId.isEmpty() || senderId.isEmpty()) {
                Log.e(Extras.LOG_SOCKET_RECV, "[INCOMING] missing id/sender — dropping");
                return;
            }

            // Don't insert messages we sent ourselves (prevents self-echo loops when
            // server re-broadcasts). Our own sends are tracked via delivery_status.
            if (senderId.equals(userId)) {
                Log.e(Extras.LOG_SOCKET_RECV, "[INCOMING] skipping self-sent echo");
                return;
            }

            // Decide local message type constant
            int localMessageType    =   "file".equals(messageType)
                                        ? MessagesManager.DOCUMENT_MESSAGE
                                        : MessagesManager.TEXT_MESSAGE;

            if (messagesRepository == null) {
                Log.e(Extras.LOG_SOCKET_RECV, "[INCOMING] messagesRepository null, queueing insert");
                return;
            }

            long    now             =   System.currentTimeMillis();
            int     messageStatus   =   delivered
                                        ? MessagesManager.MESSAGE_DELIVERED
                                        : MessagesManager.MESSAGE_DELIVERED_LOCALLY;

            if ("file".equals(messageType)) {
                // ---- Decrypt URL fields with the per-message AES key+iv ----
                String decryptedFileUrl = "";
                String encS3Url = payload.optString("encrypted_s3_url", "");
                if (!encS3Url.isEmpty() && !encryptionKey.isEmpty() && !encryptionIv.isEmpty()) {
                    String d = com.jippytalk.Encryption.MessageCryptoHelper.decrypt(
                            encS3Url, encryptionKey, encryptionIv);
                    decryptedFileUrl = d != null ? d : "";
                } else if (!encS3Url.isEmpty() && encS3Url.startsWith("http")) {
                    decryptedFileUrl = encS3Url;  // backend presigned URL (plaintext)
                }

                String decryptedThumbUrl = "";
                String backendThumbUrl = payload.optString("thumbnail_url", "");
                if (!backendThumbUrl.isEmpty()) {
                    decryptedThumbUrl = backendThumbUrl;  // backend presigned (best)
                } else {
                    String encThumb = payload.optString("thumbnail", "");
                    if (!encThumb.isEmpty() && !encryptionKey.isEmpty() && !encryptionIv.isEmpty()) {
                        String d = com.jippytalk.Encryption.MessageCryptoHelper.decrypt(
                                encThumb, encryptionKey, encryptionIv);
                        decryptedThumbUrl = d != null ? d : "";
                    }
                }

                final String fSenderId = senderId;
                // v8: insert into dedicated columns. The `message` column gets the
                // decrypted caption text only, so the chat list shows it directly.
                messagesRepository.insertMediaMessageToLocalStorageFromService(
                        messageId,
                        MessagesManager.MESSAGE_INCOMING,
                        senderId,                       // chat partner id = sender
                        ciphertext,                     // message column = decrypted caption
                        messageStatus,
                        MessagesManager.NO_NEED_TO_PUSH_MESSAGE,
                        now, now,
                        MessagesManager.DEFAULT_READ_TIMESTAMP,
                        MessagesManager.MESSAGE_NOT_STARRED,
                        MessagesManager.MESSAGE_NOT_EDITED,
                        MessagesManager.DOCUMENT_MESSAGE,
                        0, 0,
                        MessagesManager.DEFAULT_MSG_TO_MSG_REPLY,
                        "",
                        com.jippytalk.Managers.ChatManager.UNARCHIVE_CHAT,
                        fileName, contentType, contentSubtype,
                        ciphertext,                     // caption column = decrypted caption
                        payload.optInt("width", 0),
                        payload.optInt("height", 0),
                        payload.optLong("duration", 0),
                        payload.optLong("file_size", 0),
                        s3Key,
                        payload.optString("bucket", ""),
                        fileTransferId,
                        "",                             // local_file_path (filled after download)
                        "",                             // local_thumbnail_path (filled after auto-fetch)
                        decryptedThumbUrl,
                        decryptedFileUrl,
                        encryptionKey,
                        encryptionIv,
                        roomId,                         // v9: server-assigned room id
                        () -> {
                            if (openedChatId != null && openedChatId.equals(fSenderId)
                                    && messageCallBacks != null) {
                                messagesRepository.getMessagesForContact(fSenderId);
                            }
                        });
            } else {
                // Plain text message — original insert path is unchanged.
                final String fSenderIdT = senderId;
                messagesRepository.insertMessageToLocalStorageFromService(
                        messageId,
                        MessagesManager.MESSAGE_INCOMING,
                        senderId,                       // chat partner id = sender
                        ciphertext,
                        messageStatus,
                        MessagesManager.NO_NEED_TO_PUSH_MESSAGE,
                        now, now,
                        MessagesManager.DEFAULT_READ_TIMESTAMP,
                        MessagesManager.MESSAGE_NOT_STARRED,
                        MessagesManager.MESSAGE_NOT_EDITED,
                        localMessageType,
                        0, 0,
                        MessagesManager.DEFAULT_MSG_TO_MSG_REPLY,
                        "",
                        com.jippytalk.Managers.ChatManager.UNARCHIVE_CHAT,
                        roomId,                         // v9: server-assigned room id
                        () -> {
                            if (openedChatId != null && openedChatId.equals(fSenderIdT)
                                    && messageCallBacks != null) {
                                messagesRepository.getMessagesForContact(fSenderIdT);
                            }
                        });
            }

            // Ack back to server so delivery_status can flow back to the sender.
            // Skipped when this is an ack_callback — see the shouldAck javadoc.
            if (shouldAck) {
                sendMessageAck(messageId);
            }

        } catch (Exception e) {
            Log.e(Extras.LOG_SOCKET_RECV, "Failed to parse incoming message: " + e.getMessage());
        }
    }

    /**
     * Handles a delivery_status event. The backend now sends two forms:
     *
     *   1. Full payload (after sending): { id, room_id, sender_id, ..., delivered }
     *      — use this to correlate our pending local UUID to the server id and
     *      flip the local row's status to SYNCED/DELIVERED.
     *
     *   2. Slim payload (after receiver ack): { message_id, room_id, delivered }
     *      — just a delivery acknowledgement; mark the sent row as delivered.
     */
    /**
     * Handles delivery_status events. Two forms arrive from the server:
     *
     *   1. Full payload (delivered=false): server received the message but receiver
     *      hasn't acked yet → mark as SYNCED (✓ single grey tick)
     *   2. Slim payload (delivered=true): receiver's device got it and acked →
     *      mark as DELIVERED (✓✓ double grey tick)
     */
    private void handleDeliveryStatus(JSONObject payload) {
        String  serverMsgId     =   payload.optString("id", payload.optString("message_id", ""));
        String  clientMsgId     =   payload.optString("client_message_id", "");
        String  roomId          =   payload.optString("room_id", "");
        boolean delivered       =   payload.optBoolean("delivered", false);
        boolean isFullPayload   =   payload.has("sender_id") || payload.has("file_name");
        String  messageType     =   payload.optString("message_type", "");

        String  lookupId        =   !clientMsgId.isEmpty() ? clientMsgId : serverMsgId;

        Log.e(Extras.LOG_SOCKET_RECV, "[DELIVERY] serverId=" + serverMsgId
                + " clientId=" + clientMsgId + " room=" + roomId
                + " delivered=" + delivered + " (" + (isFullPayload ? "full" : "slim") + ")");

        if (lookupId.isEmpty()) {
            Log.e(Extras.LOG_SOCKET_RECV, "[DELIVERY] no ID to correlate — dropping");
            return;
        }

        if (!delivered) {
            Log.e(Extras.LOG_SOCKET_RECV, "[DELIVERY] SYNCED (✓) id=" + lookupId);
            markMessageAsSynced(lookupId);
        } else {
            Log.e(Extras.LOG_SOCKET_RECV, "[DELIVERY] DELIVERED (✓✓) id=" + lookupId);
            markMessageAsDelivered(lookupId);
        }
    }

    /**
     * Handles an incoming message_deleted event by removing the message from the
     * local DB and the in-memory adapter list. Called when either:
     *   - The sender issued a delete_message event (server echoes back to both parties)
     *   - Another device of the same user deleted a message
     *
     * @param messageId the ID of the message to remove
     */
    private void handleRemoteMessageDeletion(String messageId) {
        if (messagesRepository != null) {
            messagesRepository.deleteMessage(messageId);
            Log.e(Extras.LOG_MESSAGE, "Local DB delete requested for: " + messageId);
        }
        // MessagingActivity observes getMessageDeleteLiveStatusFromViewModel() and will
        // call retrieveAllMessagesOfContact() on success, which refreshes the adapter.
    }

    /**
     * Updates a sent message's status to SYNCED (removes the clock icon in the UI).
     * Updates both the local DB and the in-memory adapter list.
     *
     * @param localMessageId the local UUID of the sent message
     */
    /** Updates status to SYNCED (✓ single grey tick — server received it). */
    /**
     * Encrypts plaintext using a pre-generated Base64 key and IV.
     * Used to encrypt multiple fields (caption, URL, thumbnail) with the
     * same key+iv per message.
     */
    private String encryptWithKeyIv(String plaintext, String b64Key, String b64Iv) {
        try {
            byte[] keyBytes = android.util.Base64.decode(b64Key, android.util.Base64.NO_WRAP);
            byte[] ivBytes  = android.util.Base64.decode(b64Iv, android.util.Base64.NO_WRAP);
            javax.crypto.spec.SecretKeySpec keySpec = new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
            javax.crypto.spec.GCMParameterSpec gcmSpec = new javax.crypto.spec.GCMParameterSpec(128, ivBytes);
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            byte[] encrypted = cipher.doFinal(plaintext.getBytes("UTF-8"));
            return android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "encryptWithKeyIv failed: " + e.getMessage());
            return "";
        }
    }

    private void markMessageAsSynced(String localMessageId) {
        if (messagesRepository != null) {
            messagesRepository.updateMessageAsSyncedWithServer(
                    localMessageId,
                    MessagesManager.MESSAGE_SYNCED_WITH_SERVER,
                    MessagesManager.NO_NEED_TO_PUSH_MESSAGE,
                    isSuccess -> Log.e(Extras.LOG_MESSAGE,
                            "DB updated as SYNCED for " + localMessageId + " success=" + isSuccess)
            );
        }
        if (messageCallBacks != null) {
            messageCallBacks.updateMessageStatus(localMessageId,
                    MessagesManager.MESSAGE_SYNCED_WITH_SERVER);
        }
    }

    /** Updates status to DELIVERED (✓✓ double grey tick — receiver's device got it). */
    private void markMessageAsDelivered(String messageId) {
        if (messagesRepository != null) {
            messagesRepository.updateMessageAsDelivered(
                    messageId,
                    MessagesManager.MESSAGE_DELIVERED,
                    System.currentTimeMillis(),
                    isSuccess -> Log.e(Extras.LOG_MESSAGE,
                            "DB updated as DELIVERED for " + messageId + " success=" + isSuccess)
            );
        }
        if (messageCallBacks != null) {
            messageCallBacks.updateMessageStatus(messageId,
                    MessagesManager.MESSAGE_DELIVERED);
        }
    }

    /** Updates status to SEEN (✓✓ double blue tick — receiver read it). */
    private void markMessageAsSeen(String messageId) {
        if (messagesRepository != null) {
            messagesRepository.updateMessageStatusAsSeen(
                    messageId,
                    MessagesManager.MESSAGE_SEEN,
                    System.currentTimeMillis(),
                    MessagesManager.NO_NEED_TO_PUSH_MESSAGE
            );
        }
        if (messageCallBacks != null) {
            messageCallBacks.updateMessageStatus(messageId,
                    MessagesManager.MESSAGE_SEEN);
        }
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

    // -------------------- New Protocol Message Sending Starts Here ---------------------

    /**
     * Sends an E2E-encrypted text message using the new WebSocket protocol format:
     *   { "type": "message", "payload": { receiver_id, ciphertext, message_type, signal_message_type } }
     *
     * The ciphertext is already Base64-encoded Signal Protocol output.
     * The server generates the message_id and returns it via delivery_status.
     *
     * @param localMessageId        local UUID used only for logging/tracking
     * @param receiverId            the receiver's user ID
     * @param encryptedCiphertext   Base64-encoded Signal Protocol ciphertext
     * @param signalMessageType     Signal message type (2=WHISPER, 3=PREKEY)
     */
    public void sendEncryptedTextMessage(String localMessageId, String receiverId,
                                         String encryptedCiphertext, int signalMessageType) {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            Log.e(Extras.LOG_SOCKET_SEND, "[FAIL] WebSocket not open, cannot send encrypted message: " + localMessageId);
            return;
        }

        try {
            JSONObject  payload     =   new JSONObject();
            payload.put("client_message_id", localMessageId);
            payload.put("receiver_id", receiverId);
            payload.put("ciphertext", encryptedCiphertext);
            payload.put("message_type", "text");
            payload.put("signal_message_type", signalMessageType);

            JSONObject  envelope    =   new JSONObject();
            envelope.put("type", "message");
            envelope.put("payload", payload);

            String  jsonString      =   envelope.toString();

            Log.e(Extras.LOG_SOCKET_SEND, "═════════════ SEND encrypted message ════════════");
            Log.e(Extras.LOG_SOCKET_SEND, "ClientMsgId      : " + localMessageId);
            Log.e(Extras.LOG_SOCKET_SEND, "ReceiverId       : " + receiverId);
            Log.e(Extras.LOG_SOCKET_SEND, "SignalMsgType    : " + signalMessageType
                    + " (" + (signalMessageType == 3 ? "PREKEY" : "WHISPER") + ")");
            Log.e(Extras.LOG_SOCKET_SEND, "Raw JSON         : " + jsonString);
            Log.e(Extras.LOG_SOCKET_SEND, "══════════════════════════════════════════════════");

            webSocketClient.send(jsonString);

            Log.e(Extras.LOG_SOCKET_SEND, "[OK] encrypted message sent: " + localMessageId);

        } catch (JSONException e) {
            Log.e(Extras.LOG_SOCKET_SEND, "[ERROR] Failed to build encrypted message JSON: " + e.getMessage());
        }
    }

    /**
     * Sends a text message using the new WebSocket protocol format:
     *   { "type": "message", "payload": { receiver_id, ciphertext, message_type } }
     *
     * NOTE: The server generates the message_id and returns it via the delivery_status event.
     * Sends unencrypted content in the ciphertext field for dev fallback only.
     *
     * @deprecated Use sendEncryptedTextMessage() once Signal Protocol session is established
     *
     * @param localMessageId    local UUID used only for logging/tracking (not sent to server)
     * @param receiverId        the receiver's user ID
     * @param text              the plain text message content
     */
    @Deprecated
    public void sendPlainTextMessage(String localMessageId, String receiverId, String text) {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            Log.e(Extras.LOG_SOCKET_SEND, "[PENDING] WebSocket not open, queueing text send: " + localMessageId);
            pendingOutboundSends.offer(() -> sendPlainTextMessage(localMessageId, receiverId, text));
            ensureConnected();
            return;
        }

        try {
            // AES-256-GCM encrypt the message text
            com.jippytalk.Encryption.MessageCryptoHelper.EncryptionResult encrypted =
                    com.jippytalk.Encryption.MessageCryptoHelper.encrypt(text);

            JSONObject  payload     =   new JSONObject();
            payload.put("client_message_id", localMessageId);
            payload.put("receiver_id", receiverId);
            payload.put("message_type", "text");

            if (encrypted != null) {
                payload.put("ciphertext", encrypted.ciphertext);
                payload.put("encryption_key", encrypted.key);
                payload.put("encryption_iv", encrypted.iv);
            } else {
                Log.e(Extras.LOG_SOCKET_SEND, "[WARN] Encryption failed, sending plaintext");
                payload.put("ciphertext", text);
            }

            JSONObject  envelope    =   new JSONObject();
            envelope.put("type", "message");
            envelope.put("payload", payload);

            String  jsonString      =   envelope.toString();

            Log.e(Extras.LOG_SOCKET_SEND, "════════════════════ SEND message (encrypted) ════════");
            Log.e(Extras.LOG_SOCKET_SEND, "ClientMsgId: " + localMessageId);
            Log.e(Extras.LOG_SOCKET_SEND, "ReceiverId : " + receiverId);
            Log.e(Extras.LOG_SOCKET_SEND, "Encrypted  : " + (encrypted != null ? "YES" : "NO (fallback)"));
            Log.e(Extras.LOG_SOCKET_SEND, "Raw JSON   : " + jsonString);
            Log.e(Extras.LOG_SOCKET_SEND, "═══════════════════════════════════════════════════════");

            webSocketClient.send(jsonString);

            Log.e(Extras.LOG_SOCKET_SEND, "[OK] encrypted message sent: " + localMessageId);

        } catch (JSONException e) {
            Log.e(Extras.LOG_SOCKET_SEND, "[ERROR] Failed to build message JSON: " + e.getMessage());
        }
    }

    /**
     * Sends an acknowledgement for a received message.
     *   { "type": "ack", "payload": { message_id } }
     *
     * @param messageId the ID of the received message being acknowledged
     */
    public void sendMessageAck(String messageId) {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            Log.e(Extras.LOG_SOCKET_SEND, "[FAIL] WebSocket not open, cannot send ack: " + messageId);
            return;
        }

        try {
            JSONObject  payload     =   new JSONObject();
            payload.put("message_id", messageId);

            JSONObject  envelope    =   new JSONObject();
            envelope.put("type", "ack");
            envelope.put("payload", payload);

            String  jsonString      =   envelope.toString();

            Log.e(Extras.LOG_SOCKET_SEND, "════════════════════ SEND ack ════════════════════════");
            Log.e(Extras.LOG_SOCKET_SEND, "MessageId  : " + messageId);
            Log.e(Extras.LOG_SOCKET_SEND, "Raw JSON   : " + jsonString);
            Log.e(Extras.LOG_SOCKET_SEND, "═══════════════════════════════════════════════════════");

            webSocketClient.send(jsonString);

            Log.e(Extras.LOG_SOCKET_SEND, "[OK] ack sent: " + messageId);

        } catch (JSONException e) {
            Log.e(Extras.LOG_SOCKET_SEND, "[ERROR] Failed to build ack JSON: " + e.getMessage());
        }
    }

    /**
     * Sends a file message after the upload to S3 has completed.
     *
     *   {
     *     "type": "file",
     *     "payload": {
     *       "receiver_id", "message_type": "file",
     *       "s3_key", "encrypted_s3_url", "file_name",
     *       "content_type", "content_subtype", "caption",
     *       "width", "height", "duration", "thumbnail", "bucket",
     *       "ciphertext"
     *     }
     *   }
     *
     * Note: file_size is intentionally omitted for now — backend will populate it
     * on its side once the field is wired up. Once live, add it back here.
     *
     * The ciphertext field carries the Signal-encrypted metadata once E2E is turned
     * back on. While encryption is disabled, it's sent empty.
     */
    public void sendFileMessage(String clientMessageId, String receiverId,
                                String ciphertext, String encryptedS3Url,
                                String s3Key, String fileName, long fileSize,
                                String contentType, String contentSubtype, String caption,
                                int width, int height, long duration,
                                String thumbnail, String bucket,
                                String preGeneratedKey, String preGeneratedIv) {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            Log.e(Extras.LOG_SOCKET_SEND, "[PENDING] WebSocket not open, queueing file send for " + fileName);
            pendingOutboundSends.offer(() -> sendFileMessage(
                    clientMessageId, receiverId, ciphertext, encryptedS3Url, s3Key, fileName,
                    fileSize, contentType, contentSubtype, caption,
                    width, height, duration, thumbnail, bucket,
                    preGeneratedKey, preGeneratedIv));
            ensureConnected();
            return;
        }

        try {
            // Encrypt ONLY the caption text — all file metadata fields are
            // separate top-level keys. Receiver decrypts ciphertext for caption only.
            String  captionText =   caption != null ? caption : "";
            com.jippytalk.Encryption.MessageCryptoHelper.EncryptionResult encrypted =
                    !captionText.isEmpty()
                    ? com.jippytalk.Encryption.MessageCryptoHelper.encrypt(captionText)
                    : null;

            JSONObject  payload     =   new JSONObject();
            payload.put("client_message_id", clientMessageId != null ? clientMessageId : "");
            payload.put("receiver_id", receiverId);
            payload.put("message_type", "file");

            // Build S3 URLs from keys
            String  bucketName  =   bucket != null && !bucket.isEmpty() ? bucket : API.S3_BUCKET;
            String  s3BaseUrl   =   "https://" + bucketName + ".s3." + API.S3_REGION + ".amazonaws.com/";
            String  fileUrl     =   s3Key != null && !s3Key.isEmpty() ? s3BaseUrl + s3Key : "";
            String  thumbUrl    =   thumbnail != null && !thumbnail.isEmpty() ? s3BaseUrl + thumbnail : "";

            // ONE key + ONE IV per message — REUSED from the upload pipeline so
            // the same key that encrypted the file/thumbnail bytes is shipped
            // alongside their URLs. Falls back to fresh keys for legacy callers
            // that don't pre-generate (e.g. retries before encryption rollout).
            String  b64Key;
            String  b64Iv;
            if (preGeneratedKey != null && !preGeneratedKey.isEmpty()
                    && preGeneratedIv != null && !preGeneratedIv.isEmpty()) {
                b64Key = preGeneratedKey;
                b64Iv  = preGeneratedIv;
            } else {
                com.jippytalk.Encryption.MessageCryptoHelper.EncryptionResult keyPair =
                        com.jippytalk.Encryption.MessageCryptoHelper.generateKeyIv();
                b64Key  =   keyPair != null ? keyPair.key : "";
                b64Iv   =   keyPair != null ? keyPair.iv : "";
            }

            // Encrypt caption, file URL, thumbnail URL with same key+iv
            String encCaption   =   !captionText.isEmpty()
                    ? encryptWithKeyIv(captionText, b64Key, b64Iv) : "";
            String encFileUrl   =   !fileUrl.isEmpty()
                    ? encryptWithKeyIv(fileUrl, b64Key, b64Iv) : "";
            String encThumbUrl  =   !thumbUrl.isEmpty()
                    ? encryptWithKeyIv(thumbUrl, b64Key, b64Iv) : "";

            payload.put("encryption_key", b64Key);
            payload.put("encryption_iv", b64Iv);

            // Encrypted fields
            payload.put("ciphertext", encCaption);
            payload.put("encrypted_s3_url", encFileUrl);
            payload.put("thumbnail", encThumbUrl);

            // Plaintext fields (backend needs for routing/indexing)
            payload.put("s3_key", s3Key != null ? s3Key : "");
            payload.put("file_name", fileName != null ? fileName : "");
            payload.put("file_size", fileSize);
            payload.put("content_type", contentType != null ? contentType : "");
            payload.put("content_subtype", contentSubtype != null ? contentSubtype : "");
            payload.put("caption", captionText);
            payload.put("width", width);
            payload.put("height", height);
            payload.put("duration", duration);
            payload.put("bucket", bucketName);
            payload.put("region", API.S3_REGION);

            JSONObject  envelope    =   new JSONObject();
            envelope.put("type", "file");
            envelope.put("payload", payload);

            String  jsonString      =   envelope.toString();

            Log.e(Extras.LOG_SOCKET_SEND, "════════════════════ SEND file (encrypted) ═══════════");
            Log.e(Extras.LOG_SOCKET_SEND, "ClientMsgId : " + clientMessageId);
            Log.e(Extras.LOG_SOCKET_SEND, "ReceiverId  : " + receiverId);
            Log.e(Extras.LOG_SOCKET_SEND, "FileName    : " + fileName);
            Log.e(Extras.LOG_SOCKET_SEND, "FileSize    : " + fileSize);
            Log.e(Extras.LOG_SOCKET_SEND, "ContentType : " + contentType + "/" + contentSubtype);
            Log.e(Extras.LOG_SOCKET_SEND, "S3Key       : " + s3Key);
            Log.e(Extras.LOG_SOCKET_SEND, "Thumbnail   : " + (thumbnail != null && !thumbnail.isEmpty() ? "YES" : "NONE"));
            Log.e(Extras.LOG_SOCKET_SEND, "Dimensions  : " + width + "x" + height);
            Log.e(Extras.LOG_SOCKET_SEND, "Duration    : " + duration);
            Log.e(Extras.LOG_SOCKET_SEND, "Encrypted   : " + (encrypted != null ? "YES" : "NO"));
            Log.e(Extras.LOG_SOCKET_SEND, "Raw JSON    : " + jsonString);
            Log.e(Extras.LOG_SOCKET_SEND, "═══════════════════════════════════════════════════════");

            webSocketClient.send(jsonString);

            Log.e(Extras.LOG_SOCKET_SEND, "[OK] file message sent: " + fileName);

        } catch (JSONException e) {
            Log.e(Extras.LOG_SOCKET_SEND, "[ERROR] Failed to build file message JSON: " + e.getMessage());
        }
    }

    /**
     * Notifies the server that a received file has been downloaded.
     *   { "type": "file_downloaded", "payload": { file_transfer_id } }
     *
     * Triggers the server to delete the file from S3.
     *
     * @param fileTransferId the server's file transfer ID for the downloaded file
     */
    public void sendFileDownloaded(String fileTransferId) {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            Log.e(Extras.LOG_SOCKET_SEND, "[FAIL] WebSocket not open, cannot send file_downloaded");
            return;
        }

        try {
            JSONObject  payload     =   new JSONObject();
            payload.put("file_transfer_id", fileTransferId);

            JSONObject  envelope    =   new JSONObject();
            envelope.put("type", "file_downloaded");
            envelope.put("payload", payload);

            String  jsonString      =   envelope.toString();

            Log.e(Extras.LOG_SOCKET_SEND, "══════════════════ SEND file_downloaded ══════════════");
            Log.e(Extras.LOG_SOCKET_SEND, "FileTransferId : " + fileTransferId);
            Log.e(Extras.LOG_SOCKET_SEND, "Raw JSON       : " + jsonString);
            Log.e(Extras.LOG_SOCKET_SEND, "═══════════════════════════════════════════════════════");

            webSocketClient.send(jsonString);

            Log.e(Extras.LOG_SOCKET_SEND, "[OK] file_downloaded sent: " + fileTransferId);

        } catch (JSONException e) {
            Log.e(Extras.LOG_SOCKET_SEND, "[ERROR] Failed to build file_downloaded JSON: " + e.getMessage());
        }
    }

    /**
     * Sends a delete_message event to hard-delete a message (E2E safe).
     *   { "type": "delete_message", "payload": { message_id } }
     *
     * Only the sender can delete their own messages. The server removes the message
     * and any associated file transfer from the database and notifies both parties.
     *
     * @param messageId the server-assigned ID of the message to delete
     */
    public void sendDeleteMessage(String messageId) {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            Log.e(Extras.LOG_SOCKET_SEND, "[FAIL] WebSocket not open, cannot send delete_message");
            return;
        }

        try {
            JSONObject  payload     =   new JSONObject();
            payload.put("message_id", messageId);

            JSONObject  envelope    =   new JSONObject();
            envelope.put("type", "delete_message");
            envelope.put("payload", payload);

            String  jsonString      =   envelope.toString();

            Log.e(Extras.LOG_SOCKET_SEND, "════════════════════ SEND delete_message ═════════════");
            Log.e(Extras.LOG_SOCKET_SEND, "MessageId  : " + messageId);
            Log.e(Extras.LOG_SOCKET_SEND, "Raw JSON   : " + jsonString);
            Log.e(Extras.LOG_SOCKET_SEND, "═══════════════════════════════════════════════════════");

            webSocketClient.send(jsonString);

            Log.e(Extras.LOG_SOCKET_SEND, "[OK] delete_message sent: " + messageId);

        } catch (JSONException e) {
            Log.e(Extras.LOG_SOCKET_SEND, "[ERROR] Failed to build delete_message JSON: " + e.getMessage());
        }
    }

    /**
     * Marks messages as read. Pass a list of message IDs OR leave it empty/null to mark
     * ALL unread messages in the given room as read.
     *   { "type": "mark_read", "payload": { message_ids: [...], room_id } }
     *
     * The server notifies the original sender(s) via the messages_read event so they can
     * update read receipts (blue ticks).
     *
     * @param roomId        the room ID containing the messages
     * @param messageIds    specific message IDs to mark read, or null/empty for all unread in room
     */
    public void sendMarkRead(String roomId, java.util.List<String> messageIds) {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            Log.e(Extras.LOG_SOCKET_SEND, "[FAIL] WebSocket not open, cannot send mark_read");
            return;
        }

        try {
            JSONObject      payload     =   new JSONObject();
            JSONArray       idsArray    =   new JSONArray();
            if (messageIds != null) {
                for (String id : messageIds) {
                    idsArray.put(id);
                }
            }
            payload.put("message_ids", idsArray);
            payload.put("room_id", roomId);

            JSONObject  envelope    =   new JSONObject();
            envelope.put("type", "mark_read");
            envelope.put("payload", payload);

            String  jsonString      =   envelope.toString();

            Log.e(Extras.LOG_SOCKET_SEND, "════════════════════ SEND mark_read ══════════════════");
            Log.e(Extras.LOG_SOCKET_SEND, "RoomId     : " + roomId);
            Log.e(Extras.LOG_SOCKET_SEND, "MessageIds : " + idsArray);
            Log.e(Extras.LOG_SOCKET_SEND, "Raw JSON   : " + jsonString);
            Log.e(Extras.LOG_SOCKET_SEND, "═══════════════════════════════════════════════════════");

            webSocketClient.send(jsonString);

            Log.e(Extras.LOG_SOCKET_SEND, "[OK] mark_read sent for room: " + roomId);

        } catch (JSONException e) {
            Log.e(Extras.LOG_SOCKET_SEND, "[ERROR] Failed to build mark_read JSON: " + e.getMessage());
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

    /**
     * Listener for WebSocket connection state changes.
     * All methods are called on the main thread.
     */
    public interface ConnectionStateListener {
        /**
         * Called when the WebSocket has successfully connected and completed handshake.
         */
        void onConnected();

        /**
         * Called when the WebSocket connection has been closed.
         */
        void onDisconnected();

        /**
         * Called when the WebSocket connection has failed.
         *
         * @param error the error message describing the failure
         */
        void onConnectionFailed(String error);
    }
}
