package com.jippytalk.FirebasePushNotifications;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.RemoteMessage;
import com.jippytalk.Extras;
import com.jippytalk.Managers.AccountManager;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.MyApplication;
import com.jippytalk.WebSocketConnection;

import java.util.Map;

/**
 * FirebaseMessagingService - Handles incoming Firebase Cloud Messaging (FCM) push notifications.
 *
 * This service is responsible for:
 * 1. Receiving push notifications when the app is in the background or killed
 * 2. Reconnecting the WebSocket when a push notification arrives (to fetch new messages)
 * 3. Handling FCM token refresh for device registration with the server
 *
 * The actual message content is NOT delivered via FCM - it only serves as a wake-up signal.
 * All message data is fetched through the WebSocket connection for end-to-end encryption integrity.
 */
public class FirebaseMessagingService extends com.google.firebase.messaging.FirebaseMessagingService {

    // -------------------- Message Handling Starts Here ---------------------

    /**
     * Called when a new FCM message is received.
     * This triggers the WebSocket to reconnect and fetch any pending messages.
     *
     * @param remoteMessage the FCM message received from the server
     */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);

        Log.e(Extras.LOG_MESSAGE, "FCM message received from: " + remoteMessage.getFrom());

        try {
            Map<String, String> data    =   remoteMessage.getData();

            if (!data.isEmpty()) {
                Log.e(Extras.LOG_MESSAGE, "FCM data payload: " + data);
                handleDataPayload(data);
            }

            if (remoteMessage.getNotification() != null) {
                Log.e(Extras.LOG_MESSAGE, "FCM notification body: " + remoteMessage.getNotification().getBody());
            }

            // Reconnect WebSocket to fetch encrypted messages from the server
            reconnectWebSocketIfNeeded();

        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error processing FCM message " + e.getMessage());
        }
    }

    /**
     * Processes the data payload from the FCM message.
     * The data payload may contain metadata like message type or sender info
     * used to determine notification priority.
     *
     * @param data the key-value pairs from the FCM data payload
     */
    private void handleDataPayload(Map<String, String> data) {
        String messageType  =   data.get("type");
        String senderId     =   data.get("senderId");

        Log.e(Extras.LOG_MESSAGE, "FCM data - type: " + messageType + " sender: " + senderId);
    }

    /**
     * Reconnects the WebSocket if the app is initialized and registration is complete.
     * This ensures new messages are fetched via the encrypted WebSocket connection
     * rather than through FCM (which cannot carry encrypted payloads).
     */
    private void reconnectWebSocketIfNeeded() {
        try {
            MyApplication myApplication =   MyApplication.getInstance();
            if (myApplication == null || myApplication.getAppServiceLocator() == null) {
                Log.e(Extras.LOG_MESSAGE, "App not initialized, skipping WebSocket reconnect");
                return;
            }

            int registrationProgress    =   getSharedPreferences(
                    SharedPreferenceDetails.SHARED_PREFERENCE_NAME, MODE_PRIVATE)
                    .getInt(SharedPreferenceDetails.REGISTRATION_PROGRESS,
                            AccountManager.INITIAL_SCREENS);

            if (registrationProgress != AccountManager.REGISTRATION_DONE) {
                Log.e(Extras.LOG_MESSAGE, "Registration not complete, skipping WebSocket reconnect");
                return;
            }

            WebSocketConnection webSocketConnection =   myApplication.getAppServiceLocator().getWebSocketConnection();
            if (webSocketConnection != null && !WebSocketConnection.isConnectedToSocket) {
                webSocketConnection.connectToWebSocket();
                Log.e(Extras.LOG_MESSAGE, "WebSocket reconnecting after FCM push");
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error reconnecting WebSocket from FCM " + e.getMessage());
        }
    }

    // -------------------- Token Refresh Handling Starts Here ---------------------

    /**
     * Called when the FCM registration token is refreshed.
     * The new token should be sent to the server so it can continue delivering
     * push notifications to this device.
     *
     * @param token the new FCM registration token
     */
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        Log.e(Extras.LOG_MESSAGE, "FCM token refreshed: " + token);

        // Store the new token for later upload to the server
        getSharedPreferences(SharedPreferenceDetails.SHARED_PREFERENCE_NAME, MODE_PRIVATE)
                .edit()
                .putString(SharedPreferenceDetails.FCM_TOKEN, token)
                .putBoolean(SharedPreferenceDetails.FCM_TOKEN_UPLOADED, false)
                .apply();
    }
}
