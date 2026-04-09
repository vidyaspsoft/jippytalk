package com.jippytalk.FirebasePushNotifications;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.jippytalk.Extras;
import com.jippytalk.R;

/**
 * ArchivedChatNotifications - Handles notifications for messages arriving in
 * archived chats and message request conversations.
 *
 * These notifications are displayed when:
 * - A message arrives in an archived chat (not currently visible)
 * - A message arrives from an unknown/non-contact user (message request)
 *
 * Uses separate notification IDs to distinguish between archived chat
 * and message request notifications.
 *
 * Instantiated with no-arg constructor in HandleInsertionsFromService.
 */
public class ArchivedChatNotifications {

    private static final int    ARCHIVED_NOTIFICATION_ID    =   2001;
    private static final int    MSG_REQUEST_NOTIFICATION_ID =   2002;

    // -------------------- Archived Chat Notification Starts Here ---------------------

    /**
     * Shows a notification indicating that a new message has arrived in an archived chat.
     * This notification is shown when the archive chats screen is not visible.
     *
     * @param context application context for creating notifications
     */
    public void showArchivedChatsNotification(Context context) {
        try {
            createNotificationChannel(context);

            NotificationManager notificationManager =   (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager == null) {
                Log.e(Extras.LOG_MESSAGE, "NotificationManager is null for archived notification");
                return;
            }

            NotificationCompat.Builder builder  =   new NotificationCompat.Builder(
                    context, Extras.NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Archived Chat")
                    .setContentText("You have a new message in archived chats")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE);

            notificationManager.notify(ARCHIVED_NOTIFICATION_ID, builder.build());

            Log.e(Extras.LOG_MESSAGE, "Archived chat notification shown");
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to show archived chat notification " + e.getMessage());
        }
    }

    // -------------------- Message Request Notification Starts Here ---------------------

    /**
     * Shows a notification indicating that a new message request has arrived
     * from an unknown or non-contact user.
     *
     * @param context application context for creating notifications
     */
    public void showMessageRequestsNotification(Context context) {
        try {
            createNotificationChannel(context);

            NotificationManager notificationManager =   (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);

            if (notificationManager == null) {
                Log.e(Extras.LOG_MESSAGE, "NotificationManager is null for message request notification");
                return;
            }

            NotificationCompat.Builder builder  =   new NotificationCompat.Builder(
                    context, Extras.NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("Message Request")
                    .setContentText("You have a new message request")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE);

            notificationManager.notify(MSG_REQUEST_NOTIFICATION_ID, builder.build());

            Log.e(Extras.LOG_MESSAGE, "Message request notification shown");
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to show message request notification " + e.getMessage());
        }
    }

    // -------------------- Notification Channel Methods Starts Here ---------------------

    /**
     * Creates the notification channel if it doesn't exist.
     * Required for Android O (API 26) and above.
     *
     * @param context application context
     */
    private void createNotificationChannel(Context context) {
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
