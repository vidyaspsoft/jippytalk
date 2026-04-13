package com.jippytalk.Managers;

public class MessagesManager {

    public static final int     NEED_MESSAGE_PUSH                           =   1;
    public static final int     NO_NEED_TO_PUSH_MESSAGE                     =   0;
    public static final int     MESSAGE_SYNCED_WITH_SERVER                  =   1;
    public static final int     MESSAGE_NOT_SYNCED_WITH_SERVER              =   0;
    public static final int     MESSAGE_SEEN                                =   2;
    public static final int     MESSAGE_SEEN_LOCALLY                        =   12;
    public static final int     MESSAGE_DELIVERED                           =   3;
    public static final int     MESSAGE_DELIVERED_LOCALLY                   =   13;
    public static final int     DELETE_MESSAGE_FOR_EVERYONE                 =   4;
    public static final int     UNKNOWN_NUMBER_MESSAGE                      =   5;
    public static final int     MESSAGE_SEND_FAILED                         =   6;
    public static final int     MESSAGE_RECEIVED_AND_NO_READ_RECEIPTS       =   7;
    public static final int     MESSAGE_SEEN_AND_NO_READ_RECEIPTS           =   8;


    public static final int     MAX_DECRYPTION_TRIES                        =   3;
    public static final int     INVALID_MESSAGE_DECRYPTION                  =   10;


    public static final int     MESSAGE_NOT_EDITED                          =   0;
    public static final int     MESSAGE_EDITED                              =   1;

    public static final int     MESSAGE_NOT_REPLIED                         =   0;
    public static final int     MESSAGE_IS_REPLIED                          =   1;


    public static final int     MESSAGE_NOT_STARRED                         =   0;
    public static final int     MESSAGE_IS_STARRED                          =   1;

    public static final int     TEXT_MESSAGE                                =   0;
    public static final int     LINK_MESSAGE                                =   1;
    public static final int     LOCATION_MESSAGE                            =   2;
    public static final int     IMAGE_MESSAGE                               =   3;
    public static final int     VIDEO_MESSAGE                               =   4;
    public static final int     SYSTEM_MESSAGE_TYPE                         =   5;
    public static final int     AUDIO_MESSAGE                               =   6;
    public static final int     DOCUMENT_MESSAGE                            =   7;
    public static final int     CONTACT_MESSAGE                             =   8;

    public static final int     MESSAGE_OUTGOING                            =   0;
    public static final int     MESSAGE_INCOMING                            =   1;
    public static final int     SYSTEM_GIVEN_MESSAGE                        =   2;


    public static final int     SENT_MESSAGE                                =   0;
    public static final int     RECEIVED_MESSAGE                            =   1;
    public static final int     SENT_LOCATION                               =   2;
    public static final int     RECEIVED_LOCATION                           =   3;
    public static final int     REPLIED_SENT_MESSAGE                        =   11;
    public static final int     REPLIED_RECEIVED_MESSAGE                    =   12;
    public static final int     SYSTEM_MESSAGE_VIEW                         =   5;

    // Media View Types (for adapter)
    public static final int     SENT_MEDIA                                  =   20;
    public static final int     RECEIVED_MEDIA                              =   21;
    public static final int     SENT_VIDEO                                  =   22;
    public static final int     RECEIVED_VIDEO                              =   23;
    public static final int     SENT_AUDIO                                  =   24;
    public static final int     RECEIVED_AUDIO                              =   25;


    public static final int     DELETE_FOR_ME                               =   0;
    public static final int     DELETE_FOR_EVERYONE                         =   1;

    public static final int     EVERYONE_CAN_CONTACT_ME                     =   0;
    public static final int     ONLY_MY_CONTACTS                            =   1;

    public static final long    DEFAULT_LATITUDE                            =   0;
    public static final long    DEFAULT_LONGITUDE                           =   0;
    public static final long    DEFAULT_DELIVERED_TIMESTAMP                 =   0;
    public static final long    DEFAULT_READ_TIMESTAMP                      =   0;

    public static final int     DEFAULT_MSG_TO_MSG_REPLY                    =   0;
    public static final int     REPLIED_TO_A_MSG                            =   1;

    public static final int     MESSAGE_DELETED_SUCCESSFULLY                =   0;
    public static final int     MESSAGE_DELETION_FAILED                     =   1;



    public static final String  CONTACT_KEYS_CHANGED    =   "Contacts keys has been changed recently kindly " +
                                                            "confirm with you contact about " + "his reinstall";


}
