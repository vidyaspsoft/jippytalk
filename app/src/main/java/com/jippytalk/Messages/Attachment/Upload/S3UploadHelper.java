package com.jippytalk.Messages.Attachment.Upload;

/**
 * Developer Name: Vidya Sagar
 * Created on: 09-04-2026
 */

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.util.Log;

import com.jippytalk.API;
import com.jippytalk.Extras;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.Messages.Attachment.Model.AttachmentModel;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * S3UploadHelper - Handles file uploads to AWS S3 via presigned URLs.
 *
 * Thread safety:
 * - All heavy work (presign API call, S3 upload) runs on a background ExecutorService
 * - All callbacks are delivered on the MAIN thread via Handler so callers can safely update UI
 * - Progress is throttled to max 1 update per 200ms to avoid flooding the main thread
 * - Cancel flag uses AtomicBoolean checked at every chunk boundary
 *
 * Usage:
 *   S3UploadHelper uploadHelper = new S3UploadHelper(context);
 *   uploadHelper.uploadFile(messageId, attachmentModel, callback);  // callback runs on main thread
 *   uploadHelper.cancelUpload(messageId);
 *   uploadHelper.retryUpload(messageId, attachmentModel, callback);
 */
public class S3UploadHelper {

    // ---- Constants ----

    private static final int        CHUNK_SIZE                  =   256 * 1024;     // 256 KB
    private static final int        MAX_CONCURRENT_UPLOADS      =   3;
    private static final int        CONNECT_TIMEOUT             =   5000;           // 5 seconds
    private static final int        READ_TIMEOUT                =   60000;          // 60 seconds
    private static final long       PROGRESS_THROTTLE_MS        =   200;            // min 200ms between UI updates

    // ---- Fields ----

    private final Context                                   context;
    private final ContentResolver                           contentResolver;
    private final SharedPreferences                         sharedPreferences;
    private final ExecutorService                           uploadExecutor;
    private final ConcurrentHashMap<String, UploadTask>     activeTasks;
    private final Handler                                   mainHandler;

    // ---- Constructor ----

    public S3UploadHelper(Context context) {
        this.context            =   context.getApplicationContext();
        this.contentResolver    =   context.getApplicationContext().getContentResolver();
        this.sharedPreferences  =   context.getApplicationContext().getSharedPreferences(
                                    SharedPreferenceDetails.SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE);
        this.uploadExecutor     =   Executors.newFixedThreadPool(MAX_CONCURRENT_UPLOADS);
        this.activeTasks        =   new ConcurrentHashMap<>();
        this.mainHandler        =   new Handler(Looper.getMainLooper());
    }

    // -------------------- Public Methods Starts Here ---------------------

    /**
     * Starts uploading a file to S3 for the given message.
     * All callback methods are delivered on the main thread.
     *
     * @param messageId         unique message identifier to track this upload
     * @param attachmentModel   the attachment model containing file URI and metadata
     * @param callback          callback for progress, success, failure, and cancellation (main thread)
     */
    public void uploadFile(String messageId, AttachmentModel attachmentModel, UploadCallback callback) {
        if (activeTasks.containsKey(messageId)) {
            Log.e(Extras.LOG_MESSAGE, "Upload already in progress for messageId: " + messageId);
            return;
        }

        UploadTask  uploadTask  =   new UploadTask(messageId, attachmentModel, callback);
        activeTasks.put(messageId, uploadTask);

        Future<?>   future      =   uploadExecutor.submit(() -> executeUpload(uploadTask));
        uploadTask.setFuture(future);
    }

    /**
     * Cancels an active upload for the given message.
     * The callback.onCancelled() will be delivered on the main thread.
     *
     * @param messageId the message identifier of the upload to cancel
     */
    public void cancelUpload(String messageId) {
        UploadTask  task    =   activeTasks.get(messageId);
        if (task != null) {
            task.cancel();
            activeTasks.remove(messageId);
            mainHandler.post(() -> task.getCallback().onCancelled(messageId));
            Log.e(Extras.LOG_MESSAGE, "Upload cancelled for messageId: " + messageId);
        }
    }

    /**
     * Retries a failed or cancelled upload. Cancels any existing task first.
     *
     * @param messageId         the message identifier
     * @param attachmentModel   the attachment model
     * @param callback          callback (main thread)
     */
    public void retryUpload(String messageId, AttachmentModel attachmentModel, UploadCallback callback) {
        UploadTask  existing    =   activeTasks.remove(messageId);
        if (existing != null) {
            existing.cancel();
        }
        uploadFile(messageId, attachmentModel, callback);
    }

    public boolean isUploading(String messageId) {
        return activeTasks.containsKey(messageId);
    }

    /**
     * Cancels all active uploads. Call this in onDestroy().
     */
    public void cancelAll() {
        for (UploadTask task : activeTasks.values()) {
            task.cancel();
        }
        activeTasks.clear();
    }

    // -------------------- Upload Execution Starts Here ---------------------

    /**
     * Executes the full upload pipeline on a background thread.
     * All callbacks are posted to the main thread via mainHandler.
     */
    private void executeUpload(UploadTask task) {
        String  messageId   =   task.getMessageId();
        java.io.File encryptedTempFile = null;
        try {
            if (task.isCancelled()) {
                return;
            }

            // Step 1: Get file info
            Uri     fileUri     =   Uri.parse(task.getAttachmentModel().getMedia());
            long    fileSize    =   getFileSize(fileUri);
            String  fileName    =   task.getAttachmentModel().getName();

            if (fileName == null || fileName.isEmpty()) {
                fileName    =   getFileName(fileUri);
            }

            if (fileSize <= 0) {
                postFailure(task, "Unable to determine file size");
                return;
            }

            // Step 1b: If the model carries encryption keys, stream the plaintext
            // file through AES-256-GCM into a temp ciphertext file in cache.
            // We then upload the ciphertext and its (slightly larger) size.
            // The plaintext local file is left untouched so the sender can still
            // open it locally without decryption.
            String b64Key = task.getAttachmentModel().getEncryptionKey();
            String b64Iv  = task.getAttachmentModel().getEncryptionIv();
            Uri    uploadUri;
            long   uploadSize;
            if (b64Key != null && !b64Key.isEmpty() && b64Iv != null && !b64Iv.isEmpty()) {
                encryptedTempFile = new java.io.File(context.getCacheDir(),
                        "enc_up_" + messageId + "_" + System.currentTimeMillis() + ".bin");
                try (InputStream pin = contentResolver.openInputStream(fileUri);
                     java.io.FileOutputStream fout = new java.io.FileOutputStream(encryptedTempFile)) {
                    if (pin == null) {
                        postFailure(task, "Unable to open plaintext file for encryption");
                        return;
                    }
                  //  Log.e("Checking", "PIN :" + pin + "fout: " + fout + " b64Key: "+b64Key+"b64Iv: "+b64Iv);
                    com.jippytalk.Encryption.MessageCryptoHelper.encryptStream(
                            pin, fout, b64Key, b64Iv);
                  //  Log.e("Checking", "PIN :" + pin + "fout: " + fout + " b64Key: "+b64Key+"b64Iv: "+b64Iv);
                }
                uploadUri  = Uri.fromFile(encryptedTempFile);
                uploadSize = encryptedTempFile.length();
              //  Log.e(Extras.LOG_MESSAGE, "Encrypted file for upload: " + fileName + " plaintext=" + fileSize + " ciphertext=" + uploadSize);

            } else {
                uploadUri  = fileUri;
                uploadSize = fileSize;
            }

            Log.e(Extras.LOG_MESSAGE, "Starting upload for " + fileName + " (" + uploadSize + " bytes)+");

            // Step 2: Request presigned URL using the on-the-wire size.
            //
            // The name sent to the backend (and therefore visible in the S3
            // console / object key) is AES-256-GCM encrypted with this
            // message's per-message key — layout: base64url(IV || ct). No
            // extension is appended, so the S3 object key leaks neither
            // the original filename nor the file type. The content IV
            // ({@code b64Iv}) is NOT reused (GCM is insecure under nonce
            // reuse); the helper generates a fresh 12-byte IV per call and
            // embeds it inside the encoded name.
            //
            // Receiver recovers the filename (with extension) from the
            // Signal-encrypted WebSocket metadata (unchanged path), so no
            // decrypt step is required on download.
            //
            // If the message has no per-message key (legacy/plaintext mode),
            // fall back to the original epochMs-prefixed name so uniqueness
            // is preserved — S3 overwrites would cause 404s on a second
            // recipient after the first one's post-download delete.
            String              uploadFileName;
            String              encryptedName   =   (b64Key != null && !b64Key.isEmpty())
                                                    ? com.jippytalk.Encryption.MessageCryptoHelper
                                                        .encryptFilenameForS3(fileName, b64Key)
                                                    : null;
            if (encryptedName != null) {
                uploadFileName  =   encryptedName;
            } else {
                uploadFileName  =   System.currentTimeMillis() + "_"
                                    + (fileName != null ? fileName : "file");
            }
            String              jwtToken        =   sharedPreferences.getString(SharedPreferenceDetails.JWT_TOKEN, "");
            PresignResponse     presignResponse =   requestPresignedUrl(uploadFileName, uploadSize, jwtToken);

            if (presignResponse == null) {
                postFailure(task, "Failed to get presigned URL");
                return;
            }

            if (task.isCancelled()) return;

            Log.e(Extras.LOG_MESSAGE, "Presigned URL received. S3 key: " + presignResponse.getS3Key());

            // Step 3: Upload (encrypted or plaintext) bytes to S3 with throttled progress
            boolean uploadSuccess   =   uploadToS3(uploadUri, presignResponse.getPresignedUrl(),
                                        uploadSize, task);

            if (task.isCancelled()) return;

            if (uploadSuccess) {
                task.getAttachmentModel().setBucket(presignResponse.getBucket());
                String  s3Key   =   presignResponse.getS3Key();
                String  bucket  =   presignResponse.getBucket();
                mainHandler.post(() -> task.getCallback().onSuccess(messageId, s3Key, bucket));
            }

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Upload failed for messageId " + messageId + ": " + e.getMessage());
            if (!task.isCancelled()) {
                postFailure(task, e.getMessage());
            }
        } finally {
            activeTasks.remove(messageId);
            // Always clean up the encrypted temp file (it's a duplicate of the
            // plaintext encrypted in cache — the original local file is preserved)
            if (encryptedTempFile != null && encryptedTempFile.exists()) {
                try { encryptedTempFile.delete(); } catch (Exception ignored) {}
            }
        }
    }

    // -------------------- Presign API Starts Here ---------------------

    /**
     * Requests a presigned upload URL from the backend server.
     * Runs on background thread — no UI work here.
     */
    private PresignResponse requestPresignedUrl(String fileName, long fileSize, String jwtToken) {
        HttpURLConnection   connection  =   null;
        try {
            JSONObject  requestBody     =   new JSONObject();
            requestBody.put("file_name", fileName);
            requestBody.put("file_size", fileSize);

            URL url         =   new URL(API.FILES_PRESIGN);
            connection      =   (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.connect();

            try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(requestBody.toString());
                writer.flush();
            }

            int statusCode  =   connection.getResponseCode();

            if (statusCode >= 200 && statusCode < 300) {
                StringBuilder   response    =   new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }

                Log.e(Extras.LOG_MESSAGE, "Presign raw response: " + response);

                JSONObject  jsonResponse    =   new JSONObject(response.toString());
                // Try multiple field names — backend may use any of these
                String      presignedUrl    =   jsonResponse.optString("presigned_url", "");
                if (presignedUrl.isEmpty()) presignedUrl = jsonResponse.optString("upload_url", "");
                if (presignedUrl.isEmpty()) presignedUrl = jsonResponse.optString("url", "");
                if (presignedUrl.isEmpty()) presignedUrl = jsonResponse.optString("presignedUrl", "");
                if (presignedUrl.isEmpty()) presignedUrl = jsonResponse.optString("presigned", "");
                String      s3Key           =   jsonResponse.optString("s3_key", "");
                if (s3Key.isEmpty())        s3Key = jsonResponse.optString("key", "");
                String      bucket          =   jsonResponse.optString("bucket", "");

                if (presignedUrl.isEmpty()) {
                    Log.e(Extras.LOG_MESSAGE, "Presign response missing upload URL. Available keys: "
                            + jsonResponse.keys().toString() + " — full body: " + response);
                    return null;
                }

                return new PresignResponse(presignedUrl, s3Key, bucket);
            } else {
                Log.e(Extras.LOG_MESSAGE, "Presign API failed with status: " + statusCode);
                return null;
            }

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Presign API exception: " + e.getMessage());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    // -------------------- S3 Chunked Upload Starts Here ---------------------

    /**
     * Uploads a file to S3 via HTTP PUT with 256KB chunks.
     * Progress is throttled to max 1 main-thread post per PROGRESS_THROTTLE_MS.
     */
    private boolean uploadToS3(Uri fileUri, String presignedUrl, long fileSize, UploadTask task) {
        HttpURLConnection   connection      =   null;
        InputStream         inputStream     =   null;
        OutputStream        outputStream    =   null;

        try {
            inputStream     =   contentResolver.openInputStream(fileUri);
            if (inputStream == null) {
                postFailure(task, "Unable to open file for reading");
                return false;
            }

            URL url         =   new URL(presignedUrl);
            connection      =   (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("PUT");
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(fileSize);
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.connect();

            outputStream    =   connection.getOutputStream();

            byte[]  buffer                      =   new byte[CHUNK_SIZE];
            long    totalWritten                =   0;
            int     bytesRead;
            int     lastReportedProgress        =   -1;
            long    lastProgressPostTime        =   0;
            String  messageId                   =   task.getMessageId();
            AtomicBoolean cancelled             =   task.getCancelFlag();

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (cancelled.get()) {
                    Log.e(Extras.LOG_MESSAGE, "Upload cancelled during transfer for messageId: " + messageId);
                    return false;
                }

                outputStream.write(buffer, 0, bytesRead);
                totalWritten    +=  bytesRead;

                int     progress    =   (int) (totalWritten * 100 / fileSize);
                long    now         =   System.currentTimeMillis();

                // Throttle: only post to main thread if progress changed AND enough time has passed
                if (progress != lastReportedProgress && (now - lastProgressPostTime >= PROGRESS_THROTTLE_MS)) {
                    lastReportedProgress    =   progress;
                    lastProgressPostTime    =   now;
                    int finalProgress       =   progress;
                    mainHandler.post(() -> task.getCallback().onProgress(messageId, finalProgress));
                }
            }

            outputStream.flush();

            // Always post final 100% progress
            if (lastReportedProgress < 100 && !cancelled.get()) {
                mainHandler.post(() -> task.getCallback().onProgress(messageId, 100));
            }

            int statusCode  =   connection.getResponseCode();
            Log.e(Extras.LOG_MESSAGE, "S3 upload response code: " + statusCode + " for messageId: " + messageId);

            if (statusCode >= 200 && statusCode < 300) {
                return true;
            } else {
                // Pull the S3 error XML body so 403 becomes diagnostic.
                // Typical payload: <Error><Code>AccessDenied</Code><Message>...</Message></Error>
                String errorBody = "";
                try (java.io.InputStream es = connection.getErrorStream()) {
                    if (es != null) {
                        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                        byte[] buf = new byte[2048];
                        int n;
                        while ((n = es.read(buf)) != -1) bos.write(buf, 0, n);
                        errorBody = bos.toString("UTF-8");
                    }
                } catch (Exception ignored) {}
                Log.e(Extras.LOG_MESSAGE, "S3 upload error body: " + errorBody);
                postFailure(task, "S3 upload failed with status: " + statusCode
                        + (errorBody.isEmpty() ? "" : " — " + errorBody));
                return false;
            }

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "S3 upload exception: " + e.getMessage());
            if (!task.isCancelled()) {
                postFailure(task, "Upload error: " + e.getMessage());
            }
            return false;
        } finally {
            try {
                if (inputStream != null)    inputStream.close();
                if (outputStream != null)   outputStream.close();
            } catch (Exception ignored) {}
            if (connection != null)         connection.disconnect();
        }
    }

    // -------------------- Main Thread Posting Helpers ---------------------

    private void postFailure(UploadTask task, String error) {
        String  messageId   =   task.getMessageId();
        mainHandler.post(() -> task.getCallback().onFailure(messageId, error));
        activeTasks.remove(messageId);
    }

    // -------------------- File Utility Methods Starts Here ---------------------

    /**
     * Gets the size of a file from its URI. Handles both content:// and file:// schemes.
     *
     * @param uri the URI of the file
     * @return file size in bytes, or -1 if unable to determine
     */
    private long getFileSize(Uri uri) {
        // Handle file:// URIs directly via File API
        if ("file".equals(uri.getScheme())) {
            try {
                String  path    =   uri.getPath();
                if (path != null) {
                    java.io.File    file    =   new java.io.File(path);
                    if (file.exists()) {
                        return file.length();
                    }
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Error getting file size from file URI: " + e.getMessage());
            }
            return -1;
        }

        // content:// URIs go through ContentResolver
        try (Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex   =   cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    return cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error getting file size: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Gets the display name of a file from its URI. Handles both content:// and file:// schemes.
     *
     * @param uri the URI of the file
     * @return the file name, or "unknown" if not available
     */
    private String getFileName(Uri uri) {
        // For file:// URIs, use the last path segment
        if ("file".equals(uri.getScheme())) {
            String  path    =   uri.getPath();
            if (path != null) {
                int slashIndex  =   path.lastIndexOf('/');
                if (slashIndex >= 0 && slashIndex < path.length() - 1) {
                    return path.substring(slashIndex + 1);
                }
                return path;
            }
            return "unknown";
        }

        String  fileName    =   "unknown";
        try (Cursor cursor = contentResolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex   =   cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    fileName    =   cursor.getString(nameIndex);
                }
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error getting file name: " + e.getMessage());
        }
        return fileName;
    }

    // -------------------- Inner Classes Starts Here ---------------------

    private static class UploadTask {

        private final String                messageId;
        private final AttachmentModel       attachmentModel;
        private final UploadCallback        callback;
        private final AtomicBoolean         cancelFlag;
        private Future<?>                   future;

        public UploadTask(String messageId, AttachmentModel attachmentModel, UploadCallback callback) {
            this.messageId          =   messageId;
            this.attachmentModel    =   attachmentModel;
            this.callback           =   callback;
            this.cancelFlag         =   new AtomicBoolean(false);
        }

        public String getMessageId()               { return messageId; }
        public AttachmentModel getAttachmentModel() { return attachmentModel; }
        public UploadCallback getCallback()         { return callback; }
        public AtomicBoolean getCancelFlag()        { return cancelFlag; }
        public boolean isCancelled()                { return cancelFlag.get(); }

        public void cancel() {
            cancelFlag.set(true);
            if (future != null && !future.isDone()) {
                future.cancel(true);
            }
        }

        public void setFuture(Future<?> future) {
            this.future     =   future;
        }
    }

    // -------------------- Callback Interface Starts Here ---------------------

    /**
     * Callback interface for tracking upload lifecycle events.
     * All methods are guaranteed to be called on the MAIN thread.
     */
    public interface UploadCallback {
        void onProgress(String messageId, int percentage);
        void onSuccess(String messageId, String s3Key, String bucket);
        void onFailure(String messageId, String error);
        void onCancelled(String messageId);
    }
}
