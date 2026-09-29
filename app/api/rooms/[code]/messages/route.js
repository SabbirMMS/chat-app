const { NextResponse } = require('next/server');
const { getUserFromRequest } = require('../../../../../lib/auth');
const { getRoomByCode, isMember, getRoomMessages } = require('../../../../../lib/rooms');

export async function GET(req, { params }) {
  try {
    const authUser = getUserFromRequest(req);
    if (!authUser) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const { code } = params;
    if (!code) {
      return NextResponse.json({ error: 'Room code is required' }, { status: 400 });
    }

    const room = await getRoomByCode(code);
    if (!room) {
      return NextResponse.json({ error: 'Room not found' }, { status: 404 });
    }

    const member = await isMember(room.id, authUser.id);
    if (!member) {
      return NextResponse.json(
        { error: 'You are not a member of this room' },
        { status: 403 }
      );
    }

    const { searchParams } = new URL(req.url);
    const limit = searchParams.get('limit') || 50;
    const before = searchParams.get('before') || null;

    const messages = await getRoomMessages(room.id, limit, before);

    return NextResponse.json({ messages }, { status: 200 });
  } catch (err) {
    console.error('Get messages error:', err);
    return NextResponse.json({ error: 'Failed to fetch messages' }, { status: 500 });
  }
}
