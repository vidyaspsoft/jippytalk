package com.jippytalk.Messages.Attachment.Preview;

import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.jippytalk.Extras;
import com.jippytalk.R;

import java.util.ArrayList;

/**
 * WhatsApp-style attachment preview activity.
 * Supports multi-file selection — shows each file in a swipeable ViewPager2
 * with a bottom thumbnail strip. Each file can have its own caption.
 * Files can be removed before sending.
 *
 * Intent extras (in):
 *   EXTRA_URIS          — ArrayList<Uri> of selected files
 *   EXTRA_CONTENT_TYPES  — ArrayList<String> of content types ("image", "document", etc.)
 *
 * Intent extras (out on RESULT_OK):
 *   EXTRA_URIS           — remaining URIs after removals
 *   EXTRA_CONTENT_TYPES  — matching content types
 *   EXTRA_CAPTIONS       — per-file captions
 *   EXTRA_FILE_NAMES     — per-file display names
 *   EXTRA_EXTENSIONS     — per-file extensions
 */
public class AttachmentPreviewActivity extends AppCompatActivity {

    public static final String  EXTRA_URIS              =   "extra_uris";
    public static final String  EXTRA_CONTENT_TYPES     =   "extra_content_types";
    public static final String  EXTRA_CAPTIONS          =   "extra_captions";
    public static final String  EXTRA_FILE_NAMES        =   "extra_file_names";
    public static final String  EXTRA_EXTENSIONS        =   "extra_extensions";

    private final ArrayList<PreviewItem>    items               =   new ArrayList<>();
    private PreviewPagerAdapter             pagerAdapter;
    private PreviewThumbnailAdapter         thumbAdapter;
    private ViewPager2                      vpPreview;
    private RecyclerView                    rvThumbnailStrip;
    private TextView                        tvFileCount;
    private EditText                        etCaption;
    private int                             currentPage         =   0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Disable edge-to-edge so content doesn't go behind system bars
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            // API 35+ opts into edge-to-edge by default; override window insets manually
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        }
        getWindow().setStatusBarColor(android.graphics.Color.BLACK);
        getWindow().setNavigationBarColor(android.graphics.Color.BLACK);

        setContentView(R.layout.activity_attachment_preview);

        vpPreview           =   findViewById(R.id.vpPreview);
        rvThumbnailStrip    =   findViewById(R.id.rvThumbnailStrip);
        tvFileCount         =   findViewById(R.id.tvFileCount);
        etCaption           =   findViewById(R.id.etCaption);
        ImageView ivBack    =   findViewById(R.id.ivBack);
        ImageView ivRemove  =   findViewById(R.id.ivRemoveFile);
        FloatingActionButton fabSend = findViewById(R.id.fabSend);

        // Parse intent extras
        ArrayList<Uri>      uris            =   getIntent().getParcelableArrayListExtra(EXTRA_URIS);
        ArrayList<String>   contentTypes    =   getIntent().getStringArrayListExtra(EXTRA_CONTENT_TYPES);

        if (uris == null || uris.isEmpty()) {
            Log.e(Extras.LOG_MESSAGE, "AttachmentPreviewActivity: no URIs, finishing");
            finish();
            return;
        }

        // Build preview items from URIs + extract metadata for video/audio
        for (int i = 0; i < uris.size(); i++) {
            Uri     uri         =   uris.get(i);
            String  type        =   contentTypes != null && i < contentTypes.size()
                                    ? contentTypes.get(i) : "document";
            String  fileName    =   getFileName(uri);
            String  extension   =   extractExtension(fileName, uri);
            long    fileSize    =   getFileSize(uri);
            PreviewItem item    =   new PreviewItem(uri, type, fileName, extension, fileSize);

            // Extract duration for audio/video, dimensions for video/image
            if ("video".equals(type) || "audio".equals(type)) {
                android.media.MediaMetadataRetriever retriever = new android.media.MediaMetadataRetriever();
                try {
                    retriever.setDataSource(this, uri);
                    String dStr = retriever.extractMetadata(
                            android.media.MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (dStr != null) item.duration = Long.parseLong(dStr);
                    if ("video".equals(type)) {
                        String wStr = retriever.extractMetadata(
                                android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                        String hStr = retriever.extractMetadata(
                                android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                        if (wStr != null) item.width = Integer.parseInt(wStr);
                        if (hStr != null) item.height = Integer.parseInt(hStr);
                    }
                } catch (Exception e) {
                    Log.e(Extras.LOG_MESSAGE, "Metadata extraction failed: " + e.getMessage());
                } finally {
                    try { retriever.release(); } catch (Exception ignored) {}
                }
            }

            items.add(item);
        }

        // ViewPager2 setup
        pagerAdapter    =   new PreviewPagerAdapter(this, items);
        vpPreview.setAdapter(pagerAdapter);
        vpPreview.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                onPageChanged(position);
            }
        });

        // Thumbnail strip setup
        thumbAdapter    =   new PreviewThumbnailAdapter(this, items);
        rvThumbnailStrip.setLayoutManager(
                new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvThumbnailStrip.setAdapter(thumbAdapter);
        thumbAdapter.setClickListener(position -> vpPreview.setCurrentItem(position, true));
        updateThumbnailStripVisibility();

        // Initial state
        updateFileCount();

        // Back
        ivBack.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        // Remove current file
        ivRemove.setOnClickListener(v -> removeCurrentFile());

        // Send
        fabSend.setOnClickListener(v -> sendFiles());
    }

    private void onPageChanged(int position) {
        // Save caption from previous page
        if (currentPage >= 0 && currentPage < items.size()) {
            items.get(currentPage).caption = etCaption.getText().toString().trim();
        }
        currentPage = position;

        // Load caption for new page
        if (position >= 0 && position < items.size()) {
            etCaption.setText(items.get(position).caption);
        }

        updateFileCount();
        thumbAdapter.setSelectedPosition(position);
        rvThumbnailStrip.scrollToPosition(position);
    }

    private void removeCurrentFile() {
        if (items.isEmpty()) return;

        items.remove(currentPage);
        pagerAdapter.notifyDataSetChanged();
        thumbAdapter.notifyDataSetChanged();

        if (items.isEmpty()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        // Adjust current page
        if (currentPage >= items.size()) {
            currentPage = items.size() - 1;
        }
        vpPreview.setCurrentItem(currentPage, false);
        onPageChanged(currentPage);
        updateThumbnailStripVisibility();
    }

    private void sendFiles() {
        // Save caption for the current page
        if (currentPage >= 0 && currentPage < items.size()) {
            items.get(currentPage).caption = etCaption.getText().toString().trim();
        }

        // Build result intent
        ArrayList<Uri>      uris            =   new ArrayList<>();
        ArrayList<String>   contentTypes    =   new ArrayList<>();
        ArrayList<String>   captions        =   new ArrayList<>();
        ArrayList<String>   fileNames       =   new ArrayList<>();
        ArrayList<String>   extensions      =   new ArrayList<>();

        for (PreviewItem item : items) {
            uris.add(item.uri);
            contentTypes.add(item.contentType);
            captions.add(item.caption);
            fileNames.add(item.fileName);
            extensions.add(item.extension);
        }

        Intent result = new Intent();
        result.putParcelableArrayListExtra(EXTRA_URIS, uris);
        result.putStringArrayListExtra(EXTRA_CONTENT_TYPES, contentTypes);
        result.putStringArrayListExtra(EXTRA_CAPTIONS, captions);
        result.putStringArrayListExtra(EXTRA_FILE_NAMES, fileNames);
        result.putStringArrayListExtra(EXTRA_EXTENSIONS, extensions);
        setResult(RESULT_OK, result);
        finish();
    }

    private void updateFileCount() {
        if (items.size() <= 1) {
            tvFileCount.setText(items.isEmpty() ? "" : items.get(0).fileName);
        } else {
            tvFileCount.setText((currentPage + 1) + " of " + items.size());
        }
    }

    private void updateThumbnailStripVisibility() {
        rvThumbnailStrip.setVisibility(items.size() > 1 ? View.VISIBLE : View.GONE);
    }

    // -------------------- File Metadata Helpers ---------------------

    private String getFileName(Uri uri) {
        String name = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) name = cursor.getString(idx);
                }
            } catch (Exception ignored) {}
        }
        if (name == null) {
            name = uri.getLastPathSegment();
        }
        return name != null ? name : "file";
    }

    private long getFileSize(Uri uri) {
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                    if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx);
                }
            } catch (Exception ignored) {}
        }
        return 0;
    }

    private String extractExtension(String fileName, Uri uri) {
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf('.') + 1);
        }
        String mimeType = getContentResolver().getType(uri);
        if (mimeType != null) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (ext != null) return ext;
        }
        return "";
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }
}
