import { WEEK, state } from "../state.js";

const FOOT_STATUS = ["比赛异常", "未开赛", "上半场", "中场", "下半场", "加时赛", "加时赛", "点球决战", "完场", "推迟", "中断", "腰斩", "取消", "待定"];
const BASKET_STATUS = ["比赛异常", "未开赛", "第一节", "第一节完", "第二节", "第二节完", "第三节", "第三节完", "第四节", "加时", "完场", "中断", "取消", "延期", "腰斩", "待定"];
const OTHER_STATUS = { 1: "未开赛", 2: "比赛中", 3: "完场" };

export function sportType(match) {
  const type = Number(match && match.type);
  return type === 1 || type === 2 || type === 3 ? type : 1;
}

export function browserZone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || "Asia/Shanghai";
  } catch (error) {
    return "Asia/Shanghai";
  }
}

export function shanghaiDay(offset) {
  const key = new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
  const base = new Date(key + "T00:00:00+08:00");
  return new Intl.DateTimeFormat("en-CA", { timeZone: "Asia/Shanghai", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date(base.getTime() + offset * 86400000));
}

export function phase(match) {
  const type = sportType(match);
  if (type === 3) {
    const code = Number(match.status);
    if (code === 2) return "live";
    if (code >= 3) return "done";
    return "wait";
  }
  const name = String(match.status_up_name ?? "");
  if (/第[一二三四]节/.test(name)) return "live";
  if (/上半场|下半场|中场|进行|比赛中|加时|点球/.test(name)) return "live";
  if (/完|结束|取消|腰斩|推迟|中断|延期/.test(name)) return "done";
  const code = Number(match.status_up);
  if (!Number.isFinite(code) || (name && !/^\d+$/.test(name))) return "wait";
  if (type === 2) return code >= 2 && code <= 9 ? "live" : code >= 10 ? "done" : "wait";
  return code >= 2 && code <= 7 ? "live" : code >= 8 ? "done" : "wait";
}

export function statusText(match) {
  const type = sportType(match);
  const name = String(match.status_up_name ?? "");
  if (type === 3) {
    const code = Number(match.status);
    if (code === 2) return "比赛中";
    if (code === 3) return "完场";
    if (name && !/^\d+$/.test(name)) return name;
    return OTHER_STATUS[code] || "未开赛";
  }
  if (name && !/^\d+$/.test(name)) return name;
  const code = Number(match.status_up);
  const table = type === 2 ? BASKET_STATUS : FOOT_STATUS;
  return table[code] || "未开赛";
}

export function minuteLabel(match) {
  const time = match.time == null ? "" : String(match.time);
  if (sportType(match) === 1 && /^\d+$/.test(time)) return time + "'";
  return time;
}

export function liveStatus(match) {
  const minute = minuteLabel(match);
  return statusText(match) + (minute ? " " + minute : "");
}

export function scoreText(match) {
  if (phase(match) === "wait" || !match.score) return "VS";
  return String(match.score).replace("-", " - ");
}

export function odds(value) {
  if (!value) return [];
  return String(value).split(/[,，\s]+/).filter(Boolean).slice(0, 3);
}

export function anchor(match) {
  const lists = [match.live_urls, match.live_cartoon_url, match.global_live_urls];
  for (const list of lists) {
    if (!Array.isArray(list)) continue;
    const hit = list.find((item) => item && item.name && item.name !== "动画");
    if (hit) return hit.name;
  }
  return "";
}

export function localDayKey(date) {
  return new Intl.DateTimeFormat("en-CA", { timeZone: browserZone(), year: "numeric", month: "2-digit", day: "2-digit" }).format(date);
}

export function matchInstant(match) {
  const text = String(match.matchtime || "");
  if (text.length < 16) return null;
  const instant = new Date(text.slice(0, 10) + "T" + text.slice(11, 19) + "+08:00");
  return Number.isNaN(instant.getTime()) ? null : instant;
}

export function localClock(match) {
  const instant = matchInstant(match);
  if (!instant) return String(match.matchtime || "").slice(11, 16) || "--:--";
  return new Intl.DateTimeFormat("en-GB", { timeZone: browserZone(), hour: "2-digit", minute: "2-digit", hourCycle: "h23" }).format(instant);
}

export function localMatchDay(match) {
  const instant = matchInstant(match);
  if (!instant) return String(match.matchtime || "").slice(0, 10);
  return localDayKey(instant);
}

export function dayHeading(text) {
  const when = new Date(text + "T12:00:00");
  const week = text === localDayKey(new Date()) ? "今天" : (WEEK[when.getDay()] || "");
  return text + "（" + week + "）";
}

export function applyScore(match) {
  if (!match) return match;
  const fresh = state.scoreById.get(Number(match.id));
  if (!fresh) return match;
  const next = {};
  for (const key of ["score", "status", "status_up", "status_up_name", "time", "banchang", "jiaoqiu", "hteam_red", "hteam_yellow", "ateam_red", "ateam_yellow", "home_score_xiaojie", "away_score_xiaojie", "yazhi_jishi", "ouzhi_jishi", "daxiao_jishi"]) {
    if (fresh[key] != null && fresh[key] !== "") next[key] = fresh[key];
  }
  return Object.assign(match, next);
}

function zdLevel(match) {
  const level = Number(match && match.zd_level);
  return Number.isFinite(level) ? level : 0;
}

export function visibleMatches() {
  for (const match of state.rows) applyScore(match);
  const rows = state.resultOnly
    ? state.rows.filter((match) => phase(match) === "done")
    : state.rows.filter((match) => phase(match) !== "done");
  if (!(state.hot && !state.resultOnly)) return rows;
  const pinned = [];
  const rest = [];
  for (const match of rows) {
    if (Number(match.is_zd) === 1) pinned.push(match);
    else rest.push(match);
  }
  pinned.sort((a, b) => zdLevel(b) - zdLevel(a));
  return pinned.concat(rest);
}

export function matchExtras(match) {
  const extra = [];
  if (match.banchang) extra.push("半:" + match.banchang);
  if (match.jiaoqiu) extra.push("角:" + match.jiaoqiu);
  return extra.join(" ");
}

export function linesOf(match) {
  const seen = new Set();
  const result = [];
  for (const key of ["live_urls", "mirror_live_urls", "global_live_urls", "live_cartoon_url"]) {
    for (const item of match[key] || []) {
      const url = item && item.url;
      if (!url || seen.has(url)) continue;
      seen.add(url);
      result.push({
        name: item.name || "线路",
        url,
        animation: item.name === "动画" || /nami\.com|livetracker/.test(url)
      });
    }
  }
  result.sort((a, b) => Number(a.animation) - Number(b.animation));
  return result;
}

export function animationSrc(url) {
  if (/^https:\/\/widgets-livetracker\.nami\.com\//.test(url)) {
    return "https://player.kzbzcq.com/anim?src=" + encodeURIComponent(url);
  }
  return url;
}

export function playerPath(match) {
  return "/player/" + sportType(match) + "/1/0/" + match.id;
}

export function readPlayerRoute() {
  const match = location.pathname.match(/^\/player\/([123])\/(\d+)\/(\d+)\/(\d+)\/?$/);
  if (!match) return null;
  return { type: Number(match[1]), playtype: match[2], channel: match[3], id: match[4] };
}
