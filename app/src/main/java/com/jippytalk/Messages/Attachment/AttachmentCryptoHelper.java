package com.jippytalk.Messages.Attachment;

/**
 * Developer Name: Vidya Sagar
 * Created on: 09-04-2026
 */

import android.util.Log;

import com.jippytalk.Encryption.MessageEncryptAndDecrypt;
import com.jippytalk.Extras;
import com.jippytalk.Managers.SessionManager;
import com.jippytalk.Messages.Attachment.Model.AttachmentModel;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * AttachmentCryptoHelper - Handles encryption and decryption of attachment metadata.
 *
 * Only the METADATA (file name, s3 key, content type, dimensions, etc.) is encrypted
 * using Signal Protocol. The actual file content is uploaded raw to S3.
 *
 * Sender flow:
 *   AttachmentModel → toJson() → encrypt with Signal SessionCipher → encrypted ciphertext string
 *   This ciphertext goes into the WebSocket "file" event's "ciphertext" field.
 *
 * Receiver flow:
 *   Incoming "file" event ciphertext → decrypt with Signal SessionCipher → JSON string → fromJson() → AttachmentModel
 *
 * Usage:
 *   // Sender: encrypt metadata before sending via WebSocket
 *   String metadataJson = AttachmentCryptoHelper.toJson(attachmentModel);
 *   messageEncryptAndDecrypt.encryptMessage(contactId, deviceId, metadataJson, callback);
 *
 *   // Receiver: after decrypting the ciphertext
 *   AttachmentModel model = AttachmentCryptoHelper.fromJson(decryptedJsonString);
 */
public class AttachmentCryptoHelper {

    // ---- JSON Keys ----

    private static final String     KEY_FILE_NAME       =   "file_name";
    private static final String     KEY_FILE_SIZE       =   "file_size";
    private static final String     KEY_CONTENT_TYPE    =   "content_type";
    private static final String     KEY_CONTENT_SUBTYPE =   "content_subtype";
    private static final String     KEY_S3_KEY          =   "s3_key";
    private static final String     KEY_S3_URL          =   "s3_url";
    private static final String     KEY_CAPTION         =   "caption";
    private static final String     KEY_WIDTH           =   "width";
    private static final String     KEY_HEIGHT          =   "height";
    private static final String     KEY_DURATION        =   "duration";
    private static final String     KEY_THUMBNAIL       =   "thumbnail";
    private static final String     KEY_BUCKET          =   "bucket";

    // -------------------- Serialize (Sender Side) Starts Here ---------------------

    /**
     * Converts an AttachmentModel to a JSON string for encryption.
     * This JSON is what gets encrypted by Signal Protocol before sending.
     *
     * @param model     the attachment model with all metadata
     * @param s3Key     the S3 object key returned after upload
     * @param s3Url     the S3 URL for the receiver to download from
     * @param fileSize  the file size in bytes
     * @return JSON string ready for Signal Protocol encryption
     */
    public static String toJson(AttachmentModel model, String s3Key, String s3Url, long fileSize) {
        try {
            JSONObject  json    =   new JSONObject();
            json.put(KEY_FILE_NAME, model.getName() != null ? model.getName() : "");
            json.put(KEY_FILE_SIZE, fileSize);
            json.put(KEY_CONTENT_TYPE, model.getContentType() != null ? model.getContentType() : "");
            json.put(KEY_CONTENT_SUBTYPE, model.getContentSubtype() != null ? model.getContentSubtype() : "");
            json.put(KEY_S3_KEY, s3Key != null ? s3Key : "");
            json.put(KEY_S3_URL, s3Url != null ? s3Url : "");
            json.put(KEY_CAPTION, model.getCaption() != null ? model.getCaption() : "");
            json.put(KEY_WIDTH, model.getWidth());
            json.put(KEY_HEIGHT, model.getHeight());
            json.put(KEY_DURATION, model.getDuration());
            json.put(KEY_THUMBNAIL, model.getThumbnail() != null ? model.getThumbnail() : "");
            json.put(KEY_BUCKET, model.getBucket() != null ? model.getBucket() : "");
            return json.toString();
        } catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to serialize attachment metadata: " + e.getMessage());
            return "{}";
        }
    }

    // -------------------- Deserialize (Receiver Side) Starts Here ---------------------

    /**
     * Parses a decrypted JSON string back into an AttachmentModel.
     * Called after Signal Protocol decrypts the ciphertext from an incoming "file" event.
     *
     * @param decryptedJson the decrypted JSON metadata string
     * @return AttachmentModel populated with the sender's attachment info, or null on failure
     */
    public static AttachmentModel fromJson(String decryptedJson) {
        try {
            JSONObject  json    =   new JSONObject(decryptedJson);

            String  fileName        =   json.optString(KEY_FILE_NAME, "");
            long    fileSize        =   json.optLong(KEY_FILE_SIZE, 0);
            String  contentType     =   json.optString(KEY_CONTENT_TYPE, "");
            String  contentSubtype  =   json.optString(KEY_CONTENT_SUBTYPE, "");
            String  s3Key           =   json.optString(KEY_S3_KEY, "");
            String  s3Url           =   json.optString(KEY_S3_URL, "");
            String  caption         =   json.optString(KEY_CAPTION, "");
            int     width           =   json.optInt(KEY_WIDTH, 0);
            int     height          =   json.optInt(KEY_HEIGHT, 0);
            long    duration        =   json.optLong(KEY_DURATION, 0);
            String  thumbnail       =   json.optString(KEY_THUMBNAIL, "");
            String  bucket          =   json.optString(KEY_BUCKET, "");

            AttachmentModel model   =   new AttachmentModel(
                    s3Url,              // media URI = the S3 download URL for receiver
                    thumbnail,
                    contentType,
                    contentSubtype,
                    caption,
                    height,
                    width,
                    duration,
                    bucket,
                    "",                 // region — not sent
                    fileName
            );
            model.setS3Key(s3Key);
            return model;

        } catch (JSONException e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to deserialize attachment metadata: " + e.getMessage());
            return null;
        }
    }

    // -------------------- Encrypt Helper (Sender) Starts Here ---------------------

    /**
     * Encrypts attachment metadata using Signal Protocol and returns the result via callback.
     * This is the method to call from MediaTransferManager after upload succeeds.
     *
     * @param messageEncryptAndDecrypt  the Signal Protocol encryption engine
     * @param contactId                 the receiver's contact ID
     * @param contactDeviceId           the receiver's device ID
     * @param model                     the attachment model
     * @param s3Key                     the S3 key from upload
     * @param s3Url                     the download URL for the receiver
     * @param fileSize                  the file size in bytes
     * @param callback                  callback with encrypted ciphertext + signal message type
     */
    public static void encryptMetadata(MessageEncryptAndDecrypt messageEncryptAndDecrypt,
                                       String contactId, int contactDeviceId,
                                       AttachmentModel model, String s3Key, String s3Url, long fileSize,
                                       EncryptionResultCallback callback) {

        String  metadataJson    =   toJson(model, s3Key, s3Url, fileSize);

        messageEncryptAndDecrypt.encryptMessage(contactId, contactDeviceId, metadataJson,
                new MessageEncryptAndDecrypt.MessageEncryptionCallBacks() {
                    @Override
                    public void onMessageEncryptSuccessful(String encryptedMessage, int signalMessageType) {
                        Log.e(Extras.LOG_MESSAGE, "Attachment metadata encrypted successfully");
                        if (callback != null) {
                            callback.onEncrypted(encryptedMessage, signalMessageType);
                        }
                    }

                    @Override
                    public void onMessageEncryptionFail(int exceptionStatus) {
                        Log.e(Extras.LOG_MESSAGE, "Attachment metadata encryption failed: " + exceptionStatus);
                        if (callback != null) {
                            callback.onEncryptionFailed(exceptionStatus);
                        }
                    }
                });
    }

    // -------------------- Decrypt Helper (Receiver) Starts Here ---------------------

    /**
     * Decrypts attachment metadata from an incoming file message and returns the AttachmentModel.
     * This is the method to call from HandleInsertionsFromService when a "file" event arrives.
     *
     * @param messageEncryptAndDecrypt  the Signal Protocol decryption engine
     * @param senderId                  the sender's contact ID
     * @param deviceId                  the sender's device ID
     * @param encryptedMetadata         the Base64-decoded encrypted metadata bytes
     * @param signalMessageType         the Signal Protocol message type
     * @param callback                  callback with the decrypted AttachmentModel
     */
    public static void decryptMetadata(MessageEncryptAndDecrypt messageEncryptAndDecrypt,
                                       String senderId, int deviceId,
                                       byte[] encryptedMetadata, int signalMessageType,
                                       DecryptionResultCallback callback) {

        messageEncryptAndDecrypt.decryptMessage(senderId, deviceId, encryptedMetadata,
                signalMessageType, false, 3,
                new MessageEncryptAndDecrypt.MessageDecryptionCallBack() {
                    @Override
                    public void onMessageDecryptSuccessful(String decryptedMessage, boolean keysChanged) {
                        Log.e(Extras.LOG_MESSAGE, "Attachment metadata decrypted successfully");
                        AttachmentModel model   =   fromJson(decryptedMessage);
                        if (callback != null) {
                            callback.onDecrypted(model, keysChanged);
                        }
                    }

                    @Override
                    public void onDecryptionFail(int exceptionStatus) {
                        Log.e(Extras.LOG_MESSAGE, "Attachment metadata decryption failed: " + exceptionStatus);
                        if (callback != null) {
                            callback.onDecryptionFailed(exceptionStatus);
                        }
                    }
                });
    }

    // -------------------- Callback Interfaces ---------------------

    /**
     * Callback for attachment metadata encryption result.
     */
    public interface EncryptionResultCallback {
        /**
         * Called when metadata is encrypted successfully.
         *
         * @param encryptedCiphertext   the Base64-encoded encrypted metadata
         * @param signalMessageType     the Signal Protocol message type (WHISPER or PREKEY)
         */
        void onEncrypted(String encryptedCiphertext, int signalMessageType);

        /**
         * Called when encryption fails.
         *
         * @param exceptionStatus the exception type from SessionManager constants
         */
        void onEncryptionFailed(int exceptionStatus);
    }

    /**
     * Callback for attachment metadata decryption result.
     */
    public interface DecryptionResultCallback {
        /**
         * Called when metadata is decrypted successfully.
         *
         * @param attachmentModel   the parsed attachment model with all metadata
         * @param keysChanged       true if sender's keys have changed
         */
        void onDecrypted(AttachmentModel attachmentModel, boolean keysChanged);

        /**
         * Called when decryption fails.
         *
         * @param exceptionStatus the exception type from SessionManager constants
         */
        void onDecryptionFailed(int exceptionStatus);
    }
}
