package com.jippytalk.ServiceHandlers;

import android.content.Context;
import android.util.Log;

import com.jippytalk.Extras;
import com.jippytalk.ServiceHandlers.Models.UnSyncedDeliveredMessagesModel;
import com.jippytalk.ServiceHandlers.Models.UnSyncedSeenMessagesModel;

import org.java_websocket.client.WebSocketClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class SendUpdatesToServer {

    private final Context           context;
    private final WebSocketClient   webSocketClient;

    public SendUpdatesToServer(Context context, WebSocketClient webSocketClient) {
        this.context            =   context;
        this.webSocketClient    =   webSocketClient;
    }

    public void updateReceivedMessageAsDelivered(ArrayList<UnSyncedDeliveredMessagesModel> unSyncedDeliveredMessagesModelArrayList) {
        if (webSocketClient.isOpen()) {
            JSONArray   jsonArray   =   new JSONArray();
            for (UnSyncedDeliveredMessagesModel unSyncedDeliveredMessagesModel
                    : unSyncedDeliveredMessagesModelArrayList) {
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("message_id", unSyncedDeliveredMessagesModel.getMessageId());
                    jsonObject.put("sender_id", unSyncedDeliveredMessagesModel.getSenderId());
                    jsonObject.put("receiver_id", unSyncedDeliveredMessagesModel.getReceiverId());
                    jsonObject.put("message_status", unSyncedDeliveredMessagesModel.getMessageStatus());
                    jsonObject.put("received_timestamp", unSyncedDeliveredMessagesModel.getDeliveredTimestamp());
                    jsonArray.put(jsonObject);
                }
                catch (JSONException e) {
                    Log.e(Extras.LOG_MESSAGE,"Unable to send message updates to server "+e.getMessage());
                }
            }

            if (jsonArray.length() > 0) {
                JSONObject finalPayload = new JSONObject();
                try {
                    finalPayload.put("method", "updateReceivedMessageAsDelivered");
                    finalPayload.put("data", jsonArray);
                    webSocketClient.send(finalPayload.toString());
                    Log.e(Extras.LOG_MESSAGE,"json array is " + jsonArray);
                } catch (JSONException e) {
                    Log.e(Extras.LOG_MESSAGE,"Unable to wrap final payload: "+e.getMessage());
                }
            }
        }
        else
        {
            Log.e(Extras.LOG_MESSAGE,"No websocket client opened for sending delivered or seen status");
        }
    }

    public void updateReceivedMessageAsDeliveredAndSeen(ArrayList<UnSyncedSeenMessagesModel> unSyncedSeenMessagesModelArrayList)
    {
        if (webSocketClient.isOpen())
        {
            JSONArray   jsonArray   =   new JSONArray();
            for (UnSyncedSeenMessagesModel unSyncedSeenMessagesModel
                    : unSyncedSeenMessagesModelArrayList)
            {
                JSONObject jsonObject = new JSONObject();
                try {
                    jsonObject.put("message_id", unSyncedSeenMessagesModel.getMessageId());
                    jsonObject.put("sender_id", unSyncedSeenMessagesModel.getSenderId());
                    jsonObject.put("receiver_id", unSyncedSeenMessagesModel.getReceiverId());
                    jsonObject.put("message_status", unSyncedSeenMessagesModel.getMessageStatus());
                    jsonObject.put("received_timestamp", unSyncedSeenMessagesModel.getDeliveredTimestamp());
                    jsonObject.put("read_timestamp", unSyncedSeenMessagesModel.getSeenTimestamp());
                    jsonArray.put(jsonObject);

                    Log.e(Extras.LOG_MESSAGE,"sending message status is " + unSyncedSeenMessagesModel.getMessageStatus());
                }
                catch (JSONException e)
                {
                    Log.e(Extras.LOG_MESSAGE,"Unable to send message updates to server "+e.getMessage());
                }
            }
            if (jsonArray.length() > 0) {
                JSONObject finalPayload = new JSONObject();
                try {
                    finalPayload.put("method", "updateReceivedMessageAsDeliveredAndSeen");
                    finalPayload.put("data", jsonArray);
                    webSocketClient.send(finalPayload.toString());
                } catch (JSONException e) {
                    Log.e(Extras.LOG_MESSAGE,"Unable to wrap final payload: "+e.getMessage());
                }
            }
        }
        else
        {
            Log.e(Extras.LOG_MESSAGE,"No websocket client opened for sending seen status");
        }
    }
}
