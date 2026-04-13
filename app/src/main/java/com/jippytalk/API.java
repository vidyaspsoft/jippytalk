package com.jippytalk;

public class API {

    // ---- Server Configuration ----

    public static final String  SERVER_HOST         =   "103.194.228.68";
    public static final int     SERVER_PORT         =   8080;
    public static final String  BASE_URL            =   "http://" + SERVER_HOST + ":" + SERVER_PORT + "/";
    public static final String  WS_BASE_URL         =   "ws://" + SERVER_HOST + ":" + SERVER_PORT + "/";

    // ---- S3 Configuration (for reference only) ----
    // NOTE: AWS credentials are NOT stored in the app. The backend uses them to generate
    // presigned URLs that the app uploads to directly. The bucket/region are kept here
    // as defaults in case the presign API response doesn't include them.

    public static final String  S3_BUCKET           =   "bank-ster-dev";
    public static final String  S3_REGION           =   "ap-south-1";

    // ---- Auth ----

    public static final String  LOGIN_URL           =   BASE_URL + "api/auth/login";
    public static final String  REGISTER_URL        =   BASE_URL + "api/auth/register";

    // ---- Keys ----

    public static final String  KEYS_UPLOAD         =   BASE_URL + "api/keys/upload";
    public static final String  KEYS_BUNDLE         =   BASE_URL + "api/keys/bundle/";
    public static final String  KEYS_COUNT          =   BASE_URL + "api/keys/count";

    // ---- Files ----

    public static final String  FILES_PRESIGN       =   BASE_URL + "api/files/presign";
    public static final String  FILES_DOWNLOADED    =   BASE_URL + "api/files/";        // append {fileTransferId}/downloaded

    // ---- Rooms & Messages ----

    public static final String  GET_ROOMS           =   BASE_URL + "api/rooms";
    public static final String  GET_MESSAGES        =   BASE_URL + "api/messages/";     // append {roomId}?limit=&cursor=
    public static final String  GET_UNREAD_COUNTS   =   BASE_URL + "api/messages/unread";

    // ---- WebSocket ----

    public static final String  WS_URL              =   WS_BASE_URL + "ws";

    // ---- Legacy Endpoints (kept for reference) ----

    public static final String  GET_CONTACT_PROFILE_PIC     =   BASE_URL + "api/profilepic/";
    public static final String  GET_USER_STATUS             =   BASE_URL + "api/userstatus/";
    public static final String  GET_ONE_TIME_PRE_KEY        =   BASE_URL + "api/onetimeprekey/";
    public static final String  REFRESH_JWT_TOKEN           =   BASE_URL + "api/refreshtoken/";
    public static final String  GET_LAST_VISIT_TIMESTAMP    =   BASE_URL + "api/lastvisit/";
    public static final String  REGISTER_USER               =   BASE_URL + "api/register/";
    public static final String  VERIFY_OTP                  =   BASE_URL + "api/verifyotp/";
    public static final String  CONTACT_ONE_TIME_PRE_KEY    =   BASE_URL + "api/contactonetimeprekey/";
    public static final String  JWT_TOKEN_REFRESH_URL       =   BASE_URL + "api/refreshtoken/";
}
