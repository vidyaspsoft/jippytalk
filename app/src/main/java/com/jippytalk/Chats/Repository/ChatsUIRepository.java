package com.jippytalk.Chats.Repository;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.jippytalk.Database.MessagesDatabase.Repository.ChatListRepository;
import com.jippytalk.Extras;
import com.jippytalk.ServiceLocators.DatabaseServiceLocator;

public class ChatsUIRepository {

    // ---- Fields ----

    private static volatile ChatsUIRepository      INSTANCE;
    private final Context                          context;
    private final DatabaseServiceLocator            databaseServiceLocator;
    private final ChatListRepository               chatListRepository;
    private final SharedPreferences                sharedPreferences;

    // ---- Constructor ----

    private ChatsUIRepository(Context context, DatabaseServiceLocator databaseServiceLocator,
                              ChatListRepository chatListRepository, SharedPreferences sharedPreferences) {
        this.context                    =   context.getApplicationContext();
        this.databaseServiceLocator     =   databaseServiceLocator;
        this.chatListRepository         =   chatListRepository;
        this.sharedPreferences          =   sharedPreferences;
    }

    // ---- Singleton ----

    public static ChatsUIRepository getInstance(Context context, DatabaseServiceLocator databaseServiceLocator,
                                                 ChatListRepository chatListRepository, SharedPreferences sharedPreferences) {
        if (INSTANCE == null) {
            synchronized (ChatsUIRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE    =   new ChatsUIRepository(context.getApplicationContext(),
                                    databaseServiceLocator, chatListRepository, sharedPreferences);
                }
            }
        }
        return INSTANCE;
    }
}
