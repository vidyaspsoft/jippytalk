package com.jippytalk.Database.ContactsDatabase.Repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.telephony.PhoneNumberUtils;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.jippytalk.ContactProfile.Model.ContactProfileModel;
import com.jippytalk.Managers.AccountManager;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.MessagesForward.Model.MessageForwardChatsListModel;
import com.jippytalk.ServiceLocators.DatabaseServiceLocator;
import com.jippytalk.BlockedContacts.Model.BlockedContactsModel;
import com.jippytalk.Common.SingleLiveEvent;
import com.jippytalk.Contacts.Model.UsersModal;
import com.jippytalk.Database.ContactsDatabase.ContactsDatabase;
import com.jippytalk.Database.ContactsDatabase.DAO.ContactsDatabaseDAO;
import com.jippytalk.Extras;
import com.jippytalk.FavouriteContacts.FavouriteContactsModel;
import com.jippytalk.Managers.ChatManager;
import com.jippytalk.Managers.ContactManager;
import com.jippytalk.Messages.Model.ContactDetailsModel;


import org.signal.libsignal.protocol.IdentityKey;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

public class ContactsRepository {

    private final Context                                               context;
    private static volatile ContactsRepository                          INSTANCE;
    private DatabaseServiceLocator                                      databaseServiceLocator;
    private final SharedPreferences                                     sharedPreferences;
    private final ContactsDatabaseDAO                                   contactsDatabaseDAO;
    private final ExecutorService                                       writeExecutor;
    private final ExecutorService                                       readExecutor;

    private final MutableLiveData<Integer>                              contactFavouriteLiveData            =   new MutableLiveData<>();
    private final SingleLiveEvent<Integer>                              contactBlockStatus                  =   new SingleLiveEvent<>();
    private final MutableLiveData<Pair<String, Integer>>                contactFavUpdateStatus              =   new MutableLiveData<>();
    private final MutableLiveData<String>                               contactProfilePicStatus             =   new MutableLiveData<>();
    private final MutableLiveData<String>                               contactAboutMutable                 =   new MutableLiveData<>();
    private final MutableLiveData<ArrayList<String>>                    chatsSearchMutableData              =   new MutableLiveData<>();
    private final MutableLiveData<Pair<String, String>>                 contactNameAndPicMutable            =   new MutableLiveData<>();
    private final SingleLiveEvent<Pair<String, String>>                 contactNameAndIdMutable             =   new SingleLiveEvent<>();

    private final MutableLiveData<ContactDetailsModel>                  contactDetailsModelMutableLiveData  =   new MutableLiveData<>();
    private final MutableLiveData<ContactProfileModel>                  contactProfileModelMutableLiveData  =   new MutableLiveData<>();
    private final MutableLiveData<ArrayList<UsersModal>>                contactListMutableLiveData          =   new MutableLiveData<>();
    private final MutableLiveData<ArrayList<MessageForwardChatsListModel>>  forwardToContactsMutable            =   new MutableLiveData<>();
    private final MutableLiveData<ArrayList<FavouriteContactsModel>>    favouriteContactsMutableLiveData    =   new MutableLiveData<>();
    private final MutableLiveData<ArrayList<BlockedContactsModel>>      blockedContactsMutableData          =   new MutableLiveData<>();


    public LiveData<Integer> getContactFavouritesLiveData() {
        return contactFavouriteLiveData;
    }

    public SingleLiveEvent<Integer> getContactBlockStatus() {
        return contactBlockStatus;
    }

    public LiveData<String> getContactProfilePicStatus() {
        return contactProfilePicStatus;
    }

    public LiveData<String> getContactAboutLiveData() {
        return contactAboutMutable;
    }

    public LiveData<ArrayList<String>> getChatsSearchLiveData() {
        return chatsSearchMutableData;
    }

    public LiveData<Pair<String, String>> getContactNameAndIdLiveData() {
        return contactNameAndIdMutable;
    }

    public LiveData<Pair<String, String>> getContactNameAndPicLiveData() {
        return contactNameAndPicMutable;
    }

    public LiveData<Pair<String, Integer>> getContactFavUpdateStatus() {
        return contactFavUpdateStatus;
    }

    public LiveData<ContactDetailsModel> getContactDetailsLiveData() {
        return contactDetailsModelMutableLiveData;
    }

    public LiveData<ContactProfileModel> getContactProfileDetails() {
        return contactProfileModelMutableLiveData;
    }

    public LiveData<ArrayList<UsersModal>> getAllContactUsers() {
        return contactListMutableLiveData;
    }

    public LiveData<ArrayList<MessageForwardChatsListModel>> getForwardToContactsLiveData() {
        return forwardToContactsMutable;
    }


    public LiveData<ArrayList<FavouriteContactsModel>> getAllFavouriteContacts() {
        return favouriteContactsMutableLiveData;
    }

    public LiveData<ArrayList<BlockedContactsModel>> getAllBlockedContactsLiveData() {
        return blockedContactsMutableData;
    }

    public ContactsRepository(Context context, DatabaseServiceLocator databaseServiceLocator,
                              SharedPreferences sharedPreferences) {
        this.context                                    =   context.getApplicationContext();
        this.databaseServiceLocator                     =   databaseServiceLocator;
        this.sharedPreferences                          =   sharedPreferences;
        contactsDatabaseDAO                             =   databaseServiceLocator.getContactsDatabaseDAO();
        writeExecutor                                   =   databaseServiceLocator.getWriteExecutor();
        readExecutor                                    =   databaseServiceLocator.getReadExecutor();
    }

    public static ContactsRepository getInstance(Context context, DatabaseServiceLocator databaseServiceLocator,
                                                 SharedPreferences sharedPreferences) {
        if (INSTANCE == null) {
            synchronized (ContactsRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ContactsRepository(context.getApplicationContext(), databaseServiceLocator,
                                sharedPreferences);
                }
            }
        }
        return INSTANCE;
    }

    // ------------------------- Database Insert Operations Starts Here -----------------------------

    public void insertContactsIntoLocalDatabase(ArrayList<UsersModal> usersModalArrayList) {
        writeExecutor.execute(() -> contactsDatabaseDAO.batchInsertContact(usersModalArrayList));
    }

    public void insertNonContactUsers(int rawContactId, String contactUserId,
                                      int isAppUser, int isContact, String contactName,
                                      String contactPhone, String contactAbout, long contactJoinedDate,
                                      String contactHashedPhoneNumber, int isFavourite, String contactProfilePicId,
                                      int deviceId) {

        writeExecutor.execute(() -> {
            boolean insert  =   contactsDatabaseDAO.insertNonContacts(rawContactId, contactUserId, isAppUser,
                    isContact, contactName, contactPhone, contactAbout, contactJoinedDate, contactHashedPhoneNumber,
                    isFavourite, contactProfilePicId, deviceId);

            if (insert) {
                Log.e(Extras.LOG_MESSAGE,"Contacts inserted successfully");
            } else {
                Log.e(Extras.LOG_MESSAGE,"Unable to insert contacts " + contactPhone);
            }
        });
    }

    public void blockContact(String contactId, String contactNumber) {
        writeExecutor.execute(() -> {
            try {
                boolean block   =   contactsDatabaseDAO.insertBlockedUsers(contactId, contactNumber, System.currentTimeMillis());
                if (block) {
                    contactBlockStatus.postValue(ChatManager.BLOCK_CONTACT);
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to block contact error in repository " + e.getMessage());
            }
        });
    }

    // ------------------------- Database Insert Operations Ends Here -----------------------------


    // ------------------------- Database Read Operations Starts Here -----------------------------

    public IdentityKey getContactPublicIdentityKey(String contactId) {
        FutureTask<IdentityKey> task = new FutureTask<>(() -> {
            try (Cursor cursor = contactsDatabaseDAO.getContactPublicIdentityKey(contactId)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String contactPublicKey         =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_PUBLIC_KEY));
                    byte[] contactPublicKeyBytes    =   Base64.decode(contactPublicKey, Base64.NO_WRAP);
                    return new IdentityKey(contactPublicKeyBytes);
                }
            } catch (Exception e) {
                Log.e("KeyRepo", "Failed to load identity key pair", e);
            }
            return null;
        });

        readExecutor.execute(task);

        try {
            return task.get(); // Blocks until result is ready
        } catch (Exception e) {
            Log.e("KeyRepo", "Executor task failed", e);
            return null;
        }
    }

    public ArrayList<String> getAllContacts() {
        ArrayList<String> contactsArrayList   =   new ArrayList<>();
        try (Cursor cursor = contactsDatabaseDAO.getAllContacts()) {
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    try {
                        String contactPhoneNumber   =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_PHONE));
                        contactsArrayList.add(contactPhoneNumber);
                    }
                    catch (IllegalArgumentException e) {
                        Log.e(Extras.LOG_MESSAGE , "Unable to get contacts error in UsersActivity cursor null "+e.getMessage());
                    }
                }
                while (cursor.moveToNext());
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"unable to get contacts error in UsersActivity "+e.getMessage());
        }
        return contactsArrayList;
    }

    public void getAllUsers() {
        readExecutor.execute(() -> {
            int profilePicPrivacyOption =   sharedPreferences.getInt(SharedPreferenceDetails.PROFILE_PIC_PRIVACY_OPTION,
                                            AccountManager.MY_CONTACTS);
            ArrayList<UsersModal> usersModalArrayList   =   new ArrayList<>();
            try (Cursor cursor = contactsDatabaseDAO.getAllUsers()) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        try {
                            String  rawContactId    =     cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.RAW_CONTACT_ID));
                            int     isAppUser       =     cursor.getInt(cursor.getColumnIndexOrThrow(ContactsDatabase.IS_APP_USER));
                            String  contactUserId   =     cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_USER_ID));
                            String  contactName     =     cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_NAME));
                            String  contactPhone    =     cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_PHONE));
                            String  contactAbout    =     cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_ABOUT));
                            String  profilePic      =     cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.PROFILE_PIC));

                            if (profilePicPrivacyOption == AccountManager.NO_ONE) {
                                profilePic = "";
                            }

                            Log.d("DB_CHECK", "Name: " + contactName + " | isAppUser value: " + isAppUser);

                            UsersModal usersModal = new UsersModal(
                                    rawContactId,
                                    isAppUser,
                                    contactUserId,
                                    contactName,
                                    contactPhone,
                                    contactAbout,
                                    profilePic
                            );
                            usersModalArrayList.add(usersModal);
                        }
                        catch (IllegalArgumentException e) {
                            contactListMutableLiveData.postValue(new ArrayList<>());
                            Log.e(Extras.LOG_MESSAGE , "Unable to get contacts error in UsersActivity cursor null "+e.getMessage());
                        }
                    }
                    while (cursor.moveToNext());
                    contactListMutableLiveData.postValue(usersModalArrayList);
                } else {
                    contactListMutableLiveData.postValue(new ArrayList<>());
                }
            }
            catch (Exception e) {
                 Log.e(Extras.LOG_MESSAGE,"unable to get contacts error in UsersActivity "+e.getMessage());
                 contactListMutableLiveData.postValue(new ArrayList<>());
            }
        });
    }

    public void getAllUsersForMessageForwardActivity() {
        readExecutor.execute(() -> {
            int profilePicPrivacyOption =   sharedPreferences.getInt(SharedPreferenceDetails.PROFILE_PIC_PRIVACY_OPTION,
                                            AccountManager.MY_CONTACTS);
            ArrayList<MessageForwardChatsListModel> usersModalArrayList   =   new ArrayList<>();
            try (Cursor cursor = contactsDatabaseDAO.getAllUsers()) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        try {
                            String  contactUserId   =     cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_USER_ID));
                            String  contactName     =     cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_NAME));
                            String  profilePic      =     cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.PROFILE_PIC));

                            if (profilePicPrivacyOption == AccountManager.NO_ONE) {
                                profilePic = "";
                            }

                            MessageForwardChatsListModel messageForwardChatsListModel   =   new MessageForwardChatsListModel(
                                    contactUserId,
                                    contactName,
                                    profilePic,
                                    false
                            );
                            usersModalArrayList.add(messageForwardChatsListModel);
                        }
                        catch (IllegalArgumentException e) {
                            forwardToContactsMutable.postValue(new ArrayList<>());
                            Log.e(Extras.LOG_MESSAGE , "Unable to get contacts error in UsersActivity cursor null "+e.getMessage());
                        }
                    }
                    while (cursor.moveToNext());
                    forwardToContactsMutable.postValue(usersModalArrayList);
                } else {
                    forwardToContactsMutable.postValue(new ArrayList<>());
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"unable to get contacts error in UsersActivity "+e.getMessage());
                forwardToContactsMutable.postValue(new ArrayList<>());
            }
        });
    }

    public void getFavouriteContacts() {
        readExecutor.execute(() -> {
            int profilePicPrivacyOption =   sharedPreferences.getInt(SharedPreferenceDetails.PROFILE_PIC_PRIVACY_OPTION,
                                            AccountManager.MY_CONTACTS);
            ArrayList<FavouriteContactsModel> favouriteContactsModelArrayList = new ArrayList<>();
            try (Cursor cursor = contactsDatabaseDAO.getFavouriteContacts()) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        String contactUserId = cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_USER_ID));
                        String contactName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_NAME));
                        String profilePic = cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.PROFILE_PIC));

                        if (profilePicPrivacyOption == AccountManager.NO_ONE) {
                            profilePic = "";
                        }

                        FavouriteContactsModel favouriteContactsModel = new FavouriteContactsModel(
                                contactUserId,
                                contactName,
                                profilePic
                        );
                        favouriteContactsModelArrayList.add(favouriteContactsModel);
                    } while (cursor.moveToNext());

                    favouriteContactsMutableLiveData.postValue(favouriteContactsModelArrayList);
                } else {
                    favouriteContactsMutableLiveData.postValue(new ArrayList<>());
                }

            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Error in retrieving fav contacts " + e.getMessage());
                favouriteContactsMutableLiveData.postValue(new ArrayList<>());
            }
        });
    }

    public void getAllBlockedContacts() {
        readExecutor.execute(() -> {
            ArrayList<BlockedContactsModel> blockedContactsModelArrayList   =   new ArrayList<>();
            try (Cursor cursor  =   contactsDatabaseDAO.getBlockedContacts()) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        BlockedContactsModel blockedContactsModel = new BlockedContactsModel(

                                cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.BLOCKED_USER_ID)),
                                cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.BLOCKED_PHONE_NUMBER)),
                                cursor.getLong(cursor.getColumnIndexOrThrow(ContactsDatabase.BLOCKED_TIMESTAMP))
                        );
                        blockedContactsModelArrayList.add(blockedContactsModel);
                    }
                    while (cursor.moveToNext());
                    blockedContactsMutableData.postValue(blockedContactsModelArrayList);
                }
                else {
                    blockedContactsMutableData.postValue(new ArrayList<>());
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to fetch the blocked contacts error in repo " + e.getMessage());
                blockedContactsMutableData.postValue(new ArrayList<>());
            }
        });
    }

    public String getContactNameForRepliedMessages(String contactId) {
        String contactName = "Unknown"; // default fallback
        try (Cursor cursor = contactsDatabaseDAO.getContactNameFromDatabase(contactId)) {
            if (cursor != null && cursor.moveToFirst()) {
                contactName = cursor.getString(
                        cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_NAME)
                );
            }
        } catch (Exception e) {
            Log.e("ContactsRepository", "Failed to fetch contact name: " + e.getMessage());
        }
        return contactName;
    }

    public int getContactDeviceId(String contactId) {
        int contactDeviceId =   0;
        try (Cursor cursor = contactsDatabaseDAO.getContactDeviceId(contactId)) {
            if (cursor != null && cursor.moveToFirst()) {
                contactDeviceId = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_DEVICE_ID));
            }
        }
        return contactDeviceId;
    }


    public void getContactNameAndProfilePicData(String contactId) {
        readExecutor.execute(() -> {
            try (Cursor cursor  =   contactsDatabaseDAO.getContactNameAndProfilePic(contactId)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String contactName      =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_NAME));
                    String contactNumber    =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_PHONE));
                    String profilePic       =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.PROFILE_PIC));

                    if (contactName == null || contactName.isEmpty()) {
                        String finalPhoneNumber     =   PhoneNumberUtils.formatNumber(contactNumber, Locale.getDefault().getCountry());
                        contactNameAndPicMutable.postValue(new Pair<>(finalPhoneNumber, profilePic));
                    } else {
                        contactNameAndPicMutable.postValue(new Pair<>(contactName, profilePic));
                    }
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to  retrieve contact name and profile pic error in repository " + e.getMessage());
            }
        });
    }

    public void getContactNameOrNumberFromId(String contactId) {
        readExecutor.execute(() -> {
            try (Cursor cursor  =   contactsDatabaseDAO.getContactNameAndPhoneNumber(contactId)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String contactName      =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_NAME));
                    String contactNumber    =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_PHONE));

                    if (contactName == null || contactName.isEmpty()) {
                        String finalPhoneNumber     =   PhoneNumberUtils.formatNumber(contactNumber, Locale.getDefault().getCountry());
                        contactNameAndIdMutable.postValue(new Pair<>(contactId, finalPhoneNumber));
                    } else {
                        contactNameAndIdMutable.postValue(new Pair<>(contactId, contactName));
                    }
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to  retrieve contact name and Id error in repository " + e.getMessage());
            }
        });
    }

    public void getContactNameOrNumberFromIdForNotification(String contactId, ContactNameOrNumberCallBack callback) {
        readExecutor.execute(() -> {
            String result = contactId; // fallback if nothing found
            Cursor cursor = null;
            try {
                cursor = contactsDatabaseDAO.getContactNameAndPhoneNumber(contactId);
                if (cursor != null && cursor.moveToFirst()) {
                    String contactName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_NAME));
                    String contactNumber = cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_PHONE));

                    if (contactName == null || contactName.isEmpty()) {
                        result = PhoneNumberUtils.formatNumber(contactNumber, Locale.getDefault().getCountry());
                    } else {
                        result = contactName;
                    }
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to retrieve contact name/number: " + e.getMessage());
            } finally {
                if (cursor != null) cursor.close();
            }

            String finalResult = result;
            callback.onContactNameOrNumberRetrieved(finalResult);
        });
    }

    public interface ContactNameOrNumberCallBack {
        void onContactNameOrNumberRetrieved(String contactName);
    }

    public void getContactDetailsForMessagingActivity(String contactId) {
        readExecutor.execute(() -> {
            try (Cursor cursor  =   contactsDatabaseDAO.getContactDetailsForChatScreen(contactId)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String  contactName     =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_NAME));
                    String  contactNumber   =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_PHONE));
                    String  profilePic      =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.PROFILE_PIC));
                    int     contactDeviceId =   cursor.getInt(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_DEVICE_ID));

                    Log.e(Extras.LOG_MESSAGE,"device id in rep is " + contactDeviceId + " id is " + contactId);

                    if (contactName == null || contactName.isEmpty()) {
                        String finalPhoneNumber     =   PhoneNumberUtils.formatNumber(contactNumber, Locale.getDefault().getCountry());
                        contactDetailsModelMutableLiveData.postValue(new ContactDetailsModel(
                                finalPhoneNumber, profilePic, contactDeviceId
                        ));
                    } else {
                        contactDetailsModelMutableLiveData.postValue(new ContactDetailsModel(
                                contactName, profilePic, contactDeviceId
                        ));
                    }
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to  retrieve contact name and profile pic error in repository " + e.getMessage());
            }
        });
    }

//    public void getContactDetailsForProfileActivity(String contactId) {
//        readExecutor.execute(() -> {
//            int profilePicPrivacy   =   sharedPreferences.getInt(SharedPreferenceDetails.PROFILE_PIC_PRIVACY_OPTION,
//                                         AccountManager.MY_CONTACTS);
//            try (Cursor cursor  =   contactsDatabaseDAO.getUsersDetails(contactId)) {
//                if (cursor != null && cursor.moveToFirst()) {
//                    String  contactNumber               =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_PHONE));
//                    String  contactName                 =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_NAME));
//                    String  contactAbout                =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_ABOUT));
//                    String  contactProfilePictureId     =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.PROFILE_PIC));
//                    long    contactJoinedDate           =   cursor.getLong(cursor.getColumnIndexOrThrow(ContactsDatabase.DATE_JOINED));
//                    int     isContact                   =   cursor.getInt(cursor.getColumnIndexOrThrow(ContactsDatabase.IS_CONTACT));
//                    int     isFavourite                 =   cursor.getInt(cursor.getColumnIndexOrThrow(ContactsDatabase.IS_FAVOURITE));
//
//                    ContactProfileModel contactProfileModel =   new ContactProfileModel(contactName, contactNumber, contactAbout,
//                            contactProfilePictureId, contactJoinedDate, 0, isFavourite);
//                    contactProfileModelMutableLiveData.postValue(contactProfileModel);
//                }
//            }
//        });
//    }

    public void getContactDetailsForProfileActivity(String contactId) {
        readExecutor.execute(() -> {
            int profilePicPrivacy = sharedPreferences.getInt(
                    SharedPreferenceDetails.PROFILE_PIC_PRIVACY_OPTION,
                    AccountManager.MY_CONTACTS
            );

            try (Cursor cursor = contactsDatabaseDAO.getUsersDetails(contactId)) {
                if (cursor != null && cursor.moveToFirst()) {

                    String  contactNumber               =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_PHONE));
                    String  contactName                 =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_NAME));
                    String  contactAbout                =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_ABOUT));
                    String  contactProfilePictureId     =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.PROFILE_PIC));
                    long    contactJoinedDate           =   cursor.getLong(cursor.getColumnIndexOrThrow(ContactsDatabase.DATE_JOINED));
                    int     isContact                   =   cursor.getInt(cursor.getColumnIndexOrThrow(ContactsDatabase.IS_CONTACT));
                    int     isFavourite                 =   cursor.getInt(cursor.getColumnIndexOrThrow(ContactsDatabase.IS_FAVOURITE));

                    if (profilePicPrivacy == AccountManager.NO_ONE) {
                        contactProfilePictureId = "0";
                    }

                    if (contactProfilePictureId == null || contactProfilePictureId.isEmpty()) {
                        contactProfilePictureId =   null;
                    }

                    ContactProfileModel contactProfileModel =
                            new ContactProfileModel(
                                    contactName,
                                    contactNumber,
                                    contactAbout,
                                    contactProfilePictureId,
                                    contactJoinedDate,
                                    0,
                                    isFavourite
                            );
                    contactProfileModelMutableLiveData.postValue(contactProfileModel);
                }
            }
        });
    }


    public int getBlockCount() {
        FutureTask<Integer> getBlockedCountTask =   new FutureTask<>(() -> {
            try (Cursor cursor  = contactsDatabaseDAO.getBlockedContacts()) {
                if (cursor != null && cursor.moveToFirst()) {
                    return cursor.getCount();
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to get blocked contacts count "+e.getMessage());
            }
            return -1;
        });

        readExecutor.execute(getBlockedCountTask);

        try {
            return getBlockedCountTask.get();
        }
        catch (Exception e) {
            return -1;
        }
    }

    public void searchContactIdsByNameOrPhone(String searchText) {
        readExecutor.execute(() -> chatsSearchMutableData.postValue(contactsDatabaseDAO.getContactIdsBySearch(searchText)));
    }

    public boolean checkContactIsFavouriteOrNot(String contactId) {
       return contactsDatabaseDAO.checkContactIsFavouriteOrNot(contactId);
    }

    public boolean checkContactIsBlockedOrNot(String contactId) {
        return contactsDatabaseDAO.checkContactsBlocked(contactId);
    }

    public boolean checkIsContactBlockedOrNot(String contactId) {
        return contactsDatabaseDAO.checkContactsBlocked(contactId);
    }

    // ------------------------- Database Read Operations Ends Here -------------------------------

    // ------------------------- Database Update Operations Starts Here -------------------------------

    public void updateContactAsUsers(String contactId, String contactNumber, int registrationId,
                                     int deviceId, String about, long joinedOn, String profilePicId,
                                     String contactPublicKey, int signedPreKeyId, String signedPrePublicKey,
                                     String signature) {
        writeExecutor.execute(() -> {
            try {
                boolean update  =   contactsDatabaseDAO.updateContactAsUserAndInsertKeysData(
                        contactId, contactNumber, registrationId, deviceId,
                        about, joinedOn, profilePicId, contactPublicKey,
                        signedPreKeyId, signedPrePublicKey, signature);

                if (update) {
                    Log.e(Extras.LOG_MESSAGE,"contact updated as user successfully");
                    getAllUsers();
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to update contact as user error in repository " + e.getMessage());
            }
        });
    }

    public void updateContactKeys(String contactId, int registrationId, int deviceId,
                                  String contactPublicKey, int signedPreKeyId, String signedPrePublicKey,
                                  String signature) {
        writeExecutor.execute(() -> {
            try {
                boolean update  =   contactsDatabaseDAO.updateContactKeysData(
                        contactId, registrationId, deviceId, contactPublicKey,
                        signedPreKeyId, signedPrePublicKey, signature);
                if (update) {
                    Log.e(Extras.LOG_MESSAGE,"contact keys are updated successfully");
                    getAllUsers();
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to update contact as user error in repository " + e.getMessage());
            }
        });
    }

    public void updateContactAsUserInCaseOfSyncFail(String contactId, String contactNumber, int deviceId,
                                                    String about, long joinedOn, String profilePicId) {
        writeExecutor.execute(() -> {
            try {
                boolean update = contactsDatabaseDAO.updateContactAsUserInCaseOfSyncFail(
                        contactId, contactNumber, deviceId, about, joinedOn, profilePicId);
                if (update) {
                    Log.e(Extras.LOG_MESSAGE, "contact updated as user successfully");
                    getAllUsers();
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to update contact as user in sync fail method error in repository " + e.getMessage());
            }
        });
    }

    public void updateContactFavourite(String contactId) {
        writeExecutor.execute(() -> {
            try {
                if (checkContactIsFavouriteOrNot(contactId)) {
                    boolean update  =   contactsDatabaseDAO.updateContactFavourite(contactId, ContactManager.CONTACT_NOT_FAVOURITE);
                    if (update) {
                        contactFavUpdateStatus.postValue(new Pair<>(contactId, ContactManager.CONTACT_NOT_FAVOURITE));
                        contactFavouriteLiveData.postValue(ContactManager.CONTACT_NOT_FAVOURITE);
                    }
                    else {
                        contactFavUpdateStatus.postValue(new Pair<>(contactId, ContactManager.CONTACT_FAVOURITE));
                        contactFavouriteLiveData.postValue(ContactManager.CONTACT_FAVOURITE);
                    }
                } else {
                    boolean update  =   contactsDatabaseDAO.updateContactFavourite(contactId, ContactManager.CONTACT_FAVOURITE);
                    if (update) {
                        contactFavUpdateStatus.postValue(new Pair<>(contactId, ContactManager.CONTACT_FAVOURITE));
                        contactFavouriteLiveData.postValue(ContactManager.CONTACT_FAVOURITE);
                    }
                    else {
                        contactFavUpdateStatus.postValue(new Pair<>(contactId, ContactManager.CONTACT_NOT_FAVOURITE));
                        contactFavouriteLiveData.postValue(ContactManager.CONTACT_NOT_FAVOURITE);
                    }
                }
                getFavouriteContacts();
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to update contact favourite status caught error in repository " + e.getMessage());
            }
        });
    }

    public void updateContactProfilePicture(String contactId, String contactProfilePicId) {
        writeExecutor.execute(() -> {
            boolean updated = contactsDatabaseDAO.updateContactProfilePic(contactId, contactProfilePicId);
            if (updated) {
                Log.e(Extras.LOG_MESSAGE,"Profile pic has been updated");
                contactProfilePicStatus.postValue(contactProfilePicId);
            }
            else {
                contactProfilePicStatus.postValue(contactProfilePicId);
            }
        });
    }

    public void updateContactAbout(String contactId, String about) {
        writeExecutor.execute(() -> {
            Log.e(Extras.LOG_MESSAGE,"contact about is  " + about);
            boolean updated =   contactsDatabaseDAO.updateContactAbout(contactId, about);
            contactAboutMutable.postValue(about);
        });
    }

    public void unBlockContact(String contactId) {
        writeExecutor.execute(() -> {
            try {
                boolean unBlock     =   contactsDatabaseDAO.deleteBlockedContacts(contactId);
                if (unBlock) {
                    contactBlockStatus.postValue(ChatManager.UNBLOCK_CONTACT);
                }
                else {
                    contactBlockStatus.postValue(ChatManager.BLOCK_CONTACT);
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to unblock the contact " + e.getMessage());
            }
        });
    }

    // ------------------------- Database Update Operations Ends Here --------------------------------

}

