package com.jippytalk.ChatLockOptions.Repository;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.Context;
import android.util.Log;

import com.jippytalk.Extras;

public class ChatLockOptionsRepository {

    // ---- Fields ----

    private static volatile ChatLockOptionsRepository      INSTANCE;
    private final Context                                  context;

    // ---- Constructor ----

    private ChatLockOptionsRepository(Context context) {
        this.context    =   context.getApplicationContext();
    }

    // ---- Singleton ----

    public static ChatLockOptionsRepository getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (ChatLockOptionsRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE    =   new ChatLockOptionsRepository(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }
}
