package com.jippytalk.Messages.Api;

/**
 * Developer Name: Vidya Sagar
 * Created on: 10-04-2026
 */

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.jippytalk.API;
import com.jippytalk.Common.ApiLogger;
import com.jippytalk.Extras;
import com.jippytalk.Managers.SharedPreferenceDetails;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * MessagesApi - REST client for room and message endpoints.
 *
 * Wraps:
 *   - GET /api/rooms                        → list of rooms with unread counts + last message
 *   - GET /api/messages/{roomId}?limit&cursor → paginated message history
 *   - GET /api/messages/unread              → unread counts per room
 *
 * All network calls run on a background executor and deliver callbacks on the main thread.
 * All calls are logged via ApiLogger.
 *
 * Usage:
 *   MessagesApi.getInstance(context).fetchRooms(callback);
 *   MessagesApi.getInstance(context).fetchMessages(roomId, cursor, callback);
 */
public class MessagesApi {

    // ---- Constants ----

    private static final int    CONNECT_TIMEOUT     =   15000;
    private static final int    READ_TIMEOUT        =   30000;
    private static final int    DEFAULT_PAGE_SIZE   =   50;

    // ---- Fields ----

    private static volatile MessagesApi     INSTANCE;
    private final SharedPreferences         sharedPreferences;
    private final ExecutorService           executorService     =   Executors.newFixedThreadPool(2);
    private final Handler                   mainHandler         =   new Handler(Looper.getMainLooper());

    // ---- Constructor ----

    private MessagesApi(Context context) {
        this.sharedPreferences  =   context.getApplicationContext().getSharedPreferences(
                SharedPreferenceDetails.SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    public static MessagesApi getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (MessagesApi.class) {
                if (INSTANCE == null) {
                    INSTANCE    =   new MessagesApi(context);
                }
            }
        }
        return INSTANCE;
    }

    // -------------------- Public API Methods Starts Here ---------------------

    /**
     * Fetches all rooms for the authenticated user, including unread counts and last message.
     * GET /api/rooms
     *
     * @param callback delivered on main thread
     */
    public void fetchRooms(RoomsCallback callback) {
        executorService.execute(() -> {
            List<Room>  rooms   =   executeFetchRooms();
            mainHandler.post(() -> {
                if (rooms != null) {
                    callback.onSuccess(rooms);
                } else {
                    callback.onFailure("Failed to fetch rooms");
                }
            });
        });
    }

    /**
     * Fetches a page of messages for the given room.
     * GET /api/messages/{roomId}?limit=50&cursor=
     *
     * @param roomId    the room identifier
     * @param cursor    pagination cursor (empty for first page)
     * @param callback  delivered on main thread
     */
    public void fetchMessages(String roomId, String cursor, MessagesCallback callback) {
        executorService.execute(() -> {
            MessagesPage    page    =   executeFetchMessages(roomId, cursor);
            mainHandler.post(() -> {
                if (page != null) {
                    callback.onSuccess(page);
                } else {
                    callback.onFailure("Failed to fetch messages");
                }
            });
        });
    }

    /**
     * Fetches unread message counts per room.
     * GET /api/messages/unread
     *
     * @param callback delivered on main thread
     */
    public void fetchUnreadCounts(UnreadCountsCallback callback) {
        executorService.execute(() -> {
            JSONObject counts   =   executeFetchUnreadCounts();
            mainHandler.post(() -> {
                if (counts != null) {
                    callback.onSuccess(counts);
                } else {
                    callback.onFailure("Failed to fetch unread counts");
                }
            });
        });
    }

    // -------------------- Network Execution (background thread) ---------------------

    private List<Room> executeFetchRooms() {
        HttpURLConnection   connection  =   null;
        String              jwtToken    =   sharedPreferences.getString(SharedPreferenceDetails.JWT_TOKEN, "");
        long                startTime   =   ApiLogger.logRequest("GET", API.GET_ROOMS, null, jwtToken);

        try {
            URL url     =   new URL(API.GET_ROOMS);
            connection  =   (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.connect();

            int statusCode  =   connection.getResponseCode();
            String body     =   readBody(connection, statusCode);
            ApiLogger.logResponse("GET", API.GET_ROOMS, statusCode, body, startTime);

            if (statusCode >= 200 && statusCode < 300) {
                return parseRoomsResponse(body);
            }
            return null;

        } catch (Exception e) {
            ApiLogger.logError("GET", API.GET_ROOMS, e.getMessage(), startTime);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private MessagesPage executeFetchMessages(String roomId, String cursor) {
        HttpURLConnection   connection  =   null;
        String              jwtToken    =   sharedPreferences.getString(SharedPreferenceDetails.JWT_TOKEN, "");
        String              cursorParam =   cursor != null ? cursor : "";
        String              fullUrl     =   API.GET_MESSAGES + roomId + "?limit=" + DEFAULT_PAGE_SIZE
                                            + "&cursor=" + cursorParam;
        long                startTime   =   ApiLogger.logRequest("GET", fullUrl, null, jwtToken);

        try {
            URL url     =   new URL(fullUrl);
            connection  =   (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.connect();

            int statusCode  =   connection.getResponseCode();
            String body     =   readBody(connection, statusCode);
            ApiLogger.logResponse("GET", fullUrl, statusCode, body, startTime);

            if (statusCode >= 200 && statusCode < 300) {
                return parseMessagesResponse(body);
            }
            return null;

        } catch (Exception e) {
            ApiLogger.logError("GET", fullUrl, e.getMessage(), startTime);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private JSONObject executeFetchUnreadCounts() {
        HttpURLConnection   connection  =   null;
        String              jwtToken    =   sharedPreferences.getString(SharedPreferenceDetails.JWT_TOKEN, "");
        long                startTime   =   ApiLogger.logRequest("GET", API.GET_UNREAD_COUNTS, null, jwtToken);

        try {
            URL url     =   new URL(API.GET_UNREAD_COUNTS);
            connection  =   (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Bearer " + jwtToken);
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.connect();

            int statusCode  =   connection.getResponseCode();
            String body     =   readBody(connection, statusCode);
            ApiLogger.logResponse("GET", API.GET_UNREAD_COUNTS, statusCode, body, startTime);

            if (statusCode >= 200 && statusCode < 300) {
                return new JSONObject(body);
            }
            return null;

        } catch (Exception e) {
            ApiLogger.logError("GET", API.GET_UNREAD_COUNTS, e.getMessage(), startTime);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    // -------------------- Response Parsing ---------------------

    private List<Room> parseRoomsResponse(String body) {
        try {
            JSONObject  root            =   new JSONObject(body);
            JSONArray   roomsArray      =   root.optJSONArray("rooms");
            List<Room>  rooms           =   new ArrayList<>();

            if (roomsArray != null) {
                for (int i = 0; i < roomsArray.length(); i++) {
                    JSONObject  obj     =   roomsArray.getJSONObject(i);
                    Room room = new Room();
                    room.id             =   obj.optString("id", "");
                    room.user1Id        =   obj.optString("user1_id", "");
                    room.user2Id        =   obj.optString("user2_id", "");
                    room.createdAt      =   obj.optString("created_at", "");
                    room.unreadCount    =   obj.optInt("unread_count", 0);

                    JSONObject lastMsgObj = obj.optJSONObject("last_message");
                    if (lastMsgObj != null) {
                        room.lastMessageId          =   lastMsgObj.optString("id", "");
                        room.lastMessageCiphertext  =   lastMsgObj.optString("ciphertext", "");
                        room.lastMessageType        =   lastMsgObj.optString("message_type", "text");
                        room.lastMessageCreatedAt   =   lastMsgObj.optString("created_at", "");
                    }
                    rooms.add(room);
                }
            }
            return rooms;
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to parse rooms response: " + e.getMessage());
            return null;
        }
    }

    private MessagesPage parseMessagesResponse(String body) {
        try {
            JSONObject      root            =   new JSONObject(body);
            JSONArray       msgArray        =   root.optJSONArray("messages");
            MessagesPage    page            =   new MessagesPage();
            page.nextCursor                 =   root.optString("next_cursor", "");
            page.messages                   =   new ArrayList<>();

            if (msgArray != null) {
                for (int i = 0; i < msgArray.length(); i++) {
                    JSONObject      obj     =   msgArray.getJSONObject(i);
                    ServerMessage   msg     =   new ServerMessage();
                    msg.id                  =   obj.optString("id", "");
                    msg.roomId              =   obj.optString("room_id", "");
                    msg.senderId            =   obj.optString("sender_id", "");
                    msg.receiverId          =   obj.optString("receiver_id", "");
                    msg.clientMessageId     =   obj.optString("client_message_id", "");
                    msg.ciphertext          =   obj.optString("ciphertext", "");
                    msg.messageType         =   obj.optString("message_type", "text");
                    msg.encryptionKey       =   obj.optString("encryption_key", "");
                    msg.encryptionIv        =   obj.optString("encryption_iv", "");
                    msg.delivered           =   obj.optBoolean("delivered", false);
                    msg.readAt              =   obj.optString("read_at", "");
                    msg.createdAt           =   obj.optString("created_at", "");
                    // File fields (only present for message_type=file)
                    // file_name is AES-GCM ciphertext (base64url IV || ct) of
                    // the original name, using this message's encryption_key.
                    // Decrypt here so downstream consumers see plaintext.
                    // Falls back to raw value if decryption fails (legacy).
                    String rawName          =   obj.optString("file_name", "");
                    if (!rawName.isEmpty() && msg.encryptionKey != null
                            && !msg.encryptionKey.isEmpty()) {
                        String dec = com.jippytalk.Encryption.MessageCryptoHelper
                                .decryptFilenameFromS3Name(rawName, msg.encryptionKey);
                        msg.fileName        =   (dec != null && !dec.isEmpty()) ? dec : rawName;
                    } else {
                        msg.fileName        =   rawName;
                    }
                    msg.s3Key               =   obj.optString("s3_key", "");
                    msg.contentType         =   obj.optString("content_type", "");
                    msg.contentSubtype      =   obj.optString("content_subtype", "");
                    msg.fileTransferId      =   obj.optString("file_transfer_id", "");
                    msg.encryptedS3Url      =   obj.optString("encrypted_s3_url", "");
                    msg.thumbnail           =   obj.optString("thumbnail", "");
                    msg.fileSize            =   obj.optLong("file_size", 0);
                    msg.caption             =   obj.optString("caption", "");
                    msg.width               =   obj.optInt("width", 0);
                    msg.height              =   obj.optInt("height", 0);
                    msg.duration            =   obj.optLong("duration", 0);
                    page.messages.add(msg);
                }
            }
            return page;
        } catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Failed to parse messages response: " + e.getMessage());
            return null;
        }
    }

    // -------------------- Body Reader ---------------------

    private String readBody(HttpURLConnection connection, int statusCode) throws Exception {
        java.io.InputStream stream = statusCode >= 200 && statusCode < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    // -------------------- Response Models ---------------------

    /**
     * Room - represents a single chat room between two users.
     */
    public static class Room {
        public String   id;
        public String   user1Id;
        public String   user2Id;
        public String   createdAt;
        public int      unreadCount;
        public String   lastMessageId;
        public String   lastMessageCiphertext;
        public String   lastMessageType;
        public String   lastMessageCreatedAt;

        /**
         * Returns the "other" user in this room given the current user's ID.
         */
        public String getOtherUserId(String currentUserId) {
            return user1Id.equals(currentUserId) ? user2Id : user1Id;
        }
    }

    /**
     * ServerMessage - a message as returned by /api/messages/{roomId}.
     */
    public static class ServerMessage {
        public String   id;
        public String   clientMessageId;
        public String   roomId;
        public String   senderId;
        public String   receiverId;
        public String   ciphertext;
        public String   messageType;
        public String   encryptionKey;
        public String   encryptionIv;
        public boolean  delivered;
        public String   readAt;
        public String   createdAt;
        // File fields
        public String   fileName;
        public String   s3Key;
        public String   contentType;
        public String   contentSubtype;
        public String   fileTransferId;
        public String   encryptedS3Url;
        public String   thumbnail;
        public long     fileSize;
        public String   caption;
        public int      width;
        public int      height;
        public long     duration;
    }

    /**
     * MessagesPage - a page of messages with pagination cursor.
     */
    public static class MessagesPage {
        public List<ServerMessage>  messages;
        public String               nextCursor;
    }

    // -------------------- Callback Interfaces ---------------------

    public interface RoomsCallback {
        void onSuccess(List<Room> rooms);
        void onFailure(String error);
    }

    public interface MessagesCallback {
        void onSuccess(MessagesPage page);
        void onFailure(String error);
    }

    public interface UnreadCountsCallback {
        void onSuccess(JSONObject counts);
        void onFailure(String error);
    }
}
