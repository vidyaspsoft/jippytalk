package com.jippytalk.Managers.SharedPreferenceManager;

import android.content.Context;
import android.content.SharedPreferences;

import com.jippytalk.ServiceLocators.DatabaseServiceLocator;
import com.jippytalk.ArchiveChatSettings.Repository.ArchiveChatSettingsRepository;
import com.jippytalk.Managers.AccountManager;
import com.jippytalk.Managers.ChatManager;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.MyApplication;
import com.jippytalk.ServiceLocators.RepositoryServiceLocator;

public class SharedPreferencesManager {

    private static volatile SharedPreferencesManager    sharedPreferencesManager;
    private final SharedPreferences                     sharedPreferences;

    public SharedPreferencesManager(Context context) {
        RepositoryServiceLocator repositoryServiceLocator   =   ((MyApplication) context.getApplicationContext()).getRepositoryServiceLocator();
        sharedPreferences                                   =   repositoryServiceLocator.getSharedPreferences();
    }

    public static SharedPreferencesManager getInstance(Context context) {
        if (sharedPreferencesManager == null) {
            synchronized (ArchiveChatSettingsRepository.class) {
                if (sharedPreferencesManager == null) {
                    sharedPreferencesManager = new SharedPreferencesManager(context.getApplicationContext());
                }
            }
        }
        return sharedPreferencesManager;
    }

    public void updateUserAboutInSharedPreference(String userAbout) {
        sharedPreferences.edit().putString(SharedPreferenceDetails.USER_ABOUT, userAbout).apply();
    }

    public void hideArchiveChatsDisplayMessage() {
        sharedPreferences.edit().putBoolean(SharedPreferenceDetails.ARCHIVE_CHATS_MESSAGE, true).apply();
    }

    public void saveDeviceIdAndRegistrationId(int deviceId, int registrationId) {
        sharedPreferences.edit().
                putInt(SharedPreferenceDetails.DEVICE_ID, deviceId).
                putInt(SharedPreferenceDetails.REGISTRATION_ID, registrationId).apply();
    }

    // get details from sharedPreference

    public String getUserId() {
        return sharedPreferences.getString(SharedPreferenceDetails.USERID, "");
    }

    public String getUserAbout() {
        return sharedPreferences.getString(SharedPreferenceDetails.USER_ABOUT, "");
    }

    public String getChatUnlockPassword() {
        return sharedPreferences.getString(SharedPreferenceDetails.CHAT_LOCK_PASSWORD, "");
    }

    public String getChatLockResetQuestion() {
        return sharedPreferences.getString(SharedPreferenceDetails.CHAT_LOCK_QUESTION, "");
    }

    public String getChatLockResetQuestionAnswer() {
        return sharedPreferences.getString(SharedPreferenceDetails.CHAT_UNLOCK_ANSWER, "");
    }

    public int getOptionsWhenChatGotLocked() {
        return sharedPreferences.getInt(SharedPreferenceDetails.OPTIONS_WHEN_CHAT_LOCKED, ChatManager.DO_NOTHING);
    }

    public int getUserRegistrationId() {
        return sharedPreferences.getInt(SharedPreferenceDetails.REGISTRATION_ID, -1);
    }

    public boolean getArchiveChatsDisplayMessage() {
        return sharedPreferences.getBoolean(SharedPreferenceDetails.ARCHIVE_CHATS_MESSAGE, false);
    }
}
