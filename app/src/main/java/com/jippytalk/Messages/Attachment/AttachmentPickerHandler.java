package com.jippytalk.Messages.Attachment;

/**
 * Developer Name: Vidya Sagar
 * Created on: 09-04-2026
 */

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.provider.ContactsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.jippytalk.Common.AttachmentBottomSheet;
import com.jippytalk.Extras;
import com.jippytalk.Messages.Attachment.Model.AttachmentModel;
import com.jippytalk.R;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AttachmentPickerHandler - Handles all attachment picking logic in a single class.
 *
 * Responsibilities:
 * - Registers all ActivityResultLaunchers (must be created before onStart)
 * - Checks and requests runtime permissions based on the selected option
 * - Opens the appropriate system picker (camera, gallery, document, etc.)
 * - Extracts metadata (content type, dimensions, duration, file name) from the selected URI
 * - Returns a fully populated AttachmentModel via callback
 *
 * Usage:
 *   // Declare as a field (before onCreate so launchers register correctly)
 *   private final AttachmentPickerHandler attachmentPickerHandler = new AttachmentPickerHandler(this);
 *
 *   // Set callback after initialization
 *   attachmentPickerHandler.setCallback(attachmentModel -> { ... });
 *
 *   // Handle an option from the bottom sheet
 *   attachmentPickerHandler.handleOption(AttachmentBottomSheet.OPTION_GALLERY);
 */
public class AttachmentPickerHandler {

    // ---- Fields ----

    private final AppCompatActivity                 activity;
    private AttachmentResultCallback                callback;
    private int                                     pendingOption               =   -1;
    private final ExecutorService                   metadataExecutor            =   Executors.newSingleThreadExecutor();

    // ---- Activity Result Launchers ----

    private final ActivityResultLauncher<String>     permissionLauncher;
    private final ActivityResultLauncher<Intent>     cameraLauncher;
    private final ActivityResultLauncher<Intent>     galleryImageLauncher;
    private final ActivityResultLauncher<Intent>     videoCameraLauncher;
    private final ActivityResultLauncher<Intent>     videoGalleryLauncher;
    private final ActivityResultLauncher<Intent>     documentLauncher;
    private final ActivityResultLauncher<Intent>     contactPickerLauncher;
    private final ActivityResultLauncher<Intent>     audioLauncher;
    private final ActivityResultLauncher<Intent>     previewLauncher;

    // Tracks which content type the current picker was opened for
    private String                                   pendingContentType          =   CONTENT_TYPE_DOCUMENT;

    // ---- Content Type Constants ----

    private static final String     CONTENT_TYPE_IMAGE      =   "image";
    private static final String     CONTENT_TYPE_VIDEO      =   "video";
    private static final String     CONTENT_TYPE_AUDIO      =   "audio";
    private static final String     CONTENT_TYPE_DOCUMENT   =   "document";
    private static final String     CONTENT_TYPE_CONTACT    =   "contact";
    private static final String     CONTENT_TYPE_LOCATION   =   "location";

    // ---- Constructor ----

    /**
     * Creates a new AttachmentPickerHandler and registers all activity result launchers.
     * MUST be called during field declaration (before onStart) for launchers to work.
     *
     * @param activity the host activity
     */
    public AttachmentPickerHandler(AppCompatActivity activity) {
        this.activity   =   activity;

        // ---- Register Permission Launcher ----

        permissionLauncher  =   activity.registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), isGranted -> {
                    if (isGranted) {
                        openPickerForOption(pendingOption);
                    } else {
                        Toast.makeText(activity, R.string.permission_denied, Toast.LENGTH_SHORT).show();
                    }
                });

        // ---- Register Picker Launchers ----

        cameraLauncher  =   activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                        Uri photoUri    =   result.getData().getData();
                        if (photoUri != null) {
                            launchPreviewForSingleUri(photoUri, CONTENT_TYPE_IMAGE);
                        }
                    }
                });

        galleryImageLauncher    =   activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                        launchPreviewFromPickerResult(result.getData(), CONTENT_TYPE_IMAGE);
                    }
                });

        videoCameraLauncher =   activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                        Uri videoUri    =   result.getData().getData();
                        if (videoUri != null) {
                            launchPreviewForSingleUri(videoUri, CONTENT_TYPE_VIDEO);
                        }
                    }
                });

        videoGalleryLauncher    =   activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                        launchPreviewFromPickerResult(result.getData(), CONTENT_TYPE_VIDEO);
                    }
                });

        documentLauncher    =   activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                        launchPreviewFromPickerResult(result.getData(), CONTENT_TYPE_DOCUMENT);
                    }
                });

        contactPickerLauncher   =   activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                        Uri contactUri  =   result.getData().getData();
                        if (contactUri != null) {
                            buildContactAttachmentModel(contactUri);
                        }
                    }
                });

        audioLauncher   =   activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                        launchPreviewFromPickerResult(result.getData(), CONTENT_TYPE_AUDIO);
                    }
                });

        // Preview activity result — returns list of files user approved to send
        previewLauncher =   activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == AppCompatActivity.RESULT_OK && result.getData() != null) {
                        handlePreviewResult(result.getData());
                    }
                });
    }

    // -------------------- Public Methods Starts Here ---------------------

    /**
     * Sets the callback to receive the attachment model after a file is selected.
     *
     * @param callback the callback to receive the AttachmentModel
     */
    public void setCallback(AttachmentResultCallback callback) {
        this.callback   =   callback;
    }

    /**
     * Handles an attachment option selected from the bottom sheet.
     * Checks the required permission and opens the picker if granted,
     * or requests the permission if not.
     *
     * @param option the attachment option constant from AttachmentBottomSheet
     */
    public void handleOption(int option) {
        pendingOption   =   option;
        String  requiredPermission  =   getPermissionForOption(option);

        if (requiredPermission == null) {
            openPickerForOption(option);
            return;
        }

        if (ContextCompat.checkSelfPermission(activity, requiredPermission)
                == PackageManager.PERMISSION_GRANTED) {
            openPickerForOption(option);
        } else {
            permissionLauncher.launch(requiredPermission);
        }
    }

    // -------------------- Permission Logic Starts Here ---------------------

    /**
     * Returns the required runtime permission for a given attachment option.
     * Handles Android 13+ (TIRAMISU) media permission changes.
     * Returns null if no permission is needed (e.g., document picker).
     *
     * @param option the attachment option constant
     * @return the permission string, or null if none required
     */
    private String getPermissionForOption(int option) {
        switch (option) {
            case AttachmentBottomSheet.OPTION_PHOTO          ->  { return Manifest.permission.CAMERA; }
            case AttachmentBottomSheet.OPTION_VIDEO          ->  { return Manifest.permission.CAMERA; }
            case AttachmentBottomSheet.OPTION_GALLERY        ->  {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return Manifest.permission.READ_MEDIA_IMAGES;
                }
                return Manifest.permission.READ_EXTERNAL_STORAGE;
            }
            case AttachmentBottomSheet.OPTION_VIDEO_GALLERY  ->  {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return Manifest.permission.READ_MEDIA_VIDEO;
                }
                return Manifest.permission.READ_EXTERNAL_STORAGE;
            }
            case AttachmentBottomSheet.OPTION_AUDIO          ->  {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    return Manifest.permission.READ_MEDIA_AUDIO;
                }
                return Manifest.permission.READ_EXTERNAL_STORAGE;
            }
            case AttachmentBottomSheet.OPTION_LOCATION       ->  { return Manifest.permission.ACCESS_FINE_LOCATION; }
            case AttachmentBottomSheet.OPTION_CONTACT        ->  { return Manifest.permission.READ_CONTACTS; }
            case AttachmentBottomSheet.OPTION_DOCUMENT       ->  { return null; }
            default                                          ->  { return null; }
        }
    }

    // -------------------- Picker Opening Logic Starts Here ---------------------

    /**
     * Opens the appropriate system picker based on the selected attachment option.
     *
     * @param option the attachment option constant from AttachmentBottomSheet
     */
    private void openPickerForOption(int option) {
        switch (option) {
            case AttachmentBottomSheet.OPTION_PHOTO          ->  openCamera();
            case AttachmentBottomSheet.OPTION_GALLERY        ->  openImageGallery();
            case AttachmentBottomSheet.OPTION_VIDEO          ->  openVideoCamera();
            case AttachmentBottomSheet.OPTION_VIDEO_GALLERY  ->  openVideoGallery();
            case AttachmentBottomSheet.OPTION_DOCUMENT       ->  openDocumentPicker();
            case AttachmentBottomSheet.OPTION_LOCATION       ->  openLocationPicker();
            case AttachmentBottomSheet.OPTION_CONTACT        ->  openContactPicker();
            case AttachmentBottomSheet.OPTION_AUDIO          ->  openAudioPicker();
            default -> Log.e(Extras.LOG_MESSAGE, "Unknown attachment option: " + option);
        }
    }

    private void openCamera() {
       /* Intent  intent  =   new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraLauncher.launch(intent);*/
    }

    private void openImageGallery() {
    /*    Intent  intent  =   new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        galleryImageLauncher.launch(intent);*/
    }

    private void openVideoCamera() {
      /*  Intent  intent  =   new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
        videoCameraLauncher.launch(intent);*/
    }

    private void openVideoGallery() {
      /*  Intent  intent  =   new Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
        intent.setType("video/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        videoGalleryLauncher.launch(intent);*/
    }

    /**
     * Opens the system file picker restricted to "document-style" MIME types.
     * Images are intentionally included so the user can share a photo as a
     * file attachment (same behaviour as WhatsApp's "Document" option, which
     * lets you pick images + PDFs + office docs together). Whatever the user
     * picks here gets stamped with content_type="document" downstream, so
     * the file keeps its original bytes and isn't re-compressed like a photo.
     */
    private void openDocumentPicker() {
        String[] documentMimeTypes  =   new String[] {
                // Images — shown in the picker but sent as documents (no compression)
                "image/*",

                // Office / text documents
                "application/pdf",
                "application/msword",                                                       // .doc
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",  // .docx
                "application/vnd.ms-excel",                                                 // .xls
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",        // .xlsx
                "application/vnd.ms-powerpoint",                                            // .ppt
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",// .pptx
                "application/rtf",
                "application/zip",
                "application/x-zip-compressed",
                "application/x-rar-compressed",
                "application/x-7z-compressed",
                "application/x-tar",
                "application/gzip",
                "application/json",
                "application/xml",
                "text/plain",
                "text/csv",
                "text/html",
                "text/xml"
        };

        Intent  intent  =   new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Setting type to */* first, then narrowing via EXTRA_MIME_TYPES, is the
        // recommended way to show multiple specific MIME types in the picker.
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, documentMimeTypes);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        documentLauncher.launch(intent);
    }

    private void openLocationPicker() {
        // TODO: Implement Google Maps location picker activity
        Log.e(Extras.LOG_MESSAGE, "Location picker - to be implemented");
    }

    private void openContactPicker() {
      /*  Intent  intent  =   new Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
        contactPickerLauncher.launch(intent);*/
    }

    private void openAudioPicker() {
       /* Intent  intent  =   new Intent(Intent.ACTION_PICK, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
        intent.setType("audio/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        audioLauncher.launch(intent);*/
    }

    // -------------------- Preview Launch Helpers Starts Here ---------------------

    /**
     * Extracts all URIs from a picker result (handles both single and multi-select)
     * and launches the AttachmentPreviewActivity.
     */
    private void launchPreviewFromPickerResult(Intent data, String contentType) {
        ArrayList<Uri>      uris            =   new ArrayList<>();
        ArrayList<String>   contentTypes    =   new ArrayList<>();

        // Multi-select: ClipData contains multiple URIs
        if (data.getClipData() != null) {
            android.content.ClipData clipData = data.getClipData();
            for (int i = 0; i < clipData.getItemCount(); i++) {
                Uri uri = clipData.getItemAt(i).getUri();
                if (uri != null) {
                    uris.add(uri);
                    contentTypes.add(contentType);
                }
            }
        }
        // Single select: just getData()
        else if (data.getData() != null) {
            uris.add(data.getData());
            contentTypes.add(contentType);
        }

        if (!uris.isEmpty()) {
            launchPreviewActivity(uris, contentTypes);
        }
    }

    /** Convenience for camera launchers that always produce a single URI. */
    private void launchPreviewForSingleUri(Uri uri, String contentType) {
        ArrayList<Uri>      uris            =   new ArrayList<>();
        ArrayList<String>   contentTypes    =   new ArrayList<>();
        uris.add(uri);
        contentTypes.add(contentType);
        launchPreviewActivity(uris, contentTypes);
    }

    /** Opens the AttachmentPreviewActivity with the collected URIs. */
    private void launchPreviewActivity(ArrayList<Uri> uris, ArrayList<String> contentTypes) {
        Intent intent = new Intent(activity,
                com.jippytalk.Messages.Attachment.Preview.AttachmentPreviewActivity.class);
        intent.putParcelableArrayListExtra(
                com.jippytalk.Messages.Attachment.Preview.AttachmentPreviewActivity.EXTRA_URIS, uris);
        intent.putStringArrayListExtra(
                com.jippytalk.Messages.Attachment.Preview.AttachmentPreviewActivity.EXTRA_CONTENT_TYPES, contentTypes);

        // Grant read permission for ALL content:// URIs via ClipData.
        // FLAG_GRANT_READ_URI_PERMISSION only applies to data/clipData, NOT to
        // parcelable extras — so we must attach URIs as ClipData items.
        if (!uris.isEmpty()) {
            android.content.ClipData clipData = android.content.ClipData.newRawUri("", uris.get(0));
            for (int i = 1; i < uris.size(); i++) {
                clipData.addItem(new android.content.ClipData.Item(uris.get(i)));
            }
            intent.setClipData(clipData);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }

        previewLauncher.launch(intent);
    }

    /**
     * Handles the result from AttachmentPreviewActivity. For each approved file,
     * builds an AttachmentModel and delivers it to the callback one by one.
     */
    private void handlePreviewResult(Intent data) {
        ArrayList<Uri>      uris            =   data.getParcelableArrayListExtra(
                com.jippytalk.Messages.Attachment.Preview.AttachmentPreviewActivity.EXTRA_URIS);
        ArrayList<String>   contentTypes    =   data.getStringArrayListExtra(
                com.jippytalk.Messages.Attachment.Preview.AttachmentPreviewActivity.EXTRA_CONTENT_TYPES);
        ArrayList<String>   captions        =   data.getStringArrayListExtra(
                com.jippytalk.Messages.Attachment.Preview.AttachmentPreviewActivity.EXTRA_CAPTIONS);
        ArrayList<String>   fileNames       =   data.getStringArrayListExtra(
                com.jippytalk.Messages.Attachment.Preview.AttachmentPreviewActivity.EXTRA_FILE_NAMES);
        ArrayList<String>   extensions      =   data.getStringArrayListExtra(
                com.jippytalk.Messages.Attachment.Preview.AttachmentPreviewActivity.EXTRA_EXTENSIONS);

        if (uris == null || uris.isEmpty()) return;

        for (int i = 0; i < uris.size(); i++) {
            Uri     uri         =   uris.get(i);
            String  type        =   contentTypes != null && i < contentTypes.size()
                                    ? contentTypes.get(i) : CONTENT_TYPE_DOCUMENT;
            String  caption     =   captions != null && i < captions.size()
                                    ? captions.get(i) : "";
            String  fileName    =   fileNames != null && i < fileNames.size()
                                    ? fileNames.get(i) : "";
            String  extension   =   extensions != null && i < extensions.size()
                                    ? extensions.get(i) : "";

            // Build AttachmentModel with the caption from the preview page
            buildAttachmentModelWithCaption(uri, type, caption, fileName, extension);
        }
    }

    /**
     * Builds an AttachmentModel like buildAttachmentModelAsync but with caption pre-set
     * from the preview page.
     */
    private void buildAttachmentModelWithCaption(Uri uri, String contentType, String caption,
                                                  String fileName, String extension) {
        metadataExecutor.execute(() -> {
            try {
                ContentResolver contentResolver = activity.getContentResolver();
                String  mimeType    =   contentResolver.getType(uri);
                if (fileName == null || fileName.isEmpty()) {
                    // fallback
                }
                String  subtype     =   extractSubtype(mimeType, fileName);
                int     width       =   0;
                int     height      =   0;
                long    duration    =   0;

                switch (contentType) {
                    case CONTENT_TYPE_IMAGE -> {
                        int[] dimensions = getImageDimensions(contentResolver, uri);
                        width   =   dimensions[0];
                        height  =   dimensions[1];
                    }
                    case CONTENT_TYPE_VIDEO -> {
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        try {
                            retriever.setDataSource(activity, uri);
                            String wStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                            String hStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                            String dStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                            width    = wStr != null ? Integer.parseInt(wStr) : 0;
                            height   = hStr != null ? Integer.parseInt(hStr) : 0;
                            duration = dStr != null ? Long.parseLong(dStr) : 0;
                        } catch (Exception e) {
                            Log.e(Extras.LOG_MESSAGE, "Video metadata error: " + e.getMessage());
                        } finally {
                            retriever.release();
                        }
                    }
                    case CONTENT_TYPE_AUDIO -> {
                        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                        try {
                            retriever.setDataSource(activity, uri);
                            String dStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                            duration = dStr != null ? Long.parseLong(dStr) : 0;
                        } catch (Exception e) {
                            Log.e(Extras.LOG_MESSAGE, "Audio metadata error: " + e.getMessage());
                        } finally {
                            retriever.release();
                        }
                    }
                }

                AttachmentModel model = new AttachmentModel(
                        uri.toString(), "", contentType,
                        extension != null && !extension.isEmpty() ? extension : subtype,
                        caption, height, width, duration, "", "", fileName
                );

                Log.e(Extras.LOG_MESSAGE, "AttachmentModel built (from preview): " + model);

                activity.runOnUiThread(() -> {
                    if (callback != null) {
                        callback.onAttachmentReady(model);
                    }
                });

            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Error building attachment from preview: " + e.getMessage());
            }
        });
    }

    // -------------------- Metadata Extraction Starts Here ---------------------

    /**
     * Builds an AttachmentModel asynchronously by extracting metadata from the selected URI.
     * Runs on a background thread and delivers the result on the main thread via callback.
     *
     * @param uri           the content URI of the selected file
     * @param contentType   the type of content ("image", "video", "audio", "document")
     */
    private void buildAttachmentModelAsync(Uri uri, String contentType) {
        metadataExecutor.execute(() -> {
            try {
                ContentResolver contentResolver =   activity.getContentResolver();
                String          mimeType        =   contentResolver.getType(uri);
                String          fileName        =   getFileName(contentResolver, uri);
                String          subtype         =   extractSubtype(mimeType, fileName);
                int             width           =   0;
                int             height          =   0;
                long            duration        =   0;

                switch (contentType) {
                    case CONTENT_TYPE_IMAGE  ->  {
                        int[] dimensions    =   getImageDimensions(contentResolver, uri);
                        width               =   dimensions[0];
                        height              =   dimensions[1];
                    }
                    case CONTENT_TYPE_VIDEO  ->  {
                        MediaMetadataRetriever retriever =   new MediaMetadataRetriever();
                        try {
                            retriever.setDataSource(activity, uri);
                            String widthStr     =   retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                            String heightStr    =   retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                            String durationStr  =   retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                            width               =   widthStr != null ? Integer.parseInt(widthStr) : 0;
                            height              =   heightStr != null ? Integer.parseInt(heightStr) : 0;
                            duration            =   durationStr != null ? Long.parseLong(durationStr) : 0;
                        } catch (Exception e) {
                            Log.e(Extras.LOG_MESSAGE, "Error extracting video metadata: " + e.getMessage());
                        } finally {
                            retriever.release();
                        }
                    }
                    case CONTENT_TYPE_AUDIO  ->  {
                        MediaMetadataRetriever retriever =   new MediaMetadataRetriever();
                        try {
                            retriever.setDataSource(activity, uri);
                            String durationStr  =   retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                            duration            =   durationStr != null ? Long.parseLong(durationStr) : 0;
                        } catch (Exception e) {
                            Log.e(Extras.LOG_MESSAGE, "Error extracting audio metadata: " + e.getMessage());
                        } finally {
                            retriever.release();
                        }
                    }
                }

                AttachmentModel attachmentModel =   new AttachmentModel(
                        uri.toString(),
                        "",
                        contentType,
                        subtype,
                        "",
                        height,
                        width,
                        duration,
                        "",
                        "",
                        fileName
                );

                Log.e(Extras.LOG_MESSAGE, "AttachmentModel built: " + attachmentModel);

                activity.runOnUiThread(() -> {
                    if (callback != null) {
                        callback.onAttachmentReady(attachmentModel);
                    }
                });

            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Error building attachment model: " + e.getMessage());
            }
        });
    }

    /**
     * Builds an AttachmentModel for a selected contact.
     * Extracts the contact display name from the URI.
     *
     * @param contactUri the content URI of the selected contact
     */
    private void buildContactAttachmentModel(Uri contactUri) {
        metadataExecutor.execute(() -> {
            String  contactName     =   "";
            try (Cursor cursor = activity.getContentResolver().query(
                    contactUri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex   =   cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        contactName =   cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Error reading contact: " + e.getMessage());
            }

            AttachmentModel attachmentModel =   new AttachmentModel(
                    contactUri.toString(),
                    "",
                    CONTENT_TYPE_CONTACT,
                    "",
                    "",
                    0,
                    0,
                    0,
                    "",
                    "",
                    contactName
            );

            Log.e(Extras.LOG_MESSAGE, "Contact AttachmentModel built: " + attachmentModel);

            activity.runOnUiThread(() -> {
                if (callback != null) {
                    callback.onAttachmentReady(attachmentModel);
                }
            });
        });
    }

    // -------------------- Helper Methods Starts Here ---------------------

    /**
     * Extracts the subtype (file extension) from a MIME type string.
     * e.g. "image/png" -> "png", "video/mp4" -> "mp4"
     *
     * @param mimeType the full MIME type string
     * @return the subtype, or empty string if not available
     */
    /**
     * Extracts a short, human-readable subtype for use as extension and UI label.
     *
     * For standard types (image/jpeg, audio/mp3, application/pdf) the MIME subtype
     * is already short and clean. But for Office documents the MIME subtype is a
     * long vendor string like "vnd.openxmlformats-officedocument.spreadsheetml.sheet".
     * In that case we prefer the file extension from the original filename.
     *
     * Examples:
     *   "application/pdf"          + "report.pdf"    → "pdf"
     *   "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
     *                               + "data.xlsx"    → "xlsx"
     *   "application/msword"       + "doc.doc"       → "doc"
     *   "image/jpeg"               + "photo.jpg"     → "jpeg"
     *   "application/zip"          + "archive.zip"   → "zip"
     */
    private String extractSubtype(String mimeType, String fileName) {
        // Try file extension first — always short and correct
        if (fileName != null && fileName.contains(".")) {
            String ext = fileName.substring(fileName.lastIndexOf('.') + 1);
            if (!ext.isEmpty()) return ext;
        }
        // Fallback to MIME subtype
        if (mimeType != null && mimeType.contains("/")) {
            return mimeType.substring(mimeType.indexOf("/") + 1);
        }
        return "";
    }

    /**
     * Gets the display file name from a content URI using ContentResolver.
     *
     * @param contentResolver the content resolver
     * @param uri             the content URI
     * @return the file name, or empty string if not available
     */
    private String getFileName(ContentResolver contentResolver, Uri uri) {
        String  fileName    =   "";
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

    /**
     * Gets image dimensions (width, height) from a content URI.
     *
     * @param contentResolver the content resolver
     * @param uri             the image content URI
     * @return int array [width, height]
     */
    private int[] getImageDimensions(ContentResolver contentResolver, Uri uri) {
        int     width   =   0;
        int     height  =   0;
        String[] projection =   { MediaStore.Images.Media.WIDTH, MediaStore.Images.Media.HEIGHT };
        try (Cursor cursor = contentResolver.query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int widthIndex  =   cursor.getColumnIndex(MediaStore.Images.Media.WIDTH);
                int heightIndex =   cursor.getColumnIndex(MediaStore.Images.Media.HEIGHT);
                if (widthIndex >= 0)    width   =   cursor.getInt(widthIndex);
                if (heightIndex >= 0)   height  =   cursor.getInt(heightIndex);
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error getting image dimensions: " + e.getMessage());
        }
        return new int[]{width, height};
    }

    // -------------------- Callback Interface ---------------------

    /**
     * Callback interface for receiving the built AttachmentModel after file selection.
     */
    public interface AttachmentResultCallback {
        /**
         * Called on the main thread when an attachment has been selected and its metadata extracted.
         *
         * @param attachmentModel the fully populated attachment model
         */
        void onAttachmentReady(AttachmentModel attachmentModel);
    }
}
