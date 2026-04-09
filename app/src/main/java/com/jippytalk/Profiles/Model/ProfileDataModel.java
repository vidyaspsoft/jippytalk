package com.jippytalk.Profiles.Model;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.graphics.Bitmap;

public class ProfileDataModel {

    // ---- Fields ----

    private final String        userId;
    private final String        userName;
    private final String        userAbout;
    private final String        userPhoneNumber;
    private final long          userJoinedDate;
    private final Bitmap        userProfilePicture;

    // ---- Constructor ----

    public ProfileDataModel(String userId, String userName, String userAbout,
                            String userPhoneNumber, long userJoinedDate,
                            Bitmap userProfilePicture) {
        this.userId                 =   userId;
        this.userName               =   userName;
        this.userAbout              =   userAbout;
        this.userPhoneNumber        =   userPhoneNumber;
        this.userJoinedDate         =   userJoinedDate;
        this.userProfilePicture     =   userProfilePicture;
    }

    // ---- Getters ----

    public String getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserAbout() {
        return userAbout;
    }

    public String getUserPhoneNumber() {
        return userPhoneNumber;
    }

    public long getUserJoinedDate() {
        return userJoinedDate;
    }

    public Bitmap getUserProfilePicture() {
        return userProfilePicture;
    }
}
