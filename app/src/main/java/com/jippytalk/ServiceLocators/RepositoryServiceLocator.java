package com.jippytalk.ServiceLocators;

import android.content.Context;
import android.content.SharedPreferences;

import com.jippytalk.ArchiveChatSettings.Repository.ArchiveChatSettingsRepository;
import com.jippytalk.ChatLockOptions.Repository.ChatLockOptionsRepository;
import com.jippytalk.Chats.Repository.ChatsUIRepository;
import com.jippytalk.Contacts.Repository.ContactsUIRepository;
import com.jippytalk.Database.ContactsDatabase.Repository.ContactsRepository;
import com.jippytalk.Database.MessagesDatabase.Repository.ChatListRepository;
import com.jippytalk.Database.MessagesDatabase.Repository.MessagesRepository;
import com.jippytalk.Database.User.Repository.UserDatabaseRepository;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.WebSocketConnection;
import com.jippytalk.UserDetailsRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RepositoryServiceLocator {

    private final Context                           context;
    private DatabaseServiceLocator                  databaseServiceLocator;
    private AppServiceLocator                       appServiceLocator;
    private final SharedPreferences                 sharedPreferences;
    private final ContactsRepository                contactsRepository;
    private final ChatListRepository                chatListRepository;
    private final MessagesRepository                messagesRepository;
    private final UserDatabaseRepository            userDatabaseRepository;
    private final ContactsUIRepository              contactsUIRepository;
    private final ChatsUIRepository                 chatsUIRepository;
    private final UserDetailsRepository             userDetailsRepository;
    private final ChatLockOptionsRepository         chatLockOptionsRepository;
    private final ArchiveChatSettingsRepository     archiveChatSettingsRepository;
    private ExecutorService                         appLevelExecutorService;

    public RepositoryServiceLocator(Context context, DatabaseServiceLocator databaseServiceLocator) {
        this.context                    =   context.getApplicationContext();
        this.databaseServiceLocator     =   databaseServiceLocator;
        sharedPreferences               =   context.getApplicationContext().getSharedPreferences(
                                            SharedPreferenceDetails.SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE);
        contactsRepository              =   ContactsRepository.getInstance(context.getApplicationContext(), databaseServiceLocator,
                                            sharedPreferences);
        chatListRepository              =   ChatListRepository.getInstance(context.getApplicationContext(), databaseServiceLocator,
                                            sharedPreferences);
        messagesRepository              =   MessagesRepository.getInstance(context.getApplicationContext(), databaseServiceLocator,
                                            contactsRepository, sharedPreferences);
        userDatabaseRepository          =   UserDatabaseRepository.getInstance(context.getApplicationContext(), databaseServiceLocator);
        chatLockOptionsRepository       =   ChatLockOptionsRepository.getInstance(context.getApplicationContext());
        contactsUIRepository            =   new ContactsUIRepository(chatListRepository, sharedPreferences);
        chatsUIRepository               =   ChatsUIRepository.getInstance(context.getApplicationContext(),
                                            databaseServiceLocator, chatListRepository, sharedPreferences);
        userDetailsRepository           =   UserDetailsRepository.getInstance(context.getApplicationContext(), sharedPreferences);
        archiveChatSettingsRepository   =   ArchiveChatSettingsRepository.getInstance(context.getApplicationContext(),
                                            sharedPreferences);

    }

    public SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }

    public synchronized ContactsRepository getContactsRepository() {
        return contactsRepository;
    }

    public synchronized ChatListRepository getChatListRepository() {
        return chatListRepository;
    }

    public synchronized MessagesRepository getMessagesRepository() {
        return messagesRepository;
    }

    public synchronized UserDatabaseRepository getUserDatabaseRepository() { return userDatabaseRepository; }

    public synchronized ContactsUIRepository getContactsUIRepository() {
        return contactsUIRepository;
    }

    public synchronized ChatsUIRepository getChatsUIRepository() {
        return chatsUIRepository;
    }

    public synchronized UserDetailsRepository getUserDetailsRepository() {
        return userDetailsRepository;
    }

    public synchronized ChatLockOptionsRepository getChatLockOptionsRepository() { return chatLockOptionsRepository; }

    public synchronized ArchiveChatSettingsRepository getArchiveChatSettingsRepository() { return  archiveChatSettingsRepository; }

    public synchronized ExecutorService getAppLevelExecutorService() {
        if (appLevelExecutorService == null) {
            appLevelExecutorService = Executors.newFixedThreadPool(2);
        }
        return appLevelExecutorService;
    }
}
