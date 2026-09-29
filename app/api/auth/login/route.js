const { NextResponse } = require('next/server');
const { z } = require('zod');
const { query } = require('../../../../lib/db');
const { comparePassword, signToken, getClientIp, checkRateLimit } = require('../../../../lib/auth');

const loginSchema = z.object({
  username: z.string().trim().min(1, 'Username is required'),
  password: z.string().min(1, 'Password is required'),
});

export async function POST(req) {
  try {
    const ip = getClientIp(req);
    if (!checkRateLimit(`login:${ip}`, 10, 60 * 1000)) {
      return NextResponse.json(
        { error: 'Too many login attempts. Please try again later.' },
        { status: 429 }
      );
    }

    let body;
    try {
      body = await req.json();
    } catch {
      return NextResponse.json({ error: 'Invalid JSON body' }, { status: 400 });
    }

    const validation = loginSchema.safeParse(body);
    if (!validation.success) {
      const msg = validation.error.issues?.[0]?.message || validation.error.errors?.[0]?.message || 'Validation failed';
      return NextResponse.json(
        { error: msg },
        { status: 400 }
      );
    }

    const { username, password } = validation.data;

    const result = await query(
      `SELECT id, username, password_hash, created_at
       FROM users
       WHERE LOWER(username) = LOWER($1)`,
      [username]
    );

    if (result.rowCount === 0) {
      return NextResponse.json({ error: 'Invalid username or password' }, { status: 401 });
    }

    const user = result.rows[0];
    const passwordMatch = await comparePassword(password, user.password_hash);
    if (!passwordMatch) {
      return NextResponse.json({ error: 'Invalid username or password' }, { status: 401 });
    }

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
      { status: 200 }
    );
  } catch (err) {
    console.error('Login error:', err);
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 });
  }
}
