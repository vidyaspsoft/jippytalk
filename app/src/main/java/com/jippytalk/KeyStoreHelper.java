package com.jippytalk;

import android.util.Log;

import com.jippytalk.CryptoKeys.KeyStoreGeneration;
import com.jippytalk.Extras;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class KeyStoreHelper {

    private static final String TRANSFORMATION  =   "AES/GCM/NoPadding";
    private static final int    IV_LENGTH       =   12;

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
