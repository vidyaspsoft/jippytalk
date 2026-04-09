package com.jippytalk.Profiles.Profile.API;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.jippytalk.Extras;

import org.json.JSONObject;

public class ProfileUpdatesAPI {

    // ---- Fields ----

    private final Context                              context;
    private final MutableLiveData<Pair<String, String>> profilePicUpdateLiveData    =   new MutableLiveData<>();

    // ---- Constructor ----

    public ProfileUpdatesAPI(Context context) {
        this.context    =   context.getApplicationContext();
        Log.e(Extras.LOG_MESSAGE, "ProfileUpdatesAPI initialized");
    }

    // ---- Public Methods ----

    public String updateUsernameInServer(JSONObject jsonObject, String jwtToken) {
        // TODO: Implement API call to update username on server
        Log.e(Extras.LOG_MESSAGE, "updateUsernameInServer called");
        return "failed";
    }

    public String uploadProfilePicture(JSONObject jsonObject, String jwtToken) {
        // TODO: Implement API call to upload profile picture
        Log.e(Extras.LOG_MESSAGE, "uploadProfilePicture called");
        return "failed";
    }

    // ---- LiveData Getters ----

    public LiveData<Pair<String, String>> getProfilePicUpdateLiveData() {
        return profilePicUpdateLiveData;
    }
}
