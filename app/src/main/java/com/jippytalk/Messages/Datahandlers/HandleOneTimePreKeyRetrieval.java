package com.jippytalk.Messages.Datahandlers;

import android.content.Context;
import android.util.Log;

import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.work.ListenableWorker;

import com.jippytalk.API;
import com.jippytalk.TokenRefreshAPI;
import com.jippytalk.Extras;
import com.jippytalk.Sessions.SessionCreation;
import com.jippytalk.UserDetailsRepository;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HandleOneTimePreKeyRetrieval {

    private final Context                       context;
    private final HandleOneTimePreKeyCallback   handleOneTimePreKeyCallback;
    private final UserDetailsRepository         userDetailsRepository;
    private final TokenRefreshAPI               tokenRefreshAPI;
    private int                                 tokenAttempt        =   3;
    private final ExecutorService               executorService     = Executors.newSingleThreadExecutor();

    public HandleOneTimePreKeyRetrieval(Context context, UserDetailsRepository userDetailsRepository,
                                        TokenRefreshAPI tokenRefreshAPI, HandleOneTimePreKeyCallback handleOneTimePreKeyCallback) {
        this.context                        =   context;
        this.userDetailsRepository          =   userDetailsRepository;
        this.tokenRefreshAPI                =   tokenRefreshAPI;
        this.handleOneTimePreKeyCallback    =   handleOneTimePreKeyCallback;
    }


    public void getContactOneTimePreKey(String contactId) {
        String userId       =   userDetailsRepository.retrieveUserId();
        String jwtToken     =   userDetailsRepository.retrieveJwtToken();
        executorService.execute(() -> {
            try {
                JSONObject jsonObject  =   new JSONObject();
                jsonObject.put("userId", userId);
                jsonObject.put("contactId", contactId);

                URL url     =   new URL(API.CONTACT_ONE_TIME_PRE_KEY);
                HttpURLConnection httpsURLConnection = (HttpURLConnection) url.openConnection();
                httpsURLConnection.setRequestMethod("POST");
                httpsURLConnection.setDoOutput(true);
                httpsURLConnection.setRequestProperty("Content-Type", "application/json");
                httpsURLConnection.setRequestProperty("Authorization", "Bearer " + jwtToken);
                httpsURLConnection.setReadTimeout(20000);
                httpsURLConnection.setConnectTimeout(20000);
                httpsURLConnection.connect();

                OutputStream outputStream       =   new BufferedOutputStream(httpsURLConnection.getOutputStream());
                outputStream.write(jsonObject.toString().getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
                outputStream.close();

                int statusCode  =   httpsURLConnection.getResponseCode();

                Log.e(Extras.LOG_MESSAGE,"status code for OT pre keys is " + statusCode);

                if (statusCode == 401) {
                    refreshJwtToken(userId, contactId);
                }

                StringBuilder   sb              =   new StringBuilder();
                BufferedReader bufferedReader  =   new BufferedReader(new InputStreamReader(httpsURLConnection.getInputStream()));
                String          line            =   "";

                while ((line = bufferedReader.readLine()) != null) {
                    sb.append(line).append("\n");
                }

                handleOneTimePreKeyResponse(sb.toString(), contactId);

            } catch (JSONException | MalformedURLException e) {
                Log.e(Extras.LOG_MESSAGE," URL Except Unable to retrieve contact one time pre key " + e.getMessage());
            } catch (IOException e) {
                Log.e(Extras.LOG_MESSAGE,"Io exception Unable to retrieve contact one time pre key " + e.getMessage());
            }
        });
    }

    private void handleOneTimePreKeyResponse(String response, String contactId) {
        if (response == null) {
            Log.e(Extras.LOG_MESSAGE,"Unable to retrieve contact one time pre key response is null ");
            return;
        }
        Log.e(Extras.LOG_MESSAGE, "response is " + response);
        try {
            JSONObject  jsonObject      =   new JSONObject(response);
            String      status          =   jsonObject.getString("status");
            String      oneTimePreKey   =   jsonObject.getString("oneTimePreKey");
            int         oneTimePreKeyID =   jsonObject.getInt("oneTimePreKeyId");
            int         kyberPreKeyId   =   jsonObject.getInt("kyberPreKeyId");
            String      kyberPublicKey  =   jsonObject.getString("kyberPreKey");
            String      kyberSignature  =   jsonObject.getString("kyberSignature");

            Log.e(Extras.LOG_MESSAGE,"status is " + status);
            switch (status) {
                case "success" ->
                {
                    SessionCreation sessionCreation     =   new SessionCreation(context,
                            handleOneTimePreKeyCallback::onRetrievePreKeyAndCreatedSession);

                    Log.e("PreKeyBundle", "Keys are " + kyberPreKeyId + " " + kyberPublicKey + " " + kyberSignature);
                    sessionCreation.getContactKeysData(contactId, oneTimePreKeyID, oneTimePreKey, kyberPreKeyId, kyberPublicKey,
                            kyberSignature);
                }
                case "failed" -> Log.e(Extras.LOG_MESSAGE,"Unable to retrieve contact one time pre key failed ");
            }

        } catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE,"JSON except Unable to retrieve contact one time pre key " + e.getMessage());
        }
    }


    private void refreshJwtToken(String userId, String contactId) {
        if (tokenAttempt != 0) {
            tokenAttempt--;
            try {
                JSONObject tokenRefreshObject   =   new JSONObject();
                tokenRefreshObject.put("userId", userId);
                tokenRefreshAPI.refreshUserJwtToken(tokenRefreshObject, (isSuccess, newJwtToken) -> {
                    if (isSuccess) {
                        getContactOneTimePreKey(contactId);
                    }
                    else {
                        Log.e(Extras.LOG_MESSAGE, "Failed to refresh JWT token");
                    }
                });
            } catch (JSONException e) {
                Log.e(Extras.LOG_MESSAGE, "JSON exception when refreshing token " + e.getMessage());
            }
        } else {
            Log.e(Extras.LOG_MESSAGE, "Failed to refresh JWT token");
        }
    }

    public interface HandleOneTimePreKeyCallback {
        void onRetrievePreKeyAndCreatedSession(String contactId, int deviceId);
    }
}
