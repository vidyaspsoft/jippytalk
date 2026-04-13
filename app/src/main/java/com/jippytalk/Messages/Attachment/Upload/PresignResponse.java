package com.jippytalk.Messages.Attachment.Upload;

/**
 * Developer Name: Vidya Sagar
 * Created on: 09-04-2026
 */

/**
 * PresignResponse - Holds the presigned upload URL and S3 metadata returned by the server.
 *
 * Response from POST /api/files/presign:
 * { "presigned_url": "...", "s3_key": "uploads/userId/file.pdf", "bucket": "bucket-name" }
 */
public class PresignResponse {

    private final String    presignedUrl;
    private final String    s3Key;
    private final String    bucket;

    public PresignResponse(String presignedUrl, String s3Key, String bucket) {
        this.presignedUrl   =   presignedUrl;
        this.s3Key          =   s3Key;
        this.bucket         =   bucket;
    }

    public String getPresignedUrl() {
        return presignedUrl;
    }

    public String getS3Key() {
        return s3Key;
    }

    public String getBucket() {
        return bucket;
    }
}
