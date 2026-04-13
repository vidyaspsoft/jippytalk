package com.jippytalk.Messages.Datahandlers;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.jippytalk.API;
import com.jippytalk.Common.SingleLiveEvent;
import com.jippytalk.Extras;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.net.HttpURLConnection;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HandleMessagesNetworkingAPI {


    private static volatile HandleMessagesNetworkingAPI handleMessagesNetworkingAPI;
    private final ExecutorService                       executorService;
    private final SingleLiveEvent<Integer>              contactBusyStatus       =   new SingleLiveEvent<>();
    private final SingleLiveEvent<Integer>              contactLastVisitTime    =   new SingleLiveEvent<>();


    public LiveData<Integer> getContactBusyStatusLiveDataFromAPI() {
        return contactBusyStatus;
    }

    public HandleMessagesNetworkingAPI(ExecutorService executorService) {
        this.executorService        =   executorService;
    }


    public void getUserStatus(String contactId) {

        executorService.execute(() -> {

            try {

                String data  = URLEncoder.encode("user_id", "UTF-8") + "=" + URLEncoder.encode(contactId, "UTF-8");
                URL url = new URL(API.GET_USER_STATUS);
                HttpURLConnection httpsURLConnection = (HttpURLConnection) url.openConnection();
                httpsURLConnection.setRequestMethod("POST");
                httpsURLConnection.setDoOutput(true);
                httpsURLConnection.connect();

                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(httpsURLConnection.getOutputStream());
                outputStreamWriter.write(data);
                outputStreamWriter.flush();
                StringBuilder sb = new StringBuilder();
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpsURLConnection.getInputStream()));
                String line = "";

                while ((line = bufferedReader.readLine()) != null) {

                    sb.append(line).append("\n");
                }
                handleGetUserStatusResponse(sb.toString(), contactId);
            }
            catch (IOException e) {
                Log.e(Extras.LOG_MESSAGE,"unable to get user account status "+e.getMessage());
                contactBusyStatus.postValue(-1);
            }
        });
    }

    private void handleGetUserStatusResponse(String response, String contactId) {
        if (response    ==  null) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get the contact current status");
            contactBusyStatus.postValue(-1);
            return;
        }

        try {
            JSONObject  jsonObject  =   new JSONObject(response);
            String      status      =   jsonObject.getString("status");
            if (status.equals("success")) {
                JSONArray jsonArray  = jsonObject.getJSONArray("details");
                for(int i = 0; i <jsonArray.length(); i++) {
                    JSONObject  jsonObject1 = jsonArray.getJSONObject(i);
                    jsonObject1.getInt("user_status");
                    contactBusyStatus.postValue(jsonObject1.getInt("user_status"));
                }
            }
            else if (status.equals("Fail")) {
                contactBusyStatus.postValue(-1);
            }
        }
        catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE,"unable to get user account status "+e.getMessage());
        }
    }

    public void getLastVisitTimestamp(JSONObject jsonObject, String jwtToken)
    {
        executorService.execute(() -> {

            try {

//                String data  = URLEncoder.encode("requester_id", "UTF-8") + "=" + URLEncoder.encode(userId, "UTF-8");
//                data += "&" + URLEncoder.encode("approver_id", "UTF-8") + "=" + URLEncoder.encode(contactId, "UTF-8");

                URL url = new URL(API.GET_LAST_VISIT_TIMESTAMP);
                HttpURLConnection httpsURLConnection = (HttpURLConnection) url.openConnection();
                httpsURLConnection.setRequestMethod("POST");
                httpsURLConnection.setDoOutput(true);
                httpsURLConnection.connect();

                try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(httpsURLConnection.getOutputStream())) {
                    outputStreamWriter.write(jsonObject.toString());
                    outputStreamWriter.flush();
                }

                StringBuilder sb = new StringBuilder();
                try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpsURLConnection.getInputStream()))) {
                    String line;
                    while((line = bufferedReader.readLine())!= null) {
                        sb.append(line).append("\n");
                    }
                }
                handleGetLastVisitTimestampResponse(sb.toString());

            } catch (IOException e) {
                Log.e(Extras.LOG_MESSAGE,"unable to get contact last visit timestamp "+e.getMessage());
            }
        });
    }

    private void handleGetLastVisitTimestampResponse(String response) {
        if (response == null) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get last visit timestamp due to response null");
            return;
        }

        try
        {
            JSONObject  jsonObject      =   new JSONObject(response);
            String      status          =   jsonObject.getString("status");
            JSONArray   jsonArray       =   jsonObject.getJSONArray("details");

            if (status.equals("success")) {

                for (int i = 0; i < jsonArray.length(); i++) {
                    JSONObject  jsonObject1         =   jsonArray.getJSONObject(i);
                    long        lastVisitTimestamp  =   jsonObject1.getLong("last_visit_timestamp");
                }

            }
        }
        catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE, "Error in retrieving profile pic from server "+e.getMessage());
        }
    }

    private String getTime(long timestamp)
    {
        try
        {
            Date date                        =   new Date(timestamp);
            SimpleDateFormat simpleDateFormat            =   new SimpleDateFormat("h:mm a", Locale.getDefault());
            return simpleDateFormat.format(date);
        }
        catch (Exception e)
        {
            Log.e("time_error","error in converting the time "+e.getMessage());
            return "date";
        }
    }

    public interface OnMessageNetworkingCallBack {
        void sendContactCurrentStatus(String status);
    }
}
