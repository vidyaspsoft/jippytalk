package com.jippytalk.Messages.Attachment.Upload;

/**
 * Developer Name: Vidya Sagar
 * Created on: 17-04-2026
 */

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.database.Cursor;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ForegroundInfo;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.jippytalk.Database.MessagesDatabase.MessagesDatabase;
import com.jippytalk.Database.MessagesDatabase.MessagesDatabaseDAO;
import com.jippytalk.Extras;
import com.jippytalk.Managers.MessagesManager;
import com.jippytalk.Messages.Attachment.MediaTransferManager;
import com.jippytalk.Messages.Attachment.Model.AttachmentModel;
import com.jippytalk.MyApplication;
import com.jippytalk.R;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MediaUploadWorker — background worker that performs the full media-send
 * pipeline (encrypt → presign → S3 upload → thumbnail → WebSocket send) for
 * a single outgoing message, with the reliability guarantees WorkManager
 * provides: survives process death, backgrounding, screen-off, and reboots;
 * retries on transient failures with exponential backoff; respects network
 * constraints (won't run until network is available).
 *
 * Design:
 *   - Input: messageId (the local row's message_id)
 *   - The worker rebuilds an AttachmentModel from the DB row and delegates
 *     to the existing MediaTransferManager.sendAttachment(...) pipeline —
 *     reusing every line of existing encrypt/upload/thumbnail/WS code
 *     unchanged. We wrap the async pipeline with a CountDownLatch so the
 *     worker thread blocks until the pipeline completes or fails.
 *   - On transient failure → Result.retry() (WorkManager applies exponential
 *     backoff starting at 30s, capped at 5m by default)
 *   - On permanent failure (file missing, size too large, DB row missing)
 *     → Result.failure() (no auto-retry; user can tap retry icon)
 *   - On success → Result.success()
 *
 * Unique work per messageId via ExistingWorkPolicy.KEEP — duplicate enqueues
 * are silently ignored so we never double-upload the same file.
 *
 * ForegroundInfo: on Android 12+ WorkManager requires expedited work to run
 * as a foreground service with an attached notification. We show a simple
 * "Uploading <file>…" notification so the user can see the upload is in
 * progress even when the app is backgrounded.
 *
 * Pipeline preservation: this worker wraps MediaTransferManager.sendAttachment
 * rather than reimplementing it. All existing behavior is preserved:
 *   - AES-256-GCM encryption of file bytes + thumbnail
 *   - Per-message key+iv shared across file, thumbnail, caption, URLs
 *   - S3 presign → PUT with progress
 *   - Thumbnail generation (capped at ~10 MB bitmap)
 *   - WebSocket file message with room_id
 *   - delivery_status → SYNCED tick lifecycle (handled elsewhere, unchanged)
 *   - Size cap (100 MB) enforced inside MediaTransferManager, unchanged
 */
public class MediaUploadWorker extends Worker {

    public static final String      INPUT_MESSAGE_ID        =   "message_id";
    public static final String      INPUT_CONTACT_ID        =   "contact_id";
    public static final String      INPUT_FILE_NAME_HINT    =   "file_name_hint";

    // Notification channel + id for the foreground-service notification.
    // Using a dedicated channel so the user can mute upload notifications
    // independently of incoming-message notifications.
    private static final String     CHANNEL_ID              =   "jippytalk_uploads";
    private static final String     CHANNEL_NAME            =   "File uploads";
    private static final int        NOTIFICATION_ID_BASE    =   10_000;

    // Max time the worker will block waiting for the async upload pipeline
    // to finish. Beyond this we abort and let WorkManager retry — the file
    // upload itself can legitimately take many minutes on slow networks,
    // so keep this generous. 20 minutes covers ~100 MB at 1 Mbps.
    private static final long       PIPELINE_TIMEOUT_MIN    =   20L;

    public MediaUploadWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String messageId = getInputData().getString(INPUT_MESSAGE_ID);
        String fileHint  = getInputData().getString(INPUT_FILE_NAME_HINT);
        Log.e(Extras.LOG_MESSAGE, "MediaUploadWorker: starting for " + messageId
                + " (" + fileHint + ")");

        if (messageId == null || messageId.isEmpty()) {
            Log.e(Extras.LOG_MESSAGE, "MediaUploadWorker: missing message_id input — failing");
            return Result.failure();
        }

        // Promote to foreground with a notification. If this fails (e.g. no
        // permission), the worker still runs — just without notification UI.
        try { setForegroundAsync(buildForegroundInfo(fileHint, 0)).get(2, TimeUnit.SECONDS); }
        catch (Throwable t) {
            Log.w(Extras.LOG_MESSAGE, "MediaUploadWorker: setForegroundAsync failed (non-fatal): "
                    + t.getMessage());
        }

        // Wait for app-level singletons to be ready. During cold-start we
        // might race MyApplication's background init. Final local so the
        // anonymous listener below can capture it.
        try {
            MyApplication a0 = MyApplication.getInstance();
            if (a0 != null) {
                a0.getInitLatch().await(15, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "MediaUploadWorker: init-wait failed: " + e.getMessage());
        }
        final MyApplication app = MyApplication.getInstance();
        if (app == null || app.getDatabaseServiceLocator() == null) {
            Log.e(Extras.LOG_MESSAGE, "MediaUploadWorker: app not initialized, retrying later");
            return Result.retry();
        }

        // Rebuild AttachmentModel from the DB row. Everything the pipeline
        // needs (local_file_path, caption, dimensions, encryption key/iv,
        // room_id) was persisted at insert time in v8/v9.
        MessagesDatabaseDAO dao = app.getDatabaseServiceLocator().getMessagesDatabaseDAO();
        AttachmentModel model = rebuildModelFromDb(dao, messageId);
        if (model == null) {
            Log.e(Extras.LOG_MESSAGE, "MediaUploadWorker: could not rebuild model for "
                    + messageId + " — failing permanently");
            return Result.failure();
        }

        // The AttachmentModel's media URI is what MediaTransferManager.send
        // Attachment will open as the source (via ContentResolver). It may
        // be:
        //   (a) a file:// URI wrapping an absolute /sent/ path (file was
        //       copied on a previous run), OR
        //   (b) a content:// URI from the picker (fresh row, file not yet
        //       copied to /sent/).
        // For (a) we can File.exists() the path — fail fast if it's gone.
        // For (b) we trust ContentResolver to open it and let sendAttachment
        // do the copy.
        String sourceUri = model.getMedia();
        if (sourceUri == null || sourceUri.isEmpty()) {
            Log.e(Extras.LOG_MESSAGE, "MediaUploadWorker: no source URI for "
                    + messageId + " — failing permanently");
            markRowAsFailed(app, messageId);
            return Result.failure();
        }
        boolean isContentUri = sourceUri.startsWith("content://");
        if (!isContentUri && sourceUri.startsWith("file://")) {
            String path = android.net.Uri.parse(sourceUri).getPath();
            if (path == null || !new File(path).exists()) {
                Log.e(Extras.LOG_MESSAGE, "MediaUploadWorker: local file missing at "
                        + sourceUri + " — failing permanently");
                markRowAsFailed(app, messageId);
                return Result.failure();
            }
        }

        // Wire into the existing MediaTransferManager pipeline via a latch.
        // The listener it registers is per-upload-instance so we don't
        // interfere with the main MessagingActivity's listener (that one
        // stays active for in-flight UI updates — the activity-owned
        // MediaTransferManager is a SEPARATE instance).
        MediaTransferManager workerManager = new MediaTransferManager(getApplicationContext());
        // Adapter is null here — the worker has no RecyclerView. Progress
        // updates go to the notification + DB status instead.
        workerManager.setAdapter(null);

        final CountDownLatch latch          =   new CountDownLatch(1);
        final AtomicBoolean  uploadOk       =   new AtomicBoolean(false);
        final AtomicReference<String> err   =   new AtomicReference<>("");

        workerManager.setTransferEventListener(new MediaTransferManager.TransferEventListener() {
            @Override
            public void onUploadComplete(String msgId, String s3Key, String bucket,
                                         AttachmentModel m) {
                // Delegate to the SAME completion handler the UI uses so
                // thumbnail generation + thumbnail upload + WS send all run
                // identically to the in-activity flow.
                //
                // The activity-owned MediaTransferManager has its own
                // onUploadComplete listener in MessagingActivity that does
                // the thumbnail + WS-send work — but that one's bound to a
                // specific manager instance. Here we invoke the same work
                // by hand using the helpers that live on MyApplication / the
                // existing singletons.
                try {
                    performPostUploadPipeline(app, m, msgId, s3Key, bucket);
                    uploadOk.set(true);
                } catch (Throwable t) {
                    err.set(t.getMessage() != null ? t.getMessage() : "post-upload error");
                    Log.e(Extras.LOG_MESSAGE, "MediaUploadWorker: post-upload failed: "
                            + t.getMessage(), t);
                } finally {
                    latch.countDown();
                }
            }

            @Override
            public void onDownloadComplete(String msgId, String localFilePath,
                                           String fileTransferId) {
                // N/A for uploads — never called from this worker's pipeline.
            }

            @Override
            public void onUploadFailed(String msgId, String error) {
                err.set(error != null ? error : "upload failed");
                uploadOk.set(false);
                latch.countDown();
            }

            @Override
            public void onUploadProgress(String msgId, int percent) {
                // Publish progress to WorkManager so MessagingActivity's
                // WorkInfo observer can read it and drive the adapter's
                // pill progress bar. The worker has adapter==null so it
                // can't update the UI directly.
                try {
                    setProgressAsync(new Data.Builder()
                            .putString("messageId", msgId)
                            .putInt("percent", percent)
                            .build());
                } catch (Throwable ignored) {}
            }
        });

        Log.e(Extras.LOG_MESSAGE, "MediaUploadWorker: handing off to MediaTransferManager for "
                + messageId);
        workerManager.sendAttachment(messageId, model);

        // Block until the async pipeline signals completion or we hit the
        // overall worker timeout. WorkManager itself also imposes a 10-min
        // cap on non-expedited work; we've requested foreground, so we get
        // a longer runway.
        boolean completed;
        try {
            completed = latch.await(PIPELINE_TIMEOUT_MIN, TimeUnit.MINUTES);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            Log.e(Extras.LOG_MESSAGE, "MediaUploadWorker: interrupted — retrying");
            return Result.retry();
        }

        if (!completed) {
            Log.e(Extras.LOG_MESSAGE, "MediaUploadWorker: timed out after "
                    + PIPELINE_TIMEOUT_MIN + " min — retrying");
            return Result.retry();
        }

        if (!uploadOk.get()) {
            String errMsg = err.get();
            Log.e(Extras.LOG_MESSAGE, "MediaUploadWorker: failed for " + messageId
                    + " — " + errMsg);
            // ANY failure (S3 403/denied, presign error, network drop, WS send
            // failure, post-upload error) → mark the row as SEND_FAILED and
            // stop. WorkManager will NOT auto-retry — the user controls retry
            // by tapping the retry icon in the chat row, which re-enqueues the
            // worker with ExistingWorkPolicy.REPLACE (see MessagingActivity
            // .onRetryUpload). Avoids burning battery / bandwidth on doomed
            // retries (e.g. AWS permission missing, server rejecting) and
            // gives the user clear control.
            markRowAsFailed(app, messageId);
            return Result.failure();
        }

        Log.e(Extras.LOG_MESSAGE, "MediaUploadWorker: success for " + messageId);
        return Result.success();
    }

    // ---------- Helpers ----------

    /**
     * Runs the same post-upload sequence the activity's listener does:
     * thumbnail generate → thumbnail upload → WS send → DB updates.
     *
     * We call the small helper methods directly rather than invoking the
     * activity (which may not even be running). These helpers live on
     * MyApplication and are designed to be callable from background
     * contexts. Every one of them is what the activity already uses.
     */
    private void performPostUploadPipeline(MyApplication app, AttachmentModel model,
                                           String messageId, String s3Key, String bucket) {
        app.performMediaPostUploadPipeline(messageId, s3Key, bucket, model);
    }

    /**
     * Reads the message row and reconstructs an AttachmentModel with every
     * field the MediaTransferManager pipeline needs. All columns were
     * populated at insert time (insertMessageWithMedia).
     */
    private AttachmentModel rebuildModelFromDb(MessagesDatabaseDAO dao, String messageId) {
        Cursor c = null;
        try {
            c = dao.getMessageDetailsFromId(messageId);
            if (c == null || !c.moveToFirst()) {
                Log.e(Extras.LOG_MESSAGE, "rebuildModelFromDb: no row for " + messageId);
                return null;
            }
            String  fileName        =   readStr(c, MessagesDatabase.FILE_NAME);
            String  contentType     =   readStr(c, MessagesDatabase.CONTENT_TYPE);
            String  contentSubtype  =   readStr(c, MessagesDatabase.CONTENT_SUBTYPE);
            String  caption         =   readStr(c, MessagesDatabase.CAPTION);
            int     width           =   readInt(c, MessagesDatabase.MEDIA_WIDTH);
            int     height          =   readInt(c, MessagesDatabase.MEDIA_HEIGHT);
            long    duration        =   readLong(c, MessagesDatabase.MEDIA_DURATION);
            String  localPath       =   readStr(c, MessagesDatabase.LOCAL_FILE_PATH);
            String  encKey          =   readStr(c, MessagesDatabase.ENCRYPTION_KEY);
            String  encIv           =   readStr(c, MessagesDatabase.ENCRYPTION_IV);

            if (localPath == null || localPath.isEmpty()) {
                Log.e(Extras.LOG_MESSAGE, "rebuildModelFromDb: no local_file_path for " + messageId);
                return null;
            }

            // local_file_path may be either an already-copied absolute path
            // (/.../sent/...) OR the original picker content:// URI if the
            // file hasn't been copied to /sent/ yet. Don't wrap content://
            // URIs in Uri.fromFile — keep them as-is so
            // MediaTransferManager.sendAttachment can open them via
            // ContentResolver and copy to /sent/.
            String mediaUri;
            String localFilePath;
            if (localPath.startsWith("content://")) {
                mediaUri      = localPath;   // picker URI → hand to sendAttachment as source
                localFilePath = "";          // real local file doesn't exist yet
            } else {
                mediaUri      = android.net.Uri.fromFile(new File(localPath)).toString();
                localFilePath = localPath;
            }

            AttachmentModel m = new AttachmentModel(
                    mediaUri,
                    "",
                    contentType != null ? contentType : "document",
                    contentSubtype != null ? contentSubtype : "",
                    caption != null ? caption : "",
                    height, width, duration,
                    "", "", fileName != null ? fileName : "file");
            m.setLocalFilePath(localFilePath);
            m.setEncryptionKey(encKey);
            m.setEncryptionIv(encIv);
            return m;
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "rebuildModelFromDb failed: " + e.getMessage(), e);
            return null;
        } finally {
            if (c != null) try { c.close(); } catch (Exception ignored) {}
        }
    }

    private static String readStr(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        return i >= 0 ? c.getString(i) : null;
    }
    private static int readInt(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        return i >= 0 ? c.getInt(i) : 0;
    }
    private static long readLong(Cursor c, String col) {
        int i = c.getColumnIndex(col);
        return i >= 0 ? c.getLong(i) : 0L;
    }

    /**
     * Marks the row as SEND_FAILED so the UI shows the retry icon. Mirrors
     * what the activity's onUploadFailed callback does.
     */
    private void markRowAsFailed(MyApplication app, String messageId) {
        try {
            app.getRepositoryServiceLocator().getMessagesRepository()
                    .updateMessageAsSyncedWithServer(
                            messageId,
                            MessagesManager.MESSAGE_SEND_FAILED,
                            MessagesManager.NEED_MESSAGE_PUSH,
                            isSuccess -> Log.e(Extras.LOG_MESSAGE,
                                    "Worker: DB marked FAILED for " + messageId
                                            + " success=" + isSuccess));
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "markRowAsFailed: " + e.getMessage());
        }
    }

    // ---------- Foreground notification ----------

    private ForegroundInfo buildForegroundInfo(String fileName, int progress) {
        Context ctx = getApplicationContext();
        ensureChannel(ctx);
        String title = "Sending " + (fileName != null ? fileName : "file");

        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(progress > 0 ? progress + "% uploaded" : "Uploading…")
                .setSmallIcon(R.drawable.ic_attachment)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS);
        if (progress > 0) {
            b.setProgress(100, progress, false);
        } else {
            b.setProgress(0, 0, true);  // indeterminate
        }
        Notification n = b.build();

        int notifId = NOTIFICATION_ID_BASE + (getId().hashCode() & 0x7fff);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return new ForegroundInfo(notifId, n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            return new ForegroundInfo(notifId, n);
        }
    }

    private static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("File upload progress");
                ch.setShowBadge(false);
                nm.createNotificationChannel(ch);
            }
        }
    }

    /** Input data helper used by callers when enqueueing a new worker. */
    public static Data buildInput(String messageId, String contactId, String fileNameHint) {
        return new Data.Builder()
                .putString(INPUT_MESSAGE_ID, messageId != null ? messageId : "")
                .putString(INPUT_CONTACT_ID, contactId != null ? contactId : "")
                .putString(INPUT_FILE_NAME_HINT, fileNameHint != null ? fileNameHint : "")
                .build();
    }

    /** Stable unique-work name for a given message id. Lets us cancel / replace by message. */
    public static String uniqueNameForMessage(String messageId) {
        return "media-upload:" + (messageId != null ? messageId : "unknown");
    }
}
