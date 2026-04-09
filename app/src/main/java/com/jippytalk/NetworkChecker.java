package com.jippytalk;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * NetworkChecker - BroadcastReceiver that listens for custom data-saved broadcasts.
 *
 * This receiver is triggered when local data operations complete and need to be
 * synchronized with the server. It acts as a signal to retry pending operations
 * such as sending unsynced messages or updating delivery/read receipts.
 *
 * Registered in AndroidManifest.xml with action: com.jippytalk.datasaved
 */
public class NetworkChecker extends BroadcastReceiver {

    private static final String ACTION_DATA_SAVED   =   "com.jippytalk.datasaved";

    // -------------------- Broadcast Handling Starts Here ---------------------

    /**
     * Called when the broadcast is received.
     * Checks if the WebSocket is connected and triggers data sync if needed.
     *
     * @param context the context in which the receiver is running
     * @param intent  the Intent being received, containing the action
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        String action   =   intent.getAction();
        Log.e(Extras.LOG_MESSAGE, "NetworkChecker received action: " + action);

        if (ACTION_DATA_SAVED.equals(action)) {
            handleDataSavedAction(context);
        }
    }

    /**
     * Handles the data-saved action by checking if the WebSocket is connected
     * and triggering synchronization of any pending local data to the server.
     *
     * @param context the application context
     */
    private void handleDataSavedAction(Context context) {
        try {
            MyApplication myApplication =   MyApplication.getInstance();
            if (myApplication == null || myApplication.getAppServiceLocator() == null) {
                Log.e(Extras.LOG_MESSAGE, "NetworkChecker - App not initialized");
                return;
            }

            WebSocketConnection webSocketConnection =   myApplication.getAppServiceLocator().getWebSocketConnection();
            if (webSocketConnection != null && WebSocketConnection.isConnectedToSocket) {
                Log.e(Extras.LOG_MESSAGE, "NetworkChecker - WebSocket connected, data sync can proceed");
            }
            else {
                Log.e(Extras.LOG_MESSAGE, "NetworkChecker - WebSocket not connected, reconnecting");
                if (webSocketConnection != null) {
                    webSocketConnection.connectToWebSocket();
                }
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "NetworkChecker error " + e.getMessage());
        }
    }
}
