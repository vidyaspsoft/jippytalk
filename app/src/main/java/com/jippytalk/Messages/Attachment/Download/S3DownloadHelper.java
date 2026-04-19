package com.jippytalk.Messages.Attachment.Download;

/**
 * Developer Name: Vidya Sagar
 * Created on: 09-04-2026
 */

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.jippytalk.Extras;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * S3DownloadHelper - Handles file downloads from S3 URLs.
 *
 * Thread safety:
 * - All heavy work (HTTP download, file write) runs on a background ExecutorService
 * - All callbacks are delivered on the MAIN thread via Handler so callers can safely update UI
 * - Progress is throttled to max 1 update per 200ms to avoid flooding the main thread
 * - Cancel flag uses AtomicBoolean checked at every chunk boundary
 * - Partial files are deleted on cancel or failure
 *
 * Downloaded files are stored in: context.getFilesDir()/media/{fileName}
 *
 * Usage:
 *   S3DownloadHelper helper = new S3DownloadHelper(context);
 *   helper.downloadFile(messageId, url, fileName, targetDir, key, iv, callback);
 *   helper.cancelDownload(messageId);
 *
 * Retry is handled one level up in MediaTransferManager, which re-invokes
 * downloadFile with the stored DownloadMetadata after clearing any partial
 * state — this class only knows how to do a single download transaction.
 */
public class S3DownloadHelper {

    // ---- Constants ----

    private static final int        CHUNK_SIZE                  =   256 * 1024;     // 256 KB
    private static final int        MAX_CONCURRENT_DOWNLOADS    =   3;
    private static final int        CONNECT_TIMEOUT             =   15000;          // 15 seconds
    private static final int        READ_TIMEOUT                =   60000;          // 60 seconds
    private static final long       PROGRESS_THROTTLE_MS        =   200;            // min 200ms between UI updates
    // ---- Fields ----

    private final Context                                       context;
    private final ExecutorService                               downloadExecutor;
    private final ConcurrentHashMap<String, DownloadTask>       activeTasks;
    private final Handler                                       mainHandler;

    // ---- Constructor ----

    public S3DownloadHelper(Context context) {
        this.context            =   context.getApplicationContext();
        this.downloadExecutor   =   Executors.newFixedThreadPool(MAX_CONCURRENT_DOWNLOADS);
        this.activeTasks        =   new ConcurrentHashMap<>();
        this.mainHandler        =   new Handler(Looper.getMainLooper());
    }

    // -------------------- Public Methods Starts Here ---------------------

    /**
     * Starts downloading a file to the specified target directory.
     * All callbacks are delivered on the main thread.
     *
     * @param messageId         unique message identifier to track this download
     * @param downloadUrl       the URL to download from
     * @param fileName          the name to save the file as locally
     * @param targetDirectory   the directory to save the downloaded file in
     * @param callback          callback for progress, success, failure, cancellation (main thread)
     */
    /**
     * Downloads a file from S3 and, when b64Key/b64Iv are non-empty, AES-256-GCM
     * decrypts the downloaded ciphertext bytes before writing to the final
     * destination. Pass empty strings for b64Key/b64Iv to skip decryption.
     */
    public void downloadFile(String messageId, String downloadUrl, String fileName,
                             File targetDirectory, String b64Key, String b64Iv,
                             DownloadCallback callback) {
        Log.e(Extras.LOG_MESSAGE, "S3DownloadHelper.downloadFile ENTRY for " + messageId
                + " hasKey=" + (b64Key != null && !b64Key.isEmpty())
                + " activeTasks=" + activeTasks.size());
        if (activeTasks.containsKey(messageId)) {
            Log.e(Extras.LOG_MESSAGE, "Download already in progress for messageId: " + messageId);
            return;
        }

        if (!targetDirectory.exists()) {
            boolean mk = targetDirectory.mkdirs();
            Log.e(Extras.LOG_MESSAGE, "Created target dir " + targetDirectory + " ok=" + mk);
        }

        DownloadTask    downloadTask    =   new DownloadTask(messageId, downloadUrl, fileName,
                targetDirectory, b64Key, b64Iv, callback);
        activeTasks.put(messageId, downloadTask);

        Log.e(Extras.LOG_MESSAGE, "S3DownloadHelper submitting executeDownload to executor for " + messageId);
        Future<?>       future          =   downloadExecutor.submit(() -> executeDownload(downloadTask));
        downloadTask.setFuture(future);
    }

    /**
     * Cancels an active download. Deletes any partial file.
     * callback.onCancelled() is delivered on the main thread.
     */
    public void cancelDownload(String messageId) {
        DownloadTask    task    =   activeTasks.get(messageId);
        if (task != null) {
            task.cancel();
            activeTasks.remove(messageId);

            File    partialFile =   new File(task.getTargetDirectory(), task.getFileName());
            if (partialFile.exists()) {
                partialFile.delete();
            }

            mainHandler.post(() -> task.getCallback().onCancelled(messageId));
            Log.e(Extras.LOG_MESSAGE, "Download cancelled for messageId: " + messageId);
        }
    }

    public boolean isDownloading(String messageId) {
        return activeTasks.containsKey(messageId);
    }

    /**
     * Cancels all active downloads. Call this in onDestroy().
     */
    public void cancelAll() {
        for (DownloadTask task : activeTasks.values()) {
            task.cancel();
            File partialFile = new File(task.getTargetDirectory(), task.getFileName());
            if (partialFile.exists()) partialFile.delete();
        }
        activeTasks.clear();
    }

    // -------------------- Download Execution Starts Here ---------------------

    /**
     * Executes the download on a background thread.
     * All callbacks are posted to the main thread via mainHandler.
     * Progress is throttled to PROGRESS_THROTTLE_MS intervals.
     */
    private void executeDownload(DownloadTask task) {
        Log.e(Extras.LOG_MESSAGE, "executeDownload START for " + task.getMessageId()
                + " url=" + task.getDownloadUrl());
        HttpURLConnection   connection      =   null;
        InputStream         inputStream     =   null;
        OutputStream        outputStream    =   null;
        boolean             needsDecrypt    =   task.getEncryptionKey() != null && !task.getEncryptionKey().isEmpty()
                                                && task.getEncryptionIv() != null && !task.getEncryptionIv().isEmpty();
        // When decrypting, write ciphertext to a cache temp file first, then
        // decrypt-stream into the final destination once download finishes.
        File                finalFile       =   new File(task.getTargetDirectory(), task.getFileName());
        File                writeTarget     =   needsDecrypt
                                                ? new File(context.getCacheDir(),
                                                        "dl_enc_" + task.getMessageId() + "_"
                                                            + System.currentTimeMillis() + ".bin")
                                                : finalFile;
        String              messageId       =   task.getMessageId();

        try {
            if (task.isCancelled()) return;

            URL url     =   new URL(task.getDownloadUrl());
            connection  =   (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            // Publish the connection on the task so cancelDownload (called
            // from the network-loss callback) can disconnect from another
            // thread and immediately abort a hung read.
            task.setConnection(connection);
            Log.e(Extras.LOG_MESSAGE, "executeDownload connecting to " + task.getDownloadUrl());
            connection.connect();

            int statusCode  =   connection.getResponseCode();
            Log.e(Extras.LOG_MESSAGE, "executeDownload HTTP " + statusCode + " for " + task.getMessageId());
            if (statusCode < 200 || statusCode >= 300) {
                // Pull S3 error body for diagnostics (e.g. <Code>AccessDenied</Code>)
                String errorBody = "";
                try (InputStream es = connection.getErrorStream()) {
                    if (es != null) {
                        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                        byte[] eb = new byte[2048];
                        int en;
                        while ((en = es.read(eb)) != -1) bos.write(eb, 0, en);
                        errorBody = bos.toString("UTF-8");
                    }
                } catch (Exception ignored) {}
                Log.e(Extras.LOG_MESSAGE, "executeDownload S3 error body: " + errorBody);
                postFailure(task, "Download failed with status: " + statusCode
                        + (errorBody.isEmpty() ? "" : " — " + errorBody));
                return;
            }

            long    contentLength           =   connection.getContentLengthLong();
            Log.e(Extras.LOG_MESSAGE, "executeDownload contentLength=" + contentLength
                    + " for " + messageId);
            inputStream                     =   connection.getInputStream();
            outputStream                    =   new FileOutputStream(writeTarget);

            byte[]  buffer                  =   new byte[CHUNK_SIZE];
            long    totalRead               =   0;
            int     bytesRead;
            int     lastReportedProgress    =   -1;
            long    lastProgressPostTime    =   0;
            long    lastBytesLogPoint       =   0;            // log every ~5MB of progress
            AtomicBoolean cancelled         =   task.getCancelFlag();

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (cancelled.get()) {
                    Log.e(Extras.LOG_MESSAGE, "Download cancelled during transfer for messageId: " + messageId);
                    // writeTarget cleanup happens in finally
                    return;
                }

                outputStream.write(buffer, 0, bytesRead);
                totalRead   +=  bytesRead;

                // Unconditional bytes-flowing log every ~5 MB so we can see if
                // the read loop is actually progressing vs hung. Useful when
                // contentLength is -1 (chunked transfer) and the throttled
                // percent log below stays silent.
                if (totalRead - lastBytesLogPoint >= 5L * 1024 * 1024) {
                    lastBytesLogPoint = totalRead;
                    Log.e(Extras.LOG_MESSAGE, "executeDownload progress: "
                            + (totalRead / (1024 * 1024)) + " MB read for " + messageId);
                }

                // Throttled progress reporting — drives the UI progress bar.
                // Falls back to "indeterminate" updates (progress 0) when the
                // server didn't send a Content-Length so the spinner still
                // animates instead of looking frozen.
                long    now         =   System.currentTimeMillis();
                if (contentLength > 0) {
                    int     progress    =   (int) (totalRead * 100 / contentLength);
                    if (progress != lastReportedProgress && (now - lastProgressPostTime >= PROGRESS_THROTTLE_MS)) {
                        lastReportedProgress    =   progress;
                        lastProgressPostTime    =   now;
                        int finalProgress       =   progress;
                        mainHandler.post(() -> task.getCallback().onProgress(messageId, finalProgress));
                    }
                } else if (now - lastProgressPostTime >= PROGRESS_THROTTLE_MS) {
                    lastProgressPostTime = now;
                    mainHandler.post(() -> task.getCallback().onProgress(messageId, 0));
                }
            }
            Log.e(Extras.LOG_MESSAGE, "executeDownload read-loop EXIT totalRead=" + totalRead
                    + " for " + messageId);

            outputStream.flush();
            try { outputStream.close(); } catch (Exception ignored) {}
            outputStream = null;

            if (task.isCancelled()) {
                if (writeTarget.exists()) writeTarget.delete();
                return;
            }

            // Decrypt cache temp → final destination, then delete cipher temp.
            //
            // IMPORTANT: Android's CipherInputStream(AES/GCM) buffers the
            // ENTIRE ciphertext in memory before releasing any plaintext —
            // GCM auth-tag verification requires the full input. For a 50 MB
            // file this allocates ~150 MB of Large Object Space and thrashes
            // GC; for bigger files it'll OOM. The proper fix is chunked
            // encryption (per SOW M2 spec). For now we catch BOTH Exception
            // and OutOfMemoryError so a failure surfaces as a retry icon
            // instead of silently killing the executor thread (which leaves
            // the row stuck on the spinner forever).
            if (needsDecrypt) {
                Log.e(Extras.LOG_MESSAGE, "Decrypt START for " + messageId
                        + " ciphertext=" + writeTarget.length() + " bytes");
                long decStart = System.currentTimeMillis();
                try (InputStream cin = new java.io.FileInputStream(writeTarget);
                     OutputStream pout = new FileOutputStream(finalFile)) {
                    com.jippytalk.Encryption.MessageCryptoHelper.decryptStream(
                            cin, pout, task.getEncryptionKey(), task.getEncryptionIv());
                } catch (OutOfMemoryError oom) {
                    Log.e(Extras.LOG_MESSAGE, "Decrypt OOM for " + messageId
                            + " — file too large for in-memory GCM decrypt: "
                            + oom.getMessage());
                    if (finalFile.exists()) finalFile.delete();
                    if (writeTarget.exists()) writeTarget.delete();
                    postFailure(task, "File too large to decrypt on this device "
                            + "(needs chunked encryption — SOW M2 work)");
                    return;
                } catch (Exception decryptErr) {
                    Log.e(Extras.LOG_MESSAGE, "Decrypt failed for " + messageId
                            + ": " + decryptErr.getMessage());
                    if (finalFile.exists()) finalFile.delete();
                    if (writeTarget.exists()) writeTarget.delete();
                    postFailure(task, "Decrypt failed: " + decryptErr.getMessage());
                    return;
                }
                Log.e(Extras.LOG_MESSAGE, "Decrypt DONE for " + messageId
                        + " plaintext=" + finalFile.length() + " bytes elapsed="
                        + (System.currentTimeMillis() - decStart) + " ms");
                if (writeTarget.exists()) writeTarget.delete();
            }

            // Always post final 100%
            if (lastReportedProgress < 100) {
                mainHandler.post(() -> task.getCallback().onProgress(messageId, 100));
            }

            Log.e(Extras.LOG_MESSAGE, "Download completed: " + finalFile.getAbsolutePath());
            String  localPath   =   finalFile.getAbsolutePath();
            mainHandler.post(() -> task.getCallback().onSuccess(messageId, localPath));

        } catch (Throwable e) {
            // Catching Throwable (not just Exception) so OOM and other Errors
            // surface as a retry icon instead of silently killing the
            // executor thread.
            Log.e(Extras.LOG_MESSAGE, "Download failed for messageId " + messageId
                    + " (" + e.getClass().getSimpleName() + "): " + e.getMessage());
            if (writeTarget.exists()) writeTarget.delete();
            if (needsDecrypt && finalFile.exists()) finalFile.delete();
            if (!task.isCancelled()) {
                postFailure(task, "Download error (" + e.getClass().getSimpleName()
                        + "): " + e.getMessage());
            }
        } finally {
            try {
                if (inputStream != null)    inputStream.close();
                if (outputStream != null)   outputStream.close();
            } catch (Exception ignored) {}
            if (connection != null)         connection.disconnect();
            // Clean up the cipher temp file in the cache. On the success path we
            // already deleted it after decryption; this is a safety net for the
            // cancel-mid-stream and exception paths.
            if (needsDecrypt && writeTarget != finalFile && writeTarget.exists()) {
                try { writeTarget.delete(); } catch (Exception ignored) {}
            }
            activeTasks.remove(messageId);
        }
    }

    // -------------------- Main Thread Posting Helpers ---------------------

    private void postFailure(DownloadTask task, String error) {
        String  messageId   =   task.getMessageId();
        mainHandler.post(() -> task.getCallback().onFailure(messageId, error));
        activeTasks.remove(messageId);
    }

    // -------------------- Inner Classes Starts Here ---------------------

    private static class DownloadTask {

        private final String                messageId;
        private final String                downloadUrl;
        private final String                fileName;
        private final File                  targetDirectory;
        private final String                encryptionKey;
        private final String                encryptionIv;
        private final DownloadCallback      callback;
        private final AtomicBoolean         cancelFlag;
        private Future<?>                   future;
        // Live connection ref so cancel can disconnect from another thread
        // and unblock the executor thread's blocked inputStream.read(),
        // which otherwise sits for up to READ_TIMEOUT (60s) when the
        // network drops mid-download.
        private volatile HttpURLConnection  connection;

        public DownloadTask(String messageId, String downloadUrl, String fileName,
                            File targetDirectory, String encryptionKey, String encryptionIv,
                            DownloadCallback callback) {
            this.messageId          =   messageId;
            this.downloadUrl        =   downloadUrl;
            this.fileName           =   fileName;
            this.targetDirectory    =   targetDirectory;
            this.encryptionKey      =   encryptionKey;
            this.encryptionIv       =   encryptionIv;
            this.callback           =   callback;
            this.cancelFlag         =   new AtomicBoolean(false);
        }

        public String getMessageId()           { return messageId; }
        public String getDownloadUrl()         { return downloadUrl; }
        public String getFileName()            { return fileName; }
        public File getTargetDirectory()       { return targetDirectory; }
        public String getEncryptionKey()       { return encryptionKey; }
        public String getEncryptionIv()        { return encryptionIv; }
        public DownloadCallback getCallback()  { return callback; }
        public AtomicBoolean getCancelFlag()   { return cancelFlag; }
        public boolean isCancelled()           { return cancelFlag.get(); }

        public void setConnection(HttpURLConnection c) { this.connection = c; }

        public void cancel() {
            cancelFlag.set(true);
            // Force the in-flight HTTP read to throw IOException by
            // disconnecting from another thread. Without this the
            // executor thread sits blocked in inputStream.read() for
            // the full 60s socket timeout after the network drops.
            HttpURLConnection c = connection;
            if (c != null) {
                try { c.disconnect(); } catch (Throwable ignored) {}
            }
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
     * Callback interface for tracking download lifecycle events.
     * All methods are guaranteed to be called on the MAIN thread.
     */
    public interface DownloadCallback {
        void onProgress(String messageId, int percentage);
        void onSuccess(String messageId, String localFilePath);
        void onFailure(String messageId, String error);
        void onCancelled(String messageId);
    }
}
