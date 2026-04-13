package com.jippytalk.Encryption;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.util.Log;

import com.jippytalk.API;
import com.jippytalk.Database.ContactsDatabase.Repository.ContactsRepository;
import com.jippytalk.Extras;
import com.jippytalk.TokenRefreshAPI;
import com.jippytalk.UpdatedSignalProtocolStore;
import com.jippytalk.UserDetailsRepository;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;

import java.net.HttpURLConnection;

/**
 * DecryptionFailScenario - Handles the scenario when message decryption fails
 * due to changed or mismatched Signal Protocol keys.
 *
 * When decryption fails (e.g. InvalidMessageException), this class retrieves
 * fresh contact keys from the server and updates the local contacts database
 * so a new session can be established.
 *
 * Initialized in APIServiceLocator.setAppServiceLocator() and used by
 * ContactSessionRebuild and MessageEncryptAndDecrypt.
 */
public class DecryptionFailScenario {

    private final ExecutorService               executorService;
    private final UserDetailsRepository         userDetailsRepository;
    private final ContactsRepository            contactsRepository;
    private final TokenRefreshAPI               tokenRefreshAPI;
    private final UpdatedSignalProtocolStore    signalProtocolStore;
    private int                                 tokenAttempt        =   3;

    public DecryptionFailScenario(ExecutorService executorService,
                                  UserDetailsRepository userDetailsRepository,
                                  ContactsRepository contactsRepository,
                                  TokenRefreshAPI tokenRefreshAPI,
                                  UpdatedSignalProtocolStore signalProtocolStore) {
        this.executorService            =   executorService;
        this.userDetailsRepository      =   userDetailsRepository;
        this.contactsRepository         =   contactsRepository;
        this.tokenRefreshAPI            =   tokenRefreshAPI;
        this.signalProtocolStore        =   signalProtocolStore;
    }

    // -------------------- Key Retrieval On Keys Change Starts Here ---------------------

    /**
     * Retrieves fresh Signal Protocol keys for a contact from the server
     * when their keys have changed (e.g. after reinstall or identity change).
     *
     * Updates the local contacts database with the new keys so that a
     * new session can be built.
     *
     * @param contactId the ID of the contact whose keys need refreshing
     * @param callback  callback to notify success or failure
     */
    public void retrieveContactSignalKeysOnKeysChange(String contactId, RetrieveKeysCallback callback) {
        String  userId      =   userDetailsRepository.retrieveUserId();
        String  jwtToken    =   userDetailsRepository.retrieveJwtToken();

        executorService.execute(() -> {
            try {
                JSONObject  jsonObject  =   new JSONObject();
                jsonObject.put("userId", userId);
                jsonObject.put("contactId", contactId);

                URL url =   new URL(API.CONTACT_ONE_TIME_PRE_KEY);
                HttpURLConnection httpsURLConnection   =   (HttpURLConnection) url.openConnection();
                httpsURLConnection.setRequestMethod("POST");
                httpsURLConnection.setDoOutput(true);
                httpsURLConnection.setRequestProperty("Content-Type", "application/json");
                httpsURLConnection.setRequestProperty("Authorization", "Bearer " + jwtToken);
                httpsURLConnection.setReadTimeout(20000);
                httpsURLConnection.setConnectTimeout(20000);
                httpsURLConnection.connect();

                OutputStream outputStream   =   new BufferedOutputStream(httpsURLConnection.getOutputStream());
                outputStream.write(jsonObject.toString().getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
                outputStream.close();

                int statusCode  =   httpsURLConnection.getResponseCode();

                if (statusCode == 401) {
                    // JWT token expired, attempt refresh
                    handleTokenRefreshAndRetry(contactId, callback);
                    return;
                }

                StringBuilder   sb              =   new StringBuilder();
                BufferedReader  bufferedReader   =   new BufferedReader(
                        new InputStreamReader(httpsURLConnection.getInputStream()));
                String          line;

                while ((line = bufferedReader.readLine()) != null) {
                    sb.append(line).append("\n");
                }

                handleKeysResponse(sb.toString(), contactId, callback);

            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Failed to retrieve contact keys on change " + e.getMessage());
                if (callback != null) {
                    callback.onResult(false);
                }
            }
        });
    }

    /**
     * Parses the server response containing the contact's updated Signal Protocol keys
     * and updates the local contacts database.
     *
     * @param response   the server response JSON string
     * @param contactId  the contact whose keys were retrieved
     * @param callback   callback to notify success or failure
     */
    private void handleKeysResponse(String response, String contactId, RetrieveKeysCallback callback) {
        if (response == null) {
            Log.e(Extras.LOG_MESSAGE, "Null response when retrieving contact keys");
            if (callback != null) {
                callback.onResult(false);
            }
            return;
        }

        try {
            JSONObject  jsonObject          =   new JSONObject(response);
            String      status              =   jsonObject.getString("status");

            if ("success".equals(status)) {
                int     registrationId      =   jsonObject.optInt("registrationId", 0);
                int     deviceId            =   jsonObject.optInt("deviceId", 1);
                String  contactPublicKey    =   jsonObject.optString("identityKey", "");
                int     signedPreKeyId      =   jsonObject.optInt("signedPreKeyId", 0);
                String  signedPrePublicKey  =   jsonObject.optString("signedPreKey", "");
                String  signature           =   jsonObject.optString("signedPreKeySignature", "");

                // Update contact keys in the local database
                contactsRepository.updateContactKeys(contactId, registrationId, deviceId,
                        contactPublicKey, signedPreKeyId, signedPrePublicKey, signature);

                // Delete the old session so a new one will be created
                signalProtocolStore.deleteAllSessions(contactId);

                Log.e(Extras.LOG_MESSAGE, "Contact keys updated successfully for " + contactId);

                if (callback != null) {
                    callback.onResult(true);
                }
            }
            else {
                Log.e(Extras.LOG_MESSAGE, "Failed status when retrieving contact keys");
                if (callback != null) {
                    callback.onResult(false);
                }
            }
        }
        catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE, "JSON exception parsing contact keys response " + e.getMessage());
            if (callback != null) {
                callback.onResult(false);
            }
        }
    }

    /**
     * Handles JWT token refresh and retries the key retrieval.
     *
     * @param contactId the contact whose keys are being retrieved
     * @param callback  callback to notify success or failure
     */
    private void handleTokenRefreshAndRetry(String contactId, RetrieveKeysCallback callback) {
        if (tokenAttempt > 0) {
            tokenAttempt--;
            try {
                String      userId              =   userDetailsRepository.retrieveUserId();
                JSONObject  tokenRefreshObject  =   new JSONObject();
                tokenRefreshObject.put("userId", userId);
                tokenRefreshAPI.refreshUserJwtToken(tokenRefreshObject, (isSuccess, newJwtToken) -> {
                    if (isSuccess) {
                        retrieveContactSignalKeysOnKeysChange(contactId, callback);
                    }
                    else {
                        Log.e(Extras.LOG_MESSAGE, "Failed to refresh JWT token during key retrieval");
                        if (callback != null) {
                            callback.onResult(false);
                        }
                    }
                });
            }
            catch (JSONException e) {
                Log.e(Extras.LOG_MESSAGE, "JSON exception refreshing token " + e.getMessage());
                if (callback != null) {
                    callback.onResult(false);
                }
            }
        }
        else {
            Log.e(Extras.LOG_MESSAGE, "Max token refresh attempts reached");
            if (callback != null) {
                callback.onResult(false);
            }
        }
    }

    // -------------------- Callback Interface ---------------------

    /**
     * Callback interface for notifying the result of contact key retrieval.
     */
    public interface RetrieveKeysCallback {
        void onResult(boolean isSuccess);
    }
}
