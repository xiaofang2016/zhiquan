import { reactive } from "vue";
import { state, toast } from "../state.js";
import { postAccount } from "./api.js";
import { openAccount } from "./session.js";
import { animationSrc, applyScore, linesOf, playerPath, readPlayerRoute, sportType } from "./match.js";
import { listedMatch } from "./list.js";

const CHAT_API = "https://chat.kzbzcq.com/chat";
const DETAIL_API = {
  live: "https://live.kzbzcq.com/live",
  odds: "https://index.kzbzcq.com/index",
  data: "https://data.kzbzcq.com/data",
  tip: "https://tip.kzbzcq.com/tip"
};

export const room = reactive({
  mid: "",
  type: 1,
  messages: [],
  badges: {},
  exhausted: false,
  closed: false,
  loading: false,
  error: "",
  stick: true
});

let flvPlayer = null;
let chatTimer = null;
let detailTimer = null;

export function stopPlayback() {
  if (flvPlayer) {
    try { flvPlayer.pause(); flvPlayer.unload(); flvPlayer.detachMediaElement(); flvPlayer.destroy(); } catch (error) {}
    flvPlayer = null;
  }
}

export function stopRoomChat() {
  if (chatTimer) clearInterval(chatTimer);
  chatTimer = null;
}

export function stopDetailRoom() {
  if (detailTimer) clearInterval(detailTimer);
  detailTimer = null;
}

function ensureFlv() {
  if (window.flvjs) return Promise.resolve(window.flvjs);
  return new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://cdn.jsdelivr.net/npm/flv.js@1.6.2/dist/flv.min.js";
    script.onload = () => resolve(window.flvjs);
    script.onerror = () => reject(new Error("播放器加载失败"));
    document.head.append(script);
  });
}

export function openPlayer(match, nav) {
  state.tab = "match";
  state.detail = match;
  state.detailTab = "chat";
  state.detailData = null;
  state.detailError = "";
  const path = playerPath(match);
  if (nav !== "stay" && location.pathname !== path) {
    history.pushState({ player: String(match.id) }, "", path);
  }
  document.body.classList.add("playing");
}

export function closePlayer() {
  if (location.pathname.startsWith("/player/")) {
    if (history.state && history.state.player) {
      history.back();
      return;
    }
    history.replaceState({}, "", "/");
  }
  state.detail = null;
  stopPlayback();
  stopRoomChat();
  stopDetailRoom();
  document.body.classList.remove("playing");
}

export async function loadPlayer(route) {
  let match = null;
  try {
    const query = new URLSearchParams({
      mid: route.id,
      type: String(route.type),
      isnew: "1",
      pid: "4",
      langtype: "zh",
      test: "1",
      zoneId: "Asia/Shanghai"
    });
    const response = await fetch("https://kzb001.net/prod-api/match/detail?" + query);
    const body = await response.json();
    const info = body && body.data && body.data.matchinfo;
    if (info && String(info.id) === String(route.id)) match = info;
  } catch (error) {}
  if (!match) match = await listedMatch(route);
  const current = readPlayerRoute();
  if (current && current.id !== String(route.id)) return;
  if (!match) match = { id: route.id, type: route.type, status_up_name: "未开始" };
  openPlayer(match, "stay");
}

export function openPlayback(href) {
  if (!href || !href.startsWith("/player/")) return false;
  if (location.pathname !== href) history.pushState({ player: href }, "", href);
  onRoute();
  return true;
}

export function onRoute() {
  const route = readPlayerRoute();
  if (!route) {
    state.detail = null;
    stopPlayback();
    stopRoomChat();
    stopDetailRoom();
    document.body.classList.remove("playing");
    return;
  }
  if (state.detail && String(state.detail.id) === route.id) return;
  loadPlayer(route);
}

async function fetchRoomChat(query) {
  const response = await fetch(CHAT_API + "?" + query);
  const body = await response.json();
  if (!body || body.code !== "0") throw new Error((body && body.msg) || "聊天暂时没有拿到");
  return body;
}

function rememberBadges(body) {
  for (const [level, url] of Object.entries((body && body.badges) || {})) {
    if (url) room.badges[level] = url;
  }
  for (const message of (body && body.messages) || []) {
    if (message && message.badge && message.level != null) room.badges[message.level] = message.badge;
  }
}

function mergeRoomMessages(incoming) {
  const seen = new Set(room.messages.map((message) => message.id));
  for (const message of incoming || []) {
    if (!message || !message.id || seen.has(message.id)) continue;
    seen.add(message.id);
    room.messages.push(message);
  }
  room.messages.sort((left, right) => left.time - right.time);
}

export async function loadRoomChat(match, before) {
  const mid = String(match.id || "");
  if (!/^[0-9]+$/.test(mid)) return;
  if (room.mid !== mid) {
    room.mid = mid;
    room.type = sportType(match);
    room.messages = [];
    room.badges = {};
    room.exhausted = false;
    room.closed = false;
    room.error = "";
    room.stick = true;
  }
  if (room.loading || (before && room.exhausted)) return;
  room.loading = true;
  try {
    const query = new URLSearchParams({ mid, type: String(room.type) });
    if (before) query.set("before", String(before));
    const body = await fetchRoomChat(query);
    if (room.mid !== mid) return;
    rememberBadges(body);
    if (before) room.stick = false;
    mergeRoomMessages(body.messages);
    room.closed = Boolean(body.closed);
    room.exhausted = Boolean(body.exhausted);
    room.error = "";
  } catch (error) {
    if (!room.messages.length) room.error = error.message || "聊天暂时没有拿到";
  } finally {
    room.loading = false;
  }
}

export function openRoomChat(match) {
  const mid = String(match.id || "");
  loadRoomChat(match);
  if (chatTimer) clearInterval(chatTimer);
  chatTimer = setInterval(() => {
    if (!state.detail || String(state.detail.id) !== mid || state.detailTab !== "chat") return;
    const newest = room.messages[room.messages.length - 1];
    if (!newest || room.loading) return;
    fetchRoomChat(new URLSearchParams({ mid, type: String(room.type), after: String(newest.time) })).then((body) => {
      if (room.mid !== mid) return;
      rememberBadges(body);
      mergeRoomMessages(body.messages);
      room.closed = Boolean(body.closed);
    }).catch(() => {});
  }, 4000);
}

export async function sendRoomChat(content) {
  const text = String(content || "").trim();
  if (!text) return false;
  if (!state.user || !state.user.token) {
    openAccount("login");
    return false;
  }
  if (room.closed) {
    toast("当前聊天室已关闭");
    return false;
  }
  const message = await postAccount("/prod-api/chat/messages", {
    token: state.user.token,
    mid: room.mid,
    content: text
  });
  if (room.mid) mergeRoomMessages([message]);
  return true;
}

export function chatOlder(match) {
  const oldest = room.messages[0];
  if (oldest) loadRoomChat(match, oldest.time);
}

export async function loadDetail(match, tab, silent) {
  const mid = String(match.id || "");
  if (!silent) {
    state.detailLoading = true;
    state.detailError = "";
    state.detailData = null;
  }
  try {
    const query = new URLSearchParams({ mid, type: String(sportType(match)) });
    const response = await fetch(DETAIL_API[tab] + "?" + query);
    const body = await response.json();
    if (!state.detail || String(state.detail.id) !== mid || state.detailTab !== tab) return;
    if (!body || body.code !== "0") {
      state.detailError = (body && body.msg) || "这场资料暂时没有拿到";
      state.detailData = null;
      return;
    }
    state.detailError = "";
    state.detailData = body.data;
  } catch (error) {
    if (!silent && state.detail && String(state.detail.id) === mid && state.detailTab === tab) {
      state.detailError = "这场资料暂时没有拿到";
    }
  } finally {
    if (state.detail && String(state.detail.id) === mid && state.detailTab === tab) state.detailLoading = false;
  }
  if ((tab === "live" || tab === "odds") && !detailTimer && state.detailTab === tab) {
    detailTimer = setInterval(() => {
      if (!state.detail || String(state.detail.id) !== mid || state.detailTab !== tab) {
        stopDetailRoom();
        return;
      }
      loadDetail(match, tab, true);
    }, 8000);
  }
}

export function showDetailTab(match, tab) {
  state.detailTab = tab;
  stopRoomChat();
  stopDetailRoom();
  if (tab === "chat") openRoomChat(match);
  else loadDetail(match, tab, false);
}

export async function playLine(choice, video) {
  stopPlayback();
  if (!choice || choice.animation) return "cover";
  try {
    const flvjs = await ensureFlv();
    if (!flvjs || !flvjs.isSupported()) throw new Error("当前浏览器不能播放 FLV");
    flvPlayer = flvjs.createPlayer({ type: "flv", url: choice.url, isLive: true, cors: true }, { enableStashBuffer: false });
    flvPlayer.on(flvjs.Events.ERROR, () => video.dispatchEvent(new Event("error")));
    flvPlayer.attachMediaElement(video);
    flvPlayer.load();
    video.play().catch(() => {});
    return "video";
  } catch (error) {
    toast(error.message || "播放失败");
    return "cover";
  }
}

export function detailLines(match) {
  return linesOf(applyScore(match));
}

export function animationFrame(choice) {
  return choice && choice.animation ? animationSrc(choice.url) : "";
}
