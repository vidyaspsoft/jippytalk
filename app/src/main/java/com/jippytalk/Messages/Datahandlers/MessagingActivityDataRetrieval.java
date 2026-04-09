package com.jippytalk.Messages.Datahandlers;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.jippytalk.ServiceLocators.DatabaseServiceLocator;
import com.jippytalk.Database.MessagesDatabase.MessagesDatabaseDAO;
import com.jippytalk.Extras;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.MyApplication;
import com.jippytalk.databinding.ActivityMessagingBinding;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MessagingActivityDataRetrieval {

    private final Context                   context;
    private final MessagesDatabaseDAO       messagesDatabaseDAO;
    private final SharedPreferences         sharedPreferences;
    private final DatabaseServiceLocator databaseServiceLocator;
    private String                          contactName, contactProfilePic, contactPhoneNumber, contactPublicKey, userId;
    private int                             isContact;
    private final ExecutorService           executorService   = Executors.newSingleThreadExecutor();


    public MessagingActivityDataRetrieval(Context context) {
        this.context            =   context;
        databaseServiceLocator =   ((MyApplication) context.getApplicationContext()).getDatabaseServiceLocator();
        messagesDatabaseDAO     =   databaseServiceLocator.getMessagesDatabaseDAO();
        sharedPreferences       =   context.getSharedPreferences(SharedPreferenceDetails.SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE);
        userId                  =   sharedPreferences.getString(SharedPreferenceDetails.USERID, "");


    }

    public void getWallpaperFromInternalStorage(ActivityMessagingBinding activityMessagingBinding) {

        String          fileName        =   "chat_wallpaper.jpg";
        ContextWrapper  cw              =   new ContextWrapper(context);
        File            directory       =   cw.getDir("imageDir", Context.MODE_PRIVATE);
        File            imageFile       =   new File(directory, fileName);

        if (imageFile.exists()) {
            executorService.execute(() -> {
                try (FileInputStream fis = new FileInputStream(imageFile))
                {
                    Bitmap bitmap = BitmapFactory.decodeStream(fis);
                    if (bitmap != null) {
                        new Handler(Looper.getMainLooper()).post(() -> activityMessagingBinding.ivChatWallpaper.setImageBitmap(bitmap));
                    }
                }
                catch (Exception e) {
                    Log.e(Extras.LOG_MESSAGE, "Error retrieving chat wallpaper image: " + e.getMessage());
                }
            });
        }
        else {
            Log.e(Extras.LOG_MESSAGE, "no chat wallpaper file");
        }
    }
}
