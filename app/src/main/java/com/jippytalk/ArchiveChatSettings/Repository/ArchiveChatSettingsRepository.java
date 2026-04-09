package com.jippytalk.ArchiveChatSettings.Repository;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.jippytalk.Extras;

public class ArchiveChatSettingsRepository {

    // ---- Fields ----

    private static volatile ArchiveChatSettingsRepository   INSTANCE;
    private final Context                                   context;
    private final SharedPreferences                         sharedPreferences;

    // ---- Constructor ----

    private ArchiveChatSettingsRepository(Context context, SharedPreferences sharedPreferences) {
        this.context            =   context.getApplicationContext();
        this.sharedPreferences  =   sharedPreferences;
    }

    // ---- Singleton ----

    public static ArchiveChatSettingsRepository getInstance(Context context,
                                                             SharedPreferences sharedPreferences) {
        if (INSTANCE == null) {
            synchronized (ArchiveChatSettingsRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE    =   new ArchiveChatSettingsRepository(context.getApplicationContext(),
                                    sharedPreferences);
                }
            }
        }
        return INSTANCE;
    }
}
