package com.jippytalk.Messages.Attachment.Preview;

import android.net.Uri;

/**
 * Lightweight data holder for a single file in the attachment preview page.
 * Carries the URI, content type, display name, size, extension, and per-file caption.
 */
public class PreviewItem {

    public Uri      uri;
    public String   contentType;        // "image", "video", "audio", "document"
    public String   fileName;
    public String   extension;
    public long     fileSize;
    public String   caption     =   "";
    public int      width;
    public int      height;
    public long     duration;

    public PreviewItem(Uri uri, String contentType, String fileName,
                       String extension, long fileSize) {
        this.uri            =   uri;
        this.contentType    =   contentType;
        this.fileName       =   fileName;
        this.extension      =   extension;
        this.fileSize       =   fileSize;
    }
}
