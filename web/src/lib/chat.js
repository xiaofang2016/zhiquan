import { get } from "./api.js";
import { state, toast } from "../state.js";

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
  state.chatGroupId = group.id;
  state.chatMessages = [];
  state.chatOlder = "";
  state.chatPaging = false;
  state.chatLoading = true;
  state.chatError = "";
  try {
    const data = await get("/prod-api/chat/groups/" + group.id + "/messages");
    if (state.chatGroupId === group.id) {
      state.chatMessages = (data && data.messages) || [];
      state.chatOlder = (data && data.older) || "";
      if (data && data.imageBase) state.chatImageBase = data.imageBase;
    }
  } catch (error) {
    if (state.chatGroupId === group.id) {
      state.chatError = error.message || "这个群的消息没有拿到";
      toast(state.chatError);
    }
  } finally {
    if (state.chatGroupId === group.id) state.chatLoading = false;
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
