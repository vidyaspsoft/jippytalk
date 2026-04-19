package com.jippytalk.Messages.Attachment;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;

import com.jippytalk.Extras;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/**
 * Generates small JPEG thumbnails for images and videos to upload alongside
 * the original file. Documents/audio return null (no visual thumbnail).
 *
 * Output: max 200x200 JPEG at 70% quality, saved to the app cache directory.
 */
public class ThumbnailGenerator {

    private static final int    MAX_DIMENSION       =   200;
    private static final int    JPEG_QUALITY        =   70;

    /**
     * Generates a thumbnail for the given URI.
     *
     * @param context     app context
     * @param uri         the content:// or file:// URI of the source file
     * @param contentType "image", "video", "audio", or "document"
     * @return a File pointing to the generated thumbnail JPEG, or null if no thumbnail
     *         can be created (docs, audio, or failure)
     */
    /**
     * Convenience overload that takes an absolute file path instead of a Uri.
     */
    public static File generateThumbnail(Context context, String filePath, String contentType) {
        if (filePath == null || filePath.isEmpty()) return null;
        return generateThumbnail(context,
                android.net.Uri.fromFile(new java.io.File(filePath)), contentType);
    }

    public static File generateThumbnail(Context context, Uri uri, String contentType) {
        if (contentType == null) return null;

        try {
            Bitmap bitmap = null;

            switch (contentType) {
                case "image"    -> bitmap = generateImageThumbnail(context, uri);
                case "video"    -> bitmap = generateVideoThumbnail(context, uri);
                case "document" -> bitmap = generatePdfThumbnail(context, uri);
                default         -> { return null; }
            }

            if (bitmap == null) return null;

            // PDFs: keep full resolution for readable text preview.
            // Images/videos: scale down to save memory + upload size.
            boolean isPdf = "document".equals(contentType);
            if (!isPdf) {
                bitmap = scaleBitmap(bitmap, MAX_DIMENSION);
            }

            // Save to cache dir (higher quality for PDFs so text stays sharp)
            File thumbFile = new File(context.getCacheDir(),
                    "thumb_" + System.currentTimeMillis() + (isPdf ? ".png" : ".jpg"));
            try (FileOutputStream fos = new FileOutputStream(thumbFile)) {
                if (isPdf) {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                } else {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, fos);
                }
                fos.flush();
            }
            bitmap.recycle();

            Log.e(Extras.LOG_MESSAGE, "Thumbnail generated: " + thumbFile.getAbsolutePath()
                    + " (" + thumbFile.length() + " bytes)");
            return thumbFile;

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Thumbnail generation failed: " + e.getMessage());
            return null;
        }
    }

    private static Bitmap generateImageThumbnail(Context context, Uri uri) {
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;

            // Decode bounds first
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeStream(is, null, opts);

            // Calculate inSampleSize
            opts.inSampleSize = calculateInSampleSize(opts, MAX_DIMENSION, MAX_DIMENSION);
            opts.inJustDecodeBounds = false;

            // Re-open stream (can't reuse) and decode scaled
            try (InputStream is2 = context.getContentResolver().openInputStream(uri)) {
                return is2 != null ? BitmapFactory.decodeStream(is2, null, opts) : null;
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Image thumbnail failed: " + e.getMessage());
            return null;
        }
    }

    // Hard cap for the PDF thumbnail raster. At 1600 × 1600 × 4 B/px
    // that's ~10 MB peak — safe even on low-RAM devices. A 200dp chat
    // thumbnail never needs more resolution than this.
    private static final int PDF_THUMB_MAX_DIMENSION   =   1600;
    // Never allocate a bitmap larger than this (in bytes). 20 MB gives
    // plenty of headroom but guarantees we never OOM on pathological PDFs.
    private static final long PDF_THUMB_MAX_BYTES      =   20L * 1024 * 1024;

    private static Bitmap generatePdfThumbnail(Context context, Uri uri) {
        try {
            // Open file descriptor — use direct file access for file:// URIs
            // (ContentResolver fails on file:// URIs on modern Android)
            android.os.ParcelFileDescriptor pfd;
            String scheme = uri.getScheme();
            if ("file".equals(scheme)) {
                java.io.File file = new java.io.File(uri.getPath());
                pfd = android.os.ParcelFileDescriptor.open(file,
                        android.os.ParcelFileDescriptor.MODE_READ_ONLY);
            } else {
                pfd = context.getContentResolver().openFileDescriptor(uri, "r");
            }
            if (pfd == null) return null;

            android.graphics.pdf.PdfRenderer renderer =
                    new android.graphics.pdf.PdfRenderer(pfd);
            if (renderer.getPageCount() == 0) {
                renderer.close();
                pfd.close();
                return null;
            }

            android.graphics.pdf.PdfRenderer.Page page = renderer.openPage(0);
            int pageW = page.getWidth();
            int pageH = page.getHeight();

            // Compute a safe render size. Start at 4× zoom for sharp text,
            // then shrink to fit within both the max-dimension AND max-bytes
            // caps. For a pathological PDF (e.g. a 200 MB stress-test file
            // with huge pages declared in points), we'd otherwise allocate
            // a 128 MB+ bitmap and OOM. This cap keeps us at ~10 MB peak.
            float scale = 4f;
            // Dimension cap
            int largest = Math.max(pageW, pageH);
            if (largest > 0 && largest * scale > PDF_THUMB_MAX_DIMENSION) {
                scale = (float) PDF_THUMB_MAX_DIMENSION / largest;
            }
            // Byte cap (4 bytes per pixel for ARGB_8888)
            long bytesAtScale = (long) (pageW * scale) * (long) (pageH * scale) * 4L;
            if (bytesAtScale > PDF_THUMB_MAX_BYTES) {
                double ratio = Math.sqrt((double) PDF_THUMB_MAX_BYTES / (double) bytesAtScale);
                scale = (float) (scale * ratio);
            }
            // Floor at 1× — going smaller than original rarely helps and
            // can produce unreadable text; clamp to min 0.5× just in case.
            if (scale < 0.5f) scale = 0.5f;

            int width  = Math.max(1, Math.round(pageW * scale));
            int height = Math.max(1, Math.round(pageH * scale));

            Log.e(Extras.LOG_MESSAGE, "PDF thumb: pageSize=" + pageW + "x" + pageH
                    + " → renderSize=" + width + "x" + height
                    + " scale=" + String.format(java.util.Locale.US, "%.2f", scale)
                    + " bytes≈" + (width * height * 4 / 1024) + " KB");

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            // White background (PDF pages are transparent by default)
            bitmap.eraseColor(android.graphics.Color.WHITE);
            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            renderer.close();
            pfd.close();

            return bitmap;
        } catch (OutOfMemoryError oom) {
            // Defensive — we've already capped the bitmap size, but huge
            // PDFs combined with low-RAM devices can still fail. Don't
            // crash the app; return null and let the caller show the
            // generic doc icon instead.
            Log.e(Extras.LOG_MESSAGE, "PDF thumbnail OOM (page too large): "
                    + oom.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "PDF thumbnail failed: " + e.getMessage());
            return null;
        }
    }

    private static Bitmap generateVideoThumbnail(Context context, Uri uri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(context, uri);
            return retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Video thumbnail failed: " + e.getMessage());
            return null;
        } finally {
            try { retriever.release(); } catch (Exception ignored) {}
        }
    }

    private static Bitmap scaleBitmap(Bitmap source, int maxDim) {
        int w = source.getWidth();
        int h = source.getHeight();
        if (w <= maxDim && h <= maxDim) return source;

        float scale = Math.min((float) maxDim / w, (float) maxDim / h);
        int nw = Math.round(w * scale);
        int nh = Math.round(h * scale);
        return Bitmap.createScaledBitmap(source, nw, nh, true);
    }

    private static int calculateInSampleSize(BitmapFactory.Options options,
                                             int reqWidth, int reqHeight) {
        int height      =   options.outHeight;
        int width       =   options.outWidth;
        int inSampleSize =  1;
        if (height > reqHeight || width > reqWidth) {
            int halfH = height / 2;
            int halfW = width / 2;
            while ((halfH / inSampleSize) >= reqHeight && (halfW / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
