package com.jippytalk.Encryption;

/**
 * Developer Name: Vidya Sagar
 * Created on: 12-04-2026
 */

import android.os.Build;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.jippytalk.Extras;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
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
    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    public static void encryptStream(InputStream in, OutputStream out,
                                     String b64Key, String b64Iv) throws Exception {
        byte[]              keyBytes    =   Base64.decode(b64Key, Base64.NO_WRAP);
        byte[]              ivBytes     =   Base64.decode(b64Iv, Base64.NO_WRAP);
        SecretKeySpec       keySpec     =   new SecretKeySpec(keyBytes, ALGORITHM);
        GCMParameterSpec    gcmSpec     =   new GCMParameterSpec(GCM_TAG_BITS, ivBytes);

        Cipher              cipher      =   Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
       // Log.e("Checking", "InputStream :" + in + "\nOutputStream: " + out+ " \nb64Key: "+b64Key+"\nb64Iv: "+b64Iv);
        String is= new String(in.readAllBytes(), StandardCharsets.UTF_8);
        Log.e("Checking", "InputStream :" + is + "\nOutputStream: " + out+ " \nb64Key: "+b64Key+"\nb64Iv: "+b64Iv);
        CipherOutputStream cos = new CipherOutputStream(out, cipher);
        try {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                cos.write(buf, 0, n);
            }
            String is1= new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Log.e("Checking 2", "InputStream :" + is1 + "\nOutputStream: " + out+ " \nb64Key: "+b64Key+"\nb64Iv: "+b64Iv);
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

    // -------------------- Filename Encryption (for S3 object keys) --------

    /**
     * Encrypts a filename with the per-message AES-256 key for use as an S3
     * object name, so neither the plaintext filename nor its extension ever
     * appears in the S3 console or object listing.
     *
     * Output layout:  base64url( IV || ciphertext || authTag )
     *   - A fresh 12-byte random IV is generated per call and embedded at
     *     the front of the blob. The content IV ({@code b64Iv}) is NEVER
     *     reused here — AES-GCM is insecure under nonce reuse across
     *     different plaintexts with the same key.
     *   - No extension is appended. Every uploaded object looks identical
     *     on S3 (opaque ciphertext), so file type cannot be inferred from
     *     the key. Any MIME-aware backend handling has to rely on the
     *     Content-Type header or on data stored separately; the S3 PUT
     *     already uses {@code application/octet-stream} for encrypted
     *     payloads, so this does not regress anything.
     *
     * Receiver does NOT need to call {@link #decryptFilenameFromS3Name} —
     * the original filename (with extension) travels Signal-encrypted
     * inside the WebSocket metadata and is read from there. This method
     * exists purely to keep the plaintext off the server/S3 side; the
     * decrypt counterpart is provided for symmetry and future use.
     *
     * @param fileName original filename — may include extension, which
     *                 becomes part of the ciphertext, NOT appended to the
     *                 output
     * @param b64Key   Base64 AES-256 key (the message's encryption_key)
     * @return encrypted S3 object name (no extension), or {@code null} if
     *         encryption fails
     */
    public static String encryptFilenameForS3(String fileName, String b64Key) {
        if (b64Key == null || b64Key.isEmpty()) {
            Log.e(Extras.LOG_MESSAGE, "encryptFilenameForS3: missing key");
            return null;
        }
        if (fileName == null) fileName = "";
        try {
            byte[]              keyBytes    =   Base64.decode(b64Key, Base64.NO_WRAP);
            SecretKeySpec       keySpec     =   new SecretKeySpec(keyBytes, ALGORITHM);

            byte[]              iv          =   new byte[IV_SIZE_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher              cipher      =   Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[]              ct          =   cipher.doFinal(fileName.getBytes("UTF-8"));

            byte[]              blob        =   new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, blob, 0, iv.length);
            System.arraycopy(ct, 0, blob, iv.length, ct.length);

            return Base64.encodeToString(blob,
                    Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING);

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "encryptFilenameForS3 failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Inverse of {@link #encryptFilenameForS3} — recovers the original
     * filename from an encrypted S3 object name, given the per-message AES
     * key. The name is assumed to have no extension (matching the output
     * of the encrypt method). Returns {@code null} on any failure (bad
     * base64, short blob, auth tag mismatch, etc.) — callers should fall
     * back to the plaintext filename from the WebSocket metadata.
     *
     * @param s3Name the encrypted S3 object name
     * @param b64Key Base64 AES-256 key (the message's encryption_key)
     * @return the decrypted original filename, or {@code null} on failure
     */
    public static String decryptFilenameFromS3Name(String s3Name, String b64Key) {
        if (s3Name == null || s3Name.isEmpty() || b64Key == null || b64Key.isEmpty()) {
            return null;
        }
        try {
            byte[] blob = Base64.decode(s3Name, Base64.URL_SAFE | Base64.NO_WRAP);
            if (blob.length <= IV_SIZE_BYTES) return null;

            byte[] iv = new byte[IV_SIZE_BYTES];
            byte[] ct = new byte[blob.length - IV_SIZE_BYTES];
            System.arraycopy(blob, 0, iv, 0, IV_SIZE_BYTES);
            System.arraycopy(blob, IV_SIZE_BYTES, ct, 0, ct.length);

            byte[]              keyBytes    =   Base64.decode(b64Key, Base64.NO_WRAP);
            SecretKeySpec       keySpec     =   new SecretKeySpec(keyBytes, ALGORITHM);
            Cipher              cipher      =   Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[]              pt          =   cipher.doFinal(ct);

            return new String(pt, "UTF-8");
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "decryptFilenameFromS3Name failed: " + e.getMessage());
            return null;
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
