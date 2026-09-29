const { NextResponse } = require('next/server');
const { z } = require('zod');
const { getUserFromRequest } = require('../../../../lib/auth');
const { joinRoomByCode } = require('../../../../lib/rooms');

const joinSchema = z.object({
  code: z
    .string()
    .trim()
    .min(1, 'Room code is required')
    .max(10, 'Invalid room code length'),
});

export async function POST(req) {
  try {
    const authUser = getUserFromRequest(req);
    if (!authUser) {
      return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    let body;
    try {
      body = await req.json();
    } catch {
      return NextResponse.json({ error: 'Invalid JSON body' }, { status: 400 });
    }

    const validation = joinSchema.safeParse(body);
    if (!validation.success) {
      const msg = validation.error.issues?.[0]?.message || validation.error.errors?.[0]?.message || 'Invalid room code';
      return NextResponse.json(
        { error: msg },
        { status: 400 }
      );
    }

    const { code } = validation.data;
    const room = await joinRoomByCode(code, authUser.id);
    if (!room) {
      return NextResponse.json({ error: 'Room not found' }, { status: 404 });
    }

    return NextResponse.json({ room }, { status: 200 });
  } catch (err) {
    console.error('Join room error:', err);
    return NextResponse.json({ error: 'Failed to join room' }, { status: 500 });
  }
}
