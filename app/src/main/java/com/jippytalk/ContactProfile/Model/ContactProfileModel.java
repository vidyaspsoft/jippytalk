package com.jippytalk.ContactProfile.Model;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

/**
 * ContactProfileModel - Data model representing a contact's profile details.
 * Used in ContactsRepository to deliver contact profile data to the UI via LiveData.
 */
public class ContactProfileModel {

    private final String    contactName;
    private final String    contactNumber;
    private final String    contactAbout;
    private final String    contactProfilePicId;
    private final long      contactJoinedDate;
    private final int       isContact;
    private final int       isFavourite;

    public ContactProfileModel(String contactName, String contactNumber, String contactAbout,
                               String contactProfilePicId, long contactJoinedDate,
                               int isContact, int isFavourite) {
        this.contactName            =   contactName;
        this.contactNumber          =   contactNumber;
        this.contactAbout           =   contactAbout;
        this.contactProfilePicId    =   contactProfilePicId;
        this.contactJoinedDate      =   contactJoinedDate;
        this.isContact              =   isContact;
        this.isFavourite            =   isFavourite;
    }

    // ---- Getters ----

    public String getContactName()          { return contactName; }
    public String getContactNumber()        { return contactNumber; }
    public String getContactAbout()         { return contactAbout; }
    public String getContactProfilePicId()  { return contactProfilePicId; }
    public long   getContactJoinedDate()    { return contactJoinedDate; }
    public int    getIsContact()            { return isContact; }
    public int    getIsFavourite()          { return isFavourite; }
}
