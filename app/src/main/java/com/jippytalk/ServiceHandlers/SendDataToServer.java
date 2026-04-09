package com.jippytalk.ServiceHandlers;

import android.content.Context;
import android.util.Log;
import com.jippytalk.Extras;


import org.java_websocket.client.WebSocketClient;
import org.json.JSONException;
import org.json.JSONObject;

public class SendDataToServer {

    private final Context               context;

    public SendDataToServer(Context context) {
        this.context            =   context.getApplicationContext();
    }


    public void sendMessageToServer(WebSocketClient webSocketClient, String messageId, String senderId,
                                    String receiverId, String message,
                                    int signalMessageType, int messageType, int messageStatus, int isEdited,
                                    long latitude, long longitude, long sentTimestamp, long deliveredTimestamp,
                                    long readTimestamp, int isReply, String replyToMsgId) {

        if (webSocketClient != null && webSocketClient.isOpen()) {

            JSONObject jsonObject = new JSONObject();
            try {
                jsonObject.put("message_id", messageId);
                jsonObject.put("sender_id", senderId);
                jsonObject.put("receiver_id", receiverId);
                jsonObject.put("message", message);
                jsonObject.put("signalMessageType", signalMessageType);
                jsonObject.put("message_type", messageType);
                jsonObject.put("message_status", messageStatus);
                jsonObject.put("is_edited", isEdited);
                jsonObject.put("latitude", latitude);
                jsonObject.put("longitude", longitude);
                jsonObject.put("sent_timestamp", sentTimestamp);
                jsonObject.put("delivered_timestamp", deliveredTimestamp);
                jsonObject.put("read_timestamp", readTimestamp);
                jsonObject.put("is_reply", isReply);
                jsonObject.put("reply_to_message_id", replyToMsgId);
                jsonObject.put("method","insert");

            } catch (JSONException e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to send message to server due to failed in " +
                        "encryption or json encryption " + e.getMessage());
            }

            if (jsonObject.length() > 0) {
                webSocketClient.send(jsonObject.toString());
                Log.e(Extras.LOG_MESSAGE,"message sent to server " + jsonObject);
            }
        }
        else {
            Log.e(Extras.LOG_MESSAGE,"No websocket client opened");
        }

    }

    public void senderIsTyping(WebSocketClient webSocketClient, String senderId, String receiverId,
                               boolean isTyping, String method) {
        if (webSocketClient != null && webSocketClient.isOpen()) {

            JSONObject jsonObject = new JSONObject();

            try {
                jsonObject.put("sender_id", senderId);
                jsonObject.put("receiver_id", receiverId);
                jsonObject.put("is_typing", isTyping);
                jsonObject.put("method",method);
            }
            catch (JSONException e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to send typing status " + e.getMessage());
            }

            if (jsonObject.length() > 0) {
                webSocketClient.send(jsonObject.toString());
            }
        }
    }
}
