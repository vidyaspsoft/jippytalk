package com.jippytalk.Messages.Attachment.Preview;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.jippytalk.R;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewPager2 adapter — shows full-size preview for each selected attachment.
 *
 *   Images  → Glide-loaded full preview
 *   Videos  → Glide-loaded first frame + play icon overlay
 *   Docs    → document icon card with filename + size/type
 *   Audio   → audio icon card with filename + duration
 */
public class PreviewPagerAdapter extends RecyclerView.Adapter<PreviewPagerAdapter.PreviewViewHolder> {

    private final Context               context;
    private final List<PreviewItem>     items;
    // Single-thread pool for PDF rendering — PdfRenderer is not thread-safe,
    // and we only ever render one page at a time.
    private final ExecutorService       pdfRenderExecutor = Executors.newSingleThreadExecutor();

    public PreviewPagerAdapter(Context context, List<PreviewItem> items) {
        this.context    =   context;
        this.items      =   items;
    }

    @NonNull
    @Override
    public PreviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_preview_page, parent, false);
        return new PreviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PreviewViewHolder h, int position) {
        PreviewItem item = items.get(position);

        // Reset all sections to GONE
        h.ivPreview.setVisibility(View.GONE);
        h.ivPlayOverlay.setVisibility(View.GONE);
        h.llDocPreview.setVisibility(View.GONE);
        h.llAudioPreview.setVisibility(View.GONE);

        switch (item.contentType != null ? item.contentType : "") {
            case "image" -> {
                h.ivPreview.setVisibility(View.VISIBLE);
                Glide.with(context)
                        .load(item.uri)
                        .into(h.ivPreview);
            }
            case "video" -> {
                h.ivPreview.setVisibility(View.VISIBLE);
                h.ivPlayOverlay.setVisibility(View.VISIBLE);
                // Glide can extract a frame from video content:// URIs
                Glide.with(context)
                        .load(item.uri)
                        .into(h.ivPreview);
            }
            case "audio" -> {
                h.llAudioPreview.setVisibility(View.VISIBLE);
                h.tvAudioFileName.setText(item.fileName != null ? item.fileName : "Audio");
                String durLabel = item.duration > 0 ? formatDuration(item.duration) : "";
                String ext = item.extension != null ? item.extension.toUpperCase(Locale.US) : "";
                h.tvAudioDuration.setText(buildSubtitle(durLabel, ext, item.fileSize));
            }
            default -> {
                // Document: try to render PDF first page as image preview.
                // For non-PDF docs (docx, xlsx, etc.) show the generic icon.
                boolean isPdf = item.extension != null
                        && item.extension.equalsIgnoreCase("pdf");
                if (isPdf) {
                    // Show the doc icon immediately so the UI doesn't stall.
                    // Kick off PDF rendering off the main thread — the
                    // ThumbnailGenerator caps bitmap size so it won't OOM,
                    // but PdfRenderer on a 200MB file can still take seconds.
                    showDocIcon(h, item);
                    renderPdfAsync(h, item);
                } else {
                    showDocIcon(h, item);
                }
            }
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void showDocIcon(PreviewViewHolder h, PreviewItem item) {
        h.llDocPreview.setVisibility(View.VISIBLE);
        h.ivPreview.setVisibility(View.GONE);
        h.tvDocFileName.setText(item.fileName != null ? item.fileName : "File");
        String ext = item.extension != null ? item.extension.toUpperCase(Locale.US) : "";
        h.tvDocFileSize.setText(buildSubtitle("", ext, item.fileSize));
    }

    /**
     * Renders the first PDF page on a background thread and, if successful,
     * swaps the doc icon for the rendered image. Safe against OOM: the
     * ThumbnailGenerator caps bitmap size to ~10 MB. On failure, the doc
     * icon stays visible.
     *
     * We snapshot the ViewHolder + item at invocation time. If the holder
     * has been recycled for a different item by the time the render
     * finishes, we skip the UI update (tag check below).
     */
    private void renderPdfAsync(PreviewViewHolder h, PreviewItem item) {
        // Tag the ImageView with the current item's URI so we can detect
        // stale results if the holder has been recycled for a different
        // item by the time the PDF render finishes.
        final Object currentTag = item.uri;
        h.ivPreview.setTag(currentTag);
        pdfRenderExecutor.execute(() -> {
            java.io.File thumb;
            try {
                thumb = com.jippytalk.Messages.Attachment.ThumbnailGenerator
                        .generateThumbnail(context, item.uri, "document");
            } catch (Throwable t) {
                android.util.Log.e("JippyTalk",
                        "PreviewPagerAdapter: PDF render failed — " + t.getMessage());
                return;
            }
            if (thumb == null || !thumb.exists()) return;
            h.ivPreview.post(() -> {
                // Bail if the holder has been rebound to a different item
                if (h.ivPreview.getTag() != currentTag) return;
                h.llDocPreview.setVisibility(View.GONE);
                h.ivPreview.setVisibility(View.VISIBLE);
                Glide.with(context).load(thumb).into(h.ivPreview);
            });
        });
    }

    // -------------------- Formatting Helpers ---------------------

    private String buildSubtitle(String prefix, String ext, long fileSize) {
        String sizeLabel = fileSize > 0 ? formatByteSize(fileSize) : "";
        StringBuilder sb = new StringBuilder();
        if (!sizeLabel.isEmpty()) sb.append(sizeLabel);
        if (!ext.isEmpty()) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(ext);
        }
        if (!prefix.isEmpty()) {
            if (sb.length() > 0) sb.insert(0, prefix + " · ");
            else sb.append(prefix);
        }
        return sb.length() > 0 ? sb.toString() : "Document";
    }

    private String formatByteSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return (bytes / 1024) + " KB";
        if (bytes < 1024L * 1024 * 1024)
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    /** Formats milliseconds to "M:SS" or "H:MM:SS". */
    private String formatDuration(long millis) {
        long totalSec   =   millis / 1000;
        long hours      =   totalSec / 3600;
        long mins       =   (totalSec % 3600) / 60;
        long secs       =   totalSec % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, mins, secs);
        }
        return String.format(Locale.US, "%d:%02d", mins, secs);
    }

    // -------------------- ViewHolder ---------------------

    static class PreviewViewHolder extends RecyclerView.ViewHolder {
        final ImageView     ivPreview;
        final ImageView     ivPlayOverlay;
        final LinearLayout  llDocPreview;
        final TextView      tvDocFileName;
        final TextView      tvDocFileSize;
        final LinearLayout  llAudioPreview;
        final TextView      tvAudioFileName;
        final TextView      tvAudioDuration;

        PreviewViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPreview           =   itemView.findViewById(R.id.ivPreview);
            ivPlayOverlay       =   itemView.findViewById(R.id.ivPlayOverlay);
            llDocPreview        =   itemView.findViewById(R.id.llDocPreview);
            tvDocFileName       =   itemView.findViewById(R.id.tvDocFileName);
            tvDocFileSize       =   itemView.findViewById(R.id.tvDocFileSize);
            llAudioPreview      =   itemView.findViewById(R.id.llAudioPreview);
            tvAudioFileName     =   itemView.findViewById(R.id.tvAudioFileName);
            tvAudioDuration     =   itemView.findViewById(R.id.tvAudioDuration);
        }
    }
}
