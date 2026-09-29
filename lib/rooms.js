const crypto = require('crypto');
const { query } = require('./db');

// 6 chars, uppercase alphanumeric, no confusing chars (no 0, O, I, 1, L)
const SAFE_CHARS = '23456789ABCDEFGHJKMNPQRSTUVWXYZ';

function generateRoomCode(length = 6) {
  let result = '';
  const bytes = crypto.randomBytes(length);
  for (let i = 0; i < length; i++) {
    result += SAFE_CHARS[bytes[i] % SAFE_CHARS.length];
  }
  return result;
}

async function createRoom(userId) {
  let attempts = 0;
  while (attempts < 5) {
    const code = generateRoomCode(6);
    try {
      const roomResult = await query(
        `INSERT INTO rooms (code, created_by)
         VALUES ($1, $2)
         RETURNING id, code, created_by, created_at`,
        [code, userId]
      );
      const room = roomResult.rows[0];

      // Add creator as member
      await query(
        `INSERT INTO room_members (room_id, user_id)
         VALUES ($1, $2)
         ON CONFLICT DO NOTHING`,
        [room.id, userId]
      );

      return room;
    } catch (err) {
      // If code unique constraint collision, retry
      if (err.code === '23505' && err.constraint?.includes('code')) {
        attempts++;
        continue;
      }
      throw err;
    }
  }
  throw new Error('Failed to generate unique room code after multiple attempts');
}

async function getRoomByCode(code) {
  const result = await query(
    `SELECT r.id, r.code, r.created_by, r.created_at, u.username as creator_username
     FROM rooms r
     JOIN users u ON r.created_by = u.id
     WHERE r.code = $1`,
    [code.toUpperCase().trim()]
  );
  return result.rows[0] || null;
}

async function getUserRooms(userId) {
  const result = await query(
    `SELECT r.id, r.code, r.created_by, r.created_at, rm.joined_at,
            u.username as creator_username,
            (SELECT COUNT(*) FROM room_members WHERE room_id = r.id)::int as member_count
     FROM room_members rm
     JOIN rooms r ON rm.room_id = r.id
     JOIN users u ON r.created_by = u.id
     WHERE rm.user_id = $1
     ORDER BY rm.joined_at DESC`,
    [userId]
  );
  return result.rows;
}

async function joinRoomByCode(code, userId) {
  const room = await getRoomByCode(code);
  if (!room) {
    return null;
  }

  await query(
    `INSERT INTO room_members (room_id, user_id)
     VALUES ($1, $2)
     ON CONFLICT (room_id, user_id) DO NOTHING`,
    [room.id, userId]
  );

  return room;
}

async function isMember(roomId, userId) {
  const result = await query(
    `SELECT 1 FROM room_members WHERE room_id = $1 AND user_id = $2`,
    [roomId, userId]
  );
  return result.rowCount > 0;
}

async function getRoomMessages(roomId, limit = 50, before = null) {
  const safeLimit = Math.min(Math.max(parseInt(limit, 10) || 50, 1), 100);
  
  let sql = `
    SELECT m.id, m.content, m.created_at,
           json_build_object('id', u.id, 'username', u.username) as sender
    FROM messages m
    JOIN users u ON m.sender_id = u.id
    WHERE m.room_id = $1
  `;
  const params = [roomId];

  if (before) {
    const beforeDate = new Date(before);
    if (!isNaN(beforeDate.getTime())) {
      params.push(beforeDate.toISOString());
      sql += ` AND m.created_at < $${params.length}`;
    }
  }

  params.push(safeLimit);
  sql += ` ORDER BY m.created_at DESC LIMIT $${params.length}`;

  const result = await query(sql, params);
  // Return in chronological order (oldest to newest)
  return result.rows.reverse();
}

async function saveMessage(roomId, userId, content) {
  const result = await query(
    `INSERT INTO messages (room_id, sender_id, content)
     VALUES ($1, $2, $3)
     RETURNING id, content, created_at`,
    [roomId, userId, content]
  );
  return result.rows[0];
}

module.exports = {
  generateRoomCode,
  createRoom,
  getRoomByCode,
  getUserRooms,
  joinRoomByCode,
  isMember,
  getRoomMessages,
  saveMessage,
};
