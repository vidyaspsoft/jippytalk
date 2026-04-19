package com.jippytalk.Messages.Attachment.Model;

/**
 * Developer Name: Vidya Sagar
 * Created on: 09-04-2026
 */

/**
 * AttachmentModel - Data model representing a selected attachment from the device.
 *
 * Fields are populated locally after the user picks a file. S3-related fields
 * (bucket, region) are left empty and filled after uploading to the server.
 *
 * Content types: "image", "video", "audio", "document", "contact", "location"
 */
public class AttachmentModel {

    private String      media;
    private String      thumbnail;
    private String      contentType;
    private String      contentSubtype;
    private String      caption;
    private int         height;
    private int         width;
    private long        duration;
    private String      bucket;
    private String      region;
    private String      name;
    private String      s3Key;
    private String      fileTransferId;
    private String      localFilePath;
    // Per-message AES-256-GCM key + IV (Base64). Generated once when the
    // attachment is picked and reused for: file bytes, thumbnail bytes,
    // caption text, S3 URL, and thumbnail URL. Receiver uses the same
    // key+iv (delivered via WS payload) to decrypt all of them.
    private String      encryptionKey   =   "";
    private String      encryptionIv    =   "";

    // ---- Constructor ----

    public AttachmentModel(String media, String thumbnail, String contentType, String contentSubtype,
                           String caption, int height, int width, long duration,
                           String bucket, String region, String name) {
        this.media              =   media;
        this.thumbnail          =   thumbnail;
        this.contentType        =   contentType;
        this.contentSubtype     =   contentSubtype;
        this.caption            =   caption;
        this.height             =   height;
        this.width              =   width;
        this.duration           =   duration;
        this.bucket             =   bucket;
        this.region             =   region;
        this.name               =   name;
    }

    // ---- Getters ----

    public String getMedia() {
        return media;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public String getContentType() {
        return contentType;
    }

    public String getContentSubtype() {
        return contentSubtype;
    }

    public String getCaption() {
        return caption;
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public long getDuration() {
        return duration;
    }

    public String getBucket() {
        return bucket;
    }

    public String getRegion() {
        return region;
    }

    public String getName() {
        return name;
    }

    // ---- Setters ----

    public void setMedia(String media) {
        this.media              =   media;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail          =   thumbnail;
    }

    public void setCaption(String caption) {
        this.caption            =   caption;
    }

    public void setBucket(String bucket) {
        this.bucket             =   bucket;
    }

    public void setRegion(String region) {
        this.region             =   region;
    }

    public void setName(String name) {
        this.name               =   name;
    }

    public String getS3Key() {
        return s3Key;
    }

    public void setS3Key(String s3Key) {
        this.s3Key              =   s3Key;
    }

    public String getFileTransferId() {
        return fileTransferId;
    }

    public void setFileTransferId(String fileTransferId) {
        this.fileTransferId     =   fileTransferId;
    }

    public String getLocalFilePath() {
        return localFilePath;
    }

    public void setLocalFilePath(String localFilePath) {
        this.localFilePath      =   localFilePath;
    }

    public String getEncryptionKey() {
        return encryptionKey != null ? encryptionKey : "";
    }

    public void setEncryptionKey(String encryptionKey) {
        this.encryptionKey      =   encryptionKey != null ? encryptionKey : "";
    }

    public String getEncryptionIv() {
        return encryptionIv != null ? encryptionIv : "";
    }

    public void setEncryptionIv(String encryptionIv) {
        this.encryptionIv       =   encryptionIv != null ? encryptionIv : "";
    }

    // ---- toString ----

    @Override
    public String toString() {
        return "AttachmentModel{" +
                "media='" + media + '\'' +
                ", thumbnail='" + thumbnail + '\'' +
                ", contentType='" + contentType + '\'' +
                ", contentSubtype='" + contentSubtype + '\'' +
                ", caption='" + caption + '\'' +
                ", height=" + height +
                ", width=" + width +
                ", duration=" + duration +
                ", bucket='" + bucket + '\'' +
                ", region='" + region + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
