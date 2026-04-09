package com.jippytalk.Managers;

public class SharedPreferenceDetails {

    public static final String  SHARED_PREFERENCE_NAME                      =   "MyUser";
    public static final String  USERID                                      =   "userId";
    public static final String  JWT_TOKEN                                   =   "jwtToken";
    public static final String  DATABASE_PASSWORD                           =   "databasePassword";

    // to store and check user login and account setup

    public static final String  USER_LOGIN                                  =   "isLogged";
    public static final String  ACCOUNT_SETUP                               =   "accountSetup";
    public static final String  REGISTRATION_PROGRESS                       =   "registrationProgress";
    public static final String  SIGNAL_KEYS_UPLOAD                          =   "signalKeysUpload";
    public static final String  ONE_TIME_PRE_KEYS_UPLOAD                    =   "oneTimePreKeysUpload";
    public static final String  KYBER_PRE_KEYS_UPLOAD                       =   "kyberPreKeysUpload";

    public static final String  REGISTRATION_ID                             =   "registrationId";
    public static final String  DEVICE_ID                                   =   "deviceId";

    // to store and retrieve user details

    public static final String  USERNAME                                    =   "userName";
    public static final String  USER_PHONE_NUMBER                           =   "phoneNumber";
    public static final String  USER_PHONE_NUMBER_INTERNATIONAL_FORMAT      =   "phoneNumberInternationalFormat";
    public static final String  USER_ABOUT                                  =   "about";
    public static final String  PROFILE_PIC_ID                              =   "profilePicId";
    public static final String  ACCOUNT_CREATED_ON                          =   "accountCreatedOn";
    public static final String  ACCOUNT_STATUS                              =   "accountStatus";

    public static final String  APP_THEME                                   =   "appTheme";



    // to display or hide any messages to the user in the activity

    public static final String  ARCHIVE_CHATS_MESSAGE                       =   "archiveChatsMessage";
    public static final String  NORMAL_CHATS_MESSAGE                        =   "normalChatsMessage";

    public static final String  HIDE_ARCHIVE_CHATS_PASSWORD                 =   "hideArchiveChatsPassword";
    public static final String  HIDE_ARCHIVE_CHATS_SWITCH                   =   "hideArchiveChatsSwitch";

    public static final String  CONTACTS_MESSAGE                            =   "contactsMessage";




    // to store and retrieve user status option and if busy how much time


    public static final String  USER_STATUS_OPTION                          =   "userStatus";
    public static final String  USER_BUSY_TIME_OPTION                       =   "userBusyTime";

    //  for chat lock switch and question and answer when turning the chat lock on

    public static final String  LOCK_CHAT_SWITCH                            =   "lockChatSwitch";
    public static final String  CHAT_LOCK_QUESTION                          =   "chatLockQuestion";
    public static final String  CHAT_UNLOCK_ANSWER                          =   "chatUnLockAnswer";
    public static final String  CHAT_LOCK_PASSWORD                          =   "chatLockPassword";


    // to check the options when chat lock password passed the max attempts


    public static final String  OPTIONS_WHEN_CHAT_LOCKED                    =   "chatLockedOption";
    public static final String  CHAT_LOCKED_TIME_OPTION                     =   "chatLockedTimeOption";

    public static final String  HIDE_LAST_MSG_SWITCH                        =   "hideLastMsgSwitch";

    public static final String  HIDE_MAIN_CHATS_LAST_MSG_SWITCH             =   "hideMainChatsLastMsgSwitch";


    public static final String  ARCHIVE_CHATS_LOCK_SWITCH                   =   "archiveChatsLockSwitch";
    public static final String  ARCHIVE_CHATS_UNLOCK_PASSWORD               =   "archiveChatsUnlockPassword";

    public static final String  MESSAGES_NOTIFICATIONS_SWITCH               =   "messagesNotificationSwitch";
    public static final String  MESSAGES_RQST_NOTIFICATIONS_SWITCH          =   "messagesNotificationSwitch";

    public static final String  MESSAGES_READ_RECEIPTS                      =   "messageReadReceipts";
    public static final String  LAST_VISIT_OPTION                           =   "lastVisitOption";
    public static final String  PROFILE_PIC_PRIVACY_OPTION                  =   "profilePicPrivacyOption";
    public static final String  ABOUT_PRIVACY_OPTION                        =   "aboutPrivacyOption";
    public static final String  CONTACT_ME_PRIVACY_OPTION                   =   "contactMePrivacyOption";


    public static final String  LATEST_ONLINE_TIMESTAMP                     =   "latestOnlineTimeStamp";
    public static final String  LAST_ONLINE_TIMESTAMP                       =   "lastOnlineTimestamp";
    public static final String  LAST_OFFLINE_TIMESTAMP                      =   "lastOfflineTimestamp";

    public static final String  MESSAGE_THEME_BACKGROUND                    =   "messageThemeBackground";


    public static final String  ARCHIVE_CHAT_SETTINGS_TUTORIAL              =   "archiveChatSettingsTutorial";

    // Firebase Cloud Messaging
    public static final String  FCM_TOKEN                                   =   "fcmToken";
    public static final String  FCM_TOKEN_UPLOADED                          =   "fcmTokenUploaded";

}
