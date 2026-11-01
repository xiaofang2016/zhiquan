import { get } from "./api.js";
import { CHAT_WS, state, toast } from "../state.js";

export function chatTime(value) {
  const date = new Date(Number(value));
  if (!value || Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23"
  }).format(date);
}

export async function loadChatGroups() {
  if (state.chatLoading) return;
  state.chatLoading = true;
  state.chatError = "";
  try {
    const data = await get("/prod-api/chat/groups");
    state.chatPartition = (data && data.partition) || "";
    state.chatGroups = (data && data.groups) || [];
    if (data && data.imageBase) state.chatImageBase = data.imageBase;
    const current = state.chatGroups.find((group) => group.id === state.chatGroupId);
    if (!current) {
      state.chatGroupId = 0;
      state.chatMessages = [];
      state.chatOlder = "";
    }
  } catch (error) {
    state.chatError = error.message || "聊天数据没有拿到";
    toast(state.chatError);
  } finally {
    state.chatLoading = false;
  }
  const current = state.chatGroups.find((group) => group.id === state.chatGroupId);
  if (current) await openChatGroup(current);
}

export async function openChatGroup(group) {
  if (!group) return;
  const epoch = ++openEpoch;
  state.chatGroupId = group.id;
  state.chatMessages = [];
  state.chatOlder = "";
  state.chatPaging = false;
  state.chatLoading = true;
  state.chatError = "";
  try {
    const data = await get("/prod-api/chat/groups/" + group.id + "/messages");
    if (state.chatGroupId !== group.id || epoch !== openEpoch) return;
    const incoming = (data && data.messages) || [];
    const seen = new Set(incoming.map((message) => message.id).filter(Boolean));
    const raced = state.chatMessages.filter((message) => message.id && !seen.has(message.id));
    state.chatMessages = incoming.concat(raced);
    state.chatOlder = (data && data.older) || "";
    if (data && data.imageBase) state.chatImageBase = data.imageBase;
  } catch (error) {
    if (state.chatGroupId === group.id) {
      state.chatError = error.message || "这个群的消息没有拿到";
      toast(state.chatError);
    }
  } finally {
    if (state.chatGroupId === group.id && epoch === openEpoch) state.chatLoading = false;
  }
}

export async function loadOlderMessages() {
  const groupId = state.chatGroupId;
  const before = state.chatOlder;
  if (!groupId || !before || state.chatPaging || state.chatLoading) return 0;
  state.chatPaging = true;
  try {
    const data = await get("/prod-api/chat/groups/" + groupId + "/messages?before=" + encodeURIComponent(before));
    if (state.chatGroupId !== groupId) return 0;
    const older = (data && data.messages) || [];
    const seen = new Set(state.chatMessages.map((message) => message.id).filter(Boolean));
    const fresh = older.filter((message) => !message.id || !seen.has(message.id));
    state.chatMessages = fresh.concat(state.chatMessages);
    state.chatOlder = (data && data.older) || "";
    return fresh.length;
  } catch (error) {
    if (state.chatGroupId === groupId) toast(error.message || "更早的消息没有拿到");
    return -1;
  } finally {
    if (state.chatGroupId === groupId) state.chatPaging = false;
  }
}

let openEpoch = 0;
let socket = null;
let liveTimer = 0;
let liveStopped = true;
let liveRetry = 1000;

export function startChatLive() {
  liveStopped = false;
  connectChatLive();
}

export function stopChatLive() {
  liveStopped = true;
  clearInterval(liveTimer);
  liveTimer = 0;
  if (!socket) return;
  socket.onclose = null;
  socket.close();
  socket = null;
}

export function subscribeChatLive() {
  sendLive({ action: "subscribe", groupId: state.chatGroupId || 0 });
}

function connectChatLive() {
  if (liveStopped || !CHAT_WS || (socket && socket.readyState < 2)) return;
  const ws = new WebSocket(CHAT_WS);
  socket = ws;
  ws.onopen = () => {
    liveRetry = 1000;
    subscribeChatLive();
    clearInterval(liveTimer);
    liveTimer = setInterval(() => sendLive({ action: "ping" }), 240000);
  };
  ws.onmessage = (event) => {
    let body;
    try {
      body = JSON.parse(event.data);
    } catch {
      return;
    }
    if (body && body.message) acceptLive(body.groupId, body.message);
  };
  ws.onclose = () => {
    clearInterval(liveTimer);
    liveTimer = 0;
    if (socket === ws) socket = null;
    if (liveStopped) return;
    setTimeout(connectChatLive, liveRetry);
    liveRetry = Math.min(liveRetry * 2, 30000);
  };
}

function sendLive(body) {
  if (!socket || socket.readyState !== 1) return;
  socket.send(JSON.stringify(body));
}

function acceptLive(groupId, message) {
  const id = Number(groupId);
  if (!id || !message || !message.id) return;
  const group = state.chatGroups.find((item) => item.id === id);
  if (group && (!group.latest || Number(message.time) >= Number(group.latest.time))) {
    group.latest = message;
    state.chatGroups.sort((a, b) => ((b.latest && b.latest.time) || 0) - ((a.latest && a.latest.time) || 0));
  }
  if (id !== state.chatGroupId) return;
  if (state.chatMessages.some((item) => item.id === message.id)) return;
  state.chatMessages.push(message);
  state.chatLiveSeq += 1;
}
