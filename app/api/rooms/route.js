const { NextResponse } = require('next/server');
const { getUserFromRequest } = require('../../../lib/auth');
const { createRoom, getUserRooms } = require('../../../lib/rooms');

export async function POST(req) {
  try {
    const authUser = getUserFromRequest(req);
    if (!authUser) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const room = await createRoom(authUser.id);
    return NextResponse.json({ room }, { status: 201 });
  } catch (err) {
    console.error('Create room error:', err);
    return NextResponse.json({ error: 'Failed to create room' }, { status: 500 });
  }
}

export async function GET(req) {
  try {
    const authUser = getUserFromRequest(req);
    if (!authUser) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const rooms = await getUserRooms(authUser.id);
    return NextResponse.json({ rooms }, { status: 200 });
  } catch (err) {
    console.error('Get rooms error:', err);
    return NextResponse.json({ error: 'Failed to fetch rooms' }, { status: 500 });
  }
}
