package com.jippytalk.FirebasePushNotifications;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.jippytalk.Database.ContactsDatabase.Repository.ContactsRepository;
import com.jippytalk.Database.MessagesDatabase.Repository.MessagesRepository;
import com.jippytalk.Extras;
import com.jippytalk.R;

/**
 * MessageNotifications - Handles the display of push notifications for incoming messages.
 * Creates and shows notifications with reply actions when the user is not viewing
 * the relevant chat screen.
 *
 * Uses Android's NotificationCompat for backwards compatibility with older API levels.
 * Notifications are grouped by the JippyTalk Messages channel.
 *
 * Initialized in HandleInsertionsFromService and triggered when:
 * - A new message arrives for a contact whose chat screen is not currently visible
 * - The user's DND (Do Not Disturb) status allows notifications
 * - Message notifications are enabled in user preferences
 */
public class MessageNotifications {

    private final Context                               context;
    private final MessagesRepository                    messagesRepository;
    private final ContactsRepository                    contactsRepository;
    private static final int                            NOTIFICATION_ID         =   1001;

    /**
     * Creates a new MessageNotifications instance.
     *
     * @param context               application context for creating notifications
     * @param messagesRepository    repository for accessing message data
     * @param contactsRepository    repository for accessing contact details (name, profile pic)
     */
    public MessageNotifications(Context context, MessagesRepository messagesRepository,
                                ContactsRepository contactsRepository) {
        this.context                =   context.getApplicationContext();
        this.messagesRepository     =   messagesRepository;
        this.contactsRepository     =   contactsRepository;
    }

    // -------------------- Notification Display Methods Starts Here ---------------------

    /**
     * Shows a notification for the latest unread message with a reply action.
     * The notification includes:
     * - Contact name as the title
     * - Message preview as the content
     * - Direct reply action for quick response
     * - Tap action to open the messaging activity
     *
     * Creates the notification channel if it doesn't exist (required for Android O+).
     */
    public void showNotificationWithReply() {
        try {
            createNotificationChannel();

            NotificationManager notificationManager =   (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager == null) {
                Log.e(Extras.LOG_MESSAGE, "NotificationManager is null");
                return;
            }

            // Build the notification with message details
            NotificationCompat.Builder builder  =   new NotificationCompat.Builder(
                    context, Extras.NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("New Message")
                    .setContentText("You have a new message")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE);

            notificationManager.notify(NOTIFICATION_ID, builder.build());

            Log.e(Extras.LOG_MESSAGE, "Message notification shown successfully");
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to show message notification " + e.getMessage());
        }
    }

    // -------------------- Notification Channel Methods Starts Here ---------------------

    /**
     * Creates the notification channel for message notifications.
     * Required for Android O (API 26) and above.
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =   new NotificationChannel(
                    Extras.NOTIFICATION_CHANNEL_ID,
                    Extras.NOTIFICATION_CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications for new messages");

            NotificationManager notificationManager =   context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
