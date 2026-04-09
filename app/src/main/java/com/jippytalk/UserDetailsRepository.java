package com.jippytalk;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.jippytalk.Common.SingleLiveEvent;
import com.jippytalk.TokenRefreshAPI;
import com.jippytalk.Managers.AccountManager;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.Profiles.Model.ProfileDataModel;
import com.jippytalk.Profiles.Profile.API.ProfileUpdatesAPI;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserDetailsRepository {


    private static volatile UserDetailsRepository       userDetailsRepository;
    private final Context                               context;
    private final SharedPreferences                     sharedPreferences;
    private final ProfileUpdatesAPI                     profileUpdatesAPI;
    private final TokenRefreshAPI                       tokenRefreshAPI;

    private final MutableLiveData<String>               userIdMutable               =   new MutableLiveData<>();
    private final MutableLiveData<String>               userNameMutable             =   new MutableLiveData<>();
    private final MutableLiveData<String>               userAboutMutable            =   new MutableLiveData<>();
    private final MutableLiveData<String>               userPhoneNumberMutable      =   new MutableLiveData<>();
    private final MutableLiveData<String>               getPhoneNumberE164Format    =   new MutableLiveData<>();
    private final MutableLiveData<Bitmap>               getUserProfilePicMutable    =   new MutableLiveData<>();
    private final MutableLiveData<ProfileDataModel>     profileDataMutable          =   new MutableLiveData<>();
    private final SingleLiveEvent<Boolean>              updateStatusMutable         =   new SingleLiveEvent<>();
    private final ExecutorService                       executorService             =   Executors.newSingleThreadExecutor();

    public LiveData<String> getUserIdLiveData() {
        return userIdMutable;
    }

    public LiveData<String> getUsernameLiveData() {
        return userNameMutable;
    }

    public LiveData<String> getUserAboutLiveData() {
        return userAboutMutable;
    }

    public LiveData<String> getUserPhoneNumberLiveData() {
        return userPhoneNumberMutable;
    }

    public LiveData<String> getPhoneNumberE164FormatLiveData() {
        return getPhoneNumberE164Format;
    }

    public LiveData<ProfileDataModel> getProfileDataLiveData() {
        return profileDataMutable;
    }

    public LiveData<Boolean> getUpdateStatusLiveData() {
        return updateStatusMutable;
    }

    public LiveData<Pair<String, String>> getProfilePicUpdateStatusLiveData() {
        return profileUpdatesAPI.getProfilePicUpdateLiveData();
    }


    public UserDetailsRepository(Context context, SharedPreferences sharedPreferences) {
        this.context                =   context.getApplicationContext();
        this.sharedPreferences      =   sharedPreferences;
        profileUpdatesAPI           =   new ProfileUpdatesAPI(context.getApplicationContext());
        tokenRefreshAPI             =   new TokenRefreshAPI(this);
    }

    public static UserDetailsRepository getInstance(Context context, SharedPreferences sharedPreferences) {
        if (userDetailsRepository == null) {
            synchronized (UserDetailsRepository.class) {
                if (userDetailsRepository == null) {
                    userDetailsRepository   =   new UserDetailsRepository(context.getApplicationContext(), sharedPreferences);
                }
            }
        }
        return userDetailsRepository;
    }

    public void getUserE164PhoneNumberFromPrefs() {
        getPhoneNumberE164Format.postValue(sharedPreferences.getString(
                SharedPreferenceDetails.USER_PHONE_NUMBER_INTERNATIONAL_FORMAT, ""));
    }

    public LiveData<Bitmap> getUserProfilePicture() {
        File profilePicFile = new File(context.getApplicationContext().getFilesDir(), "profilePic.jpg");
        if (profilePicFile.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(profilePicFile.getAbsolutePath());
            getUserProfilePicMutable.postValue(bitmap);
        } else {
            getUserProfilePicMutable.postValue(null);
        }
        return getUserProfilePicMutable;
    }

    public LiveData<Bitmap> getImageFromInternalStorage() {
        final MutableLiveData<Bitmap> getUserProfileMutable =   new MutableLiveData<>();
        String          fileName    =   "profile_image.jpg";
        ContextWrapper  cw          =   new ContextWrapper(context.getApplicationContext());
        File            directory   =   cw.getDir("imageDir", Context.MODE_PRIVATE);
        File            imageFile   =   new File(directory, fileName);

        if (imageFile.exists()) {
            Bitmap imageBitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath());
            getUserProfileMutable.postValue(imageBitmap);
        }
        return getUserProfileMutable;
    }

    public LiveData<Integer> getUserSelectedAppTheme() {
        final MutableLiveData<Integer> getAppTheme  =   new MutableLiveData<>();
        getAppTheme.postValue(sharedPreferences.getInt(SharedPreferenceDetails.APP_THEME, AccountManager.SYSTEM_DEFAULT_THEME));
        return getAppTheme;
    }

    public void getUserProfileData() {
        String  userId          =   sharedPreferences.getString(SharedPreferenceDetails.USERID,"");
        String  userName        =   sharedPreferences.getString(SharedPreferenceDetails.USERNAME,"");
        String  userAbout       =   sharedPreferences.getString(SharedPreferenceDetails.USER_ABOUT,"");
        String  phoneNumber     =   sharedPreferences.getString(SharedPreferenceDetails.USER_PHONE_NUMBER,"");
        long    joinedDate      =   sharedPreferences.getLong(SharedPreferenceDetails.ACCOUNT_CREATED_ON, 0);
        Bitmap  profilePicture;
        File profilePicFile = new File(context.getApplicationContext().getFilesDir(), "profilePic.jpg");
        if (profilePicFile.exists()) {
            profilePicture  = BitmapFactory.decodeFile(profilePicFile.getAbsolutePath());
        }
        else {
            profilePicture  =   null;
        }
        profileDataMutable.postValue(new ProfileDataModel(userId, userName, userAbout, phoneNumber, joinedDate, profilePicture));
    }

    public void getUserIdFromPrefs() {
        userIdMutable.postValue(sharedPreferences.getString(SharedPreferenceDetails.USERID,""));
    }

    public void getUserAboutFromPrefs() {
        userAboutMutable.postValue(sharedPreferences.getString(SharedPreferenceDetails.USER_ABOUT,""));
    }

    public void getUserNameFromPrefs() {
        userNameMutable.postValue(sharedPreferences.getString(SharedPreferenceDetails.USERNAME,""));
    }

    public void getUserPhoneNumberFromPrefs() {
        userPhoneNumberMutable.postValue(sharedPreferences.getString(SharedPreferenceDetails.USER_PHONE_NUMBER,""));
    }

    public void getUserProfilePictureFromFile() {
        File profilePicFile = new File(context.getApplicationContext().getFilesDir(), "profilePic.jpg");
        if (profilePicFile.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(profilePicFile.getAbsolutePath());
            getUserProfilePicMutable.postValue(bitmap);
        }
    }

    public String retrieveJwtToken() {
        return sharedPreferences.getString(SharedPreferenceDetails.JWT_TOKEN,"");
    }

    public String retrieveUserId() {
        return sharedPreferences.getString(SharedPreferenceDetails.USERID, "");
    }

    // update user Details in shared preference

    public void storeUserE164FormatPhoneNumberInPrefs(String phoneNumber, String e164PhoneNumber) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(SharedPreferenceDetails.USER_PHONE_NUMBER, phoneNumber);
        editor.putString(SharedPreferenceDetails.USER_PHONE_NUMBER_INTERNATIONAL_FORMAT, e164PhoneNumber);
        editor.apply();
    }

    public void storeUserRegistrationProgressInPrefs(int progress) {
        sharedPreferences.edit().putInt(SharedPreferenceDetails.REGISTRATION_PROGRESS, progress).apply();
    }

    public void storeUserAboutInPrefs(String userAbout) {
        sharedPreferences.edit().putString(SharedPreferenceDetails.USER_ABOUT, userAbout).apply();
        userAboutMutable.postValue(userAbout);
    }

    public void storeJWTTokenInPrefs(String jwtToken) {
        sharedPreferences.edit().putString(SharedPreferenceDetails.JWT_TOKEN, jwtToken).apply();
    }

    public void storeUserIdInPrefs(String userId) {
        sharedPreferences.edit().putString(SharedPreferenceDetails.USERID, userId).apply();
    }

    public void storeProfilePicId(String profilePicId) {
        sharedPreferences.edit().putString(SharedPreferenceDetails.PROFILE_PIC_ID, profilePicId).apply();
    }

    public void storeUserDetails(String username, long createdOn) {
        SharedPreferences.Editor    editor  =   sharedPreferences.edit();
        editor.putString(SharedPreferenceDetails.USERNAME, username);
        editor.putLong(SharedPreferenceDetails.ACCOUNT_CREATED_ON, createdOn);
        editor.apply();
    }

    public void markAccountAsAccessDenied() {
        sharedPreferences.edit().putInt(SharedPreferenceDetails.ACCOUNT_STATUS, AccountManager.ACCOUNT_RESTRICTED).apply();
    }

    public void updateUsernameInAPI(String updatedUserName) {
        String  userId      =   retrieveUserId();
        String  jwtToken    =   retrieveJwtToken();

        executorService.execute(() -> {
            try {
                JSONObject  jsonObject  =   new JSONObject();
                jsonObject.put("userId", userId);
                jsonObject.put("username", updatedUserName);
                String  updateResponse  =   profileUpdatesAPI.updateUsernameInServer(jsonObject, jwtToken);
                switch (updateResponse) {
                    case "success"              ->  {
                        sharedPreferences.edit().putString(SharedPreferenceDetails.USERNAME, updatedUserName).apply();
                        ProfileDataModel profileDataModel   =   profileDataMutable.getValue();
                        if (profileDataModel != null) {
                            profileDataMutable.postValue(
                                    new ProfileDataModel(
                                            profileDataModel.getUserId(), updatedUserName,
                                            profileDataModel.getUserAbout(), profileDataModel.getUserPhoneNumber(),
                                            profileDataModel.getUserJoinedDate(), profileDataModel.getUserProfilePicture()));
                        }
                        updateStatusMutable.postValue(true);
                    }
                    case "failed"               ->  updateStatusMutable.postValue(false);
                    case "Token has expired"    -> {
                        try {
                            JSONObject tokenRefreshObject   =   new JSONObject();
                            tokenRefreshObject.put("userId", userId);
                            tokenRefreshAPI.refreshUserJwtToken(tokenRefreshObject, (isSuccess, newJwtToken) -> {
                                if (isSuccess) {
                                    updateUsernameInAPI(updatedUserName);
                                }
                                else {
                                    Log.e(Extras.LOG_MESSAGE, "Unable to refresh token");
                                    updateStatusMutable.postValue(false);
                                }
                            });
                        } catch (JSONException e) {
                            Log.e(Extras.LOG_MESSAGE, "JSON exception when refreshing token " + e.getMessage());
                        }
                    }
                }
            } catch (JSONException e) {
                Log.e(Extras.LOG_MESSAGE,"Unable to update username caught JSON exception " + e.getMessage());
            }
        });
    }

    public void uploadProfilePicture(String profilePic) {
        String userId       =   retrieveUserId();
        String  jwtToken    =   retrieveJwtToken();

        executorService.execute(() -> {
            try {
                JSONObject  jsonObject  =   new JSONObject();
                jsonObject.put("userId", userId);
                jsonObject.put("profilePicture", profilePic);
                String profilePicResponse   =   profileUpdatesAPI.uploadProfilePicture(jsonObject, jwtToken);
                switch (profilePicResponse) {
                    case "success"              ->  {
                        saveProfilePictureLocally(profilePic);
                        updateStatusMutable.postValue(true);
                    }
                    case "failed"               ->  updateStatusMutable.postValue(false);
                    case "Token has expired"    -> {
                        try {
                            JSONObject tokenRefreshObject   =   new JSONObject();
                            tokenRefreshObject.put("userId", userId);
                            tokenRefreshAPI.refreshUserJwtToken(tokenRefreshObject, (isSuccess, newJwtToken) -> {
                                if (isSuccess) {
                                    uploadProfilePicture(profilePic);
                                }
                                else {
                                    Log.e(Extras.LOG_MESSAGE, "Unable to refresh token");
                                    updateStatusMutable.postValue(false);
                                }
                            });
                    } catch (JSONException e) {
                        Log.e(Extras.LOG_MESSAGE, "JSON exception when refreshing token " + e.getMessage());
                        updateStatusMutable.postValue(false);
                        }
                    }
                }

            } catch (Exception e) {
                Log.e(Extras.LOG_MESSAGE,"Caught exception when uploading profile picture in repo " + e.getMessage());
                updateStatusMutable.postValue(false);
            }
        });
    }

    private void saveProfilePictureLocally(String base64Image) {
        try {
            byte[] decodedBytes = Base64.decode(base64Image, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);

            File file = new File(context.getFilesDir(), "profilePic.jpg");
            try (FileOutputStream out = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            }
            Log.d("ProfileRepo", "Profile picture saved permanently.");
            ProfileDataModel profileDataModel   =   profileDataMutable.getValue();
            if (profileDataModel != null) {
                profileDataMutable.postValue(
                        new ProfileDataModel(
                                profileDataModel.getUserId(), profileDataModel.getUserName(),
                                profileDataModel.getUserAbout(), profileDataModel.getUserPhoneNumber(),
                                profileDataModel.getUserJoinedDate(), bitmap));
            }
        } catch (Exception e) {
            Log.e("ProfileRepo", "Failed to save profile picture locally: " + e.getMessage());
        }
    }

}
