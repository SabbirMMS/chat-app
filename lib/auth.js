const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');

const JWT_SECRET = process.env.JWT_SECRET || 'super_secret_jwt_key_chatapp_development_2026';

async function hashPassword(password) {
  return await bcrypt.hash(password, 10);
}

async function comparePassword(password, hash) {
  return await bcrypt.compare(password, hash);
}

function signToken(user) {
  return jwt.sign(
    {
      id: user.id,
      username: user.username,
    },
    JWT_SECRET,
    { expiresIn: '7d' }
  );
}

function verifyToken(token) {
  try {
    return jwt.verify(token, JWT_SECRET);
  } catch (err) {
    return null;
  }
}

/**
 * Extracts and verifies JWT from standard Next.js Web Request
 * @param {Request} req
 * @returns {{id: string, username: string} | null}
 */
function getUserFromRequest(req) {
  try {
    const authHeader = req.headers.get('authorization') || req.headers.get('Authorization');
    if (!authHeader || !authHeader.startsWith('Bearer ')) {
      return null;
    }
    const token = authHeader.substring(7).trim();
    return verifyToken(token);
  } catch (err) {
    return null;
  }
}

/**
 * Extracts client IP considering Cloudflare / reverse proxy headers
 * @param {Request} req
 * @returns {string}
 */
function getClientIp(req) {
  const cfIp = req.headers.get('cf-connecting-ip');
  if (cfIp) return cfIp.trim();

  const forwarded = req.headers.get('x-forwarded-for');
  if (forwarded) {
    return forwarded.split(',')[0].trim();
  }

  const realIp = req.headers.get('x-real-ip');
  if (realIp) return realIp.trim();

  return '127.0.0.1';
}

// In-memory sliding window rate limiter
const rateLimitMap = new Map();

/**
 * Basic rate limiting helper for routes
 * @param {string} key (e.g. IP + route)
 * @param {number} limit max requests allowed
 * @param {number} windowMs window size in milliseconds
 * @returns {boolean} true if allowed, false if rate limited
 */
function checkRateLimit(key, limit = 10, windowMs = 60 * 1000) {
  const now = Date.now();
  const record = rateLimitMap.get(key) || { count: 0, resetAt: now + windowMs };

  if (now > record.resetAt) {
    record.count = 1;
    record.resetAt = now + windowMs;
    rateLimitMap.set(key, record);
    return true;
  }

  if (record.count >= limit) {
    return false;
  }

  record.count += 1;
  rateLimitMap.set(key, record);
  return true;
}

// Clean up old rate limit records periodically
setInterval(() => {
  const now = Date.now();
  for (const [key, value] of rateLimitMap.entries()) {
    if (now > value.resetAt) {
      rateLimitMap.delete(key);
    }
  }
}, 5 * 60 * 1000).unref();

module.exports = {
  hashPassword,
  comparePassword,
  signToken,
  verifyToken,
  getUserFromRequest,
  getClientIp,
  checkRateLimit,
};
