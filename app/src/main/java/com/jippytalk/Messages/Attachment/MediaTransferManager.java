package com.jippytalk.Messages.Attachment;

/**
 * Developer Name: Vidya Sagar
 * Created on: 09-04-2026
 */

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.jippytalk.Extras;
import com.jippytalk.Messages.Adapter.MessageAdapter;
import com.jippytalk.Messages.Attachment.Download.S3DownloadHelper;
import com.jippytalk.Messages.Attachment.Model.AttachmentModel;
import com.jippytalk.Messages.Attachment.Storage.MediaStorageHelper;
import com.jippytalk.Messages.Attachment.Upload.S3UploadHelper;

import java.io.File;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MediaTransferManager - Single entry point for all media upload/download operations.
 *
 * Orchestrates the full WhatsApp-style transfer flow:
 *
 * SENDER:
 *   1. Copies picked file to sent/ folder (background thread)
 *   2. Shows message in adapter immediately from local sent/ path
 *   3. Uploads to S3 via presigned URL with progress
 *   4. On success → notifies via callback to send WebSocket file event
 *   5. On failure → shows retry in adapter
 *
 * RECEIVER:
 *   1. Shows message with download button
 *   2. Downloads from S3 to received/ folder with progress
 *   3. On success → notifies via callback to send WebSocket file_downloaded event
 *   4. On failure → shows retry in adapter
 *
 * All adapter updates happen on the main thread (helpers guarantee this).
 * All file copy/IO runs on background threads.
 *
 * Usage:
 *   MediaTransferManager manager = new MediaTransferManager(context);
 *   manager.setAdapter(adapter);
 *   manager.setTransferEventListener(listener);
 *   manager.sendAttachment(messageId, attachmentModel);
 *   manager.downloadAttachment(messageId, downloadUrl, fileName, contentType, fileTransferId);
 */
public class MediaTransferManager {

    // ---- Fields ----

    private final Context                                           context;
    private final MediaStorageHelper                                mediaStorageHelper;
    private final S3UploadHelper                                    s3UploadHelper;
    private final S3DownloadHelper                                  s3DownloadHelper;
    private final ExecutorService                                   fileCopyExecutor;
    private MessageAdapter                                          adapter;
    private TransferEventListener                                   transferEventListener;
    private final ConcurrentHashMap<String, AttachmentModel>        pendingUploads;
    private final ConcurrentHashMap<String, DownloadMetadata>       pendingDownloads;

    // ---- Constructor ----

    public MediaTransferManager(Context context) {
        this.context            =   context.getApplicationContext();
        this.mediaStorageHelper =   MediaStorageHelper.getInstance(context);
        this.s3UploadHelper     =   new S3UploadHelper(context);
        this.s3DownloadHelper   =   new S3DownloadHelper(context);
        this.fileCopyExecutor   =   Executors.newSingleThreadExecutor();
        this.pendingUploads     =   new ConcurrentHashMap<>();
        this.pendingDownloads   =   new ConcurrentHashMap<>();
    }

    // -------------------- Setup Starts Here ---------------------

    /**
     * Sets the adapter for progress/state updates.
     * Must be called before any transfer operations.
     *
     * @param adapter the message adapter to update
     */
    public void setAdapter(MessageAdapter adapter) {
        this.adapter    =   adapter;
    }

    /**
     * Sets the listener to be notified when transfers complete or need WebSocket events.
     *
     * @param listener the transfer event listener
     */
    public void setTransferEventListener(TransferEventListener listener) {
        this.transferEventListener  =   listener;
    }

    // -------------------- Sender Flow Starts Here ---------------------

    /**
     * Starts the full sender flow for an attachment:
     * 1. Copy file to sent/ folder (background)
     * 2. Update adapter with upload progress overlay
     * 3. Upload to S3
     * 4. Notify listener on success with s3Key for WebSocket event
     *
     * @param messageId         the unique message ID for this attachment
     * @param attachmentModel   the attachment model from the picker
     */
    public void sendAttachment(String messageId, AttachmentModel attachmentModel) {
        pendingUploads.put(messageId, attachmentModel);

        // If no internet, skip the spinner entirely — the 500ms delayed
        // postTransferFailed will show the retry icon once the DB row exists.
        boolean hasNetwork = isNetworkAvailable();

        if (hasNetwork && adapter != null) {
            adapter.updateTransferProgress(messageId, 0, MessageAdapter.TRANSFER_IN_PROGRESS);
        }

        // Copy file to sent/ folder on background thread, then upload.
        fileCopyExecutor.execute(() -> {
            try {
                String  contentType     =   attachmentModel.getContentType();
                String  originalName   =   attachmentModel.getName();
                String  extension;
                if (originalName != null && originalName.contains(".")) {
                    extension = originalName.substring(originalName.lastIndexOf('.') + 1);
                } else {
                    extension = attachmentModel.getContentSubtype();
                }
                Uri     sourceUri       =   Uri.parse(attachmentModel.getMedia());

                String  localPath       =   mediaStorageHelper.copyToSentFolder(sourceUri, contentType, extension, originalName);

                if (localPath == null) {
                    Log.e(Extras.LOG_MESSAGE, "Failed to copy file to sent folder for messageId: " + messageId);
                    postTransferFailed(messageId, "Failed to copy file");
                    return;
                }

                // Update model with local path
                attachmentModel.setLocalFilePath(localPath);

                // Check network AFTER file copy — by now the DB row exists,
                // so markTransferFailed can find it and show the retry icon.
                if (!isNetworkAvailable()) {
                    Log.e(Extras.LOG_MESSAGE, "No internet — marking upload as failed for retry: " + messageId);
                    postTransferFailed(messageId, "No internet connection");
                    return;
                }

                // Now upload the local file to S3
                startUpload(messageId, attachmentModel, localPath);

            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Error in sendAttachment: " + e.getMessage());
                postTransferFailed(messageId, e.getMessage());
            }
        });
    }

    /**
     * Starts the S3 upload from the local sent/ file path.
     * Called after file is copied to sent/ folder.
     */
    private void startUpload(String messageId, AttachmentModel attachmentModel, String localFilePath) {
        // Override media URI with local file path for upload
        AttachmentModel uploadModel =   attachmentModel;
        uploadModel.setMedia(Uri.fromFile(new File(localFilePath)).toString());

        s3UploadHelper.uploadFile(messageId, uploadModel, new S3UploadHelper.UploadCallback() {
            @Override
            public void onProgress(String msgId, int percentage) {
                if (adapter != null) {
                    adapter.updateTransferProgress(msgId, percentage, MessageAdapter.TRANSFER_IN_PROGRESS);
                }
            }

            @Override
            public void onSuccess(String msgId, String s3Key, String bucket) {
                Log.e(Extras.LOG_MESSAGE, "Upload success for " + msgId + " s3Key: " + s3Key);
                attachmentModel.setS3Key(s3Key);
                attachmentModel.setBucket(bucket);
                pendingUploads.remove(msgId);

                if (adapter != null) {
                    adapter.markTransferComplete(msgId);
                }

                // Notify listener to send WebSocket file event
                if (transferEventListener != null) {
                    transferEventListener.onUploadComplete(msgId, s3Key, bucket, attachmentModel);
                }
            }

            @Override
            public void onFailure(String msgId, String error) {
                Log.e(Extras.LOG_MESSAGE, "Upload failed for " + msgId + ": " + error);
                if (adapter != null) {
                    adapter.markTransferFailed(msgId);
                }
                if (transferEventListener != null) {
                    transferEventListener.onUploadFailed(msgId, error);
                }
            }

            @Override
            public void onCancelled(String msgId) {
                Log.e(Extras.LOG_MESSAGE, "Upload cancelled for " + msgId);
                pendingUploads.remove(msgId);
                if (adapter != null) {
                    adapter.updateTransferProgress(msgId, 0, MessageAdapter.TRANSFER_CANCELLED);
                }
            }
        });
    }

    // -------------------- Receiver Flow Starts Here ---------------------

    /**
     * Starts the full receiver flow for an attachment:
     * 1. Check if file already exists locally
     * 2. Download from S3 to received/ folder
     * 3. On success → notify listener to send file_downloaded WebSocket event
     *
     * @param messageId         the unique message ID
     * @param downloadUrl       the S3 URL to download from
     * @param fileName          the original file name
     * @param contentType       "image", "video", "audio", or "document"
     * @param fileTransferId    the server's file transfer ID (for file_downloaded event)
     */
    public void downloadAttachment(String messageId, String downloadUrl, String fileName,
                                   String contentType, String fileTransferId) {

        // Check if already downloaded
        File    targetDir   =   mediaStorageHelper.getReceivedFolder(contentType);
        File    targetFile  =   new File(targetDir, fileName);
        if (targetFile.exists()) {
            Log.e(Extras.LOG_MESSAGE, "File already downloaded: " + targetFile.getAbsolutePath());
            if (adapter != null) {
                adapter.markTransferComplete(messageId);
            }
            if (transferEventListener != null) {
                transferEventListener.onDownloadComplete(messageId, targetFile.getAbsolutePath(), fileTransferId);
            }
            return;
        }

        // Store metadata for retry
        pendingDownloads.put(messageId, new DownloadMetadata(downloadUrl, fileName, contentType, fileTransferId));

        if (adapter != null) {
            adapter.updateTransferProgress(messageId, 0, MessageAdapter.TRANSFER_IN_PROGRESS);
        }

        s3DownloadHelper.downloadFile(messageId, downloadUrl, fileName, targetDir,
                new S3DownloadHelper.DownloadCallback() {
            @Override
            public void onProgress(String msgId, int percentage) {
                if (adapter != null) {
                    adapter.updateTransferProgress(msgId, percentage, MessageAdapter.TRANSFER_IN_PROGRESS);
                }
            }

            @Override
            public void onSuccess(String msgId, String localFilePath) {
                Log.e(Extras.LOG_MESSAGE, "Download success for " + msgId + " path: " + localFilePath);
                pendingDownloads.remove(msgId);

                if (adapter != null) {
                    adapter.markTransferComplete(msgId);
                }

                // Notify listener to send WebSocket file_downloaded event
                if (transferEventListener != null) {
                    transferEventListener.onDownloadComplete(msgId, localFilePath, fileTransferId);
                }
            }

            @Override
            public void onFailure(String msgId, String error) {
                Log.e(Extras.LOG_MESSAGE, "Download failed for " + msgId + ": " + error);
                if (adapter != null) {
                    adapter.markTransferFailed(msgId);
                }
            }

            @Override
            public void onCancelled(String msgId) {
                Log.e(Extras.LOG_MESSAGE, "Download cancelled for " + msgId);
                pendingDownloads.remove(msgId);
                if (adapter != null) {
                    adapter.updateTransferProgress(msgId, 0, MessageAdapter.TRANSFER_CANCELLED);
                }
            }
        });
    }

    // -------------------- Cancel / Retry Starts Here ---------------------

    /**
     * Cancels an active upload. The local file in sent/ is preserved for retry.
     *
     * @param messageId the message ID to cancel
     */
    public void cancelUpload(String messageId) {
        s3UploadHelper.cancelUpload(messageId);
    }

    /**
     * Retries a failed upload. Uses the already-copied file in sent/ folder.
     * Does NOT re-copy the file — goes straight to S3 upload.
     *
     * @param messageId the message ID to retry
     */
    public void retryUpload(String messageId) {
        AttachmentModel model   =   pendingUploads.get(messageId);
        if (model != null && model.getLocalFilePath() != null) {
            startUpload(messageId, model, model.getLocalFilePath());
        } else {
            Log.e(Extras.LOG_MESSAGE, "Cannot retry upload — no pending model for: " + messageId);
        }
    }

    /** Returns true if the messageId has an in-memory pending upload model. */
    public boolean hasPendingUpload(String messageId) {
        AttachmentModel model = pendingUploads.get(messageId);
        return model != null && model.getLocalFilePath() != null;
    }

    /**
     * Retries an upload using a rebuilt AttachmentModel (from DB metadata).
     * Called when the in-memory pendingUploads map has been cleared (app restart).
     */
    public void retryUploadWithModel(String messageId, AttachmentModel model) {
        if (model == null || model.getLocalFilePath() == null || model.getLocalFilePath().isEmpty()) {
            Log.e(Extras.LOG_MESSAGE, "Cannot retry — model has no local file path: " + messageId);
            return;
        }
        pendingUploads.put(messageId, model);
        startUpload(messageId, model, model.getLocalFilePath());
    }

    /**
     * Cancels an active download. Partial file is deleted.
     *
     * @param messageId the message ID to cancel
     */
    public void cancelDownload(String messageId) {
        s3DownloadHelper.cancelDownload(messageId);
    }

    /**
     * Retries a failed download using stored metadata.
     *
     * @param messageId the message ID to retry
     */
    public void retryDownload(String messageId) {
        DownloadMetadata    metadata    =   pendingDownloads.get(messageId);
        if (metadata != null) {
            downloadAttachment(messageId, metadata.downloadUrl, metadata.fileName,
                    metadata.contentType, metadata.fileTransferId);
        } else {
            Log.e(Extras.LOG_MESSAGE, "Cannot retry download — no pending metadata for: " + messageId);
        }
    }

    // -------------------- Failure Helper Starts Here ---------------------

    /**
     * Posts transfer failure to adapter + listener on the main thread.
     * Uses postDelayed(500ms) to ensure the DB insert from
     * insertLocalOutgoingAttachmentRow() has completed and the adapter
     * has the row — otherwise markTransferFailed can't find the position
     * and the retry icon never shows.
     */
    private void postTransferFailed(String messageId, String error) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (adapter != null) {
                adapter.markTransferFailed(messageId);
            }
            if (transferEventListener != null) {
                transferEventListener.onUploadFailed(messageId, error);
            }
        }, 500);
    }

    // -------------------- Network Check Starts Here ---------------------

    /** Quick check if the device has any active network connection. */
    private boolean isNetworkAvailable() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            android.net.Network network = cm.getActiveNetwork();
            if (network == null) return false;
            android.net.NetworkCapabilities caps = cm.getNetworkCapabilities(network);
            return caps != null && (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                    || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
                    || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------- Lifecycle Starts Here ---------------------

    /**
     * Cancels all active transfers. Call this in onDestroy().
     */
    public void cancelAll() {
        s3UploadHelper.cancelAll();
        s3DownloadHelper.cancelAll();
        pendingUploads.clear();
        pendingDownloads.clear();
    }

    // ---- Getters ----

    public MediaStorageHelper getMediaStorageHelper() {
        return mediaStorageHelper;
    }

    // -------------------- Inner Classes Starts Here ---------------------

    /**
     * Holds metadata needed for download retry.
     */
    private static class DownloadMetadata {

        final String    downloadUrl;
        final String    fileName;
        final String    contentType;
        final String    fileTransferId;

        DownloadMetadata(String downloadUrl, String fileName, String contentType, String fileTransferId) {
            this.downloadUrl        =   downloadUrl;
            this.fileName           =   fileName;
            this.contentType        =   contentType;
            this.fileTransferId     =   fileTransferId;
        }
    }

    // -------------------- Callback Interface Starts Here ---------------------

    /**
     * Listener for transfer events that require WebSocket communication.
     * All methods are called on the main thread.
     */
    public interface TransferEventListener {

        /**
         * Called when an upload completes successfully.
         * The caller should send a WebSocket "file" event with the s3Key.
         *
         * @param messageId         the message ID
         * @param s3Key             the S3 object key
         * @param bucket            the S3 bucket name
         * @param attachmentModel   the full attachment model with local path + S3 info
         */
        void onUploadComplete(String messageId, String s3Key, String bucket, AttachmentModel attachmentModel);

        /**
         * Called when a download completes successfully.
         * The caller should send a WebSocket "file_downloaded" event with the fileTransferId,
         * which triggers S3 deletion on the server.
         *
         * @param messageId         the message ID
         * @param localFilePath     the absolute path to the downloaded file in received/ folder
         * @param fileTransferId    the server's file transfer ID for the deletion event
         */
        void onDownloadComplete(String messageId, String localFilePath, String fileTransferId);

        /**
         * Called when an upload fails (network error, S3 rejection, etc.).
         * The caller should update the local DB row's status to MESSAGE_SEND_FAILED
         * so the retry icon persists across app restarts.
         *
         * @param messageId the message ID that failed
         * @param error     the error description
         */
        default void onUploadFailed(String messageId, String error) {}
    }
}
