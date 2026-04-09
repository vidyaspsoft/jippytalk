package com.jippytalk.Contacts.Model;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

public class UsersModal {

    // ---- Fields ----

    private final String        rawContactId;
    private final int           isAppUser;
    private final String        contactUserId;
    private final String        contactName;
    private final String        contactPhone;
    private final String        contactAbout;
    private final String        profilePic;

    // ---- Constructor ----

    public UsersModal(String rawContactId, int isAppUser, String contactUserId,
                      String contactName, String contactPhone, String contactAbout,
                      String profilePic) {
        this.rawContactId       =   rawContactId;
        this.isAppUser          =   isAppUser;
        this.contactUserId      =   contactUserId;
        this.contactName        =   contactName;
        this.contactPhone       =   contactPhone;
        this.contactAbout       =   contactAbout;
        this.profilePic         =   profilePic;
    }

    // ---- Getters ----

    public String getRawContactId() {
        return rawContactId;
    }

    public int getIsAppUser() {
        return isAppUser;
    }

    public String getContactUserId() {
        return contactUserId;
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getContactAbout() {
        return contactAbout;
    }

    public String getProfilePic() {
        return profilePic;
    }
}
