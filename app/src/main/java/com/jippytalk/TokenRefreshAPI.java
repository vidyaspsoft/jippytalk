package com.jippytalk;


import android.util.Log;

import com.jippytalk.API;
import com.jippytalk.Extras;
import com.jippytalk.UserDetailsRepository;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import java.net.HttpURLConnection;

public class TokenRefreshAPI {

    private final ExecutorService   executorService     =   Executors.newSingleThreadExecutor();
    private final                   UserDetailsRepository   userDetailsRepository;

    public TokenRefreshAPI(UserDetailsRepository userDetailsRepository) {
        this.userDetailsRepository      =   userDetailsRepository;
    }


    public void refreshUserJwtToken(JSONObject jsonObject, TokenRefreshCallBacks tokenRefreshCallBacks) {
        executorService.execute(() -> {
            HttpURLConnection httpsURLConnection = null;
            try {

                URL url = new URL(API.JWT_TOKEN_REFRESH_URL);
                httpsURLConnection = (HttpURLConnection) url.openConnection();
                httpsURLConnection.setRequestMethod("POST");
                httpsURLConnection.setDoOutput(true);
                httpsURLConnection.setRequestProperty("Content-Type", "application/json");

                httpsURLConnection.setConnectTimeout(8000);
                httpsURLConnection.setReadTimeout(80000);
                httpsURLConnection.connect();

                try (OutputStreamWriter outputStreamWriter = new OutputStreamWriter(httpsURLConnection.getOutputStream())) {
                    outputStreamWriter.write(jsonObject.toString());
                    outputStreamWriter.flush();
                }

                int statusCode = httpsURLConnection.getResponseCode();
                InputStream inputStream;

                if (statusCode >= 200 && statusCode < 300) {
                    inputStream = httpsURLConnection.getInputStream();
                } else {
                    inputStream = httpsURLConnection.getErrorStream(); // This contains FastAPI's error JSON
                }

                Log.e(Extras.LOG_MESSAGE, jsonObject.toString());

                if (inputStream != null) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                    StringBuilder response = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                    reader.close();
                    handleUploadAboutResponse(response.toString(), tokenRefreshCallBacks);
                    System.out.println("Server Response: " + response);
                } else {
                    System.out.println("No response body");
                }
            } catch (IOException e) {
                Log.e(Extras.LOG_MESSAGE, "IO Exception occurred during refresh token " + e.getMessage(), e);
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Exception occurred during refresh token " + e.getMessage());
            } finally {
                if (httpsURLConnection != null) {
                    httpsURLConnection.disconnect();
                }
            }
        });
    }

    private void handleUploadAboutResponse(String response, TokenRefreshCallBacks tokenRefreshCallBacks) {
        if (response == null) {
            return;
        }
        try {
            JSONObject  jsonObject  =   new JSONObject(response);
            String      status      =   jsonObject.getString("message");
            String      jwtToken    =   jsonObject.getString("token");

            Log.e(Extras.LOG_MESSAGE,"response is " + response);
            if (status.equals("Token Refreshed")) {
                userDetailsRepository.storeJWTTokenInPrefs(jwtToken);
                tokenRefreshCallBacks.onTokenRefresh(true, jwtToken);
            }
            else {
                tokenRefreshCallBacks.onTokenRefresh(false,"");
            }

        } catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE,"Caught exception when getting response from refresh token " + e.getMessage() + " " + response);
            tokenRefreshCallBacks.onTokenRefresh(false,"");
        }
    }

    public interface TokenRefreshCallBacks {
        void onTokenRefresh(boolean isSuccess, String jwtToken);
    }
}
