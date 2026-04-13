package com.jippytalk.Messages.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.telephony.PhoneNumberUtils;
import android.util.Log;
import android.util.TypedValue;

import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.jippytalk.Database.ContactsDatabase.ContactsDatabase;
import com.jippytalk.Database.ContactsDatabase.DAO.ContactsDatabaseDAO;
import com.jippytalk.Extras;
import com.jippytalk.Managers.AccountManager;
import com.jippytalk.Managers.MessagesManager;
import com.jippytalk.Managers.SharedPreferenceDetails;
import com.jippytalk.Messages.Model.MessageModal;
import com.jippytalk.R;
import com.jippytalk.UserDetailsRepository;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MessageUtils {

    private final Context                   context;
    private SharedPreferences               sharedPreferences;
    private static String                   userName            =   "";
    private final static ExecutorService    executorService     =   Executors.newSingleThreadExecutor();


    public MessageUtils(Context context, SharedPreferences sharedPreferences) {
        this.context                =   context;
        this.sharedPreferences      =   sharedPreferences;
    }

    public static String getContactName(ContactsDatabaseDAO contactsDatabaseDAO, String chatId) {

        executorService.execute(() -> {
            try {
                boolean contactCheck = contactsDatabaseDAO.checkDuplicate(chatId);

                if (contactCheck) {

                    try (Cursor cursor = contactsDatabaseDAO.getContactNameFromDatabase(chatId)) {

                        if (cursor != null && cursor.moveToFirst()) {
                            userName = cursor.getString(cursor.getColumnIndexOrThrow(ContactsDatabase.CONTACT_NAME));
                        } else {
                            Log.e(Extras.LOG_ERROR, "unable to get name in main activity");
                        }
                    } catch (Exception e) {
                        Log.e(Extras.LOG_ERROR, "unable to get name in main activity " + e.getMessage());
                    }
                } else {
                    userName = PhoneNumberUtils.formatNumber(userName, Locale.getDefault().getCountry());
                }
            } catch (Exception e) {
                Log.e(Extras.LOG_ERROR, "unable to get name in main activity " + e.getMessage());
            }
        });
        return userName;
    }

    public static boolean isFirstMessageOfDay(List<MessageModal> messageModalArrayList, int position) {

        if (position > 0) {

            long        currentMessageTime      =   messageModalArrayList.get(position).getTimestamp();
            long        previousMessageTime     =   messageModalArrayList.get(position - 1).getTimestamp();
            Calendar    currentCalendar         =   Calendar.getInstance();
            Calendar    previousCalendar        =   Calendar.getInstance();

            currentCalendar.setTimeInMillis(currentMessageTime);
            previousCalendar.setTimeInMillis(previousMessageTime);

            return !isSameDay(currentCalendar, previousCalendar);
        }
        return true; // If it's the first message, consider it the first of the day
    }

    public static boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }

    public static String getDateLabel(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);

        if (isSameDay(calendar, Calendar.getInstance())) {
            return "Today";
        } else {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
            if (isSameDay(calendar, Calendar.getInstance())) {
                return "Yesterday";
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());
                return sdf.format(new Date(timestamp));
            }
        }
    }

    public static String getTime(long timestamp) {
        try {
            Date                date                =   new Date(timestamp);
            SimpleDateFormat    simpleDateFormat    =   new SimpleDateFormat("h:mm a",Locale.getDefault());
            return simpleDateFormat.format(date);
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE,"error in converting the time "+e.getMessage());
            return "date";
        }
    }

    /**
     * Formats a duration in milliseconds to a human-readable string.
     * Returns "M:SS" for durations under an hour, "H:MM:SS" for longer.
     *
     * @param durationMs the duration in milliseconds
     * @return formatted string like "1:30" or "1:02:30"
     */
    public static String formatDuration(long durationMs) {
        long    totalSeconds    =   durationMs / 1000;
        long    hours           =   totalSeconds / 3600;
        long    minutes         =   (totalSeconds % 3600) / 60;
        long    seconds         =   totalSeconds % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    /**
     * Returns a display label for the replied-to message based on its type.
     * For text/link messages, returns the message text itself.
     * For media messages, returns a type label like "Photo", "Video", etc.
     *
     * @param context       the context for string resources
     * @param messageType   the message type constant from MessagesManager
     * @param messageText   the original message text (used for text/link messages)
     * @return the display label for the reply card
     */
    public static String getReplyLabel(Context context, int messageType, String messageText) {
        switch (messageType) {
            case MessagesManager.IMAGE_MESSAGE      ->  { return context.getString(R.string.reply_photo); }
            case MessagesManager.VIDEO_MESSAGE      ->  { return context.getString(R.string.reply_video); }
            case MessagesManager.AUDIO_MESSAGE      ->  { return context.getString(R.string.reply_audio); }
            case MessagesManager.DOCUMENT_MESSAGE   ->  { return context.getString(R.string.reply_document); }
            case MessagesManager.CONTACT_MESSAGE    ->  { return context.getString(R.string.reply_contact); }
            case MessagesManager.LOCATION_MESSAGE   ->  { return context.getString(R.string.reply_location); }
            default                                 ->  {
                if (messageText == null || messageText.isEmpty()) {
                    return context.getString(R.string.message_deleted);
                }
                return messageText;
            }
        }
    }

    /**
     * Checks if the given message type is a media type that should show a thumbnail in reply cards.
     *
     * @param messageType the message type constant
     * @return true if the message type has a visual thumbnail (image or video)
     */
    public static boolean isMediaTypeWithThumbnail(int messageType) {
        return messageType == MessagesManager.IMAGE_MESSAGE
                || messageType == MessagesManager.VIDEO_MESSAGE;
    }

    public Drawable getSentMsgBackground(Context context, int messageTheme, CardView cardView) {
        TypedValue typedValue = new TypedValue();
        int attr;

        switch (messageTheme) {
            case 1 -> {
                attr = R.attr.sentMessageBackgroundColor_1;
                context.getTheme().resolveAttribute(attr, typedValue, true);
                cardView.setCardBackgroundColor(typedValue.data);
                return ContextCompat.getDrawable(context, R.drawable.sent_message_sea_blue);
            }
            case 2 -> {
                attr = R.attr.sentMessageBackgroundColor_2;
                context.getTheme().resolveAttribute(attr, typedValue, true);
                cardView.setCardBackgroundColor(typedValue.data);
                return ContextCompat.getDrawable(context, R.drawable.sent_message_pinky);
            }
            case 3 -> {
                attr = R.attr.sentMessageBackgroundColor_3;
                context.getTheme().resolveAttribute(attr, typedValue, true);
                cardView.setCardBackgroundColor(typedValue.data);
                return ContextCompat.getDrawable(context, R.drawable.sent_message_antartica);
            }
            default -> {
                return ContextCompat.getDrawable(context, R.drawable.sent_message_background);
            }
        }
    }

}
