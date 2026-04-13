package com.jippytalk.Messages.Attachment.Storage;

/**
 * Developer Name: Vidya Sagar
 * Created on: 09-04-2026
 */

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.jippytalk.Extras;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * MediaStorageHelper - Manages the WhatsApp-style local media storage structure.
 *
 * Folder structure (on device):
 *   /storage/emulated/0/Android/media/com.jippytalk/JippyTalk/
 *   ├── .nomedia                    ← hides from gallery
 *   ├── sent/
 *   │   ├── images/
 *   │   ├── videos/
 *   │   ├── audio/
 *   │   └── documents/
 *   └── received/
 *       ├── images/
 *       ├── videos/
 *       ├── audio/
 *       └── documents/
 *
 * Files live under the app's external media directory (getExternalMediaDirs()).
 * This is identical to the strategy WhatsApp and Telegram use:
 *   - No runtime permissions required (scoped storage)
 *   - Visible to the user via file managers under Android/media/<package>/
 *   - Automatically deleted when the app is uninstalled
 *   - Hidden from Gallery / MediaStore because of the .nomedia markers
 */
public class MediaStorageHelper {

    // ---- Constants ----

    private static final String     ROOT_DIR        =   "JippyTalk";
    private static final String     SENT_DIR        =   "sent";
    private static final String     RECEIVED_DIR    =   "received";
    private static final String     IMAGES_DIR      =   "images";
    private static final String     VIDEOS_DIR      =   "videos";
    private static final String     AUDIO_DIR       =   "audio";
    private static final String     DOCUMENTS_DIR   =   "documents";
    private static final String     NO_MEDIA_FILE   =   ".nomedia";
    private static final int        COPY_BUFFER     =   256 * 1024;     // 256 KB

    public static final String      TYPE_IMAGE      =   "image";
    public static final String      TYPE_VIDEO      =   "video";
    public static final String      TYPE_AUDIO      =   "audio";
    public static final String      TYPE_DOCUMENT   =   "document";

    // ---- Fields ----

    private static volatile MediaStorageHelper  INSTANCE;
    private final Context                       context;
    private final ContentResolver               contentResolver;
    private final File                          rootDirectory;

    // ---- Constructor ----

    private MediaStorageHelper(Context context) {
        this.context            =   context.getApplicationContext();
        this.contentResolver    =   context.getApplicationContext().getContentResolver();
        this.rootDirectory      =   resolveRootDirectory(this.context);
        initializeDirectoryStructure();
    }

    /**
     * Picks the best available storage root. Prefers external media storage
     * (Android/media/<package>/JippyTalk) so files are browsable in file
     * managers like WhatsApp. Falls back to internal filesDir if the external
     * media dirs aren't mounted (emulator quirks, MTP disconnects, etc.).
     */
    private static File resolveRootDirectory(Context ctx) {
        try {
            File[]  externalMediaDirs   =   ctx.getExternalMediaDirs();
            if (externalMediaDirs != null && externalMediaDirs.length > 0
                    && externalMediaDirs[0] != null) {
                return new File(externalMediaDirs[0], ROOT_DIR);
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "External media dir unavailable: " + e.getMessage());
        }
        return new File(ctx.getFilesDir(), ROOT_DIR);
    }

    public static MediaStorageHelper getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (MediaStorageHelper.class) {
                if (INSTANCE == null) {
                    INSTANCE    =   new MediaStorageHelper(context);
                }
            }
        }
        return INSTANCE;
    }

    // -------------------- Directory Initialization Starts Here ---------------------

    /**
     * Creates the full folder tree and .nomedia files on first use.
     * Safe to call multiple times — skips existing directories.
     */
    private void initializeDirectoryStructure() {
        try {
            // Create root
            createDirectoryIfNeeded(rootDirectory);

            // Create .nomedia in root to block gallery indexing
            createNoMediaFile(rootDirectory);

            // Create sent subdirectories
            createDirectoryIfNeeded(getSentFolder(TYPE_IMAGE));
            createDirectoryIfNeeded(getSentFolder(TYPE_VIDEO));
            createDirectoryIfNeeded(getSentFolder(TYPE_AUDIO));
            createDirectoryIfNeeded(getSentFolder(TYPE_DOCUMENT));

            // Create received subdirectories
            createDirectoryIfNeeded(getReceivedFolder(TYPE_IMAGE));
            createDirectoryIfNeeded(getReceivedFolder(TYPE_VIDEO));
            createDirectoryIfNeeded(getReceivedFolder(TYPE_AUDIO));
            createDirectoryIfNeeded(getReceivedFolder(TYPE_DOCUMENT));

            // Add .nomedia to sent and received roots too
            createNoMediaFile(new File(rootDirectory, SENT_DIR));
            createNoMediaFile(new File(rootDirectory, RECEIVED_DIR));

            Log.e(Extras.LOG_MESSAGE, "Media storage initialized at: " + rootDirectory.getAbsolutePath());

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to initialize media storage: " + e.getMessage());
        }
    }

    private void createDirectoryIfNeeded(File directory) {
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    private void createNoMediaFile(File directory) {
        File    noMediaFile =   new File(directory, NO_MEDIA_FILE);
        if (!noMediaFile.exists()) {
            try {
                noMediaFile.createNewFile();
            } catch (IOException e) {
                Log.e(Extras.LOG_MESSAGE, "Failed to create .nomedia in " + directory.getAbsolutePath());
            }
        }
    }

    // -------------------- Folder Getters Starts Here ---------------------

    /**
     * Returns the sent folder for a given content type.
     * e.g. getSentFolder("image") → .../JippyTalk/sent/images/
     *
     * @param contentType one of TYPE_IMAGE, TYPE_VIDEO, TYPE_AUDIO, TYPE_DOCUMENT
     * @return the File directory
     */
    public File getSentFolder(String contentType) {
        return new File(rootDirectory, SENT_DIR + File.separator + getSubdirectoryName(contentType));
    }

    /**
     * Returns the received folder for a given content type.
     * e.g. getReceivedFolder("video") → .../JippyTalk/received/videos/
     *
     * @param contentType one of TYPE_IMAGE, TYPE_VIDEO, TYPE_AUDIO, TYPE_DOCUMENT
     * @return the File directory
     */
    public File getReceivedFolder(String contentType) {
        return new File(rootDirectory, RECEIVED_DIR + File.separator + getSubdirectoryName(contentType));
    }

    /**
     * Maps content type to subdirectory name.
     */
    private String getSubdirectoryName(String contentType) {
        if (contentType == null) return DOCUMENTS_DIR;
        switch (contentType) {
            case TYPE_IMAGE     -> { return IMAGES_DIR; }
            case TYPE_VIDEO     -> { return VIDEOS_DIR; }
            case TYPE_AUDIO     -> { return AUDIO_DIR; }
            default             -> { return DOCUMENTS_DIR; }
        }
    }

    // -------------------- File Copy (Sender) Starts Here ---------------------

    /**
     * Copies a picked file from its content:// URI to the sent folder with a timestamped name.
     * This runs synchronously — call from a background thread.
     *
     * @param sourceUri     the content:// URI of the picked file
     * @param contentType   "image", "video", "audio", or "document"
     * @param extension     the file extension (e.g. "jpg", "mp4", "pdf")
     * @return the absolute path of the copied file in sent/ folder, or null on failure
     */
    public String copyToSentFolder(Uri sourceUri, String contentType, String extension) {
        return copyToSentFolder(sourceUri, contentType, extension, null);
    }

    public String copyToSentFolder(Uri sourceUri, String contentType, String extension,
                                   String originalFileName) {
        InputStream     inputStream     =   null;
        OutputStream    outputStream    =   null;

        try {
            File    targetDir   =   getSentFolder(contentType);
            String  fileName    =   originalFileName != null && !originalFileName.isEmpty()
                                    ? generateUniqueFileName(originalFileName)
                                    : generateFileName(contentType, extension);
            File    targetFile  =   new File(targetDir, fileName);

            inputStream     =   contentResolver.openInputStream(sourceUri);
            if (inputStream == null) {
                Log.e(Extras.LOG_MESSAGE, "Failed to open source URI: " + sourceUri);
                return null;
            }

            outputStream    =   new FileOutputStream(targetFile);
            byte[]  buffer  =   new byte[COPY_BUFFER];
            int     bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }

            outputStream.flush();

            Log.e(Extras.LOG_MESSAGE, "File copied to sent: " + targetFile.getAbsolutePath());
            return targetFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to copy file to sent folder: " + e.getMessage());
            return null;
        } finally {
            try {
                if (inputStream != null)    inputStream.close();
                if (outputStream != null)   outputStream.close();
            } catch (Exception ignored) {}
        }
    }

    // -------------------- File Path (Receiver) Starts Here ---------------------

    /**
     * Returns the target File path where a received file should be saved.
     * Does NOT create the file — used by S3DownloadHelper as the target.
     *
     * @param contentType   "image", "video", "audio", or "document"
     * @param fileName      the original file name from the server
     * @return the File object pointing to received/{type}/{fileName}
     */
    public File getReceivedFilePath(String contentType, String fileName) {
        File    targetDir   =   getReceivedFolder(contentType);
        return new File(targetDir, fileName);
    }

    // -------------------- File Name Generation Starts Here ---------------------

    /**
     * Generates a timestamped file name based on content type.
     * Format: PREFIX_yyyyMMdd_HHmmssSSS.extension
     *
     * @param contentType   "image", "video", "audio", or "document"
     * @param extension     file extension without dot (e.g. "jpg", "mp4")
     * @return generated file name like "IMG_20260409_143022123.jpg"
     */
    /**
     * Generates a unique file name: epoch_originalName.extension
     * e.g. 1775967534726_report.pdf
     */
    public String generateFileName(String contentType, String extension) {
        long    epoch   =   System.currentTimeMillis();

        if (extension == null || extension.isEmpty()) {
            extension   =   getDefaultExtension(contentType);
        }

        return epoch + "_" + getFilePrefix(contentType) + "." + extension;
    }

    /**
     * Generates a unique file name preserving the original name.
     * Format: epoch_originalFileName
     * e.g. 1775967534726_report.pdf
     */
    public String generateUniqueFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isEmpty()) {
            return System.currentTimeMillis() + "_file";
        }
        return System.currentTimeMillis() + "_" + originalFileName;
    }

    /**
     * Returns the file name prefix based on content type.
     */
    private String getFilePrefix(String contentType) {
        if (contentType == null) return "DOC";
        switch (contentType) {
            case TYPE_IMAGE     -> { return "IMG"; }
            case TYPE_VIDEO     -> { return "VID"; }
            case TYPE_AUDIO     -> { return "AUD"; }
            default             -> { return "DOC"; }
        }
    }

    /**
     * Returns a default file extension if none is provided.
     */
    private String getDefaultExtension(String contentType) {
        if (contentType == null) return "bin";
        switch (contentType) {
            case TYPE_IMAGE     -> { return "jpg"; }
            case TYPE_VIDEO     -> { return "mp4"; }
            case TYPE_AUDIO     -> { return "m4a"; }
            default             -> { return "bin"; }
        }
    }

    // -------------------- File Utility Methods Starts Here ---------------------

    /**
     * Checks if a file exists at the given path.
     *
     * @param filePath the absolute file path
     * @return true if the file exists
     */
    public boolean fileExists(String filePath) {
        if (filePath == null || filePath.isEmpty()) return false;
        return new File(filePath).exists();
    }

    /**
     * Deletes a file at the given path.
     *
     * @param filePath the absolute file path
     * @return true if the file was deleted
     */
    public boolean deleteFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) return false;
        File    file    =   new File(filePath);
        if (file.exists()) {
            boolean deleted =   file.delete();
            if (deleted) {
                Log.e(Extras.LOG_MESSAGE, "File deleted: " + filePath);
            }
            return deleted;
        }
        return false;
    }

    /**
     * Returns the total size of all files in a directory (non-recursive).
     * Useful for showing storage usage.
     *
     * @param directory the directory to measure
     * @return total size in bytes
     */
    public long getDirectorySize(File directory) {
        long    size    =   0;
        if (directory != null && directory.isDirectory()) {
            File[]  files   =   directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        size    +=  file.length();
                    }
                }
            }
        }
        return size;
    }

    /**
     * Determines the content type from a MIME type string.
     * e.g. "image/png" → TYPE_IMAGE, "video/mp4" → TYPE_VIDEO
     *
     * @param mimeType the full MIME type
     * @return one of TYPE_IMAGE, TYPE_VIDEO, TYPE_AUDIO, TYPE_DOCUMENT
     */
    public static String getContentTypeFromMime(String mimeType) {
        if (mimeType == null) return TYPE_DOCUMENT;
        if (mimeType.startsWith("image/"))  return TYPE_IMAGE;
        if (mimeType.startsWith("video/"))  return TYPE_VIDEO;
        if (mimeType.startsWith("audio/"))  return TYPE_AUDIO;
        return TYPE_DOCUMENT;
    }
}
