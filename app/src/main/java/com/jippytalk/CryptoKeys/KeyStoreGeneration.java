package com.jippytalk.CryptoKeys;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import com.jippytalk.Extras;

import java.security.KeyStore;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/**
 * KeyStoreGeneration - Manages the generation and retrieval of the AES-256 key
 * stored in the Android KeyStore. This key is used to encrypt database passwords
 * and sensitive cryptographic material like Signal Protocol private keys.
 */
public class KeyStoreGeneration {

    private static final String     ANDROID_KEY_STORE   =   "AndroidKeyStore";
    private static final String     KEY_ALIAS           =   "JippyTalkDatabaseKey";

    // -------------------- Key Generation Starts Here ---------------------

    /**
     * Generates a new AES-256 key in the Android KeyStore if one does not already exist.
     * This method is called during application initialization in MyApplication.onCreate().
     * The key is protected by the Android KeyStore and never leaves the secure hardware.
     */
    public static void generateAESKeyIfNecessary() {
        try {
            KeyStore keyStore   =   KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);

            // Check if the key already exists in the KeyStore
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                KeyGenerator keyGenerator   =   KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE);

                KeyGenParameterSpec keyGenParameterSpec  =   new KeyGenParameterSpec.Builder(
                        KEY_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build();

                keyGenerator.init(keyGenParameterSpec);
                keyGenerator.generateKey();

                Log.i(Extras.LOG_MESSAGE, "AES-256 key generated successfully in Android KeyStore");
            }
            else {
                Log.i(Extras.LOG_MESSAGE, "AES key already exists in Android KeyStore");
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to generate AES key " + e.getMessage());
        }
    }

    // -------------------- Key Retrieval Starts Here ---------------------

    /**
     * Retrieves the AES-256 SecretKey from the Android KeyStore.
     * Used by KeyStoreHelper for encryption and decryption operations.
     *
     * @return the AES SecretKey, or null if the key cannot be retrieved
     */
    public static SecretKey getAESKey() {
        try {
            KeyStore    keyStore    =   KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);

            KeyStore.SecretKeyEntry secretKeyEntry  =   (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
            if (secretKeyEntry != null) {
                return secretKeyEntry.getSecretKey();
            }
            else {
                Log.e(Extras.LOG_MESSAGE, "AES key not found in Android KeyStore");
                return null;
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to retrieve AES key from KeyStore " + e.getMessage());
            return null;
        }
    }
}
