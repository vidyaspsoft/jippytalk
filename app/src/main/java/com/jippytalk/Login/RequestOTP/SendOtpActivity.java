package com.jippytalk.Login.RequestOTP;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;
import com.jippytalk.API;
import com.jippytalk.Chats.MainActivity;
import com.jippytalk.Common.ApiLogger;
import com.jippytalk.Extras;
import com.jippytalk.Managers.AccountManager;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.MyApplication;
import com.jippytalk.R;
import com.jippytalk.WebSocketConnection;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SendOtpActivity - Login screen for the application.
 * Dev-mode picker: the user selects one of a fixed list of test accounts
 * from a dropdown, and the app authenticates with a shared hard-coded
 * password. No free-form credential input.
 *
 * On successful login:
 * - Saves userId and JWT token to SharedPreferences
 * - Sets registration progress to REGISTRATION_DONE
 * - Navigates to MainActivity
 * - Connects WebSocket with the JWT token
 */
public class SendOtpActivity extends AppCompatActivity {

    // ---- Views ----

    private TextInputLayout             tilUser;
    private AutoCompleteTextView        dropdownUser;
    private MaterialButton              btnLogin;
    private ProgressBar                 progressBar;
    private TextView                    tvError;

    // ---- Fields ----

    private final ExecutorService       executorService     =   Executors.newSingleThreadExecutor();

    // Hard-coded test users — same password for all. The login screen is
    // a dev-mode picker right now, not a real credential form.
    private static final String[]       TEST_USERS          =   {
            "alice", "bob", "charlie", "diana", "eve",
            "frank", "grace", "heidi", "ivan", "judy"
    };
    private static final String         FIXED_PASSWORD      =   "password123";

    // ---- Lifecycle ----

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initViews();
        setListeners();
    }

    // -------------------- View Initialization Starts Here ---------------------

    /**
     * Initializes all view references from the layout and wires up the
     * user dropdown with the hard-coded test users.
     */
    private void initViews() {
        tilUser          =   findViewById(R.id.tilUser);
        dropdownUser     =   findViewById(R.id.dropdownUser);
        btnLogin         =   findViewById(R.id.btnLogin);
        progressBar      =   findViewById(R.id.progressBar);
        tvError          =   findViewById(R.id.tvError);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_list_item_1, TEST_USERS);
        dropdownUser.setAdapter(adapter);
        dropdownUser.setOnItemClickListener((parent, view, position, id) -> {
            tilUser.setError(null);
            tvError.setVisibility(View.GONE);
        });
    }

    // -------------------- Listener Setup Starts Here ---------------------

    /**
     * Sets click listeners for interactive elements.
     */
    private void setListeners() {
        btnLogin.setOnClickListener(v -> attemptLogin());
    }

    // -------------------- Login Logic Starts Here ---------------------

    /**
     * Validates the dropdown selection and initiates the login API call.
     * Password is fixed — every test user authenticates with the same one.
     */
    private void attemptLogin() {
        tilUser.setError(null);
        tvError.setVisibility(View.GONE);

        String  username    =   dropdownUser.getText() != null
                                ? dropdownUser.getText().toString().trim()
                                : "";

        if (username.isEmpty()) {
            tilUser.setError(getString(R.string.user_required));
            return;
        }

        showLoading(true);
        callLoginAPI(username, FIXED_PASSWORD);
    }

    /**
     * Calls the login API endpoint with the provided credentials.
     * Runs on a background thread using ExecutorService.
     *
     * @param username the user's username
     * @param password the user's password
     */
    private void callLoginAPI(String username, String password) {
        executorService.execute(() -> {
            HttpURLConnection   httpURLConnection    =   null;
            JSONObject          requestBody          =   new JSONObject();
            long                startTime            =   0;
            try {
                requestBody.put("username", username);
                requestBody.put("password", password);

                startTime   =   ApiLogger.logRequest("POST", API.LOGIN_URL, requestBody.toString(), null);

                URL                 url             =   new URL(API.LOGIN_URL);
                httpURLConnection                   =   (HttpURLConnection) url.openConnection();
                httpURLConnection.setRequestMethod("POST");
                httpURLConnection.setDoOutput(true);
                httpURLConnection.setRequestProperty("Content-Type", "application/json");
                httpURLConnection.setConnectTimeout(15000);
                httpURLConnection.setReadTimeout(15000);
                httpURLConnection.connect();

                try (OutputStream outputStream = httpURLConnection.getOutputStream()) {
                    outputStream.write(requestBody.toString().getBytes(StandardCharsets.UTF_8));
                    outputStream.flush();
                }

                int statusCode  =   httpURLConnection.getResponseCode();

                if (statusCode == 200) {
                    StringBuilder   response        =   new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(httpURLConnection.getInputStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                    }
                    ApiLogger.logResponse("POST", API.LOGIN_URL, statusCode, response.toString(), startTime);
                    handleLoginSuccess(response.toString(), username);
                }
                else {
                    StringBuilder   errorResponse   =   new StringBuilder();
                    if (httpURLConnection.getErrorStream() != null) {
                        try (BufferedReader reader = new BufferedReader(
                                new InputStreamReader(httpURLConnection.getErrorStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                errorResponse.append(line);
                            }
                        }
                    }
                    ApiLogger.logResponse("POST", API.LOGIN_URL, statusCode, errorResponse.toString(), startTime);
                    runOnUiThread(() -> {
                        showLoading(false);
                        tvError.setText(R.string.login_failed);
                        tvError.setVisibility(View.VISIBLE);
                    });
                }
            }
            catch (Exception e) {
                ApiLogger.logError("POST", API.LOGIN_URL, e.getMessage(), startTime);
                runOnUiThread(() -> {
                    showLoading(false);
                    tvError.setText(R.string.login_network_error);
                    tvError.setVisibility(View.VISIBLE);
                });
            }
            finally {
                if (httpURLConnection != null) {
                    httpURLConnection.disconnect();
                }
            }
        });
    }

    // -------------------- Response Handling Starts Here ---------------------

    /**
     * Handles a successful login response from the server.
     * Parses the response JSON, saves user details to SharedPreferences,
     * and navigates to the main chat screen.
     *
     * Expected response: { "token": "jwt_token", "user_id": "uuid" }
     *
     * @param response  the raw JSON response string
     * @param username  the username that was used for login
     */
    private void handleLoginSuccess(String response, String username) {
        try {
            JSONObject  jsonResponse    =   new JSONObject(response);
            String      jwtToken        =   jsonResponse.getString("token");
            String      userId          =   jsonResponse.getString("user_id");

            Log.e(Extras.LOG_MESSAGE, "Login successful for userId: " + userId);

            saveUserDetailsToPreferences(userId, username, jwtToken);

            // Keep the loading indicator visible until the WebSocket finishes connecting
            runOnUiThread(this::connectWebSocketAndNavigate);
        }
        catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to parse login response: " + e.getMessage());
            runOnUiThread(() -> {
                showLoading(false);
                tvError.setText(R.string.login_failed);
                tvError.setVisibility(View.VISIBLE);
            });
        }
    }

    /**
     * Saves user authentication details to SharedPreferences.
     * Sets registration progress to REGISTRATION_DONE so the app
     * routes directly to MainActivity on next launch.
     *
     * @param userId    the user's unique ID from the server
     * @param username  the user's username
     * @param jwtToken  the JWT token for authenticated API calls
     */
    private void saveUserDetailsToPreferences(String userId, String username, String jwtToken) {
        SharedPreferences.Editor    editor  =   getSharedPreferences(
                SharedPreferenceDetails.SHARED_PREFERENCE_NAME, MODE_PRIVATE).edit();
        editor.putString(SharedPreferenceDetails.USERID, userId);
        editor.putString(SharedPreferenceDetails.USERNAME, username);
        editor.putString(SharedPreferenceDetails.JWT_TOKEN, jwtToken);
        editor.putInt(SharedPreferenceDetails.REGISTRATION_PROGRESS, AccountManager.REGISTRATION_DONE);
        editor.putInt(SharedPreferenceDetails.ACCOUNT_STATUS, AccountManager.ACCOUNT_ACTIVE);
        editor.apply();

        Log.e(Extras.LOG_MESSAGE, "User details saved to SharedPreferences");
    }

    // -------------------- Navigation Starts Here ---------------------

    /**
     * Connects the WebSocket and waits for successful connection before navigating
     * to the main chat screen. Registers a one-shot ConnectionStateListener so that
     * navigation only happens after the socket handshake completes.
     *
     * If the connection fails, shows an error and allows retry.
     */
    private void connectWebSocketAndNavigate() {
        try {
            MyApplication   myApplication   =   MyApplication.getInstance();
            if (myApplication == null || myApplication.getAppServiceLocator() == null) {
                Log.e(Extras.LOG_MESSAGE, "App not initialized, cannot connect WebSocket");
                showConnectionError();
                return;
            }

            WebSocketConnection webSocketConnection =   myApplication.getAppServiceLocator().getWebSocketConnection();
            if (webSocketConnection == null) {
                Log.e(Extras.LOG_MESSAGE, "WebSocketConnection is null");
                showConnectionError();
                return;
            }

            // Register a one-shot listener — navigate when socket connects
            webSocketConnection.setConnectionStateListener(new WebSocketConnection.ConnectionStateListener() {
                @Override
                public void onConnected() {
                    Log.e(Extras.LOG_MESSAGE, "WebSocket connected after login, navigating to chat screen");
                    // Clear the listener so it doesn't fire again on reconnects
                    if (myApplication.getAppServiceLocator() != null
                            && myApplication.getAppServiceLocator().getWebSocketConnection() != null) {
                        myApplication.getAppServiceLocator().getWebSocketConnection().setConnectionStateListener(null);
                    }
                    navigateToMainActivity();
                }

                @Override
                public void onDisconnected() {
                    Log.e(Extras.LOG_MESSAGE, "WebSocket disconnected during login");
                }

                @Override
                public void onConnectionFailed(String error) {
                    Log.e(Extras.LOG_MESSAGE, "WebSocket connection failed: " + error);
                    if (myApplication.getAppServiceLocator() != null
                            && myApplication.getAppServiceLocator().getWebSocketConnection() != null) {
                        myApplication.getAppServiceLocator().getWebSocketConnection().setConnectionStateListener(null);
                    }
                    showConnectionError();
                }
            });

            webSocketConnection.connectToWebSocket();
            Log.e(Extras.LOG_MESSAGE, "WebSocket connection initiated after login, waiting for handshake");

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to connect WebSocket after login: " + e.getMessage());
            showConnectionError();
        }
    }

    /**
     * Navigates to the main chat activity after successful login + WebSocket connection.
     */
    private void navigateToMainActivity() {
        Intent  intent  =   new Intent(SendOtpActivity.this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Shows a connection error on the login screen and re-enables the login button.
     */
    private void showConnectionError() {
        runOnUiThread(() -> {
            showLoading(false);
            tvError.setText(R.string.login_network_error);
            tvError.setVisibility(View.VISIBLE);
        });
    }

    // -------------------- UI Helper Methods Starts Here ---------------------

    /**
     * Toggles the loading state of the login screen.
     * Shows/hides the progress bar and enables/disables the login button.
     *
     * @param isLoading true to show loading state, false to hide
     */
    private void showLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            btnLogin.setVisibility(View.INVISIBLE);
            dropdownUser.setEnabled(false);
        }
        else {
            progressBar.setVisibility(View.GONE);
            btnLogin.setVisibility(View.VISIBLE);
            dropdownUser.setEnabled(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}
