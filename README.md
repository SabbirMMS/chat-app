# Realtime Chat App

A clean, single-process realtime room-based chat application built with **Next.js (App Router, JavaScript)** and a custom **Node.js HTTP server** that runs **Socket.IO** and Next.js together on **port 3000**. No external third-party services like Supabase or Vercel required.

Supports both modern Web clients and mobile clients (Android) using the exact same REST API and Socket.IO endpoints.

---

## Architecture & Features

- **Single Process on Port 3000**: `server.js` starts a standard Node `http.Server`, binds Next.js App Router for web/API routes, and attaches Socket.IO on the same server under path `/socket.io`.
- **Database**: PostgreSQL with `pg` connection pool. Parameterized queries only.
- **Authentication**: JWT (Bearer tokens in `Authorization` header and Socket.IO handshake auth) + bcrypt password hashing.
- **Validation**: Zod schema validation across all API routes and message boundaries.
- **Rate Limiting**: In-memory IP-based rate limiting on `/api/auth/*` routes respecting reverse proxy / Cloudflare headers (`CF-Connecting-IP`, `X-Forwarded-For`).
- **Room Code Generation**: Safe 6-character uppercase alphanumeric codes (`23456789ABCDEFGHJKMNPQRSTUVWXYZ`) omitting confusing characters (`0`, `O`, `1`, `I`, `L`).
- **Typing Indicator**: Ephemeral realtime socket pub/sub emitting typing pings (`typing_start`, `typing_stop`, `user_typing`) with client-side debounce.
- **Seen Member's List & Read Receipts**: Persistent message-level read tracking in PostgreSQL `message_reads` table with live broadcast (`mark_seen`, `mark_all_seen`, `message_seen`, `room_messages_seen`).
- **Active Room Members Presence**: Realtime tracking of online room members (`room_members`) with live user count and member list.
- **Web UI & Android Client**: Modern dark theme for web and native Java Android app with editable Base URL (for testing over Cloudflare Tunnel or local IP), message history pagination, auto-scroll, typing animation, and seen status indicators.

---

## Folder Structure

```text
├── server.js                          # Custom Node server attaching Next.js & Socket.IO
├── package.json                       # Scripts: "dev", "build", "start"
├── Dockerfile                         # Production multi-stage Docker build
├── docker-compose.yml                 # PostgreSQL + Web container orchestration
├── .env.example                       # Environment template
├── .gitignore
├── lib/
│   ├── db.js                          # pg Pool instance and query helper
│   ├── auth.js                        # JWT sign/verify, bcrypt helpers, IP rate limiting
│   ├── rooms.js                       # Room code generator, membership & message queries
│   └── clientAuth.js                  # Browser token storage and authenticated fetch
├── socket/
│   └── index.js                       # Socket.IO setup, JWT handshake auth, event handlers
├── app/
│   ├── globals.css                    # Tailwind CSS directives & custom styling
│   ├── layout.js                      # Root application layout
│   ├── page.js                        # Root page (redirects to /dashboard or /login)
│   ├── login/page.js                  # Login UI
│   ├── register/page.js               # Registration UI
│   ├── dashboard/page.js              # Create/Join room & active room list
│   ├── room/
│   │   └── [code]/page.js             # Chat interface (REST history + Socket.IO live)
│   └── api/
│       ├── auth/
│       │   ├── register/route.js      # POST /api/auth/register
│       │   └── login/route.js         # POST /api/auth/login
│       ├── me/route.js                # GET /api/me
│       └── rooms/
│           ├── route.js               # POST /api/rooms, GET /api/rooms
│           ├── join/route.js          # POST /api/rooms/join
│           └── [code]/
│               └── messages/route.js  # GET /api/rooms/:code/messages
├── Android App/                       # Native Android client (Java, Retrofit, Socket.IO)
│   ├── app/src/main/java/...          # Java activities, socket manager, network models
│   └── app/build.gradle               # Android build configuration
└── db/
    └── schema.sql                     # PostgreSQL schema and indexes
```

---

## Getting Started Locally

### 1. Prerequisites
- Node.js (v18+ or v20+)
- PostgreSQL running locally (port 5432)

### 2. Setup Database
Create a database named `chatapp` and apply the schema:

```bash
createdb chatapp
psql -d chatapp -f db/schema.sql
```

### 3. Environment Configuration
Copy `.env.example` to `.env`:

```bash
cp .env.example .env
```

Review values in `.env`:
```env
DATABASE_URL=postgresql://localhost:5432/chatapp
JWT_SECRET=super_secret_jwt_key_change_in_production_123456789
PORT=3000
CORS_ORIGIN=*
```

### 4. Install Dependencies & Run

```bash
npm install

# Run development server (runs "node server.js")
npm run dev

# Or for production:
npm run build
npm start
```

Open [http://localhost:3000](http://localhost:3000) in your browser.

> **Note**: Never use `next dev`. Always use `npm run dev` (`node server.js`) so that Socket.IO runs on the same HTTP server.

---

## REST API Reference

All requests and responses use JSON. Except for `/api/auth/*`, all endpoints require the header:
```text
Authorization: Bearer <jwt_token>
```

Error responses always follow:
```json
{
  "error": "Error message description"
}
```

### 1. Register User
- **`POST /api/auth/register`**
- Body: `{"username": "alice", "password": "password123"}`
- Response: `201 Created`
```bash
curl -s -X POST http://localhost:3000/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "password123"}'
```

### 2. Login User
- **`POST /api/auth/login`**
- Body: `{"username": "alice", "password": "password123"}`
- Response: `200 OK`
```bash
curl -s -X POST http://localhost:3000/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "alice", "password": "password123"}'
```

Response format:
```json
{
  "token": "<JWT_STRING>",
  "user": {
    "id": "3d22e355-fc01-4b5a-bfa0-f39350e96a2c",
    "username": "alice",
    "created_at": "2026-09-29T08:34:50.290Z"
  }
}
```

### 3. Get Authenticated User Profile
- **`GET /api/me`**
- Headers: `Authorization: Bearer <token>`
- Response: `200 OK`
```bash
curl -s -X GET http://localhost:3000/api/me \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

### 4. Create Room
- **`POST /api/rooms`**
- Automatically generates a 6-character safe room code and adds the creator as a member.
- Headers: `Authorization: Bearer <token>`
- Response: `201 Created`
```bash
curl -s -X POST http://localhost:3000/api/rooms \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

Response:
```json
{
  "room": {
    "id": "38885b81-d605-4e24-8785-714d46898130",
    "code": "EHJH8K",
    "created_by": "3d22e355-fc01-4b5a-bfa0-f39350e96a2c",
    "created_at": "2026-09-29T08:35:12.507Z"
  }
}
```

### 5. Get User's Rooms
- **`GET /api/rooms`**
- Headers: `Authorization: Bearer <token>`
- Response: `200 OK`
```bash
curl -s -X GET http://localhost:3000/api/rooms \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

### 6. Join Room by Code
- **`POST /api/rooms/join`**
- Headers: `Authorization: Bearer <token>`
- Body: `{"code": "EHJH8K"}`
- Response: `200 OK` (or `404 Not Found` if code does not exist)
```bash
curl -s -X POST http://localhost:3000/api/rooms/join \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -d '{"code": "EHJH8K"}'
```

### 7. Get Room Message History
- **`GET /api/rooms/:code/messages?limit=50&before=<iso_timestamp>`**
- Returns messages in chronological order (oldest to newest) along with `seen_by` members. Only room members can access history (`403 Forbidden` if not a member).
- Headers: `Authorization: Bearer <token>`
- Response: `200 OK`
```bash
curl -s -X GET "http://localhost:3000/api/rooms/EHJH8K/messages?limit=50" \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

Response format:
```json
{
  "messages": [
    {
      "id": "742e313c-f243-480e-92da-240202888a3c",
      "content": "Hello world",
      "created_at": "2026-09-29T08:36:23.672Z",
      "sender": {
        "id": "3d22e355-fc01-4b5a-bfa0-f39350e96a2c",
        "username": "alice"
      },
      "seen_by": [
        {
          "id": "ad235294-d8f3-4516-b314-b4e6c2435eb3",
          "username": "bob"
        }
      ]
    }
  ]
}
```

---

## Socket.IO Events Reference

- **Path**: `/socket.io`
- **Handshake Authentication**: Send the JWT in `auth: { token: "<jwt>" }` or in headers `Authorization: Bearer <jwt>`.
- Rejections: Invalid or missing token triggers a connection error (`Authentication token required`).

### Client to Server Events

1. **`join_room`**
   - Payload: `{ code: "EHJH8K" }`
   - Ack Callback: `(response) => { ... }`
   - Response: `{ ok: true, room: { id, code }, members: [...] }` or `{ ok: false, error: "..." }`
   - Behavior: Verifies membership in PostgreSQL, joins socket to room channel, and broadcasts `user_joined` and `room_members`.

2. **`send_message`**
   - Payload: `{ code: "EHJH8K", content: "Hello world" }`
   - Ack Callback: `(response) => { ... }`
   - Response: `{ ok: true, message: { id, code, content, sender, created_at, seen_by: [] } }`
   - Behavior: Validates membership + text length (1-2000 chars), persists to PostgreSQL, and broadcasts `new_message` to the room channel.

3. **`typing_start`** (Typing Indicator Ping)
   - Payload: `{ code: "EHJH8K" }`
   - Behavior: Relays `user_typing` `{ isTyping: true }` to other room members.

4. **`typing_stop`**
   - Payload: `{ code: "EHJH8K" }`
   - Behavior: Relays `user_typing` `{ isTyping: false }` to other room members.

5. **`mark_seen`** (Read Receipt)
   - Payload: `{ code: "EHJH8K", messageId: "<uuid>" }`
   - Behavior: Records reader in PostgreSQL `message_reads` and broadcasts `message_seen` `{ messageId, user }`.

6. **`mark_all_seen`**
   - Payload: `{ code: "EHJH8K" }`
   - Behavior: Records reader for all messages in the room in PostgreSQL `message_reads` and broadcasts `room_messages_seen` `{ user }`.

### Server to Client Events

1. **`new_message`**
   - Emitted to all clients in the room:
   ```json
   {
     "id": "742e313c-f243-480e-92da-240202888a3c",
     "code": "EHJH8K",
     "content": "Hello world",
     "sender": { "id": "uuid", "username": "alice" },
     "created_at": "2026-09-29T08:36:23.672Z",
     "seen_by": []
   }
   ```

2. **`user_typing`**
   - Emitted when a room member starts or stops typing:
   ```json
   {
     "code": "EHJH8K",
     "user": { "id": "uuid", "username": "alice" },
     "isTyping": true
   }
   ```

3. **`message_seen`**
   - Emitted when a room member reads a message:
   ```json
   {
     "code": "EHJH8K",
     "messageId": "742e313c-f243-480e-92da-240202888a3c",
     "user": { "id": "uuid", "username": "bob" }
   }
   ```

4. **`room_messages_seen`**
   - Emitted when a room member marks all messages in a room as seen:
   ```json
   {
     "code": "EHJH8K",
     "user": { "id": "uuid", "username": "bob" }
   }
   ```

5. **`room_members`**
   - Emitted when users join or leave, listing currently active connected users:
   ```json
   {
     "code": "EHJH8K",
     "members": [
       { "id": "uuid1", "username": "alice" },
       { "id": "uuid2", "username": "bob" }
     ]
   }
   ```

6. **`user_joined` / `user_left`**
   - Emitted on member connect or disconnect with timestamps.

---

## Deployment Guide

### Option 1: Cloudflare Named Tunnel (Local Dev / Homelab to Public Domain)

Expose `http://localhost:3000` to `chat.yourdomain.com` with free HTTPS/WSS terminated at Cloudflare:

1. **Install and Authenticate Cloudflare Tunnel (`cloudflared`)**:
   ```bash
   cloudflared tunnel login
   ```
2. **Create a Named Tunnel**:
   ```bash
   cloudflared tunnel create chatapp-tunnel
   # Note down the generated Tunnel ID
   ```
3. **Route DNS**:
   ```bash
   cloudflared tunnel route dns chatapp-tunnel chat.yourdomain.com
   ```
4. **Create Configuration File (`~/.cloudflared/config.yml`)**:
   ```yaml
   tunnel: <TUNNEL_ID>
   credentials-file: /Users/<your-user>/.cloudflared/<TUNNEL_ID>.json

   ingress:
     - hostname: chat.yourdomain.com
       service: http://localhost:3000
       originRequest:
         noTLSVerify: true
     - service: http_status:404
   ```
5. **Run the Tunnel**:
   ```bash
   cloudflared tunnel run chatapp-tunnel
   ```
   WebSockets (WSS) and HTTPS will be handled automatically by Cloudflare Tunnel, forwarding traffic directly to `localhost:3000`.

---

### Option 2: VPS Move with Docker Compose

When migrating to a VPS (Ubuntu/Debian):

1. **Export Local Database**:
   ```bash
   pg_dump -U sabbirmms -d chatapp -Fc > chatapp_backup.dump
   ```
2. **Deploy on VPS with Docker Compose**:
   Copy the project directory to your VPS.
   Run:
   ```bash
   docker compose up -d
   ```
3. **Restore Database on VPS**:
   ```bash
   cat chatapp_backup.dump | docker exec -i chatapp_postgres pg_restore -U chatuser -d chatapp --clean
   ```
4. **Point your domain** to the VPS or attach the Cloudflare Tunnel on the VPS pointing to `http://localhost:3000`. Because the domain and endpoints remain identical, **the Android app and web clients require zero changes**.

---

## Native Android Client (`Android App/`)

A complete native Android app written in **Java** is located in [`Android App/`](./Android%20App). It uses **Retrofit 2**, **OkHttp 4**, **Gson**, and **Socket.IO Java Client (`io.socket:socket.io-client:2.1.1`)**.

### Features
1. **Dynamic & Editable Base URL**:
   - Stored in `SharedPreferences` via `PrefsManager`.
   - Defaults to `http://10.0.2.2:3000` (for emulator localhost).
   - Easily editable via the "Configure Server URL" dialog on Login, Register, or Dashboard (ideal for pointing to a Cloudflare Tunnel `https://chat.yourdomain.com` or local LAN IP `http://192.168.x.x:3000`).
2. **Authentication**: Register and Login with JWT token saved to `SharedPreferences` and attached to all Retrofit requests via an OkHttp interceptor.
3. **Room Management**:
   - Create private room (generates safe 6-character code, one-tap copy, direct enter).
   - Join existing room by 6-character code.
   - List active rooms with member count and creator info (`SwipeRefreshLayout` + `RecyclerView`).
4. **Realtime Chatting**:
   - Loads initial message history via REST.
   - Live updates via Socket.IO.
   - **Typing Indicator Ping**: Text input watcher emits `typing_start` / `typing_stop` with 2.5s debounce; shows live `"Alice is typing..."` above input bar.
   - **Seen Member's List / Read Receipts**: Emits `mark_seen` / `mark_all_seen`; displays `✓ Seen by [names]` under messages.
   - **Active Room Members**: Top bar pill shows online count and opens dialog listing active users in the room.

### Building & Running the Android App
```bash
cd "Android App"
./gradlew assembleDebug
```
Output APK: `Android App/app/build/outputs/apk/debug/app-debug.apk`

---

## Verification & Testing Summary

All features have been built, compiled, and verified end-to-end:
1. `npm run build` succeeds cleanly with Next.js App Router static and dynamic routes.
2. `node server.js` boots HTTP server and Socket.IO on port 3000 in a single process.
3. User registration and login verified with bcrypt hashing, Zod validation, and JWT generation.
4. Room creation with 6-character collision-free codes, membership checking, and message pagination with `seen_by` verified against PostgreSQL.
5. Socket.IO handshake authentication verified (unauthorized connections rejected, authorized connections accepted).
6. Realtime multi-client Socket.IO messaging verified with acknowledgment callbacks and room broadcast.
7. Realtime typing indicators verified with debounce and status broadcast (`user_typing`).
8. Realtime seen receipts verified with PostgreSQL persistence (`message_reads`) and live room broadcast (`message_seen`).
9. Native Android application built with `./gradlew assembleDebug` (`BUILD SUCCESSFUL`).
