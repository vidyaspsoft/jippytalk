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
import com.jippytalk.CryptoKeys.KeyStoreHelper;
import com.jippytalk.Extras;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.Managers.SharedPreferenceManager.SharedPreferencesManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.signal.libsignal.protocol.IdentityKeyPair;
import org.signal.libsignal.protocol.ecc.ECKeyPair;
import org.signal.libsignal.protocol.kem.KEMKeyPair;
import org.signal.libsignal.protocol.kem.KEMKeyType;
import org.signal.libsignal.protocol.state.KyberPreKeyRecord;
import org.signal.libsignal.protocol.state.PreKeyRecord;
import org.signal.libsignal.protocol.state.SignedPreKeyRecord;
import org.signal.libsignal.protocol.util.KeyHelper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.jippytalk.Database.User.Repository.UserDatabaseRepository;

/**
 * SignalKeyManager - Generates the full set of Signal Protocol keys and uploads them to the server.
 *
 * Generates on first run (one-time per install):
 *   - 1 identity key pair (long-term)
 *   - 1 registration ID
 *   - 1 signed pre-key (rotated periodically on real apps)
 *   - 100 one-time pre-keys (consumed as contacts initiate sessions)
 *   - 100 Kyber pre-keys (post-quantum, required by libsignal 0.86.5)
 *
 * Private keys are encrypted via KeyStoreHelper (Android KeyStore) before being stored
 * in the local UsersDatabase (which is itself SQLCipher-encrypted).
 *
 * Public keys + signatures are uploaded via POST /api/keys/upload.
 *
 * Usage:
 *   SignalKeyManager.getInstance(context).generateAndUploadKeysIfNeeded(callback);
 */
public class SignalKeyManager {

    // ---- Constants ----

    private static final int        INITIAL_ONE_TIME_PREKEYS    =   100;
    private static final int        INITIAL_KYBER_PREKEYS       =   100;
    private static final int        SIGNED_PRE_KEY_ID           =   1;
    private static final int        CONNECT_TIMEOUT             =   15000;
    private static final int        READ_TIMEOUT                =   30000;

    // ---- Fields ----

    private static volatile SignalKeyManager        INSTANCE;
    private final Context                           context;
    private final SharedPreferences                 sharedPreferences;
    private final ExecutorService                   executorService         =   Executors.newSingleThreadExecutor();
    private final Handler                           mainHandler             =   new Handler(Looper.getMainLooper());

    // ---- Constructor ----

    private SignalKeyManager(Context context) {
        this.context            =   context.getApplicationContext();
        this.sharedPreferences  =   context.getApplicationContext().getSharedPreferences(
                                    SharedPreferenceDetails.SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    public static SignalKeyManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (SignalKeyManager.class) {
                if (INSTANCE == null) {
                    INSTANCE    =   new SignalKeyManager(context);
                }
            }
        }
        return INSTANCE;
    }

    // -------------------- Public Methods Starts Here ---------------------

    /**
     * Generates the Signal Protocol key set if it hasn't been done yet.
     * Keys are stored locally only — upload to the backend is currently disabled
     * because the /api/keys/upload endpoint isn't ready. When it is, call
     * {@link #uploadKeysToBackendIfNeeded} to push the previously generated keys.
     *
     * @param callback callback with success/failure, delivered on main thread
     */
    public void generateAndUploadKeysIfNeeded(KeyUploadCallback callback) {
        boolean alreadyGenerated    =   sharedPreferences.getBoolean(
                SharedPreferenceDetails.SIGNAL_KEYS_GENERATED, false);

        if (alreadyGenerated) {
            Log.e(Extras.LOG_MESSAGE, "Signal keys already generated locally, skipping");
            mainHandler.post(() -> {
                if (callback != null) callback.onSuccess();
            });
            return;
        }

        executorService.execute(() -> generateKeysLocally(callback));
    }

    /**
     * Uploads previously-generated keys to the backend. No-op if keys haven't been
     * generated yet, or if upload has already succeeded.
     *
     * TODO: Call this once the backend /api/keys/upload endpoint is live. For now,
     * the endpoint is a TODO and we skip the network call.
     */
    public void uploadKeysToBackendIfNeeded(KeyUploadCallback callback) {
        boolean alreadyUploaded =   sharedPreferences.getBoolean(
                SharedPreferenceDetails.SIGNAL_KEYS_UPLOAD, false);
        if (alreadyUploaded) {
            if (callback != null) callback.onSuccess();
            return;
        }
        Log.e(Extras.LOG_MESSAGE, "TODO: upload keys to backend — endpoint not ready yet");
        if (callback != null) callback.onFailure("backend endpoint not ready");
    }

    /**
     * Returns true if Signal keys have been generated and stored locally.
     */
    public boolean areKeysGenerated() {
        return sharedPreferences.getBoolean(SharedPreferenceDetails.SIGNAL_KEYS_GENERATED, false);
    }

    /**
     * Returns true if Signal keys have been uploaded to the backend.
     */
    public boolean areKeysUploaded() {
        return sharedPreferences.getBoolean(SharedPreferenceDetails.SIGNAL_KEYS_UPLOAD, false);
    }

    // -------------------- Key Generation Starts Here ---------------------

    /**
     * Generates identity + signed + one-time + kyber keys and persists them locally.
     * Currently does NOT upload to the server — the /api/keys/upload endpoint isn't
     * ready yet. The upload JSON is still built and logged so we can verify the
     * format, and so the upload call is a one-liner addition when the backend is ready.
     */
    private void generateKeysLocally(KeyUploadCallback callback) {
        try {
            Log.e(Extras.LOG_MESSAGE, "═══════ Signal Key Generation Starts ═══════");

            // Step 1: Identity key pair
            IdentityKeyPair     identityKeyPair     =   IdentityKeyPair.generate();
            Log.e(Extras.LOG_MESSAGE, "Generated identity key pair");

            // Step 2: Registration ID
            int registrationId  =   KeyHelper.generateRegistrationId(false);
            int deviceId        =   1;
            Log.e(Extras.LOG_MESSAGE, "Generated registration ID: " + registrationId);

            // Step 3: Signed pre-key (signed by identity key)
            ECKeyPair           signedPreKeyPair    =   ECKeyPair.generate();
            byte[]              signedPreKeySig     =   identityKeyPair.getPrivateKey()
                    .calculateSignature(signedPreKeyPair.getPublicKey().serialize());
            SignedPreKeyRecord  signedPreKeyRecord  =   new SignedPreKeyRecord(
                    SIGNED_PRE_KEY_ID, System.currentTimeMillis(), signedPreKeyPair, signedPreKeySig);
            Log.e(Extras.LOG_MESSAGE, "Generated signed pre-key id=" + SIGNED_PRE_KEY_ID);

            // Step 4: One-time pre-keys (100) — keep raw ECKeyPair refs for serialization
            List<PreKeyRecord>  oneTimePreKeys      =   new ArrayList<>();
            List<ECKeyPair>     oneTimeKeyPairs     =   new ArrayList<>();
            for (int i = 1; i <= INITIAL_ONE_TIME_PREKEYS; i++) {
                ECKeyPair   otkPair     =   ECKeyPair.generate();
                oneTimePreKeys.add(new PreKeyRecord(i, otkPair));
                oneTimeKeyPairs.add(otkPair);
            }
            Log.e(Extras.LOG_MESSAGE, "Generated " + INITIAL_ONE_TIME_PREKEYS + " one-time pre-keys");

            // Step 5: Kyber pre-keys (100) — required by libsignal 0.86.5.
            // Keep raw KEMKeyPair refs + signatures for serialization without calling
            // KyberPreKeyRecord.getKeyPair() which throws InvalidKeyException.
            List<KyberPreKeyRecord> kyberPreKeys            =   new ArrayList<>();
            List<KEMKeyPair>        kyberKeyPairs           =   new ArrayList<>();
            List<byte[]>            kyberSignatures         =   new ArrayList<>();
            for (int i = 1; i <= INITIAL_KYBER_PREKEYS; i++) {
                KEMKeyPair  kyberPair   =   KEMKeyPair.generate(KEMKeyType.KYBER_1024);
                byte[]      kyberSig    =   identityKeyPair.getPrivateKey()
                        .calculateSignature(kyberPair.getPublicKey().serialize());
                kyberPreKeys.add(new KyberPreKeyRecord(i, System.currentTimeMillis(), kyberPair, kyberSig));
                kyberKeyPairs.add(kyberPair);
                kyberSignatures.add(kyberSig);
            }
            Log.e(Extras.LOG_MESSAGE, "Generated " + INITIAL_KYBER_PREKEYS + " Kyber pre-keys");

            // Step 6: Persist private keys locally (encrypted via Android KeyStore)
            persistKeysLocally(identityKeyPair, signedPreKeyPair, signedPreKeySig,
                    oneTimePreKeys, kyberPreKeys);
            Log.e(Extras.LOG_MESSAGE, "Keys persisted locally");

            // Store registration ID + device ID in SharedPreferences
            SharedPreferencesManager.getInstance(context)
                    .saveDeviceIdAndRegistrationId(deviceId, registrationId);

            // Step 7: Build upload JSON (logged but NOT sent — backend endpoint pending).
            // Keeping the JSON builder call verifies the format + gives us a payload to
            // inspect; actual upload is deferred to uploadKeysToBackendIfNeeded() once
            // /api/keys/upload is live on the server.
            JSONObject  uploadJson  =   buildUploadJson(identityKeyPair, signedPreKeyPair, signedPreKeySig,
                    oneTimeKeyPairs, kyberKeyPairs, kyberSignatures, registrationId, deviceId);
            Log.e(Extras.LOG_MESSAGE, "Upload JSON prepared (not sent — endpoint pending). Size="
                    + uploadJson.toString().length() + " bytes");

            // Mark keys as generated locally so we don't regenerate on next launch
            sharedPreferences.edit()
                    .putBoolean(SharedPreferenceDetails.SIGNAL_KEYS_GENERATED, true)
                    .apply();

            Log.e(Extras.LOG_MESSAGE, "═══════ Signal Keys Generated Locally ═══════");
            mainHandler.post(() -> {
                if (callback != null) callback.onSuccess();
            });

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Signal key generation failed: " + e.getMessage(), e);
            mainHandler.post(() -> {
                if (callback != null) callback.onFailure(e.getMessage());
            });
        }
    }

    // -------------------- Local Persistence Starts Here ---------------------

    /**
     * Stores all generated private keys in the local encrypted UsersDatabase.
     *
     * Takes the raw ECKeyPair + signature for the signed pre-key directly (instead of
     * calling signedPreKeyRecord.getKeyPair() which throws InvalidKeyException).
     */
    private void persistKeysLocally(IdentityKeyPair identityKeyPair,
                                    ECKeyPair signedPreKeyPair,
                                    byte[] signedPreKeySig,
                                    List<PreKeyRecord> oneTimePreKeys,
                                    List<KyberPreKeyRecord> kyberPreKeys) {

        UserDatabaseRepository userDbRepo   =   com.jippytalk.MyApplication.getInstance()
                .getRepositoryServiceLocator().getUserDatabaseRepository();

        String  idPubBase64         =   Base64.encodeToString(
                identityKeyPair.getPublicKey().getPublicKey().serialize(), Base64.NO_WRAP);
        byte[]  encryptedIdPriv     =   KeyStoreHelper.encrypt(identityKeyPair.getPrivateKey().serialize());
        String  idPrivBase64        =   Base64.encodeToString(encryptedIdPriv, Base64.NO_WRAP);

        String  signedPubBase64     =   Base64.encodeToString(
                signedPreKeyPair.getPublicKey().serialize(), Base64.NO_WRAP);
        byte[]  encryptedSignedPriv =   KeyStoreHelper.encrypt(
                signedPreKeyPair.getPrivateKey().serialize());
        String  signedPrivBase64    =   encryptedSignedPriv != null
                ? Base64.encodeToString(encryptedSignedPriv, Base64.NO_WRAP) : "";
        String  signedSigBase64     =   Base64.encodeToString(signedPreKeySig, Base64.NO_WRAP);

        userDbRepo.insertIdentityAndSignedKeysInDatabase(
                idPubBase64, idPrivBase64,
                SIGNED_PRE_KEY_ID, signedSigBase64,
                signedPubBase64, signedPrivBase64);

        // Store one-time pre-keys + kyber pre-keys in batch
        userDbRepo.insertOTAndKyber(oneTimePreKeys, kyberPreKeys);
    }

    // -------------------- Upload JSON Building Starts Here ---------------------

    /**
     * Builds the JSON payload for POST /api/keys/upload.
     *
     * Uses raw ECKeyPair / KEMKeyPair references directly to avoid calling the record
     * classes' getKeyPair() methods which throw InvalidKeyException.
     *
     * Structure:
     * {
     *   "registration_id": int,
     *   "device_id": int,
     *   "identity_key": base64,
     *   "signed_prekey": { "key_id", "public_key", "signature" },
     *   "one_time_prekeys": [ { "key_id", "public_key" } ],
     *   "kyber_prekeys": [ { "key_id", "public_key", "signature" } ]
     * }
     */
    private JSONObject buildUploadJson(IdentityKeyPair identityKeyPair,
                                       ECKeyPair signedPreKeyPair,
                                       byte[] signedPreKeySig,
                                       List<ECKeyPair> oneTimeKeyPairs,
                                       List<KEMKeyPair> kyberKeyPairs,
                                       List<byte[]> kyberSignatures,
                                       int registrationId, int deviceId) throws JSONException {
        JSONObject root = new JSONObject();
        root.put("registration_id", registrationId);
        root.put("device_id", deviceId);

        root.put("identity_key", Base64.encodeToString(
                identityKeyPair.getPublicKey().getPublicKey().serialize(), Base64.NO_WRAP));

        // Signed pre-key
        JSONObject signedObj = new JSONObject();
        signedObj.put("key_id", SIGNED_PRE_KEY_ID);
        signedObj.put("public_key", Base64.encodeToString(
                signedPreKeyPair.getPublicKey().serialize(), Base64.NO_WRAP));
        signedObj.put("signature", Base64.encodeToString(signedPreKeySig, Base64.NO_WRAP));
        root.put("signed_prekey", signedObj);

        // One-time pre-keys — use raw ECKeyPair list (indices match INITIAL_ONE_TIME_PREKEYS loop ids)
        JSONArray oneTimeArr = new JSONArray();
        for (int i = 0; i < oneTimeKeyPairs.size(); i++) {
            JSONObject otkObj = new JSONObject();
            otkObj.put("key_id", i + 1);
            otkObj.put("public_key", Base64.encodeToString(
                    oneTimeKeyPairs.get(i).getPublicKey().serialize(), Base64.NO_WRAP));
            oneTimeArr.put(otkObj);
        }
        root.put("one_time_prekeys", oneTimeArr);

        // Kyber pre-keys — use raw KEMKeyPair list + parallel signature list
        JSONArray kyberArr = new JSONArray();
        for (int i = 0; i < kyberKeyPairs.size(); i++) {
            JSONObject kyberObj = new JSONObject();
            kyberObj.put("key_id", i + 1);
            kyberObj.put("public_key", Base64.encodeToString(
                    kyberKeyPairs.get(i).getPublicKey().serialize(), Base64.NO_WRAP));
            kyberObj.put("signature", Base64.encodeToString(
                    kyberSignatures.get(i), Base64.NO_WRAP));
            kyberArr.put(kyberObj);
        }
        root.put("kyber_prekeys", kyberArr);

        return root;
    }

    // -------------------- Network Upload Starts Here ---------------------

    /**
     * POSTs the key bundle JSON to /api/keys/upload with Bearer auth.
     *
     * @param uploadJson the key bundle JSON
     * @return true on 2xx response, false otherwise
     */
    private boolean uploadKeysToServer(JSONObject uploadJson) {
        HttpURLConnection   connection  =   null;
        String              jwtToken    =   sharedPreferences.getString(
                SharedPreferenceDetails.JWT_TOKEN, "");

        if (jwtToken.isEmpty()) {
            Log.e(Extras.LOG_MESSAGE, "Cannot upload keys: no JWT token");
            return false;
        }

        String  body        =   uploadJson.toString();
        long    startTime   =   ApiLogger.logRequestMeta("POST", API.KEYS_UPLOAD,
                body.length() + " bytes (full key bundle)", jwtToken);

        try {
            URL url     =   new URL(API.KEYS_UPLOAD);
            connection  =   (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.connect();

            try (OutputStreamWriter writer = new OutputStreamWriter(
                    connection.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(body);
                writer.flush();
            }

            int statusCode  =   connection.getResponseCode();

            if (statusCode >= 200 && statusCode < 300) {
                ApiLogger.logResponse("POST", API.KEYS_UPLOAD, statusCode, "success", startTime);
                return true;
            }

            // Log error body for debugging
            StringBuilder errorResponse =   new StringBuilder();
            if (connection.getErrorStream() != null) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        errorResponse.append(line);
                    }
                }
            }
            ApiLogger.logResponse("POST", API.KEYS_UPLOAD, statusCode,
                    errorResponse.toString(), startTime);
            return false;

        } catch (Exception e) {
            ApiLogger.logError("POST", API.KEYS_UPLOAD, e.getMessage(), startTime);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // -------------------- Callback Interface ---------------------

    /**
     * Callback for key upload result. Delivered on the main thread.
     */
    public interface KeyUploadCallback {
        void onSuccess();
        void onFailure(String error);
    }
}
