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
    const current = state.chatGroups.find((group) => group.id === state.chatGroupId);
    if (!current) {
      state.chatGroupId = 0;
      state.chatMessages = [];
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
  state.chatLoading = true;
  state.chatError = "";
  try {
    const data = await get("/prod-api/chat/groups/" + group.id + "/messages");
    if (state.chatGroupId === group.id) {
      state.chatMessages = (data && data.messages) || [];
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
