package com.jippytalk.Database.MessagesDatabase.Repository;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.util.Pair;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.jippytalk.ArchiveChat.Model.ArchiveListModel;
import com.jippytalk.Managers.AccountManager;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.MessagesForward.Model.MessageForwardChatsListModel;
import com.jippytalk.ServiceLocators.DatabaseServiceLocator;
import com.jippytalk.Chats.Model.ChatListModel;
import com.jippytalk.Common.Model.ChatClickActionsModel;
import com.jippytalk.Common.SingleLiveEvent;
import com.jippytalk.Database.ContactsDatabase.ContactsDatabase;
import com.jippytalk.Database.ContactsDatabase.DAO.ContactsDatabaseDAO;
import com.jippytalk.Database.MessagesDatabase.MessagesDatabaseDAO;
import com.jippytalk.Database.MessagesDatabase.MessagesDatabase;
import com.jippytalk.Extras;
import com.jippytalk.Managers.ChatManager;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

public class ChatListRepository {

    private final Context                                       context;
    private static volatile ChatListRepository                  INSTANCE;
    private final DatabaseServiceLocator                        databaseServiceLocator;
    private final SharedPreferences                             sharedPreferences;
    private final MessagesDatabaseDAO                           messagesDatabaseDAO;
    private final ContactsDatabaseDAO                           contactsDatabaseDAO;
    private final ExecutorService                               writeExecutor;
    private final ExecutorService                               readExecutor;
    private final MutableLiveData<Integer>                      chatLockStatusLiveData              =   new MutableLiveData<>();
    private final MutableLiveData<Integer>                      contactReadReceiptsLiveData         =   new MutableLiveData<>();
    private final SingleLiveEvent<String>                       getContactNameMutable               =   new SingleLiveEvent<>();
    private final SingleLiveEvent<ChatClickActionsModel>        getChatClickActionsMutable          =   new SingleLiveEvent<>();
    private final MutableLiveData<Pair<String, Integer>>        chatArchiveStatusMutableData        =   new MutableLiveData<>();
    private final MutableLiveData<Pair<Integer, Integer>>       chatLockAndReadReceiptsLiveData     =   new MutableLiveData<>();
    private final MutableLiveData<Pair<String, Boolean>>        chatDeleteMutualLiveData            =   new MutableLiveData<>();
    private final MutableLiveData<ArrayList<ChatListModel>>     chatListModelMutableLiveData        =   new MutableLiveData<>();
    private final MutableLiveData<ArrayList<ArchiveListModel>>  archiveChatsListMutableData         =   new MutableLiveData<>();
    private final MutableLiveData<ArrayList<ArchiveListModel>>  messageRequestsChatsMutable         =   new MutableLiveData<>();
    private final MutableLiveData<ArrayList<ChatListModel>>     searchedChatsMutable                =   new MutableLiveData<>();
    private final MutableLiveData<ArrayList<MessageForwardChatsListModel>> recentChatsMutable       =   new MutableLiveData<>();
    private final MutableLiveData<Integer>                      messageRequestsCountMutable         =   new MutableLiveData<>();
    private final MutableLiveData<Boolean>                      anyArchiveUnreadCountsMutable       =   new MutableLiveData<>();
    private final SingleLiveEvent<Integer>                      contactChatArchiveStatusMutable     =   new SingleLiveEvent<>();


    public LiveData<Integer> getChatLockStatusLiveData() {
        return chatLockStatusLiveData;
    }

    public LiveData<Integer> getContactReadReceiptsLiveData() {
        return contactReadReceiptsLiveData;
    }

    public LiveData<Integer> getContactChatArchiveStatusLiveData() {
        return contactChatArchiveStatusMutable;
    }

    public LiveData<Pair<String, Integer>> getChatArchiveStatusLiveData() {
        return chatArchiveStatusMutableData;
    }

    public LiveData<Pair<String, Boolean>> getChatDeleteStatusLiveData() {
        return chatDeleteMutualLiveData;
    }

    public LiveData<Pair<Integer, Integer>> getChatLockAndReadReceiptsLiveData() {
        return chatLockAndReadReceiptsLiveData;
    }

    public LiveData<ArrayList<ChatListModel>> getChatsListLiveData() {
        return chatListModelMutableLiveData;
    }

    public LiveData<ArrayList<ArchiveListModel>> getArchiveChatsListLiveData() {
        return archiveChatsListMutableData;
    }

    public LiveData<ArrayList<ArchiveListModel>> getMessageRequestChatsLiveData() {
        return messageRequestsChatsMutable;
    }

    public LiveData<ArrayList<ChatListModel>> getSearchedChatsLiveDataForDisplay() {
        return searchedChatsMutable;
    }

    public LiveData<ArrayList<MessageForwardChatsListModel>> getRecentChatsMutable() {
        return recentChatsMutable;
    }

    public LiveData<ChatClickActionsModel>  getDetailsWhenChatClickedLiveData() {
        return getChatClickActionsMutable;
    }

    public LiveData<Integer> getMessageRequestsCountLiveDataFromRep() {
        return messageRequestsCountMutable;
    }

    public LiveData<Boolean> getAnyArchiveUnreadCountsLiveData() {
        return anyArchiveUnreadCountsMutable;
    }

    public ChatListRepository(Context context, DatabaseServiceLocator databaseServiceLocator,
                              SharedPreferences sharedPreferences) {
        this.context                            =   context.getApplicationContext();
        this.databaseServiceLocator             =   databaseServiceLocator;
        this.sharedPreferences                  =   sharedPreferences;
        messagesDatabaseDAO                     =   databaseServiceLocator.getMessagesDatabaseDAO();
        contactsDatabaseDAO                     =   databaseServiceLocator.getContactsDatabaseDAO();
        writeExecutor                           =   databaseServiceLocator.getWriteExecutor();
        readExecutor                            =   databaseServiceLocator.getReadExecutor();
    }

    public static ChatListRepository getInstance(Context context, DatabaseServiceLocator databaseServiceLocator,
                                                 SharedPreferences sharedPreferences) {
        if (INSTANCE == null) {
            synchronized (ChatListRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new ChatListRepository(context.getApplicationContext(), databaseServiceLocator,
                            sharedPreferences);
                }
            }
        }
        return INSTANCE;
    }


    // ------------ Database Write Operations Starts Here -------------------------------



    // ------------ Database Write Operations Ends Here ---------------------------------


    // ------------- Database Read Operation starts from here -----------------------------------


    public void getAllNormalChats() {
        readExecutor.execute(() -> {
            int profilePicPrivacy   =   sharedPreferences.getInt(SharedPreferenceDetails.PROFILE_PIC_PRIVACY_OPTION,
                                        AccountManager.MY_CONTACTS);
            long totalStart = System.currentTimeMillis();

            ArrayList<ChatListModel> chatList = new ArrayList<>();

            long queryStart = System.currentTimeMillis();
            try (Cursor cursor = messagesDatabaseDAO.getChatList()) {
                long queryEnd = System.currentTimeMillis();
                Log.e("ChatDataTiming", "DB query getChatList() took " + (queryEnd - queryStart) + " ms");

                if (cursor != null && cursor.moveToFirst()) {
                    long buildStart = System.currentTimeMillis();
                    do {
                        String contactId            =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.CHAT_ID));
                        int messageDirection        =   cursor.getColumnIndexOrThrow("message_direction");
                        int finalMessageDirection   =   cursor.isNull(messageDirection) ? -1 : cursor.getInt(messageDirection);
                        long timestamp              =   (messageDirection == 1)
                                ? cursor.getLong(cursor.getColumnIndexOrThrow("receive_timestamp"))
                                : cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));

                        String profilePic       =   "";
                        String contactName      =   "";
                        String contactNumber    =   "";

                        try (Cursor contactCursor = contactsDatabaseDAO.getContactNameAndProfilePic(contactId)) {
                            if (contactCursor != null && contactCursor.moveToFirst()) {
                                profilePic      =   contactCursor.getString(contactCursor.getColumnIndexOrThrow(
                                                    ContactsDatabase.PROFILE_PIC));
                                contactName     =   contactCursor.getString(contactCursor.getColumnIndexOrThrow(
                                                    ContactsDatabase.CONTACT_NAME));
                                contactNumber   =   contactCursor.getString(contactCursor.getColumnIndexOrThrow(
                                        ContactsDatabase.CONTACT_PHONE));
                                Log.e("ChatDebug", "Contact found: name=" + contactName + ", phone=" + contactNumber + ", profilePic=" + profilePic);
                            } else {
                                Log.e("ChatDebug","unable to get contact name due to cursor null with id " + contactId);
                            }
                        } catch (Exception e) {
                            Log.e("ChatDebug","caught exception when getting contact details for " + contactId
                            + " exception is " + e.getMessage());
                        }
                        String displayName = "";
                        if (contactName == null || contactName.isEmpty()) {
                            displayName = PhoneNumberUtils.formatNumber(contactNumber, Locale.getDefault().getCountry());
                        } else {
                            displayName = contactName;
                        }

                        if (profilePicPrivacy == AccountManager.NO_ONE) {
                            profilePic  =   "";
                        }

                        Log.e("ChatDebug","display name for contact id " + contactId + " name is " + displayName);
                        ChatListModel chat = new ChatListModel(
                                contactId,
                                displayName,
                                finalMessageDirection,
                                cursor.getInt(cursor.getColumnIndexOrThrow("message_type")),
                                cursor.getString(cursor.getColumnIndexOrThrow("message")),
                                cursor.getInt(cursor.getColumnIndexOrThrow("message_status")),
                                cursor.getInt(cursor.getColumnIndexOrThrow("unread_messages_count")),
                                timestamp,
                                profilePic,
                                false
                        );
                        chatList.add(chat);
                    }
                    while (cursor.moveToNext());

                    long buildEnd = System.currentTimeMillis();
                    Log.e("ChatDataTiming", "Building ChatListModel objects took " + (buildEnd - buildStart) + " ms");

                    long postStart = System.currentTimeMillis();
                    chatListModelMutableLiveData.postValue(chatList);
                    long postEnd = System.currentTimeMillis();
                    Log.e("ChatDataTiming", "Posting LiveData took " + (postEnd - postStart) + " ms");
                } else {
                    chatListModelMutableLiveData.postValue(new ArrayList<>());
                }
            } catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to get chat list " + e.getMessage());
            }

            long totalEnd = System.currentTimeMillis();
            Log.e("ChatDataTiming", "Total getAllNormalChats() took " + (totalEnd - totalStart) + " ms");
        });
    }

    public void getRecentChatsForForwardActivity() {
        readExecutor.execute(() -> {
            int profilePicPrivacy   =   sharedPreferences.getInt(SharedPreferenceDetails.PROFILE_PIC_PRIVACY_OPTION,
                                        AccountManager.MY_CONTACTS);
            ArrayList<MessageForwardChatsListModel> chatList = new ArrayList<>();
            try (Cursor cursor = messagesDatabaseDAO.getRecentChats()) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        String contactId            =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.CHAT_ID));
                        String profilePic       =   "";
                        String contactName      =   "";
                        String contactNumber    =   "";

                        try (Cursor contactCursor = contactsDatabaseDAO.getContactNameAndProfilePic(contactId)) {
                            if (contactCursor != null && contactCursor.moveToFirst()) {
                                profilePic      =   contactCursor.getString(contactCursor.getColumnIndexOrThrow(
                                        ContactsDatabase.PROFILE_PIC));
                                contactName     =   contactCursor.getString(contactCursor.getColumnIndexOrThrow(
                                        ContactsDatabase.CONTACT_NAME));
                                contactNumber   =   contactCursor.getString(contactCursor.getColumnIndexOrThrow(
                                        ContactsDatabase.CONTACT_PHONE));
                            } else {
                                Log.e("ChatDebug","unable to get contact name due to cursor null with id " + contactId);
                            }
                        } catch (Exception e) {
                            Log.e("ChatDebug","caught exception when getting contact details for " + contactId
                                    + " exception is " + e.getMessage());
                        }
                        String displayName = "";
                        if (contactName == null || contactName.isEmpty()) {
                            displayName = PhoneNumberUtils.formatNumber(contactNumber, Locale.getDefault().getCountry());
                        } else {
                            displayName = contactName;
                        }

                        if (profilePicPrivacy == AccountManager.NO_ONE) {
                            profilePic  =   "";
                        }

                        Log.e("ChatDebug","display name for contact id " + contactId + " name is " + displayName);
                        MessageForwardChatsListModel messageForwardChatsListModel   =   new MessageForwardChatsListModel(
                                contactId,
                                displayName,
                                profilePic,
                                false
                        );
                        chatList.add(messageForwardChatsListModel);
                    }
                    while (cursor.moveToNext());
                    recentChatsMutable.postValue(chatList);
                } else {
                    recentChatsMutable.postValue(new ArrayList<>());
                }
            } catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to get recent chat list " + e.getMessage());
            }
        });
    }



    public void getAllArchivedChats() {
        readExecutor.execute(() -> {
            int profilePicPrivacy   =   sharedPreferences.getInt(SharedPreferenceDetails.PROFILE_PIC_PRIVACY_OPTION,
                                        AccountManager.MY_CONTACTS);
            ArrayList<ArchiveListModel> archivedChatsList = new ArrayList<>();
            try (Cursor cursor = messagesDatabaseDAO.getArchiveChatList()) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        String  contactId           =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.CHAT_ID));
                        int     messageDirection    =   cursor.getInt(cursor.getColumnIndexOrThrow("message_direction"));
                        long timestamp              =   (messageDirection == 1)
                                ? cursor.getLong(cursor.getColumnIndexOrThrow("receive_timestamp"))
                                : cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));

                        String profilePic       =   "";
                        String  contactName     =   "";
                        String  contactNumber   =   "";
                        try (Cursor contactCursor = contactsDatabaseDAO.getContactNameAndProfilePic(contactId)) {
                            if (contactCursor != null && contactCursor.moveToFirst()) {
                                profilePic      =   contactCursor.getString(contactCursor.getColumnIndexOrThrow(
                                        ContactsDatabase.PROFILE_PIC));
                                contactName     =   contactCursor.getString(contactCursor.getColumnIndexOrThrow(
                                        ContactsDatabase.CONTACT_NAME));
                                contactNumber   =   contactCursor.getString(contactCursor.getColumnIndexOrThrow(
                                        ContactsDatabase.CONTACT_PHONE));
                            }
                        }

                        if (profilePicPrivacy == AccountManager.NO_ONE) {
                            profilePic = "";
                        }

                        String displayName = "";
                        if (contactName == null || contactName.isEmpty()) {
                            displayName = PhoneNumberUtils.formatNumber(contactNumber, Locale.getDefault().getCountry());
                        } else {
                            displayName = contactName;
                        }

                        ArchiveListModel archiveListModel = new ArchiveListModel(
                                contactId,
                                displayName,
                                messageDirection,
                                cursor.getInt(cursor.getColumnIndexOrThrow("message_type")),
                                cursor.getString(cursor.getColumnIndexOrThrow("message")),
                                cursor.getInt(cursor.getColumnIndexOrThrow("message_status")),
                                cursor.getInt(cursor.getColumnIndexOrThrow("unread_messages_count")),
                                timestamp,
                                profilePic
                        );
                        archivedChatsList.add(archiveListModel);
                    }
                    while (cursor.moveToNext());
                    archiveChatsListMutableData.postValue(archivedChatsList);
                }
                else {
                    archiveChatsListMutableData.postValue(new ArrayList<>());
                }
            } catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to get archived chat list " + e.getMessage());
                archiveChatsListMutableData.postValue(new ArrayList<>());
            }
        });
    }

    public void getAllMessageRequestChatsCount() {
        readExecutor.execute(() -> {
            try (Cursor cursor = messagesDatabaseDAO.getMessageRequestChatList()) {
                if (cursor != null && cursor.moveToFirst()) {
                    messageRequestsCountMutable.postValue(cursor.getCount());
                }
                else {
                    messageRequestsCountMutable.postValue(0);
                }
            } catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to get archived chat list " + e.getMessage());
                messageRequestsCountMutable.postValue(0);
            }
        });
    }

    public void getAllMessageRequestChats() {
        readExecutor.execute(() -> {
            int profilePicPrivacy   =   sharedPreferences.getInt(SharedPreferenceDetails.PROFILE_PIC_PRIVACY_OPTION,
                                        AccountManager.MY_CONTACTS);
            ArrayList<ArchiveListModel> archivedChatsList = new ArrayList<>();
            try (Cursor cursor = messagesDatabaseDAO.getMessageRequestChatList()) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        String  contactId           =   cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.CHAT_ID));
                        int     messageDirection    =   cursor.getInt(cursor.getColumnIndexOrThrow("message_direction"));
                        long    timestamp           =   (messageDirection == 1)
                                ? cursor.getLong(cursor.getColumnIndexOrThrow("receive_timestamp"))
                                : cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));

                        String profilePic       =   "";
                        String  contactName     =   "";
                        String  contactNumber   =   "";
                        try (Cursor contactCursor = contactsDatabaseDAO.getContactNameAndProfilePic(contactId)) {
                            if (contactCursor != null && contactCursor.moveToFirst()) {
                                profilePic      =   contactCursor.getString(contactCursor.getColumnIndexOrThrow(
                                        ContactsDatabase.PROFILE_PIC));
                                contactName     =   contactCursor.getString(contactCursor.getColumnIndexOrThrow(
                                        ContactsDatabase.CONTACT_NAME));
                                contactNumber   =   contactCursor.getString(contactCursor.getColumnIndexOrThrow(
                                        ContactsDatabase.CONTACT_PHONE));
                            }
                        }

                        if (profilePicPrivacy == AccountManager.NO_ONE) {
                            profilePic = "";
                        }

                        String displayName = "";
                        if (contactName == null || contactName.isEmpty()) {
                            displayName = PhoneNumberUtils.formatNumber(contactNumber, Locale.getDefault().getCountry());
                        } else {
                            displayName = contactName;
                        }

                        ArchiveListModel archiveListModel = new ArchiveListModel(
                                contactId,
                                displayName,
                                messageDirection,
                                cursor.getInt(cursor.getColumnIndexOrThrow("message_type")),
                                cursor.getString(cursor.getColumnIndexOrThrow("message")),
                                cursor.getInt(cursor.getColumnIndexOrThrow("message_status")),
                                cursor.getInt(cursor.getColumnIndexOrThrow("unread_messages_count")),
                                timestamp,
                                profilePic
                        );
                        archivedChatsList.add(archiveListModel);
                    }
                    while (cursor.moveToNext());
                    messageRequestsChatsMutable.postValue(archivedChatsList);
                }
                else {
                    messageRequestsChatsMutable.postValue(new ArrayList<>());
                }
            } catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to get archived chat list " + e.getMessage());
                messageRequestsChatsMutable.postValue(new ArrayList<>());
            }
        });
    }

    public void getChatsByContactIds(ArrayList<String> contactIds) {
        readExecutor.execute(() -> {
            ArrayList<ChatListModel> chatList = new ArrayList<>();

            try (Cursor cursor = messagesDatabaseDAO.getChatsByContactIds(contactIds)) {
                if (cursor != null && cursor.moveToFirst()) {
                    do {
                        int messageDirection = cursor.getInt(cursor.getColumnIndexOrThrow("message_direction"));
                        long timestamp = (messageDirection == 1)
                                ? cursor.getLong(cursor.getColumnIndexOrThrow("receive_timestamp"))
                                : cursor.getLong(cursor.getColumnIndexOrThrow("timestamp"));

                        String contactId = cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.CHAT_ID));

                        // If you need additional contact info like profile pic, you can query it here:
                        String profilePic = "";
                        String contactName  =   "";
                        try (Cursor contactCursor = contactsDatabaseDAO.getContactNameAndProfilePic(contactId)) {
                            if (contactCursor != null && contactCursor.moveToFirst()) {
                                profilePic      =   contactCursor.getString(contactCursor.getColumnIndexOrThrow(ContactsDatabase.PROFILE_PIC));
                                contactName     =   contactCursor.getString(contactCursor.getColumnIndexOrThrow(
                                        ContactsDatabase.CONTACT_NAME));
                            }
                        }



                        ChatListModel chat = new ChatListModel(
                                contactId,
                                contactName,
                                messageDirection,
                                cursor.getInt(cursor.getColumnIndexOrThrow("message_type")),
                                cursor.getString(cursor.getColumnIndexOrThrow("message")),
                                cursor.getInt(cursor.getColumnIndexOrThrow("message_status")),
                                cursor.getInt(cursor.getColumnIndexOrThrow("unread_messages_count")),
                                timestamp,
                                profilePic,
                                false
                        );
                        chatList.add(chat);

                    } while (cursor.moveToNext());
                    searchedChatsMutable.postValue(chatList);
                } else {
                    searchedChatsMutable.postValue(new ArrayList<>());
                }
            } catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to get filtered chat list " + e.getMessage());
                searchedChatsMutable.postValue(new ArrayList<>());
            }
        });
    }


    public void getChatLockAndReadReceiptStatus(String contactId) {
        readExecutor.execute(() -> {
            try (Cursor cursor  =   messagesDatabaseDAO.getChatLockAndReadReceiptsFromDatabase(contactId)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int chatLockStatus      =   cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.CHAT_LOCK));
                    int contactReadReceipts =   cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.CHAT_READ_RECEIPTS));
                    chatLockAndReadReceiptsLiveData.postValue(new Pair<>(chatLockStatus, contactReadReceipts));
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to get chat lock and read receipts " + e.getMessage());
            }
        });
    }

    public void getContactChatArchiveStatusFromDB(String contactId) {
        readExecutor.execute(() -> {
            try (Cursor cursor  =  messagesDatabaseDAO.getArchiveStatus(contactId)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int archiveStatus = cursor.getInt(cursor.getColumnIndexOrThrow(MessagesDatabase.CHAT_ARCHIVE));
                    contactChatArchiveStatusMutable.postValue(archiveStatus);
                }
                else {
                    contactChatArchiveStatusMutable.postValue(ChatManager.UNARCHIVE_CHAT);
                    Log.e(Extras.LOG_MESSAGE,"unable to get archived status due to cursor is null in messaging activity");
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"unable to get archived status "+e.getMessage());
            }
        });
    }

    public void getContactNameAndClickActionsFromId(ChatClickActionsModel chatClickActionsModel) {
        readExecutor.execute(() -> {
            try (Cursor cursor  =   contactsDatabaseDAO.getContactNameAndPhoneNumber(chatClickActionsModel.getContactId())) {
                if (cursor != null && cursor.moveToFirst()) {
                    String contactName      =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_NAME));
                    String contactNumber    =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_PHONE));

                    if (contactName == null || contactName.isEmpty()) {
                        String finalContactNumber   =   PhoneNumberUtils.formatNumber(contactNumber, Locale.getDefault().getCountry());

                        if (finalContactNumber == null || finalContactNumber.isEmpty()) {
                            finalContactNumber  =   "";
                        }

                        getChatClickActionsMutable.postValue(
                                new ChatClickActionsModel(
                                        chatClickActionsModel.getContactId(),
                                        null,
                                        chatClickActionsModel.getChatClickActionType(),
                                        chatClickActionsModel.getChatLockedTimeLeft()));
                    } else {
                        getChatClickActionsMutable.postValue(
                                new ChatClickActionsModel(
                                        chatClickActionsModel.getContactId(),
                                        contactName,
                                        chatClickActionsModel.getChatClickActionType(),
                                        chatClickActionsModel.getChatLockedTimeLeft()));
                    }
                } else {
                    getChatClickActionsMutable.postValue(
                            new ChatClickActionsModel(
                                    chatClickActionsModel.getContactId(),
                                    null,
                                    chatClickActionsModel.getChatClickActionType(),
                                    chatClickActionsModel.getChatLockedTimeLeft()));
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to retrieve chat name caught sql exception " + e.getMessage());
            }
        });
    }

    public String getContactNameOrNumberFromId(String contactId) {
        FutureTask<String> getContactNameTask   =   new FutureTask<>(() -> {
            try (Cursor cursor  =   contactsDatabaseDAO.getContactNameAndPhoneNumber(contactId)) {
                if (cursor != null && cursor.moveToFirst()) {
                    String contactName      =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_NAME));
                    String contactNumber    =   cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_PHONE));

                    Log.e(Extras.LOG_MESSAGE,"contact name is " + contactName + " " + contactNumber);

                    if (contactName == null || contactName.isEmpty()) {
                        String finalContactNumber   =   PhoneNumberUtils.formatNumber(contactNumber, Locale.getDefault().getCountry());
                        getContactNameMutable.postValue(finalContactNumber);
                        return PhoneNumberUtils.formatNumber(contactNumber, Locale.getDefault().getCountry());
                    } else {
                        getContactNameMutable.postValue(contactName);
                        return contactName;
                    }
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to retrieve chat name caught sql exception " + e.getMessage());
            }
            return null;
        });

        readExecutor.execute(getContactNameTask);

        try {
            return getContactNameTask.get();
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to get contact name from id error in repo " + e.getMessage());
            return null;
        }
    }

    public String getContactNameFromNumber(String contactNumber) {
        FutureTask<String> getContactNameTask   =   new FutureTask<>(() -> {
            try (Cursor cursor  =   contactsDatabaseDAO.getContactNameFromPhoneNumber(contactNumber)) {
                if (cursor != null && cursor.moveToFirst()) {
                    return cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_NAME));
                }
            }
            catch (SQLException e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to retrieve chat name caught sql exception " + e.getMessage());
            }
            return null;
        });

        readExecutor.execute(getContactNameTask);

        try {
            return getContactNameTask.get();
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"Unable to get contact name from id error in repo " + e.getMessage());
            return null;
        }
    }

    public boolean isChatExists(String chatId) {
        return messagesDatabaseDAO.checkChatListId(chatId);
    }

    public boolean isChatArchived(String chatId) {
        return messagesDatabaseDAO.checkArchivedOrNot(chatId);
    }

    public void checkForUnreadCountInArchiveChatsInDb() {
        readExecutor.execute(() -> {
            try (Cursor cursor  =   messagesDatabaseDAO.anyUnreadCountsInArchiveChats()) {
                if (cursor != null && cursor.moveToFirst()) {
                    anyArchiveUnreadCountsMutable.postValue(true);
                } else {
                    anyArchiveUnreadCountsMutable.postValue(false);
                }
            }
        });
    }


    // ------------- Database Read Operations Ends from here -----------------------------------

    // ------------- Database Update Operations starts from here -----------------------------------

    public void updateChatArchiveStatus(String contactId, int archiveStatus) {
        writeExecutor.execute(() -> {
            try {
                boolean update  =   messagesDatabaseDAO.updateChatArchiveStatus(contactId, archiveStatus);
                if (update) {
                    if (archiveStatus   ==  ChatManager.ARCHIVE_CHAT) {
                        chatArchiveStatusMutableData.postValue(new Pair<>(contactId, archiveStatus));
                    } else if (archiveStatus == ChatManager.UNARCHIVE_CHAT) {
                        chatArchiveStatusMutableData.postValue(new Pair<>(contactId, archiveStatus));
                    }
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to update chat archive status error logged in repository " + e.getMessage());
            }
        });
    }

    public void updateChatStatusAsLocked(String contactId) {
        writeExecutor.execute(() -> {
            try {
                boolean updated = messagesDatabaseDAO.updateChatLockedStatusAndTime(contactId, ChatManager.CHAT_IS_LOCKED,
                        System.currentTimeMillis());
                // chatListRepositoryCallbacks.onUpdatingChatLockStatus(updated);
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to update chat lock status as locked error in repository " + e.getMessage());
            }
        });
    }

    public void resetUnreadCount(String contactId) {
        writeExecutor.execute(() -> {
            try {
                messagesDatabaseDAO.resetUnreadCount(contactId);
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to reset unread count error in repository " + e.getMessage());
            }
        });
    }

    // ---------------- Database Update Operations Ends Here  -----------------------------

    public void turnOnChatLockForContact(String contactId) {
        writeExecutor.execute(() -> {
            try {
                boolean update  =   messagesDatabaseDAO.updateChatLockStatus(contactId, ChatManager.CHAT_LOCK_IS_ON);
                if (update) {
                    chatLockStatusLiveData.postValue(ChatManager.CHAT_LOCK_IS_ON);
                } else {
                    chatLockStatusLiveData.postValue(ChatManager.CHAT_LOCK_IS_OFF);
                }

            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to turn on lock for the chat error in repository " + e.getMessage());
            }
        });
    }

    public void turnOffChatLockForContact(String contactId) {
        writeExecutor.execute(() -> {
            try {
                boolean update  =   messagesDatabaseDAO.updateChatLockStatus(contactId, ChatManager.CHAT_LOCK_IS_OFF);
                if (update) {
                    chatLockStatusLiveData.postValue(ChatManager.CHAT_LOCK_IS_OFF);
                } else {
                    chatLockStatusLiveData.postValue(ChatManager.CHAT_LOCK_IS_ON);
                }
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to turn off lock for the chat error in repository " + e.getMessage());
            }
        });
    }

    public void turnOnReadReceipts(String contactId) {
        writeExecutor.execute(() -> {
            try {
                boolean update  =   messagesDatabaseDAO.updateChatReadReceipts(contactId, ChatManager.READ_RECEIPTS_ON);
                if (update) {
                    // contactReadReceiptsLiveData.postValue(ChatManager.READ_RECEIPTS_ON);
                    getChatLockAndReadReceiptStatus(contactId);
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to turn on read receipts error in repository " + e.getMessage());
            }
        });
    }

    public void turnOffReadReceipts(String contactId) {
        writeExecutor.execute(() -> {
            try {
                boolean update  =   messagesDatabaseDAO.updateChatReadReceipts(contactId, ChatManager.READ_RECEIPTS_OFF);
                if (update) {
                    getChatLockAndReadReceiptStatus(contactId);
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to turn off read receipts error in repository " + e.getMessage());
            }
        });
    }

    // ---------------- Database Delete Operations Starts Here  -----------------------------

    public void deleteChatAndMessages(String contactId) {
        writeExecutor.execute(() -> {
            try {
                boolean delete =   messagesDatabaseDAO.deleteChatAndMessages(contactId);
                chatDeleteMutualLiveData.postValue(new Pair<>(contactId, delete));
            }
            catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE, "Unable to delete all messages of contact and chat" +
                        " caught exception in repository " + e.getMessage());
            }
        });
    }
}
