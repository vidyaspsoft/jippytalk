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
