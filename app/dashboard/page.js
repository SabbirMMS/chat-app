'use client';

import { useState, useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { getToken, getStoredUser, clearAuth, fetchWithAuth } from '../../lib/clientAuth';
import {
  MessageSquare,
  PlusCircle,
  LogIn,
  LogOut,
  Copy,
  Check,
  Users,
  Clock,
  ArrowRight,
  Sparkles,
} from 'lucide-react';

export default function DashboardPage() {
  const router = useRouter();
  const [user, setUser] = useState(null);
  const [rooms, setRooms] = useState([]);
  const [loadingRooms, setLoadingRooms] = useState(true);

  // Create room state
  const [creating, setCreating] = useState(false);
  const [createdRoom, setCreatedRoom] = useState(null);
  const [copiedCode, setCopiedCode] = useState(false);

  // Join room state
  const [joinCode, setJoinCode] = useState('');
  const [joining, setJoining] = useState(false);
  const [joinError, setJoinError] = useState('');

  const loadRooms = useCallback(async () => {
    try {
      setLoadingRooms(true);
      const res = await fetchWithAuth('/api/rooms');
      if (res.ok) {
        const data = await res.json();
        setRooms(data.rooms || []);
      }
    } catch (err) {
      console.error('Failed to load rooms:', err);
    } finally {
      setLoadingRooms(false);
    }
  }, []);

  useEffect(() => {
    const token = getToken();
    if (!token) {
      router.replace('/login');
      return;
    }

    const stored = getStoredUser();
    if (stored) {
      setUser(stored);
    }

    // Verify token and fetch fresh profile
    fetchWithAuth('/api/me')
      .then((res) => {
        if (!res.ok) throw new Error('Unauthorized');
        return res.json();
      })
      .then((data) => {
        setUser(data.user);
      })
      .catch(() => {
        clearAuth();
        router.replace('/login');
      });

    loadRooms();
  }, [router, loadRooms]);

  async function handleCreateRoom() {
    setCreating(true);
    setCreatedRoom(null);
    setCopiedCode(false);

    try {
      const res = await fetchWithAuth('/api/rooms', {
        method: 'POST',
      });
      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.error || 'Failed to create room');
      }

      setCreatedRoom(data.room);
      loadRooms();
    } catch (err) {
      alert(err.message);
    } finally {
      setCreating(false);
    }
  }

  async function handleJoinRoom(e) {
    e.preventDefault();
    setJoinError('');

    const code = joinCode.trim().toUpperCase();
    if (!code) {
      setJoinError('Please enter a room code');
      return;
    }

    setJoining(true);

    try {
      const res = await fetchWithAuth('/api/rooms/join', {
        method: 'POST',
        body: JSON.stringify({ code }),
      });

      const data = await res.json();

      if (!res.ok) {
        throw new Error(data.error || 'Failed to join room');
      }

      router.push(`/room/${data.room.code}`);
    } catch (err) {
      setJoinError(err.message);
    } finally {
      setJoining(false);
    }
  }

  function handleCopy(code) {
    navigator.clipboard.writeText(code);
    setCopiedCode(true);
    setTimeout(() => setCopiedCode(false), 2000);
  }

  function handleLogout() {
    clearAuth();
    router.replace('/login');
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      {/* Top Navbar */}
      <header className="sticky top-0 z-20 border-b border-slate-800 bg-slate-900/80 backdrop-blur-md">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-3.5 sm:px-6">
          <div className="flex items-center gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-600 text-white shadow-md shadow-indigo-500/20">
              <MessageSquare className="h-5 w-5" />
            </div>
            <div>
              <h1 className="text-base font-bold text-white tracking-tight sm:text-lg">
                Realtime Chat
              </h1>
              <p className="text-xs text-slate-400">One-Process Next.js + Socket.IO</p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <div className="hidden sm:flex items-center gap-2 rounded-lg bg-slate-800/80 px-3 py-1.5 border border-slate-700">
              <div className="h-2 w-2 rounded-full bg-emerald-400 animate-pulse" />
              <span className="text-xs font-medium text-slate-300">
                {user?.username || 'User'}
              </span>
            </div>
            <button
              onClick={handleLogout}
              className="flex items-center gap-1.5 rounded-lg border border-slate-800 bg-slate-800/60 px-3 py-1.5 text-xs font-semibold text-slate-300 transition hover:bg-red-500/10 hover:border-red-500/30 hover:text-red-400"
              title="Sign out"
            >
              <LogOut className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">Logout</span>
            </button>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6">
        {/* Actions Grid */}
        <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
          {/* Create Room Card */}
          <div className="relative overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/60 p-6 shadow-xl backdrop-blur-sm">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-indigo-500/10 text-indigo-400 ring-1 ring-indigo-500/30">
                <PlusCircle className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-lg font-semibold text-white">Create a Room</h2>
                <p className="text-xs text-slate-400">Generate a 6-character room code</p>
              </div>
            </div>

            <p className="mt-4 text-sm text-slate-300 leading-relaxed">
              Create an instant private chat room and share the unique code with anyone on web or Android.
            </p>

            <button
              onClick={handleCreateRoom}
              disabled={creating}
              className="mt-6 flex w-full items-center justify-center gap-2 rounded-xl bg-indigo-600 px-4 py-3 text-sm font-semibold text-white shadow-lg shadow-indigo-600/30 transition hover:bg-indigo-500 disabled:opacity-50"
            >
              {creating ? (
                <div className="h-5 w-5 animate-spin rounded-full border-2 border-white border-t-transparent" />
              ) : (
                <>
                  <Sparkles className="h-4 w-4" />
                  <span>Generate New Room</span>
                </>
              )}
            </button>

            {/* Newly Created Room Popup */}
            {createdRoom && (
              <div className="mt-5 rounded-xl border border-emerald-500/30 bg-emerald-500/10 p-4 animate-in fade-in slide-in-from-top-2">
                <p className="text-xs font-semibold uppercase tracking-wider text-emerald-400">
                  Room Created Successfully!
                </p>
                <div className="mt-2 flex items-center justify-between gap-2 rounded-lg bg-slate-950/80 p-2.5 border border-emerald-500/20">
                  <span className="font-mono text-xl font-bold tracking-widest text-emerald-300">
                    {createdRoom.code}
                  </span>
                  <button
                    onClick={() => handleCopy(createdRoom.code)}
                    className="flex items-center gap-1.5 rounded-md bg-emerald-600/30 px-3 py-1.5 text-xs font-semibold text-emerald-300 hover:bg-emerald-600/40 transition"
                  >
                    {copiedCode ? <Check className="h-3.5 w-3.5" /> : <Copy className="h-3.5 w-3.5" />}
                    <span>{copiedCode ? 'Copied!' : 'Copy'}</span>
                  </button>
                </div>
                <div className="mt-3 flex justify-end">
                  <Link
                    href={`/room/${createdRoom.code}`}
                    className="inline-flex items-center gap-1.5 text-xs font-semibold text-emerald-400 hover:text-emerald-300 underline underline-offset-4"
                  >
                    Enter Room Now <ArrowRight className="h-3.5 w-3.5" />
                  </Link>
                </div>
              </div>
            )}
          </div>

          {/* Join Room Card */}
          <div className="relative overflow-hidden rounded-2xl border border-slate-800 bg-slate-900/60 p-6 shadow-xl backdrop-blur-sm">
            <div className="flex items-center gap-3">
              <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-purple-500/10 text-purple-400 ring-1 ring-purple-500/30">
                <LogIn className="h-5 w-5" />
              </div>
              <div>
                <h2 className="text-lg font-semibold text-white">Join a Room</h2>
                <p className="text-xs text-slate-400">Enter a 6-character room code</p>
              </div>
            </div>

            <form onSubmit={handleJoinRoom} className="mt-4 space-y-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400">
                  Room Code
                </label>
                <input
                  type="text"
                  maxLength={6}
                  value={joinCode}
                  onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
                  placeholder="e.g. 7K2M9X"
                  className="mt-2 block w-full rounded-xl border border-slate-800 bg-slate-950/80 px-4 py-2.5 font-mono text-base uppercase tracking-widest text-white placeholder-slate-600 transition focus:border-purple-500 focus:outline-none focus:ring-1 focus:ring-purple-500"
                />
              </div>

              {joinError && (
                <div className="rounded-lg border border-red-500/30 bg-red-500/10 p-2.5 text-xs text-red-400">
                  {joinError}
                </div>
              )}

              <button
                type="submit"
                disabled={joining || !joinCode.trim()}
                className="flex w-full items-center justify-center gap-2 rounded-xl bg-purple-600 px-4 py-3 text-sm font-semibold text-white shadow-lg shadow-purple-600/30 transition hover:bg-purple-500 disabled:opacity-50"
              >
                {joining ? (
                  <div className="h-5 w-5 animate-spin rounded-full border-2 border-white border-t-transparent" />
                ) : (
                  <>
                    <span>Join Room</span>
                    <ArrowRight className="h-4 w-4" />
                  </>
                )}
              </button>
            </form>
          </div>
        </div>

        {/* My Rooms Section */}
        <div className="mt-10">
          <div className="flex items-center justify-between">
            <h2 className="text-lg font-bold text-white tracking-tight">My Active Rooms</h2>
            <button
              onClick={loadRooms}
              className="text-xs font-medium text-slate-400 hover:text-indigo-400 transition"
            >
              Refresh
            </button>
          </div>

          {loadingRooms ? (
            <div className="mt-4 flex items-center justify-center py-12">
              <div className="h-6 w-6 animate-spin rounded-full border-2 border-indigo-500 border-t-transparent" />
            </div>
          ) : rooms.length === 0 ? (
            <div className="mt-4 rounded-2xl border border-dashed border-slate-800 p-8 text-center">
              <MessageSquare className="mx-auto h-8 w-8 text-slate-600" />
              <p className="mt-2 text-sm font-medium text-slate-400">No rooms joined yet</p>
              <p className="text-xs text-slate-500">
                Create a room above or enter a code to join an existing conversation.
              </p>
            </div>
          ) : (
            <div className="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {rooms.map((room) => (
                <Link
                  key={room.id}
                  href={`/room/${room.code}`}
                  className="group relative flex flex-col justify-between rounded-xl border border-slate-800 bg-slate-900/60 p-4 transition duration-200 hover:border-indigo-500/50 hover:bg-slate-900 hover:shadow-lg hover:shadow-indigo-500/10"
                >
                  <div className="flex items-center justify-between">
                    <span className="font-mono text-lg font-bold tracking-widest text-indigo-400 group-hover:text-indigo-300">
                      {room.code}
                    </span>
                    <span className="flex items-center gap-1 rounded-full bg-slate-800 px-2 py-0.5 text-xs text-slate-400">
                      <Users className="h-3 w-3" />
                      {room.member_count || 1}
                    </span>
                  </div>

                  <div className="mt-4 flex items-center justify-between text-xs text-slate-400">
                    <span className="truncate max-w-[120px]">
                      By @{room.creator_username}
                    </span>
                    <span className="flex items-center gap-1 text-slate-500">
                      <Clock className="h-3 w-3" />
                      {new Date(room.created_at).toLocaleDateString()}
                    </span>
                  </div>
                </Link>
              ))}
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
