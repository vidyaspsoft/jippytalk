package com.jippytalk;

public class Extras {

    // Log tag
    public static final String LOG_MESSAGE                              = "JippyTalk";
    public static final String LOG_ERROR                                = "JippyTalkError";

    // Intent extras
    public static final String CHAT_ID                                  = "chatId";
    public static final String MESSAGE_ID                               = "messageId";
    public static final String CONTACT_ID                               = "contactId";
    public static final String CONTACT_NAME                             = "contactName";
    public static final String CONTACT_PHONE_NUMBER                     = "contactPhoneNumber";

    // Message status
    public static final int MESSAGE_SYNCED_WITH_SERVER                  = 1;
    public static final int DELETE_MESSAGE_FOR_EVERYONE                  = 1;

    // WebSocket actions
    public static final String ACTION_SEND_MESSAGE                      = "sendMessage";
    public static final String ACTION_TYPING                            = "typing";
    public static final String ACTION_ONLINE_STATUS                     = "onlineStatus";

    // Chat status
    public static final int UNARCHIVE_CHAT                              = 0;

    // Notification
    public static final String NOTIFICATION_CHANNEL_ID                  = "JippyTalkMessages";
    public static final String NOTIFICATION_CHANNEL_NAME                = "Messages";
}
