# JippyTalk - Backend API Contract

> This document describes all data the Android client sends to and expects from the backend.
> Backend team: use this as the source of truth for API implementation.
>
> **Base URL:** `http://103.194.228.68:8080/`
> **WebSocket:** `ws://103.194.228.68:8080/ws?token=<jwt>`

---

## 1. REST API Endpoints

### 1.1 Authentication

#### POST /api/auth/login
No auth required.

**Request:**
```json
{
    "username": "alice",
    "password": "password123"
}
```

**Response (200):**
```json
{
    "token": "jwt_token_string",
    "user_id": "uuid_string"
}
```

---

#### POST /api/auth/register
No auth required.

**Request:**
```json
{
    "username": "newuser",
    "password": "password123"
}
```

**Response (201):**
```json
{
    "token": "jwt_token_string",
    "user_id": "uuid_string"
}
```

---

### 1.2 Encryption Keys (Bearer token required)

#### POST /api/keys/upload
Upload Signal Protocol keys after registration.

**Request:**
```json
{
    "identity_key": "base64_encoded_identity_public_key",
    "signed_prekey": {
        "key_id": 1,
        "public_key": "base64_encoded_signed_prekey",
        "signature": "base64_encoded_signature"
    },
    "one_time_prekeys": [
        { "key_id": 1, "public_key": "base64_encoded_otk_1" },
        { "key_id": 2, "public_key": "base64_encoded_otk_2" }
    ]
}
```

#### GET /api/keys/bundle/{targetUserId}
Get another user's key bundle for establishing an encrypted session.

**Response:**
```json
{
    "identity_key": "base64",
    "signed_prekey": { "key_id": 1, "public_key": "base64", "signature": "base64" },
    "one_time_prekey": { "key_id": 5, "public_key": "base64" }
}
```

#### GET /api/keys/count
Response: `{ "count": 42 }`

---

### 1.3 File Transfer (Bearer token required)

#### POST /api/files/presign

**Request:**
```json
{ "file_name": "IMG_20260409_143022.jpg", "file_size": 1048576 }
```

**Response:**
```json
{
    "presigned_url": "https://s3.amazonaws.com/bucket/uploads/userId/file.jpg?X-Amz-...",
    "s3_key": "uploads/userId/IMG_20260409_143022.jpg",
    "bucket": "mariox-s3"
}
```

#### POST /api/files/{fileTransferId}/downloaded
Called by the receiver after downloading. **Server must delete the file from S3 after this.**

**Response:** `{ "status": "deleted" }`

---

### 1.4 Rooms & Messages (Bearer token required)

#### GET /api/rooms
Returns all rooms for the authenticated user with unread counts and last message.

**Response:**
```json
{
    "rooms": [
        {
            "id": "<uuid>",
            "user1_id": "<uuid>",
            "user2_id": "<uuid>",
            "created_at": "2026-01-01T00:00:00Z",
            "unread_count": 3,
            "last_message": {
                "id": "<uuid>",
                "ciphertext": "<encrypted>",
                "message_type": "text",
                "created_at": "2026-01-01T00:00:00Z"
            }
        }
    ]
}
```

#### GET /api/messages/{roomId}?limit=50&cursor=
Cursor-based pagination, newest-first. Empty `next_cursor` = no more pages.

**Query params:**
- `limit` — 1-100, default 50
- `cursor` — message ID for pagination, empty for first page

**Response:**
```json
{
    "messages": [
        {
            "id": "<uuid>",
            "room_id": "<uuid>",
            "sender_id": "<uuid>",
            "receiver_id": "<uuid>",
            "ciphertext": "<encrypted>",
            "message_type": "text",
            "delivered": true,
            "read_at": "2026-01-01T00:00:00Z",
            "created_at": "2026-01-01T00:00:00Z"
        }
    ],
    "next_cursor": "<uuid-or-empty>"
}
```

#### GET /api/messages/unread
Unread message counts per room (for badge counts).

**Response:**
```json
{
    "unread_counts": {
        "<room_id>": 5,
        "<room_id>": 2
    }
}
```

---

## 2. WebSocket Protocol

### Connection
```
ws://103.194.228.68:8080/ws?token=<jwt_token>
```
Authentication via query parameter. On connect, the server auto-delivers pending (offline) messages.

### Envelope Format
All WebSocket messages use this wrapper:
```json
{ "type": "<event_type>", "payload": { ... } }
```

---

### 2.1 Client → Server Events

#### `message` — Send a text message
```json
{
    "type": "message",
    "payload": {
        "receiver_id": "<uuid>",
        "ciphertext": "<encrypted-text>",
        "message_type": "text"
    }
}
```
> **Note:** No `message_id` in the payload — the server generates it and returns it via `delivery_status`.

#### `file` — Send a file message (after S3 upload)
```json
{
    "type": "file",
    "payload": {
        "receiver_id": "<uuid>",
        "ciphertext": "<encrypted-metadata>",
        "encrypted_s3_url": "<encrypted-s3-url>",
        "s3_key": "uploads/userId/file.pdf",
        "file_name": "file.pdf",
        "file_size": 1048576
    }
}
```

#### `ack` — Acknowledge message received
```json
{
    "type": "ack",
    "payload": { "message_id": "<uuid>" }
}
```

#### `file_downloaded` — Confirm file download (triggers S3 delete)
```json
{
    "type": "file_downloaded",
    "payload": { "file_transfer_id": "<uuid>" }
}
```

#### `delete_message` — Hard delete a message (E2E safe)
```json
{
    "type": "delete_message",
    "payload": { "message_id": "<uuid>" }
}
```
Only the sender can delete their own messages. The message and any associated file transfer are permanently removed from the database.

#### `mark_read` — Mark messages as read (for badges / blue ticks)
```json
{
    "type": "mark_read",
    "payload": {
        "message_ids": ["<uuid>", "<uuid>"],
        "room_id": "<uuid>"
    }
}
```
Send specific `message_ids` to mark individual messages, OR send only `room_id` with empty `message_ids` to mark ALL unread messages in that room as read. The server notifies the original sender(s) via the `messages_read` event.

---

### 2.2 Server → Client Events

#### `message` — Incoming text message
```json
{
    "type": "message",
    "payload": {
        "id": "<uuid>",
        "room_id": "<uuid>",
        "sender_id": "<uuid>",
        "ciphertext": "<encrypted-text>",
        "message_type": "text",
        "created_at": "2026-01-01T00:00:00Z"
    }
}
```

#### `file` — Incoming file message
```json
{
    "type": "file",
    "payload": {
        "id": "<uuid>",
        "room_id": "<uuid>",
        "sender_id": "<uuid>",
        "ciphertext": "<encrypted-metadata>",
        "message_type": "file",
        "file_transfer_id": "<uuid>",
        "encrypted_s3_url": "<url>",
        "file_name": "file.pdf",
        "file_size": 1048576,
        "created_at": "2026-01-01T00:00:00Z"
    }
}
```

#### `delivery_status` — Sent back to the sender after sending
```json
{
    "type": "delivery_status",
    "payload": { "message_id": "<uuid>", "delivered": true }
}
```

#### `file_deleted` — Confirmation after file_downloaded
```json
{
    "type": "file_deleted",
    "payload": { "file_transfer_id": "<uuid>", "status": "deleted" }
}
```

#### `message_deleted` — A message was deleted (sent to both sender and receiver)
```json
{
    "type": "message_deleted",
    "payload": { "message_id": "<uuid>", "room_id": "<uuid>" }
}
```

#### `messages_read` — Messages were read by the receiver (blue ticks)
```json
{
    "type": "messages_read",
    "payload": {
        "room_id": "<uuid>",
        "reader_id": "<uuid>",
        "message_ids": ["<uuid>", "<uuid>"]
    }
}
```
Sent to the original sender when the receiver reads their messages.

#### `error` — Server error
```json
{
    "type": "error",
    "payload": { "error": "error message" }
}
```

---

## 3. Encrypted Metadata Structure (File messages)

The `ciphertext` field of a file message contains a Signal Protocol encrypted JSON string.
After decryption, the JSON looks like:

```json
{
    "file_name": "IMG_20260409_143022.jpg",
    "file_size": 1048576,
    "content_type": "image",
    "content_subtype": "jpeg",
    "s3_key": "uploads/userId/IMG_20260409_143022.jpg",
    "s3_url": "",
    "caption": "Check this out",
    "width": 1920,
    "height": 1080,
    "duration": 0,
    "thumbnail": "",
    "bucket": "mariox-s3"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `file_name` | String | Original file name |
| `file_size` | Long | Size in bytes |
| `content_type` | String | `image`, `video`, `audio`, `document` |
| `content_subtype` | String | MIME subtype: `jpeg`, `png`, `mp4`, `pdf`, `m4a` |
| `s3_key` | String | S3 object key for server reference |
| `s3_url` | String | Direct download URL (if provided) |
| `caption` | String | Optional user caption |
| `width` | Int | Image/video width (0 for audio/doc) |
| `height` | Int | Image/video height (0 for audio/doc) |
| `duration` | Long | Audio/video duration in ms (0 for image/doc) |
| `thumbnail` | String | Thumbnail URI or empty |
| `bucket` | String | S3 bucket name |

---

## 4. File Transfer Lifecycle

```
SENDER                          SERVER                          RECEIVER
  │                                │                                │
  │ POST /api/files/presign        │                                │
  │ ─────────────────────────────► │                                │
  │ ◄───────────────────────────── │                                │
  │ { presigned_url, s3_key }      │                                │
  │                                │                                │
  │ HTTP PUT presigned_url         │                                │
  │ ─────────────────────────────► S3                               │
  │                                │                                │
  │ WS send: { type: "file",      │                                │
  │   ciphertext, s3_key, ... }    │                                │
  │ ─────────────────────────────► │ WS send: { type: "file",      │
  │                                │   id, sender_id, ciphertext,   │
  │                                │   encrypted_s3_url, ... }      │
  │                                │ ─────────────────────────────► │
  │                                │                                │
  │ WS recv: { type:               │                                │
  │   "delivery_status" }          │                                │
  │ ◄───────────────────────────── │                                │
  │                                │                                │
  │                                │         [User taps Download]   │
  │                                │                                │
  │                                │  HTTP GET s3_url               │
  │                                S3 ◄──────────────────────────── │
  │                                │                                │
  │                                │  WS send: { type:             │
  │                                │   "file_downloaded" }          │
  │                                │ ◄───────────────────────────── │
  │                                │                                │
  │                                │  SERVER DELETES FILE FROM S3   │
  │                                │                                │
  │                                │  WS send: { type:             │
  │                                │   "file_deleted" }             │
  │                                │ ─────────────────────────────► │
```

**Key points:**
1. Files are temporary on S3 — delete after `file_downloaded` event
2. The `ciphertext` is E2E encrypted — server cannot read it
3. Server only needs to store: `file_transfer_id`, `s3_key`, `sender_id`, `receiver_id`, `file_name`, `file_size`
4. Presigned upload URLs should expire after 15 minutes
5. Server should queue pending file messages for offline delivery

---

## 5. Text Message Lifecycle

```
SENDER                          SERVER                          RECEIVER
  │                                │                                │
  │ WS send: { type: "message",   │                                │
  │   receiver_id, ciphertext,     │                                │
  │   message_type: "text" }       │                                │
  │ ─────────────────────────────► │                                │
  │                                │                                │
  │                                │  [server generates id]         │
  │                                │                                │
  │                                │  WS send: { type: "message",  │
  │                                │    id, sender_id,              │
  │                                │    ciphertext, ... }           │
  │                                │ ─────────────────────────────► │
  │                                │                                │
  │ WS recv: { type:               │                                │
  │   "delivery_status",           │                                │
  │   message_id, delivered }      │                                │
  │ ◄───────────────────────────── │                                │
  │                                │                                │
  │                                │  WS recv: { type: "ack",      │
  │                                │    message_id }                │
  │                                │ ◄───────────────────────────── │
  │                                │                                │
  │                                │         [User opens chat]      │
  │                                │                                │
  │                                │  WS send: { type:             │
  │                                │    "mark_read",                │
  │                                │    message_ids, room_id }      │
  │                                │ ◄───────────────────────────── │
  │                                │                                │
  │ WS recv: { type:               │                                │
  │   "messages_read",             │                                │
  │   room_id, reader_id,          │                                │
  │   message_ids }                │                                │
  │ ◄───────────────────────────── │                                │
  │ [update blue ticks]            │                                │
```

---

## 6. Seed Users (for testing)
`alice`, `bob`, `charlie`, `diana`, `eve`, `frank`, `grace`, `heidi`, `ivan`, `judy`
Password for all: `password123`
