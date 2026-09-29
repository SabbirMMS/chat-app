const { Server } = require('socket.io');
const { verifyToken } = require('../lib/auth');
const { getRoomByCode, isMember, saveMessage, markMessageSeen, markAllMessagesSeen } = require('../lib/rooms');

/**
 * Initializes Socket.IO on the given HTTP server
 * @param {import('http').Server} httpServer
 */
function initSocket(httpServer) {
  const corsOrigin = process.env.CORS_ORIGIN || '*';

  const io = new Server(httpServer, {
    path: '/socket.io',
    cors: {
      origin: corsOrigin === '*' ? true : corsOrigin.split(','),
      methods: ['GET', 'POST'],
      credentials: true,
    },
    // Default ping/pong heartbeat configuration
    pingTimeout: 20000,
    pingInterval: 25000,
  });

  // Helper to get active members in room
  function getRoomMembers(code) {
    const roomSockets = io.sockets.adapter.rooms.get(code);
    if (!roomSockets) return [];
    const membersMap = new Map();
    for (const socketId of roomSockets) {
      const s = io.sockets.sockets.get(socketId);
      if (s && s.user) {
        membersMap.set(s.user.id, {
          id: s.user.id,
          username: s.user.username,
        });
      }
    }
    return Array.from(membersMap.values());
  }

  // JWT Handshake Authentication Middleware
  io.use((socket, next) => {
    try {
      const authHeader = socket.handshake.headers?.authorization;
      const bearerToken = authHeader && authHeader.startsWith('Bearer ')
        ? authHeader.substring(7).trim()
        : null;

      const token = socket.handshake.auth?.token || bearerToken || socket.handshake.query?.token;

      if (!token) {
        return next(new Error('Authentication token required'));
      }

      const user = verifyToken(token);
      if (!user) {
        return next(new Error('Invalid or expired authentication token'));
      }

      // Attach user to socket
      socket.user = user;
      next();
    } catch (err) {
      next(new Error('Authentication error'));
    }
  });

  io.on('connection', (socket) => {
    // Keep track of rooms this socket has explicitly joined
    const joinedRooms = new Set();

    socket.on('join_room', async (data, callback) => {
      try {
        const code = data?.code ? String(data.code).trim().toUpperCase() : null;
        if (!code) {
          if (typeof callback === 'function') {
            return callback({ ok: false, error: 'Room code is required' });
          }
          return;
        }

        const room = await getRoomByCode(code);
        if (!room) {
          if (typeof callback === 'function') {
            return callback({ ok: false, error: 'Room not found' });
          }
          return;
        }

        const member = await isMember(room.id, socket.user.id);
        if (!member) {
          if (typeof callback === 'function') {
            return callback({ ok: false, error: 'You are not a member of this room' });
          }
          return;
        }

        socket.join(code);
        joinedRooms.add(code);

        // Notify other room members of presence
        socket.to(code).emit('user_joined', {
          code,
          user: {
            id: socket.user.id,
            username: socket.user.username,
          },
          joined_at: new Date().toISOString(),
        });

        // Broadcast current active members list
        io.to(code).emit('room_members', {
          code,
          members: getRoomMembers(code),
        });

        if (typeof callback === 'function') {
          callback({ ok: true, room: { id: room.id, code: room.code }, members: getRoomMembers(code) });
        }
      } catch (err) {
        console.error('Socket join_room error:', err);
        if (typeof callback === 'function') {
          callback({ ok: false, error: 'Failed to join room' });
        }
      }
    });

    // Typing start indicator
    socket.on('typing_start', (data) => {
      const code = data?.code ? String(data.code).trim().toUpperCase() : null;
      if (!code) return;
      socket.to(code).emit('user_typing', {
        code,
        user: {
          id: socket.user.id,
          username: socket.user.username,
        },
        isTyping: true,
      });
    });

    // Typing stop indicator
    socket.on('typing_stop', (data) => {
      const code = data?.code ? String(data.code).trim().toUpperCase() : null;
      if (!code) return;
      socket.to(code).emit('user_typing', {
        code,
        user: {
          id: socket.user.id,
          username: socket.user.username,
        },
        isTyping: false,
      });
    });

    // Mark single message seen
    socket.on('mark_seen', async (data, callback) => {
      try {
        const code = data?.code ? String(data.code).trim().toUpperCase() : null;
        const messageId = data?.messageId;
        if (!code || !messageId) return;

        await markMessageSeen(messageId, socket.user.id);

        io.to(code).emit('message_seen', {
          code,
          messageId,
          user: {
            id: socket.user.id,
            username: socket.user.username,
          },
        });

        if (typeof callback === 'function') {
          callback({ ok: true });
        }
      } catch (err) {
        console.error('Socket mark_seen error:', err);
        if (typeof callback === 'function') {
          callback({ ok: false, error: 'Failed to mark message seen' });
        }
      }
    });

    // Mark all messages in room seen
    socket.on('mark_all_seen', async (data, callback) => {
      try {
        const code = data?.code ? String(data.code).trim().toUpperCase() : null;
        if (!code) return;

        const room = await getRoomByCode(code);
        if (!room) return;

        await markAllMessagesSeen(room.id, socket.user.id);

        io.to(code).emit('room_messages_seen', {
          code,
          user: {
            id: socket.user.id,
            username: socket.user.username,
          },
        });

        if (typeof callback === 'function') {
          callback({ ok: true });
        }
      } catch (err) {
        console.error('Socket mark_all_seen error:', err);
        if (typeof callback === 'function') {
          callback({ ok: false, error: 'Failed to mark all messages seen' });
        }
      }
    });

    socket.on('send_message', async (data, callback) => {
      try {
        const code = data?.code ? String(data.code).trim().toUpperCase() : null;
        const content = data?.content ? String(data.content).trim() : '';

        if (!code) {
          if (typeof callback === 'function') {
            return callback({ ok: false, error: 'Room code is required' });
          }
          return;
        }

        if (!content || content.length === 0) {
          if (typeof callback === 'function') {
            return callback({ ok: false, error: 'Message content cannot be empty' });
          }
          return;
        }

        if (content.length > 2000) {
          if (typeof callback === 'function') {
            return callback({ ok: false, error: 'Message exceeds 2000 characters limit' });
          }
          return;
        }

        const room = await getRoomByCode(code);
        if (!room) {
          if (typeof callback === 'function') {
            return callback({ ok: false, error: 'Room not found' });
          }
          return;
        }

        const member = await isMember(room.id, socket.user.id);
        if (!member) {
          if (typeof callback === 'function') {
            return callback({ ok: false, error: 'You are not a member of this room' });
          }
          return;
        }

        // Save message to PostgreSQL
        const savedMessage = await saveMessage(room.id, socket.user.id, content);

        const messagePayload = {
          id: savedMessage.id,
          code,
          content: savedMessage.content,
          sender: {
            id: socket.user.id,
            username: socket.user.username,
          },
          created_at: savedMessage.created_at,
          seen_by: [],
        };

        // Broadcast to all clients in the room including sender
        io.to(code).emit('new_message', messagePayload);

        // Also stop typing status if currently typing
        socket.to(code).emit('user_typing', {
          code,
          user: {
            id: socket.user.id,
            username: socket.user.username,
          },
          isTyping: false,
        });

        if (typeof callback === 'function') {
          callback({ ok: true, message: messagePayload });
        }
      } catch (err) {
        console.error('Socket send_message error:', err);
        if (typeof callback === 'function') {
          callback({ ok: false, error: 'Failed to send message' });
        }
      }
    });

    socket.on('disconnecting', () => {
      for (const code of joinedRooms) {
        socket.to(code).emit('user_left', {
          code,
          user: {
            id: socket.user.id,
            username: socket.user.username,
          },
          left_at: new Date().toISOString(),
        });

        // Also notify typing stop on disconnect
        socket.to(code).emit('user_typing', {
          code,
          user: {
            id: socket.user.id,
            username: socket.user.username,
          },
          isTyping: false,
        });

        // Update online members after leaving
        setTimeout(() => {
          io.to(code).emit('room_members', {
            code,
            members: getRoomMembers(code),
          });
        }, 100);
      }
    });
  });

  return io;
}

module.exports = { initSocket };
