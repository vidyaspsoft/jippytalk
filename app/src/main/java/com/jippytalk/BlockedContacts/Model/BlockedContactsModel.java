package com.jippytalk.BlockedContacts.Model;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

public class BlockedContactsModel {

    // ---- Fields ----

    private final String        userId;
    private final String        phone;
    private final long          timestamp;

    // ---- Constructor ----

    public BlockedContactsModel(String userId, String phone, long timestamp) {
        this.userId         =   userId;
        this.phone          =   phone;
        this.timestamp      =   timestamp;
    }

    // ---- Getters ----

    public String getUserId() {
        return userId;
    }

    public String getPhone() {
        return phone;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
