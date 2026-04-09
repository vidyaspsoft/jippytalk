package com.jippytalk.ServiceHandlers;

import android.util.Log;
import com.jippytalk.Extras;
import com.jippytalk.ServiceHandlers.Models.DeleteMessageDataInServerModel;

import org.java_websocket.client.WebSocketClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

public class SendDeleteRequestToServer {

    private final WebSocketClient   webSocketClient;

    public SendDeleteRequestToServer(WebSocketClient webSocketClient) {
        this.webSocketClient    =   webSocketClient;
    }

    public void deleteMessageInServerAfterDeliveredAndSeen(String messageId, String senderId, String receiverId) {
        if (webSocketClient.isOpen()) {
            JSONObject deleteMessage = new JSONObject();
            try {
                deleteMessage.put("message_id", messageId);
                deleteMessage.put("sender_id", senderId);
                deleteMessage.put("receiver_id", receiverId);
                deleteMessage.put("method","deleteMessageData");
            } catch (JSONException e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to delete message in server after delivered and seen "+e.getMessage());
            }

            if (deleteMessage.length() > 0) {
                webSocketClient.send(deleteMessage.toString());
            }
        }
        else
        {
            Log.e(Extras.LOG_MESSAGE,"Unable to delete message in server after delivered and seen");
        }
    }

    public void deleteMessageDataInServer(List<DeleteMessageDataInServerModel> deleteMessageDataInServerModels) {
        if (!webSocketClient.isOpen()) {
            Log.e(Extras.LOG_MESSAGE, "WebSocket is not open, cannot delete messages");
            return;
        }
        JSONArray deleteArray = new JSONArray();
        try {
            for (DeleteMessageDataInServerModel deleteMessageDataInServerModel : deleteMessageDataInServerModels) {
                JSONObject deleteMessage = new JSONObject();
                deleteMessage.put("message_id", deleteMessageDataInServerModel.getMessageId());
                deleteMessage.put("sender_id", deleteMessageDataInServerModel.getSenderId());
                deleteMessage.put("receiver_id", deleteMessageDataInServerModel.getReceiverId());
                deleteArray.put(deleteMessage);
            }

            if (deleteArray.length() > 0) {
                JSONObject batchDelete = new JSONObject();
                batchDelete.put("method", "deleteMessagesBatch");
                batchDelete.put("data", deleteArray);
                webSocketClient.send(batchDelete.toString());
            }

        } catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to delete messages in server after delivered and seen: " + e.getMessage());
        }
    }
}
