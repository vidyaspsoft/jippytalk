package com.jippytalk.Database.ContactsDatabase.DAO;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.SQLException;
import android.util.Log;

import com.jippytalk.CommonUtils;
import com.jippytalk.Contacts.Model.UsersModal;
import com.jippytalk.Database.ContactsDatabase.ContactsDatabase;
import com.jippytalk.Extras;
import com.jippytalk.Managers.ContactManager;

import net.zetetic.database.sqlcipher.SQLiteDatabase;
import net.zetetic.database.sqlcipher.SQLiteNotADatabaseException;

import java.util.ArrayList;

public class ContactsDatabaseDAO {

    private final ContactsDatabase              contactsDatabase;

    public ContactsDatabaseDAO(ContactsDatabase contactsDatabase) {
        this.contactsDatabase               =   contactsDatabase;
    }

    /*
    Below method inserts all contacts in app local database to display
    the contacts who are using this app and to start conversation with them
    */

    public boolean insertNonContacts(int rawContactId, String contactUserId,
                                     int isAppUser, int isContact, String contactName,
                                     String contactPhone, String contactAbout, long contactJoinedDate,
                                     String contactHashedPhoneNumber, int isFavourite, String contactProfilePicId,
                                     int deviceId) {

        boolean isSuccess = false;
        SQLiteDatabase sqLiteDatabase = contactsDatabase.getWritableDb();

        try {
            sqLiteDatabase.beginTransaction();

            // --- Insert into CONTACTS_TABLE with conflict ignore ---
            ContentValues contactValues = new ContentValues();
            contactValues.put(ContactsDatabase.RAW_CONTACT_ID, rawContactId);
            contactValues.put(ContactsDatabase.CONTACT_USER_ID, contactUserId);
            contactValues.put(ContactsDatabase.IS_APP_USER, isAppUser);
            contactValues.put(ContactsDatabase.IS_CONTACT, isContact);
            contactValues.put(ContactsDatabase.CONTACT_NAME, contactName);
            contactValues.put(ContactsDatabase.CONTACT_PHONE, contactPhone);
            contactValues.put(ContactsDatabase.CONTACT_PHONE_HASH, contactHashedPhoneNumber);
            contactValues.put(ContactsDatabase.CONTACT_ABOUT, contactAbout);
            contactValues.put(ContactsDatabase.DATE_JOINED, contactJoinedDate);
            contactValues.put(ContactsDatabase.IS_FAVOURITE, isFavourite);
            contactValues.put(ContactsDatabase.PROFILE_PIC, contactProfilePicId);

            sqLiteDatabase.insertWithOnConflict(
                    ContactsDatabase.CONTACTS_TABLE,
                    null,
                    contactValues,
                    SQLiteDatabase.CONFLICT_IGNORE
            );

            // --- Update CONTACTS_TABLE for existing row if necessary ---
            ContentValues updateContactValues = new ContentValues();
            updateContactValues.put(ContactsDatabase.CONTACT_PHONE, contactPhone);
            updateContactValues.put(ContactsDatabase.PROFILE_PIC, contactProfilePicId);

            sqLiteDatabase.update(
                    ContactsDatabase.CONTACTS_TABLE,
                    updateContactValues,
                    ContactsDatabase.CONTACT_USER_ID + "=?",
                    new String[]{contactUserId}
            );

            // --- Insert or update CONTACT_KEYS_TABLE per device ---
            ContentValues keyValues = new ContentValues();
            keyValues.put(ContactsDatabase.KEYS_CONTACT_USER_ID, contactUserId);
            keyValues.put(ContactsDatabase.CONTACT_DEVICE_ID, deviceId);

            int keyUpdateRows = sqLiteDatabase.update(
                    ContactsDatabase.CONTACT_KEYS_TABLE,
                    keyValues,
                    ContactsDatabase.KEYS_CONTACT_USER_ID + "=? AND " + ContactsDatabase.CONTACT_DEVICE_ID + "=?",
                    new String[]{contactUserId, String.valueOf(deviceId)}
            );

            if (keyUpdateRows == 0) {
                long keyInsertResult = sqLiteDatabase.insert(
                        ContactsDatabase.CONTACT_KEYS_TABLE,
                        null,
                        keyValues
                );

                if (keyInsertResult == -1) {
                    Log.e(Extras.LOG_MESSAGE, "Failed to insert contact keys: " + contactUserId);
                    return false;
                }
            }

            sqLiteDatabase.setTransactionSuccessful();
            isSuccess = true;
            Log.e(Extras.LOG_MESSAGE, "Contact inserted/updated successfully: " + contactUserId);

        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Exception inserting/updating contact: " + contactUserId, e);
        } finally {
            sqLiteDatabase.endTransaction();
        }

        return isSuccess;
    }

    public void batchInsertContact(ArrayList<UsersModal> contactList) {
        SQLiteDatabase sqLiteDatabase   =   contactsDatabase.getWritableDb();
        try  {
            sqLiteDatabase.beginTransaction();
            try {
                for (UsersModal contact : contactList) {
                    String phoneHash = CommonUtils.sha256Hash(contact.getContactPhone());
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(ContactsDatabase.RAW_CONTACT_ID, "");
                    contentValues.put(ContactsDatabase.CONTACT_USER_ID, "");
                    contentValues.put(ContactsDatabase.IS_APP_USER, ContactManager.NOT_APP_USER);
                    contentValues.put(ContactsDatabase.IS_CONTACT, ContactManager.IS_CONTACT);
                    contentValues.put(ContactsDatabase.CONTACT_NAME, contact.getContactName());
                    contentValues.put(ContactsDatabase.CONTACT_PHONE, contact.getContactPhone());
                    contentValues.put(ContactsDatabase.CONTACT_ABOUT, "");
                    contentValues.put(ContactsDatabase.DATE_JOINED, 0);
                    contentValues.put(ContactsDatabase.CONTACT_PHONE_HASH, phoneHash);
                    contentValues.put(ContactsDatabase.IS_FAVOURITE, ContactManager.CONTACT_NOT_FAVOURITE);
                    contentValues.put(ContactsDatabase.PROFILE_PIC, "");

                    long result = sqLiteDatabase.insertWithOnConflict(ContactsDatabase.CONTACTS_TABLE, null,
                            contentValues, SQLiteDatabase.CONFLICT_IGNORE);

                    if (result == -1) {
                        Log.d(Extras.LOG_MESSAGE, "Failed to insert contact: " + contact.getContactName());
                        ContentValues updateValues = new ContentValues();
                        updateValues.put(ContactsDatabase.CONTACT_NAME, contact.getContactName());
                        updateValues.put(ContactsDatabase.CONTACT_ABOUT, contact.getContactAbout());

                        sqLiteDatabase.update(
                                ContactsDatabase.CONTACTS_TABLE,
                                updateValues,
                                ContactsDatabase.CONTACT_PHONE_HASH + "=?",
                                new String[]{phoneHash}
                        );
                    }
                }
                sqLiteDatabase.setTransactionSuccessful();
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Error during batch insert: " + e.getMessage());
            } finally {
                sqLiteDatabase.endTransaction();
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to open db for batch insert: " + e.getMessage());
        }
    }

    public boolean updateContactAsUserInCaseOfSyncFail(String contactId, String contactNumber, int deviceId, String contactAbout,
                                                        long joinedOn, String profilePicId) {
        boolean isSuccess   =   false;
        SQLiteDatabase  sqLiteDatabase      =   contactsDatabase.getWritableDb();
        try {
            sqLiteDatabase.beginTransaction();
            try {
                ContentValues updateContactValues   =   new ContentValues();
                updateContactValues.put(ContactsDatabase.IS_APP_USER, ContactManager.IS_APP_USER);
                updateContactValues.put(ContactsDatabase.CONTACT_USER_ID, contactId);
                updateContactValues.put(ContactsDatabase.CONTACT_ABOUT, contactAbout);
                updateContactValues.put(ContactsDatabase.DATE_JOINED, joinedOn);
                updateContactValues.put(ContactsDatabase.PROFILE_PIC, profilePicId);

                int updateResult = sqLiteDatabase.update(ContactsDatabase.CONTACTS_TABLE, updateContactValues,
                        ContactsDatabase.CONTACT_PHONE + "=?", new String[]{contactNumber});

                if (updateResult > 0) {
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(ContactsDatabase.KEYS_CONTACT_USER_ID, contactId);
                    contentValues.put(ContactsDatabase.CONTACT_DEVICE_ID, deviceId);

                    long result = sqLiteDatabase.insert(ContactsDatabase.CONTACT_KEYS_TABLE,null, contentValues);

                    if (result != -1) {
                        sqLiteDatabase.setTransactionSuccessful();
                        isSuccess   =   true;
                        Log.e(Extras.LOG_MESSAGE,"contact keys inserted successfully");
                    }
                }
                else {
                    Log.e(Extras.LOG_MESSAGE, "failed to update contact error in db file ");
                }
            }
            catch (SQLiteNotADatabaseException e) {
                Log.e(Extras.LOG_MESSAGE, "Error updating contact error in db file  caught not database: " + e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error updating contact error in db file : " + e.getMessage());
        }
        return isSuccess;
    }

    public boolean updateContactAsUserAndInsertKeysData(String contactId, String contactHashedNumber,
                                                        int registrationId, int deviceId, String about,
                                                        long joinedOn, String profilePicId, String contactPublicKey,
                                                        int signedPreKeyId, String signedPrePublicKey, String signature) {
        boolean         isSuccess           =   false;
        SQLiteDatabase  sqLiteDatabase      =   contactsDatabase.getWritableDb();
        try {
            sqLiteDatabase.beginTransaction();
            try {

                ContentValues updateContactValues   =   new ContentValues();
                updateContactValues.put(ContactsDatabase.IS_APP_USER, ContactManager.IS_APP_USER);
                updateContactValues.put(ContactsDatabase.CONTACT_USER_ID, contactId);
                updateContactValues.put(ContactsDatabase.CONTACT_ABOUT, about);
                updateContactValues.put(ContactsDatabase.DATE_JOINED, joinedOn);
                updateContactValues.put(ContactsDatabase.PROFILE_PIC, profilePicId);

                int updateResult = sqLiteDatabase.update(ContactsDatabase.CONTACTS_TABLE, updateContactValues,
                        ContactsDatabase.CONTACT_PHONE_HASH + "=?", new String[]{contactHashedNumber});

                if (updateResult > 0) {
                    ContentValues contentValues = new ContentValues();
                    contentValues.put(ContactsDatabase.KEYS_CONTACT_USER_ID, contactId);
                    contentValues.put(ContactsDatabase.CONTACT_REGISTRATION_ID, registrationId);
                    contentValues.put(ContactsDatabase.CONTACT_DEVICE_ID, deviceId);
                    contentValues.put(ContactsDatabase.CONTACT_PUBLIC_KEY, contactPublicKey);
                    contentValues.put(ContactsDatabase.CONTACT_SIGNED_KEY_ID, signedPreKeyId);
                    contentValues.put(ContactsDatabase.CONTACT_SIGNED_PRE_PUBLIC_KEY, signedPrePublicKey);
                    contentValues.put(ContactsDatabase.CONTACT_SIGNATURE, signature);

                    long result = sqLiteDatabase.insertWithOnConflict(ContactsDatabase.CONTACT_KEYS_TABLE,null,
                            contentValues, SQLiteDatabase.CONFLICT_REPLACE);

                    if (result != -1) {
                        sqLiteDatabase.setTransactionSuccessful();
                        isSuccess   =   true;
                        Log.e(Extras.LOG_MESSAGE,"contact keys inserted successfully");
                    }
                }
                else {
                    Log.e(Extras.LOG_MESSAGE, "failed to update contact error in db file ");
                }
            }
            catch (SQLiteNotADatabaseException e) {
                Log.e(Extras.LOG_MESSAGE, "Error updating contact error in db file  caught not database: " + e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error updating contact error in db file : " + e.getMessage());
        }
        return isSuccess;
    }

    public boolean updateContactKeysData(String contactId, int registrationId, int deviceId,
                                         String contactPublicKey, int signedPreKeyId, String signedPrePublicKey,
                                         String signature) {

        boolean isSuccess   =   false;
        SQLiteDatabase  sqLiteDatabase      =   contactsDatabase.getWritableDb();
        try {
            sqLiteDatabase.beginTransaction();
            try {
                ContentValues contentValues = new ContentValues();
                contentValues.put(ContactsDatabase.KEYS_CONTACT_USER_ID, contactId);
                contentValues.put(ContactsDatabase.CONTACT_REGISTRATION_ID, registrationId);
                contentValues.put(ContactsDatabase.CONTACT_DEVICE_ID, deviceId);
                contentValues.put(ContactsDatabase.CONTACT_PUBLIC_KEY, contactPublicKey);
                contentValues.put(ContactsDatabase.CONTACT_SIGNED_KEY_ID, signedPreKeyId);
                contentValues.put(ContactsDatabase.CONTACT_SIGNED_PRE_PUBLIC_KEY, signedPrePublicKey);
                contentValues.put(ContactsDatabase.CONTACT_SIGNATURE, signature);

                long result = sqLiteDatabase.insertWithOnConflict(ContactsDatabase.CONTACT_KEYS_TABLE, null,
                        contentValues, SQLiteDatabase.CONFLICT_REPLACE);

                if (result != -1) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess   =   true;
                    Log.e(Extras.LOG_MESSAGE,"contact keys inserted successfully");
                }
                else {
                    Log.e(Extras.LOG_MESSAGE, "failed to update contact keys error in db file ");
                }
            }
            catch (SQLiteNotADatabaseException e) {
                Log.e(Extras.LOG_MESSAGE, "Error updating contact error in db file  caught not database: " + e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error updating contact error in db file : " + e.getMessage());
        }
        return isSuccess;
    }

    /*
    Below method updates the name or phone of a contact
    inside app database in case of any updates inside phone contacts
    */

    public boolean updateContactInDatabase(UsersModal usersModal) {

        boolean isSuccess   = false;
        SQLiteDatabase sqLiteDatabase   =   contactsDatabase.getWritableDb();
        try {
            sqLiteDatabase.beginTransaction();
            try {
                ContentValues values = new ContentValues();
                values.put(ContactsDatabase.CONTACT_NAME, usersModal.getContactName());
                values.put(ContactsDatabase.CONTACT_PHONE, usersModal.getContactPhone());

                if (usersModal.getContactName() != null && !usersModal.getContactName().isEmpty())
                {
                    int rowsEffected = sqLiteDatabase.update(ContactsDatabase.CONTACTS_TABLE, values,
                            ContactsDatabase.CONTACT_PHONE_HASH + "=?",
                            new String[]{CommonUtils.sha256Hash(usersModal.getContactPhone())});

                    if (rowsEffected > 0) {
                        sqLiteDatabase.setTransactionSuccessful();
                        isSuccess = true;
                    }
                    else {
                        Log.e(Extras.LOG_MESSAGE, "failed to update contact error in db file ");
                    }
                }
                else {
                    Log.e(Extras.LOG_MESSAGE, "failed to update contact due to userModel values are null error in db file ");
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Error updating contact error in db file : " + e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error updating contact error in db file : " + e.getMessage());
        }
        return isSuccess;
    }

    public boolean deleteContact(String contactId) {

        SQLiteDatabase sqLiteDatabase   =   contactsDatabase.getWritableDb();
        try {
            sqLiteDatabase.beginTransaction();
            try {
                int rowsAffected = sqLiteDatabase.delete(ContactsDatabase.CONTACTS_TABLE, ContactsDatabase.CONTACT_USER_ID + "=?", new String[]{contactId});
                if (rowsAffected > 0)
                {
                    sqLiteDatabase.setTransactionSuccessful();
                    return true;
                }
            }
            catch (Exception e)
            {
                Log.e(Extras.LOG_MESSAGE, "Error deleting contact: " + e.getMessage(), e);
            }
            finally
            {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE, "Error deleting contact: " + e.getMessage(), e);
        }
        return false;
    }

    public boolean deleteContactFromDatabase(String phoneHash) {
        SQLiteDatabase sqLiteDatabase   =   contactsDatabase.getWritableDb();
        try {
            sqLiteDatabase.beginTransaction();
            try {
                int rowsAffected = sqLiteDatabase.delete(ContactsDatabase.CONTACTS_TABLE,
                        ContactsDatabase.CONTACT_PHONE_HASH + "=?", new String[]{phoneHash});
                if (rowsAffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    return true;
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Error deleting contact: " + e.getMessage(), e);
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error deleting contact: " + e.getMessage(), e);
        }
        return false;
    }

    /*
    Below method retrieves all the contacts who are using this
    app and it'll send empty cursor in case of any issue to prevent application from crash
    */

    public Cursor getAllContacts() {
        try {
            SQLiteDatabase sqLiteDatabase = contactsDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT * FROM " + ContactsDatabase.CONTACTS_TABLE);
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get All users error logged in DAO file " + e.getMessage());
        }
        return null;
    }

    public Cursor getAllUsers() {
        try {
            SQLiteDatabase sqLiteDatabase = contactsDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT * FROM " + ContactsDatabase.CONTACTS_TABLE +
                            " WHERE " + ContactsDatabase.IS_APP_USER + "= ? AND " + ContactsDatabase.IS_CONTACT + " =?",
                    new String[] {"1", "1"});
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get All users error logged in DAO file " + e.getMessage());
        }
        return null;
    }

    public Cursor getContactPublicIdentityKey(String contactId) {
        try {
            SQLiteDatabase  sqLiteDatabase      =   contactsDatabase.getReadableDb();
            String query    =   "SELECT " + ContactsDatabase.CONTACT_PUBLIC_KEY + " FROM " + ContactsDatabase.CONTACT_KEYS_TABLE
                    + " WHERE " + ContactsDatabase.KEYS_CONTACT_USER_ID + " =?";
            return sqLiteDatabase.rawQuery(query, new String[]{contactId});
        }
        catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get identity key pair Error in DAO: " + e.getMessage());
        }
        return null;
    }

    public Cursor getKeysForSessionCreation(String contactId) {
        try {
            SQLiteDatabase  sqLiteDatabase  =   contactsDatabase.getReadableDb();
            String query = "SELECT " + ContactsDatabase.CONTACT_PUBLIC_KEY + ", " +
                    ContactsDatabase.CONTACT_SIGNATURE + ", " +
                    ContactsDatabase.CONTACT_SIGNED_KEY_ID + ", " +
                    ContactsDatabase.CONTACT_SIGNED_PRE_PUBLIC_KEY + ", " +
                    ContactsDatabase.CONTACT_REGISTRATION_ID + ", " +
                    ContactsDatabase.CONTACT_DEVICE_ID +
                    " FROM " + ContactsDatabase.CONTACT_KEYS_TABLE +
                    " WHERE " + ContactsDatabase.KEYS_CONTACT_USER_ID + "=? " +
                    " ORDER BY " + ContactsDatabase.CONTACT_KEYS_SQLITE_ID + " DESC " +
                    " LIMIT 1";
            return sqLiteDatabase.rawQuery(query, new String[] {contactId});
        }
        catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get keys error logged in DAO file " + e.getMessage());
        }
        return null;
    }



    public Cursor getContactDeviceId(String contactId) {
        try {
            SQLiteDatabase sqLiteDatabase = contactsDatabase.getReadableDb();
            String query = "SELECT " + ContactsDatabase.CONTACT_DEVICE_ID + " " +
                    "FROM " + ContactsDatabase.CONTACT_KEYS_TABLE + " " +
                    "WHERE " + ContactsDatabase.KEYS_CONTACT_USER_ID + " = ? " +
                    "ORDER BY " + ContactsDatabase.CONTACT_KEYS_SQLITE_ID + " DESC " +
                    "LIMIT 1";
            return sqLiteDatabase.rawQuery(query, new String[]{contactId});
        } catch (SQLException e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get keys error logged in DAO file " + e.getMessage());
        }
        return null;
    }

    public Cursor getAllContactsFromAppDatabase() {
        try {
            SQLiteDatabase sqLiteDatabase = contactsDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT * FROM " + ContactsDatabase.CONTACTS_TABLE,null);
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to get All database contacts error logged in DAO file "+e.getMessage());
        }
        return null;
    }

    /*
     This method retrieves all the favourite contacts of user
     */


    public Cursor getFavouriteContacts() {
        try
        {
            SQLiteDatabase sqLiteDatabase = contactsDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT * FROM " + ContactsDatabase.CONTACTS_TABLE
                    + " WHERE " + ContactsDatabase.IS_FAVOURITE + "= ?", new String[]{"1"});
        }
        catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE,"Unable to get All favourite contacts error logged in DAO file "+e.getMessage());
        }
        return null;
    }

    public ArrayList<String> getContactIdsBySearch(String searchText) {
        ArrayList<String>   contactIds          =   new ArrayList<>();
        SQLiteDatabase      sqLiteDatabase      =   contactsDatabase.getReadableDb();
        Cursor cursor = sqLiteDatabase.rawQuery(
                "SELECT " + ContactsDatabase.CONTACT_USER_ID + " FROM " + ContactsDatabase.CONTACTS_TABLE +
                        " WHERE " + ContactsDatabase.CONTACT_NAME + " LIKE ? OR " + ContactsDatabase.CONTACT_PHONE + " LIKE ?",
                new String[]{"%" + searchText + "%", "%" + searchText + "%"}
        );

        if (cursor.moveToFirst()) {
            do {
                contactIds.add(cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_USER_ID)));
            } while (cursor.moveToNext());
            cursor.close();
        }
        return contactIds;
    }

    /*
    Below method retrieves contact name of a contact based on id or phoneNumber
     */

    public Cursor getContactNameFromDatabase(String contactId) {
        try {
            SQLiteDatabase sqLiteDatabase  =   contactsDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT "+ ContactsDatabase.CONTACT_NAME + " FROM " + ContactsDatabase.CONTACTS_TABLE
                                            + " WHERE "+ ContactsDatabase.CONTACT_USER_ID + "= ?", new String[] {contactId});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to get contact name error logged in DAO file "+e.getMessage());
        }
        return null;
    }

    public Cursor getContactNameFromPhoneNumber(String contactNumber) {
        try
        {
            SQLiteDatabase sqLiteDatabase  =   contactsDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT "+ ContactsDatabase.CONTACT_NAME + " FROM " + ContactsDatabase.CONTACTS_TABLE
                    + " WHERE "+ ContactsDatabase.CONTACT_PHONE + "= ?", new String[] {contactNumber});
        }
        catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE,"Unable to get contact name error logged in DAO file "+e.getMessage());
        }
        return null;
    }



    public Cursor getContactNameAndPhoneNumber(String contactId) {
        try  {
            SQLiteDatabase sqLiteDatabase  =   contactsDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT "+ ContactsDatabase.CONTACT_NAME +", " + ContactsDatabase.CONTACT_PHONE + ", "
                    + ContactsDatabase.IS_CONTACT + " FROM " + ContactsDatabase.CONTACTS_TABLE
                    + " WHERE "+ ContactsDatabase.CONTACT_USER_ID + "= ?", new String[]{contactId});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to get contact name and phone number error logged in DAO file "+e.getMessage());
        }
        return null;
    }

    public Cursor getContactNameAndProfilePic(String contactId) {
        try {
            SQLiteDatabase sqLiteDatabase  =   contactsDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery(
                    "SELECT "+ ContactsDatabase.CONTACT_NAME +", " + ContactsDatabase.CONTACT_PHONE + ", "
                    + ContactsDatabase.PROFILE_PIC
                    + " FROM " + ContactsDatabase.CONTACTS_TABLE
                    + " WHERE "+ ContactsDatabase.CONTACT_USER_ID + "= ?", new String[] {contactId});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to get contact name and phone number error logged in DAO file "+e.getMessage());
        }
        return null;
    }


    public Cursor getContactDetailsForChatScreen(String contactId) {
        try {
            SQLiteDatabase sqLiteDatabase = contactsDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery(
                    "SELECT c." + ContactsDatabase.CONTACT_NAME + ", " +
                            "c." + ContactsDatabase.CONTACT_PHONE + ", " +
                            "c." + ContactsDatabase.PROFILE_PIC + ", " +
                            "k." + ContactsDatabase.CONTACT_DEVICE_ID + " " +
                            "FROM " + ContactsDatabase.CONTACTS_TABLE + " c " +
                            "LEFT JOIN " + ContactsDatabase.CONTACT_KEYS_TABLE + " k " +
                            "ON c." + ContactsDatabase.CONTACT_USER_ID + " = k." + ContactsDatabase.KEYS_CONTACT_USER_ID + " " +
                            "WHERE c." + ContactsDatabase.CONTACT_USER_ID + " = ? " +
                            "ORDER BY k." + ContactsDatabase.CONTACT_KEYS_SQLITE_ID + " DESC " +
                            "LIMIT 1",
                    new String[] {contactId});
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Unable to get contact name and phone number error logged in DAO file " + e.getMessage());
        }
        return null;
    }


    public Cursor getUsersDetails(String id) {
        try {
            SQLiteDatabase sqLiteDatabase = contactsDatabase.getReadableDb();
            return sqLiteDatabase.rawQuery("SELECT * FROM " + ContactsDatabase.CONTACTS_TABLE + " WHERE "+ ContactsDatabase.CONTACT_USER_ID  + "=?", new String[]{id});
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to get user details error logged in DAO file "+e.getMessage());
        }
        return null;
    }

    /*
    Below methods update the contacts as favourite and not favourite
         */

    public boolean updateContactFavourite(String contactId, int isFavourite) {
        boolean isSuccess   = false;
        SQLiteDatabase sqLiteDatabase   =   contactsDatabase.getWritableDb();
        try {
            sqLiteDatabase.beginTransaction();
            try {
                ContentValues contentValues = new ContentValues();
                contentValues.put(ContactsDatabase.IS_FAVOURITE, isFavourite);

                int rowsEffected = sqLiteDatabase.update(ContactsDatabase.CONTACTS_TABLE, contentValues,
                        ContactsDatabase.CONTACT_USER_ID + "=?", new String[]{contactId});

                if (rowsEffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                }
                else {
                    Log.e(Extras.LOG_MESSAGE, "failed to update contact favourite error in db file " + contactId);
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Error updating contact favourite status error in db file : " + e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error updating contact favourite status error in db file : " + e.getMessage());
        }

        return isSuccess;
    }

    /*
    Below method updates the contacts about whenever there is a change in their contacts account
     */

    public boolean updateContactAbout(String contact_id,String about) {
        boolean isSuccess   = false;
        SQLiteDatabase sqLiteDatabase   =   contactsDatabase.getWritableDb();
        try {
            sqLiteDatabase.beginTransaction();
            try {
                ContentValues contentValues   = new ContentValues();
                contentValues.put(ContactsDatabase.CONTACT_ABOUT,about);
                int rowsEffected    = sqLiteDatabase.update(ContactsDatabase.CONTACTS_TABLE, contentValues,
                        ContactsDatabase.CONTACT_USER_ID + "=?", new String[]{String.valueOf(contact_id)});
                if(rowsEffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                }
                else {
                    Log.e(Extras.LOG_MESSAGE, "failed to update contacts about info error in db file");
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "failed to update contacts about info error in db file "+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "failed to update contacts about info error in db file "+e.getMessage());
        }
        return isSuccess;
    }

    public boolean updateContactPublicKey(String contactId, String contactPublicKey) {
        boolean isSuccess   = false;
        SQLiteDatabase sqLiteDatabase   =   contactsDatabase.getWritableDb();
        try {
            sqLiteDatabase.beginTransaction();
            try {
                ContentValues contentValues   = new ContentValues();
                contentValues.put(ContactsDatabase.CONTACT_PUBLIC_KEY, contactPublicKey);
                int rowsEffected    = sqLiteDatabase.update(ContactsDatabase.CONTACT_KEYS_TABLE, contentValues,
                        ContactsDatabase.KEYS_CONTACT_USER_ID + "=?", new String[]{String.valueOf(contactId)});
                if(rowsEffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                }
                else {
                    Log.e(Extras.LOG_MESSAGE, "failed to update contacts about info error in db file");
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "failed to update contacts about info error in db file "+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "failed to update contacts about info error in db file "+e.getMessage());
        }
        return isSuccess;
    }


    /*
    Below method update the contact profile pic data whenever they updated their profile pictues
     */

    public boolean updateContactProfilePic(String contact_id, String imageId) {
        boolean isSuccess   = false;
        SQLiteDatabase sqLiteDatabase   =   contactsDatabase.getWritableDb();
        try {
            sqLiteDatabase.beginTransaction();
            try
            {
                ContentValues contentValues   = new ContentValues();
                contentValues.put(ContactsDatabase.PROFILE_PIC,imageId);
                int rowsEffected = sqLiteDatabase.update(ContactsDatabase.CONTACTS_TABLE, contentValues,
                        ContactsDatabase.CONTACT_USER_ID + "=?", new String[]{String.valueOf(contact_id)});

                if (rowsEffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess  = true;
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "failed to update contacts about info error in db file "+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "failed to update contacts about info error in db file "+e.getMessage());
        }
        return isSuccess;
    }

    /*
    Below method is used to check for duplicates before adding new contact to the contact local database
     */

    public boolean checkDuplicate(String id_post)
    {
        boolean isDuplicate = false;
        SQLiteDatabase sqLiteDatabase   =   contactsDatabase.getWritableDb();
        try (Cursor cursor     = sqLiteDatabase.rawQuery("SELECT "+ ContactsDatabase.CONTACT_USER_ID
                + " FROM " + ContactsDatabase.CONTACTS_TABLE
                + " WHERE " + ContactsDatabase.CONTACT_USER_ID + "=?", new String[]{id_post})){

            if(cursor.getCount() > 0) {
                isDuplicate = true;
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error checking for duplicate contacts: " + e.getMessage());
        }
        return isDuplicate;
    }

    /*
    Below method is to check whether the contact is marked as favourite or not
     */

    public boolean checkContactIsFavouriteOrNot(String contactId)
    {
        boolean isDuplicate = false;

        SQLiteDatabase sqLiteDatabase = contactsDatabase.getReadableDb();
        try (Cursor cursor     = sqLiteDatabase.rawQuery("SELECT "+ ContactsDatabase.IS_FAVOURITE +
                        " FROM " + ContactsDatabase.CONTACTS_TABLE
                        + " WHERE " + ContactsDatabase.CONTACT_USER_ID + "=?", new String[]{contactId})){

            if(cursor.moveToFirst())
            {
                int isFav   =   cursor.getInt(cursor.getColumnIndexOrThrow(ContactsDatabase.IS_FAVOURITE));
                if (isFav   ==  1) {
                    isDuplicate = true;
                }
            }
        }
        catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE, "Error checking for duplicate contacts: " + e.getMessage());
        }
        return isDuplicate;
    }

    /*
    Below method is to check whether the person is contact or not
     */

    public boolean checkIsContactOrNot(String contactPhoneNumber) {
        SQLiteDatabase sqLiteDatabase   =   contactsDatabase.getReadableDb();
        try (Cursor cursor   =   sqLiteDatabase.rawQuery("SELECT "+ ContactsDatabase.IS_CONTACT +
                " FROM " + ContactsDatabase.CONTACTS_TABLE + " WHERE " + ContactsDatabase.CONTACT_PHONE + "=?" ,
                new String[]{contactPhoneNumber}))
        {
            if (cursor.moveToFirst()) {
                int isContact   =   cursor.getInt(cursor.getColumnIndexOrThrow(ContactsDatabase.IS_CONTACT));
                if (isContact == 1) {
                    return true;
                }
            }
        }
        catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE,"Unable to check chat lock list id error logged in DAO file 123 "+e.getMessage());
        }
        return false;
    }

    /*
    Contacts and other users blocking mechanism code starts here
    */

    public boolean insertBlockedUsers(String contact_id,String phone_number,long timestamp)
    {
        boolean isSuccess = false;
        SQLiteDatabase sqLiteDatabase   =   contactsDatabase.getWritableDb();
        try
        {
            try {
                sqLiteDatabase.beginTransaction();
                ContentValues contentValues    = new ContentValues();
                contentValues.put(ContactsDatabase.BLOCKED_USER_ID,contact_id);
                contentValues.put(ContactsDatabase.BLOCKED_PHONE_NUMBER,phone_number);
                contentValues.put(ContactsDatabase.BLOCKED_TIMESTAMP,timestamp);

                long result  = sqLiteDatabase.insert(ContactsDatabase.BLOCKED_USERS_TABLE,null, contentValues);

                if (result != -1) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isSuccess = true;
                } else {
                    Log.e(Extras.LOG_MESSAGE, "Insertion failed, result: " + result);
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE, "Error inserting blocked user into database: " + e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error accessing the database: " + e.getMessage());
        }
        return isSuccess;
    }

    public boolean checkContactsBlocked(String contact_id)
    {
        boolean isBlocked = false;

        SQLiteDatabase sqLiteDatabase = contactsDatabase.getReadableDb();

        try (Cursor cursor = sqLiteDatabase.rawQuery("SELECT * FROM " + ContactsDatabase.BLOCKED_USERS_TABLE
                    + " WHERE " + ContactsDatabase.BLOCKED_USER_ID + "=?", new String[]{contact_id})) {
            if (cursor.getCount() > 0) {
                isBlocked = true;
            }
        } catch (Exception e)
        {
            Log.e(Extras.LOG_MESSAGE, "Error checking if contact is blocked: " + e.getMessage());
        }
        return isBlocked;
    }

    public boolean deleteBlockedContacts(String contact_id) {

        boolean isDeleted = false;
        SQLiteDatabase sqLiteDatabase   =   contactsDatabase.getWritableDb();
        try
        {
            try {
                sqLiteDatabase.beginTransaction();
                int rowsEffected = sqLiteDatabase.delete(ContactsDatabase.BLOCKED_USERS_TABLE,
                        ContactsDatabase.BLOCKED_USER_ID+"=?", new String[]{contact_id});
                if (rowsEffected > 0) {
                    sqLiteDatabase.setTransactionSuccessful();
                    isDeleted = true;
                }
                else {
                    Log.e(Extras.LOG_MESSAGE,"unable to delete the message");
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE,"unable to delete the blocked contacts "+e.getMessage());
            }
            finally {
                sqLiteDatabase.endTransaction();
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Error accessing the database "+e.getMessage());
        }
        return isDeleted;
    }

    public Cursor getBlockedContacts() {
        try {
            SQLiteDatabase sqLiteDatabase = contactsDatabase.getReadableDb();
            String sql  = "SELECT * FROM " + ContactsDatabase.BLOCKED_USERS_TABLE;
            return sqLiteDatabase.rawQuery(sql,null);
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"unable to get the blocked contacts error logged in DAO file "+e.getMessage());
        }
        return null;
    }
}
