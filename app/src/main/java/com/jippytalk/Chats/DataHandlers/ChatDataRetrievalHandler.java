package com.jippytalk.Chats.DataHandlers;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.Context;
import android.util.Log;

import com.jippytalk.Extras;

public class ChatDataRetrievalHandler {

    // ---- Fields ----

    private final Context               context;
    private final ChatNameCallback      chatNameCallback;

    // ---- Constructor ----

    public ChatDataRetrievalHandler(Context context, ChatNameCallback chatNameCallback) {
        this.context            =   context;
        this.chatNameCallback   =   chatNameCallback;
    }

    // ---- Public Methods ----

    public void displayChatName(String contactId) {
        Log.e(Extras.LOG_MESSAGE, "displayChatName called for contactId: " + contactId);
        // TODO: Implement chat name retrieval from database
        if (chatNameCallback != null) {
            chatNameCallback.onChatNameRetrieved(contactId);
        }
    }

    // ---- Callback Interface ----

    public interface ChatNameCallback {
        void onChatNameRetrieved(String chatName);
    }
}
