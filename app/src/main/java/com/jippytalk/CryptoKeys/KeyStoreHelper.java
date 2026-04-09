package com.jippytalk.CryptoKeys;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.util.Log;

import com.jippytalk.Extras;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * KeyStoreHelper - Provides AES/GCM encryption and decryption utilities
 * using the Android KeyStore-backed AES key from KeyStoreGeneration.
 * Used to encrypt sensitive data like database keys and private pre-keys.
 */
public class KeyStoreHelper {

    private static final String TRANSFORMATION  =   "AES/GCM/NoPadding";
    private static final int    IV_LENGTH       =   12;

    /**
     * Encrypts the given plain bytes using AES/GCM with the Android KeyStore AES key.
     * The IV is generated internally by the Cipher and prepended to the output.
     *
     * @param plainBytes the data to encrypt
     * @return IV + encrypted data as a single byte array, or null if encryption fails
     */
    public static byte[] encrypt(byte[] plainBytes) {
        try
        {
            SecretKey   key     =   KeyStoreGeneration.getAESKey();
            Cipher      cipher  =   Cipher.getInstance(TRANSFORMATION);

            // Initialize WITHOUT passing IV (let system generate it)
            cipher.init(Cipher.ENCRYPT_MODE, key);

            byte[] encrypted = cipher.doFinal(plainBytes);

            // Get the IV generated internally by Cipher
            byte[] iv = cipher.getIV();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            outputStream.write(iv);
            outputStream.write(encrypted);

            return outputStream.toByteArray();
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"unable to encrypt the key " + e.getMessage());
            return null;
        }
    }

    /**
     * Decrypts the given data which contains IV + encrypted content.
     * Extracts the 12-byte IV from the beginning and decrypts the rest using AES/GCM.
     *
     * @param encryptedIvAndData IV (12 bytes) + encrypted data
     * @return decrypted plain bytes, or null if decryption fails
     */
    public static byte[] decrypt(byte[] encryptedIvAndData) {
        try
        {
            SecretKey key = KeyStoreGeneration.getAESKey();
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);

            byte[] iv = Arrays.copyOfRange(encryptedIvAndData, 0, IV_LENGTH);
            byte[] encryptedData = Arrays.copyOfRange(encryptedIvAndData, IV_LENGTH, encryptedIvAndData.length);

            GCMParameterSpec gcmSpec = new GCMParameterSpec(128, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec);

            return cipher.doFinal(encryptedData);
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"unable to decrypt the key " + e.getMessage());
            return null;
        }
    }
}
