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
 *   helper.downloadFile(messageId, url, fileName, callback);  // callback runs on main thread
 *   helper.cancelDownload(messageId);
 *   helper.retryDownload(messageId, url, fileName, callback);
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
    public void downloadFile(String messageId, String downloadUrl, String fileName,
                             File targetDirectory, DownloadCallback callback) {
        if (activeTasks.containsKey(messageId)) {
            Log.e(Extras.LOG_MESSAGE, "Download already in progress for messageId: " + messageId);
            return;
        }

        if (!targetDirectory.exists()) {
            targetDirectory.mkdirs();
        }

        DownloadTask    downloadTask    =   new DownloadTask(messageId, downloadUrl, fileName, targetDirectory, callback);
        activeTasks.put(messageId, downloadTask);

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

    /**
     * Retries a failed or cancelled download. Cancels any existing task first.
     */
    public void retryDownload(String messageId, String downloadUrl, String fileName,
                              File targetDirectory, DownloadCallback callback) {
        DownloadTask    existing    =   activeTasks.remove(messageId);
        if (existing != null) {
            existing.cancel();
            File partialFile = new File(existing.getTargetDirectory(), existing.getFileName());
            if (partialFile.exists()) partialFile.delete();
        }
        downloadFile(messageId, downloadUrl, fileName, targetDirectory, callback);
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
        HttpURLConnection   connection      =   null;
        InputStream         inputStream     =   null;
        OutputStream        outputStream    =   null;
        File                targetFile      =   new File(task.getTargetDirectory(), task.getFileName());
        String              messageId       =   task.getMessageId();

        try {
            if (task.isCancelled()) return;

            URL url     =   new URL(task.getDownloadUrl());
            connection  =   (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.connect();

            int statusCode  =   connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                postFailure(task, "Download failed with status: " + statusCode);
                return;
            }

            long    contentLength           =   connection.getContentLength();
            inputStream                     =   connection.getInputStream();
            outputStream                    =   new FileOutputStream(targetFile);

            byte[]  buffer                  =   new byte[CHUNK_SIZE];
            long    totalRead               =   0;
            int     bytesRead;
            int     lastReportedProgress    =   -1;
            long    lastProgressPostTime    =   0;
            AtomicBoolean cancelled         =   task.getCancelFlag();

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                if (cancelled.get()) {
                    Log.e(Extras.LOG_MESSAGE, "Download cancelled during transfer for messageId: " + messageId);
                    return;
                }

                outputStream.write(buffer, 0, bytesRead);
                totalRead   +=  bytesRead;

                // Throttled progress reporting
                if (contentLength > 0) {
                    int     progress    =   (int) (totalRead * 100 / contentLength);
                    long    now         =   System.currentTimeMillis();

                    if (progress != lastReportedProgress && (now - lastProgressPostTime >= PROGRESS_THROTTLE_MS)) {
                        lastReportedProgress    =   progress;
                        lastProgressPostTime    =   now;
                        int finalProgress       =   progress;
                        mainHandler.post(() -> task.getCallback().onProgress(messageId, finalProgress));
                    }
                }
            }

            outputStream.flush();

            if (task.isCancelled()) {
                if (targetFile.exists()) targetFile.delete();
                return;
            }

            // Always post final 100%
            if (lastReportedProgress < 100) {
                mainHandler.post(() -> task.getCallback().onProgress(messageId, 100));
            }

            Log.e(Extras.LOG_MESSAGE, "Download completed: " + targetFile.getAbsolutePath());
            String  localPath   =   targetFile.getAbsolutePath();
            mainHandler.post(() -> task.getCallback().onSuccess(messageId, localPath));

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Download failed for messageId " + messageId + ": " + e.getMessage());
            if (targetFile.exists()) targetFile.delete();
            if (!task.isCancelled()) {
                postFailure(task, "Download error: " + e.getMessage());
            }
        } finally {
            try {
                if (inputStream != null)    inputStream.close();
                if (outputStream != null)   outputStream.close();
            } catch (Exception ignored) {}
            if (connection != null)         connection.disconnect();
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
        private final DownloadCallback      callback;
        private final AtomicBoolean         cancelFlag;
        private Future<?>                   future;

        public DownloadTask(String messageId, String downloadUrl, String fileName,
                            File targetDirectory, DownloadCallback callback) {
            this.messageId          =   messageId;
            this.downloadUrl        =   downloadUrl;
            this.fileName           =   fileName;
            this.targetDirectory    =   targetDirectory;
            this.callback           =   callback;
            this.cancelFlag         =   new AtomicBoolean(false);
        }

        public String getMessageId()           { return messageId; }
        public String getDownloadUrl()         { return downloadUrl; }
        public String getFileName()            { return fileName; }
        public File getTargetDirectory()       { return targetDirectory; }
        public DownloadCallback getCallback()  { return callback; }
        public AtomicBoolean getCancelFlag()   { return cancelFlag; }
        public boolean isCancelled()           { return cancelFlag.get(); }

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
