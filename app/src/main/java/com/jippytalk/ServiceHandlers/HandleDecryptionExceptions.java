package com.jippytalk.ServiceHandlers;

import android.util.Log;

import com.jippytalk.Extras;

import org.java_websocket.client.WebSocketClient;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;

public class HandleDecryptionExceptions {

    private final WebSocketClient   webSocketClient;
    private final ExecutorService   executorService;

    public HandleDecryptionExceptions(WebSocketClient webSocketClient, ExecutorService executorService) {
        this.webSocketClient        =   webSocketClient;
        this.executorService        =   executorService;
    }

    public void handleInvalidMessageException(String messageId, String senderId, String receiverId,
                                              int messageStatus) {
        executorService.execute(() -> {
            if (webSocketClient.isOpen()) {
                try {
                    JSONObject  jsonObject  =   new JSONObject();
                    jsonObject.put("messageId", messageId);
                    jsonObject.put("senderId", senderId);
                    jsonObject.put("receiverId", receiverId);
                    jsonObject.put("messageStatus", messageStatus);
                    jsonObject.put("method","invalidMessageException");

                    if (jsonObject.length() > 0) {
                        webSocketClient.send(jsonObject.toString());
                    }
                }
                catch (Exception e) {
                    logExceptions("Unable to send invalid message status to server " + e.getMessage());
                }
            }
        });
    }

    private void logExceptions(String message) {
        Log.e(Extras.LOG_MESSAGE, message);
    }
}
