const { NextResponse } = require('next/server');
const { z } = require('zod');
const { query } = require('../../../../lib/db');
const { hashPassword, signToken, getClientIp, checkRateLimit } = require('../../../../lib/auth');

const registerSchema = z.object({
  username: z
    .string()
    .trim()
    .min(3, 'Username must be at least 3 characters')
    .max(30, 'Username must be at most 30 characters')
    .regex(/^[a-zA-Z0-9_]+$/, 'Username can only contain letters, numbers, and underscores'),
  password: z
    .string()
    .min(6, 'Password must be at least 6 characters')
    .max(100, 'Password must be at most 100 characters'),
});

export async function POST(req) {
  try {
    const ip = getClientIp(req);
    if (!checkRateLimit(`register:${ip}`, 10, 60 * 1000)) {
      return NextResponse.json(
        { error: 'Too many registration attempts. Please try again later.' },
        { status: 429 }
      );
    }

    let body;
    try {
      body = await req.json();
    } catch {
      return NextResponse.json({ error: 'Invalid JSON body' }, { status: 400 });
    }

    const validation = registerSchema.safeParse(body);
    if (!validation.success) {
      const msg = validation.error.issues?.[0]?.message || validation.error.errors?.[0]?.message || 'Validation failed';
      return NextResponse.json(
        { error: msg },
        { status: 400 }
      );
    }

    const { username, password } = validation.data;

    // Check if user exists
    const existing = await query('SELECT id FROM users WHERE LOWER(username) = LOWER($1)', [username]);
    if (existing.rowCount > 0) {
      return NextResponse.json({ error: 'Username is already taken' }, { status: 409 });
    }

    const passwordHash = await hashPassword(password);
    const result = await query(
      `INSERT INTO users (username, password_hash)
       VALUES ($1, $2)
       RETURNING id, username, created_at`,
      [username, passwordHash]
    );

    const user = result.rows[0];
    const token = signToken(user);

    return NextResponse.json(
      {
        token,
        user: {
          id: user.id,
          username: user.username,
          created_at: user.created_at,
        },
      },
      { status: 201 }
    );
  } catch (err) {
    console.error('Register error:', err);
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 });
  }
}
