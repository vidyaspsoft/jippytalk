package com.jippytalk.ServiceLocators;

import android.content.Context;
import android.content.SharedPreferences;

import com.jippytalk.Database.ContactsDatabase.ContactsDatabase;
import com.jippytalk.Database.ContactsDatabase.DAO.ContactsDatabaseDAO;
import com.jippytalk.Database.MessagesDatabase.MessagesDatabaseDAO;
import com.jippytalk.Database.MessagesDatabase.MessagesDatabase;
import com.jippytalk.Database.SessionDatabase.SessionStoreDAO;
import com.jippytalk.Database.SessionDatabase.SessionsDatabase;
import com.jippytalk.Database.User.UsersDatabase;
import com.jippytalk.Database.User.UsersDatabaseDAO;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.WebSocketConnection;
import com.jippytalk.UpdatedSignalProtocolStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DatabaseServiceLocator {
    private final Context                       context;
    private final MessagesDatabase              messagesDatabase;
    private final ContactsDatabase              contactsDatabase;
    private final UsersDatabase                 usersDatabase;
    private final SessionsDatabase              sessionsDatabase;
    private final UsersDatabaseDAO              usersDatabaseDAO;
    private final MessagesDatabaseDAO           messagesDatabaseDAO;
    private final ContactsDatabaseDAO           contactsDatabaseDAO;
    private final SessionStoreDAO               sessionStoreDAO;
    private final SharedPreferences             sharedPreferences;
    private UpdatedSignalProtocolStore          customSignalProtocolStore;
    private ExecutorService                     writeExecutor;
    private ExecutorService                     readExecutor;
    private ExecutorService                     userDatabaseWriteExecutor;
    private ExecutorService                     messageDatabaseWriteExecutor;

    public DatabaseServiceLocator(Context context) {
        this.context                =   context.getApplicationContext();
        messagesDatabase            =   MessagesDatabase.getInstance(context.getApplicationContext());
        contactsDatabase            =   ContactsDatabase.getInstance(context.getApplicationContext());
        usersDatabase               =   UsersDatabase.getInstance(context.getApplicationContext());
        sessionsDatabase            =   SessionsDatabase.getInstance(context.getApplicationContext());
        messagesDatabaseDAO         =   new MessagesDatabaseDAO(messagesDatabase);
        contactsDatabaseDAO         =   new ContactsDatabaseDAO(contactsDatabase);
        sessionStoreDAO             =   new SessionStoreDAO(sessionsDatabase);
        usersDatabaseDAO            =   new UsersDatabaseDAO(context.getApplicationContext(), usersDatabase);
        sharedPreferences           =   context.getApplicationContext().getSharedPreferences(
                                        SharedPreferenceDetails.SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    public SharedPreferences getSharedPreferences() {
        return sharedPreferences;
    }

    public synchronized MessagesDatabase getSqlite() {
        return messagesDatabase;
    }

    public synchronized UsersDatabase getUsersDatabase() {
        return usersDatabase;
    }

    public synchronized ContactsDatabase getContactsDatabase() {
        return contactsDatabase;
    }

    public synchronized SessionsDatabase getSessionsDatabase() {
        return sessionsDatabase;
    }

    public synchronized MessagesDatabaseDAO getMessagesDatabaseDAO() {
        return messagesDatabaseDAO;
    }

    public synchronized ContactsDatabaseDAO getContactsDatabaseDAO() {
        return contactsDatabaseDAO;
    }

    public synchronized UsersDatabaseDAO getUsersDatabaseDAO() {
        return usersDatabaseDAO;
    }

//    public synchronized UpdatedSignalProtocolStore getSignalProtocolStore() {
//        if (customSignalProtocolStore == null) {
//            customSignalProtocolStore = new UpdatedSignalProtocolStore(
//                    context.getApplicationContext(),
//                    getContactsDatabaseDAO(),
//                    getSessionStoreDAO()
//            );
//        }
//        return customSignalProtocolStore;
//    }

    public synchronized SessionStoreDAO getSessionStoreDAO() {
        return  sessionStoreDAO;
    }

    public synchronized ExecutorService getWriteExecutor() {
        if (writeExecutor == null) {
            writeExecutor   =   Executors.newSingleThreadExecutor();
        }
        return writeExecutor;
    }

    public synchronized ExecutorService getReadExecutor() {
        if (readExecutor == null) {
            readExecutor    =   Executors.newFixedThreadPool(4);
        }
        return readExecutor;
    }

    public synchronized ExecutorService getUserDatabaseWriteExecutor() {
        if (userDatabaseWriteExecutor == null) {
            userDatabaseWriteExecutor   =   Executors.newSingleThreadExecutor();
        }
        return userDatabaseWriteExecutor;
    }

    public synchronized ExecutorService getMessageDatabaseWriteExecutor() {
        if (messageDatabaseWriteExecutor == null) {
            messageDatabaseWriteExecutor   =   Executors.newSingleThreadExecutor();
        }
        return messageDatabaseWriteExecutor;
    }
}
