package com.jippytalk.Messages.MessageToMessageReply;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import com.jippytalk.Database.ContactsDatabase.DAO.ContactsDatabaseDAO;
import com.jippytalk.Database.MessagesDatabase.MessagesDatabaseDAO;
import com.jippytalk.Database.MessagesDatabase.MessagesDatabase;
import com.jippytalk.Extras;
import com.jippytalk.Managers.MessagesManager;
import com.jippytalk.Messages.Utils.MessageUtils;
import com.jippytalk.R;

public class ReplyMessageUtils {

    public static MessageToMessageReplyModel getReplyMessageDetails(
            String messageId,
            MessagesDatabaseDAO messagesDatabaseDAO,
            ContactsDatabaseDAO contactsDatabaseDAO,
            Context context
    ) {
        String replyToMsgId = null;
        String receiverId = null;
        int messageDirection = -1;

        try (Cursor cursor = messagesDatabaseDAO.getMessageDetailsFromId(messageId)) {
            if (cursor != null && cursor.moveToFirst()) {
                replyToMsgId = cursor.getString(cursor.getColumnIndexOrThrow(MessagesDatabase.REPLY_TO_MESSAGE_ID));

                boolean messageCheck = messagesDatabaseDAO.checkMessageDuplicate(replyToMsgId);

                if (messageCheck && replyToMsgId != null && !replyToMsgId.isEmpty()) {
                    try (Cursor cursor1 = messagesDatabaseDAO.getMessageDetailsFromId(replyToMsgId)) {
                        if (cursor1 != null && cursor1.moveToFirst()) {
                            messageDirection    = cursor1.getInt(cursor1.getColumnIndexOrThrow(MessagesDatabase.MESSAGE_DIRECTION));
                            receiverId          = cursor1.getString(cursor1.getColumnIndexOrThrow(MessagesDatabase.RECEIVER_ID));
                            String message      = cursor1.getString(cursor1.getColumnIndexOrThrow(MessagesDatabase.MESSAGE));
                            String senderName   = messageDirection == MessagesManager.MESSAGE_INCOMING
                                    ? MessageUtils.getContactName(contactsDatabaseDAO, receiverId)
                                    : context.getString(R.string.you);

                            return new MessageToMessageReplyModel(replyToMsgId, message, senderName);
                        }
                    } catch (Exception e) {
                        Log.e(Extras.LOG_MESSAGE, "Failed to fetch reply message details: " + e.getMessage());
                    }
                } else {
                    return new MessageToMessageReplyModel(
                            "",
                            context.getString(R.string.this_message_has_been_deleted),
                            ""
                    );
                }
            }
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to fetch message: " + e.getMessage());
        }

        return new MessageToMessageReplyModel("", "", "");
    }
}
