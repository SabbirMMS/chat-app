'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { io } from 'socket.io-client';
import { getToken, getStoredUser, clearAuth, fetchWithAuth } from '../../../lib/clientAuth';
import {
  ArrowLeft,
  Copy,
  Check,
  CheckCheck,
  Send,
  Users,
  Wifi,
  WifiOff,
  AlertCircle,
  Eye,
} from 'lucide-react';

export default function RoomChatPage() {
  const params = useParams();
  const router = useRouter();
  const rawCode = params?.code;
  const code = typeof rawCode === 'string' ? rawCode.toUpperCase().trim() : '';

  const [user, setUser] = useState(null);
  const [messages, setMessages] = useState([]);
  const [inputContent, setInputContent] = useState('');
  const [loadingHistory, setLoadingHistory] = useState(true);
  const [error, setError] = useState('');
  const [copied, setCopied] = useState(false);
  const [socketStatus, setSocketStatus] = useState('connecting'); // 'connecting' | 'connected' | 'disconnected'
  const [systemNotices, setSystemNotices] = useState([]);

  // Live presence & typing states
  const [onlineMembers, setOnlineMembers] = useState([]);
  const [showMembersModal, setShowMembersModal] = useState(false);
  const [typingUsers, setTypingUsers] = useState([]);

  const socketRef = useRef(null);
  const messagesEndRef = useRef(null);
  const inputRef = useRef(null);
  const typingTimerRef = useRef(null);
  const isTypingRef = useRef(false);

  const scrollToBottom = (behavior = 'smooth') => {
    messagesEndRef.current?.scrollIntoView({ behavior });
  };

  // Add system notice with auto-remove
  const addSystemNotice = useCallback((text) => {
    const id = Date.now() + Math.random();
    setSystemNotices((prev) => [...prev, { id, text }]);
    setTimeout(() => {
      setSystemNotices((prev) => prev.filter((n) => n.id !== id));
    }, 4000);
  }, []);

  // 1. Authenticate user & load initial message history via REST
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

    async function loadHistory() {
      try {
        setLoadingHistory(true);
        const res = await fetchWithAuth(`/api/rooms/${code}/messages?limit=50`);
        const data = await res.json();

        if (!res.ok) {
          throw new Error(data.error || 'Failed to load message history');
        }

        setMessages(data.messages || []);
        setTimeout(() => scrollToBottom('auto'), 100);

        // Mark messages as seen once loaded
        if (socketRef.current?.connected) {
          socketRef.current.emit('mark_all_seen', { code });
        }
      } catch (err) {
        setError(err.message);
      } finally {
        setLoadingHistory(false);
      }
    }

    if (code) {
      loadHistory();
    }
  }, [code, router]);

  // 2. Setup Socket.IO connection
  useEffect(() => {
    const token = getToken();
    if (!token || !code) return;

    const socket = io({
      path: '/socket.io',
      auth: { token },
      transports: ['websocket', 'polling'],
    });

    socketRef.current = socket;

    socket.on('connect', () => {
      setSocketStatus('connected');
      socket.emit('join_room', { code }, (response) => {
        if (response && !response.ok) {
          setError(response.error || 'Failed to join socket room');
        } else if (response && response.members) {
          setOnlineMembers(response.members);
        }
        // Mark all existing messages as seen
        socket.emit('mark_all_seen', { code });
      });
    });

    socket.on('disconnect', () => {
      setSocketStatus('disconnected');
    });

    socket.on('connect_error', (err) => {
      console.error('Socket connect error:', err.message);
      setSocketStatus('disconnected');
    });

    // Realtime message arrived
    socket.on('new_message', (message) => {
      if (message.code === code) {
        setMessages((prev) => {
          if (prev.some((m) => m.id === message.id)) {
            return prev;
          }
          return [...prev, { ...message, seen_by: message.seen_by || [] }];
        });
        setTimeout(() => scrollToBottom('smooth'), 50);

        // Automatically mark as seen if from another user
        socket.emit('mark_seen', { code, messageId: message.id });
      }
    });

    // Realtime seen events
    socket.on('message_seen', (data) => {
      if (data.code === code) {
        setMessages((prev) =>
          prev.map((m) => {
            if (m.id === data.messageId) {
              const currentSeen = m.seen_by || [];
              if (currentSeen.some((u) => u.id === data.user.id)) return m;
              return { ...m, seen_by: [...currentSeen, data.user] };
            }
            return m;
          })
        );
      }
    });

    socket.on('room_messages_seen', (data) => {
      if (data.code === code) {
        setMessages((prev) =>
          prev.map((m) => {
            if (m.sender?.id === data.user.id) return m; // sender already saw their own message
            const currentSeen = m.seen_by || [];
            if (currentSeen.some((u) => u.id === data.user.id)) return m;
            return { ...m, seen_by: [...currentSeen, data.user] };
          })
        );
      }
    });

    // Realtime typing events
    socket.on('user_typing', (data) => {
      if (data.code === code && data.user) {
        setTypingUsers((prev) => {
          if (data.isTyping) {
            if (!prev.includes(data.user.username)) {
              return [...prev, data.user.username];
            }
            return prev;
          } else {
            return prev.filter((u) => u !== data.user.username);
          }
        });
      }
    });

    // Online presence updates
    socket.on('room_members', (data) => {
      if (data.code === code && data.members) {
        setOnlineMembers(data.members);
      }
    });

    socket.on('user_joined', (data) => {
      if (data.code === code && data.user?.username) {
        addSystemNotice(`${data.user.username} joined the room`);
      }
    });

    socket.on('user_left', (data) => {
      if (data.code === code && data.user?.username) {
        addSystemNotice(`${data.user.username} left the room`);
        setTypingUsers((prev) => prev.filter((u) => u !== data.user.username));
      }
    });

    return () => {
      socket.disconnect();
    };
  }, [code, addSystemNotice]);

  // Handle typing debounce
  const handleInputChange = (e) => {
    const val = e.target.value;
    setInputContent(val);

    if (!socketRef.current || socketStatus !== 'connected') return;

    if (!isTypingRef.current && val.trim().length > 0) {
      isTypingRef.current = true;
      socketRef.current.emit('typing_start', { code });
    }

    if (typingTimerRef.current) {
      clearTimeout(typingTimerRef.current);
    }

    typingTimerRef.current = setTimeout(() => {
      if (isTypingRef.current) {
        isTypingRef.current = false;
        socketRef.current?.emit('typing_stop', { code });
      }
    }, 2500);
  };

  // Send message handler
  const handleSendMessage = (e) => {
    if (e) e.preventDefault();

    const content = inputContent.trim();
    if (!content || !socketRef.current || socketStatus !== 'connected') {
      return;
    }

    if (content.length > 2000) {
      alert('Message must not exceed 2000 characters');
      return;
    }

    // Stop typing immediately
    if (typingTimerRef.current) {
      clearTimeout(typingTimerRef.current);
    }
    isTypingRef.current = false;
    socketRef.current.emit('typing_stop', { code });

    setInputContent('');

    socketRef.current.emit(
      'send_message',
      { code, content },
      (response) => {
        if (response && !response.ok) {
          alert(response.error || 'Failed to send message');
        }
      }
    );

    inputRef.current?.focus();
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const handleCopyCode = () => {
    navigator.clipboard.writeText(code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const formatTime = (isoString) => {
    try {
      const date = new Date(isoString);
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    } catch {
      return '';
    }
  };

  return (
    <div className="flex h-screen flex-col bg-slate-950 text-slate-100">
      {/* Header */}
      <header className="flex h-16 shrink-0 items-center justify-between border-b border-slate-800 bg-slate-900/90 px-4 sm:px-6 backdrop-blur-md">
        <div className="flex items-center gap-3">
          <Link
            href="/dashboard"
            className="flex h-9 w-9 items-center justify-center rounded-lg border border-slate-800 bg-slate-800/80 text-slate-300 transition hover:bg-slate-700 hover:text-white"
            title="Back to Dashboard"
          >
            <ArrowLeft className="h-4 w-4" />
          </Link>

          <div>
            <div className="flex items-center gap-2">
              <span className="font-mono text-base font-bold tracking-widest text-indigo-400 sm:text-lg">
                #{code}
              </span>
              <button
                onClick={handleCopyCode}
                className="flex items-center gap-1 rounded-md bg-slate-800 px-2 py-0.5 text-xs font-medium text-slate-300 hover:bg-slate-700 hover:text-white transition"
                title="Copy Room Code"
              >
                {copied ? <Check className="h-3 w-3 text-emerald-400" /> : <Copy className="h-3 w-3" />}
                <span className="text-[10px]">{copied ? 'Copied' : 'Share'}</span>
              </button>
            </div>
            <p className="text-[11px] text-slate-400">Realtime Conversation</p>
          </div>
        </div>

        {/* Status & Online Members Indicator */}
        <div className="flex items-center gap-2">
          {/* Active Members Button */}
          <button
            onClick={() => setShowMembersModal(!showMembersModal)}
            className="inline-flex items-center gap-1.5 rounded-full bg-slate-800 px-3 py-1 text-xs font-medium text-slate-300 hover:bg-slate-700 transition border border-slate-700"
            title="View Active Room Members"
          >
            <Users className="h-3.5 w-3.5 text-indigo-400" />
            <span>{onlineMembers.length || 1} online</span>
          </button>

          {/* Connection status */}
          {socketStatus === 'connected' ? (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-emerald-500/10 px-2.5 py-1 text-xs font-medium text-emerald-400 border border-emerald-500/20">
              <Wifi className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">Connected</span>
            </span>
          ) : socketStatus === 'connecting' ? (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-500/10 px-2.5 py-1 text-xs font-medium text-amber-400 border border-amber-500/20">
              <div className="h-2 w-2 animate-ping rounded-full bg-amber-400" />
              <span className="hidden sm:inline">Connecting</span>
            </span>
          ) : (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-red-500/10 px-2.5 py-1 text-xs font-medium text-red-400 border border-red-500/20">
              <WifiOff className="h-3.5 w-3.5" />
              <span className="hidden sm:inline">Offline</span>
            </span>
          )}
        </div>
      </header>

      {/* Online Members Dropdown / Modal */}
      {showMembersModal && (
        <div className="relative z-30 bg-slate-900 border-b border-slate-800 px-4 py-3 animate-in slide-in-from-top-2">
          <div className="mx-auto max-w-3xl flex items-center justify-between">
            <div className="flex items-center gap-2">
              <Eye className="h-4 w-4 text-indigo-400" />
              <span className="text-xs font-semibold text-white">Active Members in #{code}:</span>
            </div>
            <button
              onClick={() => setShowMembersModal(false)}
              className="text-xs text-slate-400 hover:text-white"
            >
              Close
            </button>
          </div>
          <div className="mx-auto max-w-3xl mt-2 flex flex-wrap gap-2">
            {onlineMembers.map((m) => (
              <span
                key={m.id}
                className="inline-flex items-center gap-1.5 rounded-full bg-slate-800/80 px-2.5 py-0.5 text-xs text-slate-300 border border-slate-700"
              >
                <span className="h-1.5 w-1.5 rounded-full bg-emerald-400" />
                @{m.username} {m.id === user?.id && '(You)'}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Error Banner */}
      {error && (
        <div className="flex items-center gap-2 bg-red-500/10 border-b border-red-500/20 px-4 py-2 text-xs text-red-400">
          <AlertCircle className="h-4 w-4 shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* System Presence Notifications */}
      {systemNotices.length > 0 && (
        <div className="pointer-events-none fixed top-20 left-1/2 -translate-x-1/2 z-30 flex flex-col gap-1.5">
          {systemNotices.map((n) => (
            <div
              key={n.id}
              className="rounded-full bg-slate-800/90 border border-slate-700 px-3 py-1 text-xs font-medium text-slate-300 shadow-lg backdrop-blur-sm"
            >
              {n.text}
            </div>
          ))}
        </div>
      )}

      {/* Messages Stream */}
      <div className="flex-1 overflow-y-auto px-4 py-6 sm:px-6">
        <div className="mx-auto max-w-3xl space-y-4">
          {loadingHistory ? (
            <div className="flex flex-col items-center justify-center py-20 text-slate-500">
              <div className="h-8 w-8 animate-spin rounded-full border-2 border-indigo-500 border-t-transparent" />
              <p className="mt-3 text-xs">Loading conversation history...</p>
            </div>
          ) : messages.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-24 text-center">
              <div className="flex h-12 w-12 items-center justify-center rounded-2xl bg-indigo-500/10 text-indigo-400 ring-1 ring-indigo-500/20">
                <Users className="h-6 w-6" />
              </div>
              <h3 className="mt-3 text-sm font-semibold text-white">No messages yet</h3>
              <p className="mt-1 max-w-sm text-xs text-slate-400">
                Be the first to say something! Share room code <span className="font-mono font-bold text-indigo-400">{code}</span> with your friend.
              </p>
            </div>
          ) : (
            messages.map((msg) => {
              const isMe = msg.sender?.id === user?.id;
              const seenUsers = (msg.seen_by || []).filter((u) => u.id !== msg.sender?.id);

              return (
                <div
                  key={msg.id}
                  className={`flex flex-col ${isMe ? 'items-end' : 'items-start'}`}
                >
                  <div className="flex items-center gap-2 mb-1 px-1">
                    <span className="text-xs font-semibold text-slate-400">
                      {isMe ? 'You' : msg.sender?.username || 'Unknown'}
                    </span>
                    <span className="text-[10px] text-slate-500">
                      {formatTime(msg.created_at)}
                    </span>
                  </div>

                  <div
                    className={`max-w-[85%] sm:max-w-md rounded-2xl px-4 py-2.5 text-sm break-words shadow-sm leading-relaxed ${
                      isMe
                        ? 'bg-indigo-600 text-white rounded-tr-none'
                        : 'bg-slate-800 text-slate-100 border border-slate-700/60 rounded-tl-none'
                    }`}
                  >
                    {msg.content}
                  </div>

                  {/* Seen Members List / Read Receipts */}
                  {seenUsers.length > 0 && (
                    <div className="flex items-center gap-1 mt-1 px-1 text-[11px] text-slate-400">
                      <CheckCheck className="h-3 w-3 text-indigo-400" />
                      <span>
                        Seen by {seenUsers.map((u) => u.username).join(', ')}
                      </span>
                    </div>
                  )}
                </div>
              );
            })
          )}
          <div ref={messagesEndRef} />
        </div>
      </div>

      {/* Typing Indicator Banner */}
      {typingUsers.length > 0 && (
        <div className="px-4 py-1.5 sm:px-6 bg-slate-900/60 border-t border-slate-800/60">
          <div className="mx-auto max-w-3xl flex items-center gap-2 text-xs text-indigo-400 font-medium">
            <div className="flex items-center gap-1">
              <span className="h-1.5 w-1.5 rounded-full bg-indigo-400 animate-bounce" />
              <span className="h-1.5 w-1.5 rounded-full bg-indigo-400 animate-bounce [animation-delay:0.2s]" />
              <span className="h-1.5 w-1.5 rounded-full bg-indigo-400 animate-bounce [animation-delay:0.4s]" />
            </div>
            <span>
              {typingUsers.join(', ')} {typingUsers.length > 1 ? 'are' : 'is'} typing...
            </span>
          </div>
        </div>
      )}

      {/* Input Bar */}
      <div className="border-t border-slate-800 bg-slate-900/90 p-3 sm:p-4 backdrop-blur-md">
        <form
          onSubmit={handleSendMessage}
          className="mx-auto flex max-w-3xl items-center gap-2"
        >
          <input
            ref={inputRef}
            type="text"
            value={inputContent}
            onChange={handleInputChange}
            onKeyDown={handleKeyDown}
            placeholder={
              socketStatus === 'connected'
                ? 'Type your message... (Enter to send)'
                : 'Connecting to room...'
            }
            disabled={socketStatus !== 'connected'}
            maxLength={2000}
            className="flex-1 rounded-xl border border-slate-800 bg-slate-950/80 px-4 py-3 text-sm text-white placeholder-slate-500 transition focus:border-indigo-500 focus:outline-none focus:ring-1 focus:ring-indigo-500 disabled:opacity-50"
          />

          <button
            type="submit"
            disabled={socketStatus !== 'connected' || !inputContent.trim()}
            className="flex h-11 w-11 items-center justify-center rounded-xl bg-indigo-600 text-white shadow-lg shadow-indigo-600/30 transition hover:bg-indigo-500 disabled:opacity-40 disabled:hover:bg-indigo-600"
            title="Send message"
          >
            <Send className="h-4 w-4" />
          </button>
        </form>
      </div>
    </div>
  );
}
