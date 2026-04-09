package com.jippytalk.FavouriteContacts;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

public class FavouriteContactsModel {

    // ---- Fields ----

    private final String        userId;
    private final String        name;
    private final String        profilePic;

    // ---- Constructor ----

    public FavouriteContactsModel(String userId, String name, String profilePic) {
        this.userId         =   userId;
        this.name           =   name;
        this.profilePic     =   profilePic;
    }

    // ---- Getters ----

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getProfilePic() {
        return profilePic;
    }
}
