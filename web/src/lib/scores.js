import { API, state, toast } from "../state.js";
import { phase, visibleMatches } from "./match.js";
import { stopPlayback, stopRoomChat, stopDetailRoom } from "./detail.js";

let scoreCursor = 0;
let matchAudio = null;
let soundBusy = false;
let soundBlocked = false;
const soundQueue = [];

export function matchFlag(key, fallback) {
  const raw = localStorage.getItem(key);
  if (raw === null) return fallback;
  try { return JSON.parse(raw) === true; }
  catch (error) { return fallback; }
}

export const MATCH_SOUNDS = [
  ["mute", "静音"],
  ["default", "默认"],
  ["whistle", "哨子"],
  ["drum", "鼓声"],
  ["broadcast", "广播"],
  ["horn", "号角"],
  ["victory", "胜利"]
];

export function soundChoice(side) {
  const raw = localStorage.getItem(side === "away" ? "SetUpCom-awaySound" : "SetUpCom-homeSound");
  return MATCH_SOUNDS.some((item) => item[0] === raw) ? raw : "default";
}

export function soundLabel(key) {
  const found = MATCH_SOUNDS.find((item) => item[0] === key);
  return found ? found[1] : "默认";
}

export function playMatchSound(key) {
  if (!key || key === "mute") return;
  soundQueue.push(key);
  drainSounds();
}

function drainSounds() {
  if (soundBusy || soundBlocked || !soundQueue.length) return;
  const key = soundQueue.shift();
  const audio = matchAudio || (matchAudio = new Audio());
  const next = () => { soundBusy = false; drainSounds(); };
  audio.onended = next;
  audio.onerror = next;
  audio.src = API + "/prod-api/match/sound/" + key;
  soundBusy = true;
  audio.currentTime = 0;
  const played = audio.play();
  if (played && played.catch) played.catch(() => {
    soundBusy = false;
    soundQueue.unshift(key);
    soundBlocked = true;
  });
}

document.addEventListener("pointerdown", () => {
  if (!soundBlocked) return;
  soundBlocked = false;
  drainSounds();
}, true);

function scorePair(value) {
  const matched = String(value == null ? "" : value).match(/(\d+)\s*[-:]\s*(\d+)/);
  return matched ? [Number(matched[1]), Number(matched[2])] : null;
}

function hearScore(prev, match) {
  if (!prev) return;
  const before = scorePair(prev.score);
  const after = scorePair(match.score != null && match.score !== "" ? match.score : prev.score);
  if (before && after && matchFlag("SetUpCom-checked", true)) {
    if (after[0] > before[0]) playMatchSound(soundChoice("home"));
    if (after[1] > before[1]) playMatchSound(soundChoice("away"));
  }
  if (!matchFlag("SetUpCom-checkedRed", false)) return;
  const homeRed = Number(match.hteam_red != null && match.hteam_red !== "" ? match.hteam_red : prev.hteam_red) || 0;
  const awayRed = Number(match.ateam_red != null && match.ateam_red !== "" ? match.ateam_red : prev.ateam_red) || 0;
  if (homeRed > (Number(prev.hteam_red) || 0)) playMatchSound(soundChoice("home"));
  if (awayRed > (Number(prev.ateam_red) || 0)) playMatchSound(soundChoice("away"));
}

function rememberScores(rows) {
  for (const match of rows) {
    const id = Number(match.id);
    const prev = state.scoreById.get(id);
    hearScore(prev, match);
    state.scoreById.set(id, prev ? { ...prev, ...match } : match);
  }
}

export async function fetchScorePage(page, since) {
  const query = new URLSearchParams({ type: "0", cid: "0", ishot: "-1", pn: String(page), ps: "200" });
  if (since) query.set("since", String(since));
  const response = await fetch(API + "/prod-api/match/list/new2?" + query, { cache: "no-store" });
  const body = await response.json();
  if (!response.ok || String(body.code) !== "0") throw new Error((body && body.msg) || "即时比分加载失败");
  return body.data || {};
}

function takeScores(data) {
  if (data.updatedAt) scoreCursor = data.updatedAt;
  if (!Array.isArray(data.changed)) {
    rememberScores([...(data.notopList || []), ...(data.topList || []), ...(data.dataList || []), ...(data.overList || []), ...(data.otherList || [])]);
    return false;
  }
  rememberScores(data.changed);
  for (const id of data.removed || []) state.scoreById.delete(Number(id));
  return true;
}

export async function refreshScores() {
  if (document.visibilityState === "hidden") return;
  const data = await fetchScorePage(1, scoreCursor);
  const incremental = takeScores(data);
  if (incremental && !(data.changed || []).length && !(data.removed || []).length) return;
  if (state.tab === "live") return;
  if (state.detail) return;
  visibleMatches();
}

export async function loadLive() {
  if (state.loading) return;
  state.loading = true;
  try {
    const rows = [];
    let page = 1;
    let total = 1;
    while (page <= total && page <= 3) {
      const data = await fetchScorePage(page);
      if (data.updatedAt) scoreCursor = data.updatedAt;
      if (page === 1) rows.push(...(data.notopList || []), ...(data.topList || []), ...(data.overList || []), ...(data.otherList || []));
      rows.push(...(data.dataList || []));
      total = data.totalPage || 1;
      page += 1;
    }
    rememberScores(rows);
    state.liveRows = rows;
    state.liveLoaded = true;
  } catch (error) {
    state.liveLoaded = true;
    toast(error.message || "即时比分加载失败");
  } finally {
    state.loading = false;
  }
}

export function selectTab(id) {
  if (id !== state.tab) {
    state.detail = null;
    stopPlayback();
    stopRoomChat();
    stopDetailRoom();
    document.body.classList.remove("playing");
  }
  state.tab = id;
  if (id === "live" && !state.liveLoaded && !state.loading) loadLive();
}

export function liveRows() {
  const order = { live: 0, wait: 1, done: 2 };
  return state.liveRows.filter((match) => {
    if (state.liveLeague && match.cid !== state.liveLeague) return false;
    const kind = phase(match);
    if (state.liveMode === "result") return kind === "done";
    if (state.liveMode === "schedule") return kind === "wait";
    if (state.liveMode === "all") return true;
    return kind === "live" || kind === "wait";
  }).sort((a, b) => order[phase(a)] - order[phase(b)]);
}
