package com.jippytalk.Messages.Repository;

import android.content.SharedPreferences;
import android.util.Log;
import android.util.Pair;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.jippytalk.Common.SingleLiveEvent;
import com.jippytalk.Extras;
import com.jippytalk.Managers.AccountManager;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.Messages.Datahandlers.HandleMessagesNetworkingAPI;
import com.jippytalk.UserDetailsRepository;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;

public class MessagesUIRepository {

    private volatile static MessagesUIRepository            messagesUIRepository;
    private final HandleMessagesNetworkingAPI               handleMessagesNetworkingAPI;
    private final SharedPreferences                         sharedPreferences;
    private final MutableLiveData<Integer>                  lastVisitPrivacyMutable         =   new MutableLiveData<>();
    private final MutableLiveData<Pair<String, Boolean>>    userIdAndReadReceiptsMutable    =   new MutableLiveData<>();


    public LiveData<Integer> getLastVisitPrivacyOptionLiveData() {
        return lastVisitPrivacyMutable;
    }

    public LiveData<Pair<String, Boolean>> getUserIdAndReadReceiptsLiveData() {
        return userIdAndReadReceiptsMutable;
    }


    public MessagesUIRepository(HandleMessagesNetworkingAPI handleMessagesNetworkingAPI,
                                SharedPreferences sharedPreferences) {
        this.handleMessagesNetworkingAPI        =   handleMessagesNetworkingAPI;
        this.sharedPreferences                  =   sharedPreferences;
    }

    public static MessagesUIRepository getInstance(HandleMessagesNetworkingAPI handleMessagesNetworkingAPI,
                                                   SharedPreferences sharedPreferences) {
        if (messagesUIRepository == null) {
            synchronized (MessagesUIRepository.class) {
                if (messagesUIRepository == null) {
                    messagesUIRepository    =   new MessagesUIRepository(handleMessagesNetworkingAPI, sharedPreferences);
                }
            }
        }
        return messagesUIRepository;
    }

    public LiveData<Integer> getContactBusyStatusLiveDataFromRep() {
        return handleMessagesNetworkingAPI.getContactBusyStatusLiveDataFromAPI();
    }

    public void getContactBusyStatus(String contactId) {
        handleMessagesNetworkingAPI.getUserStatus(contactId);
    }

    public void getLastVisitPrivacyOption() {
        lastVisitPrivacyMutable.setValue(sharedPreferences.getInt(SharedPreferenceDetails.LAST_VISIT_OPTION,
                                AccountManager.MY_CONTACTS));
    }

    public void getUserIdAndReadReceiptsStatus() {
        String userId           =   sharedPreferences.getString(SharedPreferenceDetails.USERID, "");
        boolean readReceipts    =   sharedPreferences.getBoolean(SharedPreferenceDetails.MESSAGES_READ_RECEIPTS, true);
        userIdAndReadReceiptsMutable.setValue(new Pair<>(userId, readReceipts));
    }
}
