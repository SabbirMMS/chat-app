const { NextResponse } = require('next/server');
const { getUserFromRequest } = require('../../../lib/auth');
const { query } = require('../../../lib/db');

export async function GET(req) {
  try {
    const authUser = getUserFromRequest(req);
    if (!authUser) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const result = await query(
      `SELECT id, username, created_at FROM users WHERE id = $1`,
      [authUser.id]
    );

    if (result.rowCount === 0) {
      return NextResponse.json({ error: 'User not found' }, { status: 404 });
    }

    const user = result.rows[0];
    return NextResponse.json({ user });
  } catch (err) {
    console.error('Me error:', err);
    return NextResponse.json({ error: 'Internal server error' }, { status: 500 });
  }
}
