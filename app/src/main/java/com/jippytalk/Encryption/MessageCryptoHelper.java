package com.jippytalk.Encryption;

/**
 * Developer Name: Vidya Sagar
 * Created on: 12-04-2026
 */

import android.util.Base64;
import android.util.Log;

import com.jippytalk.Extras;

import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * MessageCryptoHelper — AES-256-GCM per-message encryption utility.
 *
 * Every message gets a fresh random AES-256 key and 12-byte IV.
 * The key + IV travel alongside the ciphertext in the WebSocket payload,
 * making the backend blind to the content (true E2E).
 *
 * Usage:
 *   // Sender
 *   EncryptionResult result = MessageCryptoHelper.encrypt("hello");
 *   // result.ciphertext, result.key, result.iv are all Base64 strings
 *
 *   // Receiver
 *   String plaintext = MessageCryptoHelper.decrypt(result.ciphertext, result.key, result.iv);
 *   // plaintext == "hello"
 *
 * Algorithm: AES/GCM/NoPadding (same as KeyStoreHelper, but with throwaway keys)
 * Key size:  256 bits
 * IV size:   12 bytes (96 bits, GCM standard)
 * Auth tag:  128 bits (GCM default)
 */
public class MessageCryptoHelper {

    private static final String     ALGORITHM           =   "AES";
    private static final String     TRANSFORMATION      =   "AES/GCM/NoPadding";
    private static final int        KEY_SIZE_BITS       =   256;
    private static final int        IV_SIZE_BYTES       =   12;
    private static final int        GCM_TAG_BITS        =   128;

    // -------------------- Encrypt ---------------------

    /**
     * Encrypts a plaintext string with a fresh random AES-256-GCM key.
     *
     * @param plaintext the message text or metadata JSON to encrypt
     * @return EncryptionResult with Base64-encoded ciphertext, key, and iv;
     *         or null if encryption fails
     */
    public static EncryptionResult encrypt(String plaintext) {
        if (plaintext == null || plaintext.isEmpty()) {
            Log.e(Extras.LOG_MESSAGE, "MessageCryptoHelper.encrypt: empty plaintext");
            return null;
        }

        try {
            // Generate random AES-256 key
            KeyGenerator    keyGen      =   KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE_BITS, new SecureRandom());
            SecretKey       secretKey   =   keyGen.generateKey();

            // Generate random 12-byte IV
            byte[]          iv          =   new byte[IV_SIZE_BYTES];
            new SecureRandom().nextBytes(iv);

            // Encrypt
            Cipher          cipher      =   Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec    =   new GCMParameterSpec(GCM_TAG_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);
            byte[]          encrypted   =   cipher.doFinal(plaintext.getBytes("UTF-8"));

            // Base64 encode all three outputs
            String  b64Ciphertext       =   Base64.encodeToString(encrypted, Base64.NO_WRAP);
            String  b64Key              =   Base64.encodeToString(secretKey.getEncoded(), Base64.NO_WRAP);
            String  b64Iv               =   Base64.encodeToString(iv, Base64.NO_WRAP);

            return new EncryptionResult(b64Ciphertext, b64Key, b64Iv);

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "MessageCryptoHelper.encrypt failed: " + e.getMessage());
            return null;
        }
    }

    // -------------------- Decrypt ---------------------

    /**
     * Decrypts an AES-256-GCM ciphertext using the provided key and IV.
     *
     * @param b64Ciphertext Base64-encoded ciphertext (from the WS payload)
     * @param b64Key        Base64-encoded AES-256 key
     * @param b64Iv         Base64-encoded 12-byte IV
     * @return the decrypted plaintext string, or null if decryption fails
     */
    public static String decrypt(String b64Ciphertext, String b64Key, String b64Iv) {
        if (b64Ciphertext == null || b64Ciphertext.isEmpty()
                || b64Key == null || b64Key.isEmpty()
                || b64Iv == null || b64Iv.isEmpty()) {
            Log.e(Extras.LOG_MESSAGE, "MessageCryptoHelper.decrypt: missing ciphertext/key/iv");
            return null;
        }

        try {
            byte[]          cipherBytes =   Base64.decode(b64Ciphertext, Base64.NO_WRAP);
            byte[]          keyBytes    =   Base64.decode(b64Key, Base64.NO_WRAP);
            byte[]          ivBytes     =   Base64.decode(b64Iv, Base64.NO_WRAP);

            SecretKeySpec   keySpec     =   new SecretKeySpec(keyBytes, ALGORITHM);
            GCMParameterSpec gcmSpec    =   new GCMParameterSpec(GCM_TAG_BITS, ivBytes);

            Cipher          cipher      =   Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            byte[]          decrypted   =   cipher.doFinal(cipherBytes);

            return new String(decrypted, "UTF-8");

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "MessageCryptoHelper.decrypt failed: " + e.getMessage());
            return null;
        }
    }

    // -------------------- Key Generation ---------------------

    /**
     * Generates a fresh per-message AES-256 key + 12-byte IV pair.
     * The returned EncryptionResult has an empty ciphertext field — only
     * the key + iv are populated. Use this when you need to encrypt
     * multiple things (file bytes, thumbnail bytes, caption, URLs) with
     * the same key+iv for a single message.
     */
    public static EncryptionResult generateKeyIv() {
        try {
            KeyGenerator keyGen     =   KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE_BITS, new SecureRandom());
            SecretKey    secretKey  =   keyGen.generateKey();

            byte[]       iv         =   new byte[IV_SIZE_BYTES];
            new SecureRandom().nextBytes(iv);

            String b64Key   =   Base64.encodeToString(secretKey.getEncoded(), Base64.NO_WRAP);
            String b64Iv    =   Base64.encodeToString(iv, Base64.NO_WRAP);
            return new EncryptionResult("", b64Key, b64Iv);
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "MessageCryptoHelper.generateKeyIv failed: " + e.getMessage());
            return null;
        }
    }

    // -------------------- Streaming Encrypt / Decrypt ---------------------

    /**
     * Encrypts an InputStream into an OutputStream using AES-256-GCM with the
     * provided Base64 key + IV. Both streams must be opened by the caller and
     * are closed by this method on completion (the wrapping CipherOutputStream
     * is closed, which finalises the GCM auth tag).
     *
     * Used for streaming file/thumbnail bytes through encryption without
     * loading the whole file into memory.
     *
     * @param in     plaintext input
     * @param out    ciphertext output (will be flushed and closed)
     * @param b64Key Base64 AES-256 key
     * @param b64Iv  Base64 12-byte IV
     * @throws Exception on I/O or crypto failure
     */
    public static void encryptStream(InputStream in, OutputStream out,
                                     String b64Key, String b64Iv) throws Exception {
        byte[]              keyBytes    =   Base64.decode(b64Key, Base64.NO_WRAP);
        byte[]              ivBytes     =   Base64.decode(b64Iv, Base64.NO_WRAP);
        SecretKeySpec       keySpec     =   new SecretKeySpec(keyBytes, ALGORITHM);
        GCMParameterSpec    gcmSpec     =   new GCMParameterSpec(GCM_TAG_BITS, ivBytes);

        Cipher              cipher      =   Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

        CipherOutputStream cos = new CipherOutputStream(out, cipher);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                cos.write(buf, 0, n);
            }
            cos.flush();
        } finally {
            try { cos.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * Decrypts an InputStream into an OutputStream using AES-256-GCM with the
     * provided Base64 key + IV. Wraps the input in CipherInputStream so the
     * GCM auth tag is verified at end-of-stream — if the ciphertext was
     * tampered with, an exception is thrown when the last bytes are read.
     *
     * @param in     ciphertext input
     * @param out    plaintext output (will be flushed; not closed)
     * @param b64Key Base64 AES-256 key
     * @param b64Iv  Base64 12-byte IV
     * @throws Exception on I/O failure or auth tag mismatch
     */
    public static void decryptStream(InputStream in, OutputStream out,
                                     String b64Key, String b64Iv) throws Exception {
        byte[]              keyBytes    =   Base64.decode(b64Key, Base64.NO_WRAP);
        byte[]              ivBytes     =   Base64.decode(b64Iv, Base64.NO_WRAP);
        SecretKeySpec       keySpec     =   new SecretKeySpec(keyBytes, ALGORITHM);
        GCMParameterSpec    gcmSpec     =   new GCMParameterSpec(GCM_TAG_BITS, ivBytes);

        Cipher              cipher      =   Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

        CipherInputStream cis = new CipherInputStream(in, cipher);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = cis.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            out.flush();
        } finally {
            try { cis.close(); } catch (Exception ignored) {}
        }
    }

    // -------------------- Result Model ---------------------

    /**
     * Holds the three Base64-encoded outputs of an encryption operation.
     * All three must be sent to the receiver for decryption.
     */
    public static class EncryptionResult {
        public final String ciphertext;     // Base64(AES-GCM encrypted bytes)
        public final String key;            // Base64(256-bit AES key)
        public final String iv;             // Base64(12-byte IV)

        public EncryptionResult(String ciphertext, String key, String iv) {
            this.ciphertext     =   ciphertext;
            this.key            =   key;
            this.iv             =   iv;
        }
    }
}
