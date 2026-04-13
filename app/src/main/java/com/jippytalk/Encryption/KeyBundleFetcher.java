package com.jippytalk.Encryption;

/**
 * Developer Name: Vidya Sagar
 * Created on: 10-04-2026
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.jippytalk.API;
import com.jippytalk.Common.ApiLogger;
import com.jippytalk.Database.ContactsDatabase.Repository.ContactsRepository;
import com.jippytalk.Extras;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.MyApplication;
import com.jippytalk.UpdatedSignalProtocolStore;

import org.json.JSONObject;
import org.signal.libsignal.protocol.IdentityKey;
import org.signal.libsignal.protocol.SessionBuilder;
import org.signal.libsignal.protocol.SignalProtocolAddress;
import org.signal.libsignal.protocol.ecc.ECPublicKey;
import org.signal.libsignal.protocol.kem.KEMPublicKey;
import org.signal.libsignal.protocol.state.PreKeyBundle;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * KeyBundleFetcher - Fetches a contact's key bundle from the server and builds a Signal session.
 *
 * Flow:
 *   1. GET /api/keys/bundle/{targetUserId} with Bearer JWT
 *   2. Parse the response into a PreKeyBundle (requires Kyber fields - libsignal 0.86.5)
 *   3. SessionBuilder.process(bundle) — auto-stores the session via UpdatedSignalProtocolStore
 *   4. Insert the contact into local ContactsDatabase so contactDeviceId lookup works
 *   5. Callback on main thread
 *
 * Expected server response format:
 * {
 *   "user_id": "<uuid>",
 *   "registration_id": 12345,
 *   "device_id": 1,
 *   "identity_key": "base64",
 *   "signed_prekey": {
 *     "key_id": 1,
 *     "public_key": "base64",
 *     "signature": "base64"
 *   },
 *   "one_time_prekey": {
 *     "key_id": 5,
 *     "public_key": "base64"
 *   },
 *   "kyber_prekey": {
 *     "key_id": 3,
 *     "public_key": "base64",
 *     "signature": "base64"
 *   }
 * }
 */
public class KeyBundleFetcher {

    // ---- Constants ----

    private static final int    CONNECT_TIMEOUT     =   15000;
    private static final int    READ_TIMEOUT        =   30000;

    // ---- Fields ----

    private static volatile KeyBundleFetcher    INSTANCE;
    private final Context                       context;
    private final SharedPreferences             sharedPreferences;
    private final ExecutorService               executorService     =   Executors.newSingleThreadExecutor();
    private final Handler                       mainHandler         =   new Handler(Looper.getMainLooper());

    // ---- Constructor ----

    private KeyBundleFetcher(Context context) {
        this.context            =   context.getApplicationContext();
        this.sharedPreferences  =   context.getApplicationContext().getSharedPreferences(
                                    SharedPreferenceDetails.SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    public static KeyBundleFetcher getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (KeyBundleFetcher.class) {
                if (INSTANCE == null) {
                    INSTANCE    =   new KeyBundleFetcher(context);
                }
            }
        }
        return INSTANCE;
    }

    // -------------------- Public Methods Starts Here ---------------------

    /**
     * Fetches a contact's key bundle, builds a Signal session, and inserts the contact
     * into the local ContactsDatabase. All network and crypto work happens on a background
     * thread; the callback is delivered on the main thread.
     *
     * @param targetUserId  the UUID of the contact to fetch keys for
     * @param contactName   optional display name for the contact (used when inserting into DB)
     * @param contactPhone  optional phone number for the contact (used when inserting into DB)
     * @param callback      callback with session creation result
     */
    public void fetchAndBuildSession(String targetUserId, String contactName, String contactPhone,
                                     SessionCallback callback) {
        executorService.execute(() -> {
            try {
                Log.e(Extras.LOG_MESSAGE, "═══════ Fetching key bundle for " + targetUserId + " ═══════");

                JSONObject bundleJson   =   fetchKeyBundle(targetUserId);
                if (bundleJson == null) {
                    postFailure(callback, "Failed to fetch key bundle");
                    return;
                }

                // Parse + build PreKeyBundle
                PreKeyBundle bundle     =   parseBundle(bundleJson);
                if (bundle == null) {
                    postFailure(callback, "Failed to parse key bundle");
                    return;
                }

                // Build and store the session (auto-persisted via UpdatedSignalProtocolStore)
                UpdatedSignalProtocolStore store    =   MyApplication.getInstance()
                        .getAppServiceLocator().getSignalProtocolStore();

                int deviceId                        =   bundleJson.optInt("device_id", 1);
                SignalProtocolAddress address       =   new SignalProtocolAddress(targetUserId, deviceId);
                SessionBuilder sessionBuilder       =   new SessionBuilder(store, address);
                sessionBuilder.process(bundle);

                Log.e(Extras.LOG_MESSAGE, "Session built successfully for " + targetUserId);

                // Persist the contact + their keys in the local ContactsDatabase
                insertContactLocally(targetUserId, contactName, contactPhone, bundleJson);

                Log.e(Extras.LOG_MESSAGE, "═══════ Session creation complete ═══════");

                final int finalDeviceId =   deviceId;
                mainHandler.post(() -> {
                    if (callback != null) callback.onSessionBuilt(targetUserId, finalDeviceId);
                });

            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Bundle fetch / session build failed: " + e.getMessage(), e);
                postFailure(callback, e.getMessage());
            }
        });
    }

    // -------------------- Network Fetch Starts Here ---------------------

    /**
     * GETs the key bundle from /api/keys/bundle/{targetUserId} with Bearer auth.
     *
     * @param targetUserId the target user's UUID
     * @return the parsed JSONObject response, or null on failure
     */
    private JSONObject fetchKeyBundle(String targetUserId) {
        HttpURLConnection   connection  =   null;
        String              jwtToken    =   sharedPreferences.getString(
                SharedPreferenceDetails.JWT_TOKEN, "");
        String              fullUrl     =   API.KEYS_BUNDLE + targetUserId;

        if (jwtToken.isEmpty()) {
            Log.e(Extras.LOG_MESSAGE, "Cannot fetch bundle: no JWT token");
            return null;
        }

        long startTime = ApiLogger.logRequest("GET", fullUrl, null, jwtToken);

        try {
            URL url     =   new URL(fullUrl);
            connection  =   (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.connect();

            int statusCode  =   connection.getResponseCode();

            if (statusCode >= 200 && statusCode < 300) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                ApiLogger.logResponse("GET", fullUrl, statusCode, response.toString(), startTime);
                return new JSONObject(response.toString());
            }

            StringBuilder errorResponse = new StringBuilder();
            if (connection.getErrorStream() != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                }
            }
            ApiLogger.logResponse("GET", fullUrl, statusCode, errorResponse.toString(), startTime);
            return null;

        } catch (Exception e) {
            ApiLogger.logError("GET", fullUrl, e.getMessage(), startTime);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // -------------------- Bundle Parsing Starts Here ---------------------

    /**
     * Parses the server response into a PreKeyBundle.
     * Requires identity_key, signed_prekey, one_time_prekey, and kyber_prekey fields.
     */
    private PreKeyBundle parseBundle(JSONObject json) {
        try {
            int     registrationId      =   json.optInt("registration_id", 0);
            int     deviceId            =   json.optInt("device_id", 1);

            // Identity key
            String      identityKeyB64  =   json.getString("identity_key");
            IdentityKey identityKey     =   new IdentityKey(
                    Base64.decode(identityKeyB64, Base64.NO_WRAP));

            // Signed pre-key
            JSONObject  signedObj       =   json.getJSONObject("signed_prekey");
            int         signedKeyId     =   signedObj.getInt("key_id");
            ECPublicKey signedPubKey    =   new ECPublicKey(Base64.decode(
                    signedObj.getString("public_key"), Base64.NO_WRAP));
            byte[]      signedKeySig    =   Base64.decode(
                    signedObj.getString("signature"), Base64.NO_WRAP);

            // One-time pre-key (optional)
            int         oneTimeKeyId    =   PreKeyBundle.NULL_PRE_KEY_ID;
            ECPublicKey oneTimePubKey   =   null;
            JSONObject  oneTimeObj      =   json.optJSONObject("one_time_prekey");
            if (oneTimeObj != null) {
                oneTimeKeyId            =   oneTimeObj.getInt("key_id");
                oneTimePubKey           =   new ECPublicKey(Base64.decode(
                        oneTimeObj.getString("public_key"), Base64.NO_WRAP));
            }

            // Kyber pre-key (required by libsignal 0.86.5)
            JSONObject  kyberObj        =   json.getJSONObject("kyber_prekey");
            int         kyberKeyId      =   kyberObj.getInt("key_id");
            KEMPublicKey kyberPubKey    =   new KEMPublicKey(Base64.decode(
                    kyberObj.getString("public_key"), Base64.NO_WRAP));
            byte[]      kyberSig        =   Base64.decode(
                    kyberObj.getString("signature"), Base64.NO_WRAP);

            return new PreKeyBundle(
                    registrationId,
                    deviceId,
                    oneTimeKeyId,
                    oneTimePubKey,
                    signedKeyId,
                    signedPubKey,
                    signedKeySig,
                    identityKey,
                    kyberKeyId,
                    kyberPubKey,
                    kyberSig
            );

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to parse key bundle: " + e.getMessage(), e);
            return null;
        }
    }

    // -------------------- Local Contact Insertion Starts Here ---------------------

    /**
     * Inserts or updates the contact in the local ContactsDatabase with their identity +
     * signed pre-key info. This is needed so that contactDeviceId lookup works when sending.
     */
    private void insertContactLocally(String targetUserId, String contactName, String contactPhone,
                                      JSONObject bundleJson) {
        try {
            ContactsRepository contactsRepo =   MyApplication.getInstance()
                    .getRepositoryServiceLocator().getContactsRepository();

            int         registrationId     =   bundleJson.optInt("registration_id", 0);
            int         deviceId            =   bundleJson.optInt("device_id", 1);
            String      identityKey         =   bundleJson.getString("identity_key");

            JSONObject  signedObj           =   bundleJson.getJSONObject("signed_prekey");
            int         signedKeyId         =   signedObj.getInt("key_id");
            String      signedPrePubKey     =   signedObj.getString("public_key");
            String      signature           =   signedObj.getString("signature");

            String      displayName         =   contactName != null ? contactName : targetUserId;
            String      displayPhone        =   contactPhone != null ? contactPhone : "";

            contactsRepo.updateContactAsUsers(
                    targetUserId,
                    displayPhone,
                    registrationId,
                    deviceId,
                    "",         // about
                    System.currentTimeMillis(),
                    "",         // profile pic id
                    identityKey,
                    signedKeyId,
                    signedPrePubKey,
                    signature
            );

            Log.e(Extras.LOG_MESSAGE, "Contact inserted locally: " + targetUserId);

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to insert contact locally: " + e.getMessage(), e);
        }
    }

    // -------------------- Helpers ---------------------

    private void postFailure(SessionCallback callback, String error) {
        mainHandler.post(() -> {
            if (callback != null) callback.onFailure(error);
        });
    }

    // -------------------- Callback Interface ---------------------

    /**
     * Callback for session creation result. All methods are called on the main thread.
     */
    public interface SessionCallback {
        /**
         * Called when the key bundle was fetched and a Signal session was successfully built.
         *
         * @param targetUserId  the contact's user ID
         * @param deviceId      the contact's device ID (needed for SessionCipher addressing)
         */
        void onSessionBuilt(String targetUserId, int deviceId);

        /**
         * Called when bundle fetch or session creation fails.
         *
         * @param error the error message
         */
        void onFailure(String error);
    }
}
