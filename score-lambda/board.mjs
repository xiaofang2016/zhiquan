import { createHash } from "node:crypto";

const FIRST_PAGE = [
  ["notopList", "notop"],
  ["topList", "top"],
  ["overList", "over"],
  ["otherList", "other"]
];
const LIVE_FIELDS = ["score", "status", "status_up", "status_up_name", "time", "banchang", "jiaoqiu", "hteam_red", "hteam_yellow", "ateam_red", "ateam_yellow", "home_score_xiaojie", "away_score_xiaojie", "yazhi_jishi", "ouzhi_jishi", "daxiao_jishi"];

export function emptyBoard() {
  return { updatedAt: 0, rows: [] };
}

export function pageSizeIgnored(total, totalPage, dataSize, requestedPs) {
  return requestedPs > 1 && total > 1 && totalPage === total && dataSize <= 1;
}

export function pageCount(total, totalPage, dataSize, requestedPs, maxPages) {
  let pages = Math.max(totalPage, dataSize > 0 ? 1 : 0);
  if (dataSize > 0 && dataSize < requestedPs && total > dataSize) {
    const implied = Math.ceil(total / dataSize);
    if (implied > pages) pages = implied;
  }
  if (maxPages > 0) pages = Math.min(pages, maxPages);
  return pages;
}

export function collect(pages) {
  const byId = new Map();
  const next = new Map();
  pages.forEach((page, index) => {
    if (!page) return;
    if (index === 0) {
      for (const [key, section] of FIRST_PAGE) addAll(byId, next, page[key], section);
    }
    addAll(byId, next, page.dataList, "data");
  });
  return { updatedAt: 0, rows: [...byId.values()] };
}

export function overlay(board, items) {
  if (!items || !items.length) return board;
  const byId = new Map(board.rows.map((row) => [row.id, row]));
  let order = board.rows.reduce((max, row) => (row.section === "other" ? Math.max(max, row.order) : max), -1);
  for (const item of items) {
    const id = idOf(item);
    if (!id) continue;
    order += 1;
    byId.set(id, toRow(item, "other", order));
  }
  return { updatedAt: board.updatedAt, rows: [...byId.values()] };
}

export function query(board, { type, cid, hot, date, page, size, liveType }) {
  const notop = filtered(board, "notop", type, cid, hot, date);
  const top = filtered(board, "top", type, cid, hot, date);
  const over = filtered(board, "over", type, cid, hot, date);
  const other = filtered(board, "other", type, cid, hot, date);
  const data = filtered(board, "data", type, cid, hot, date);
  const total = data.length;
  const totalPage = total === 0 ? 0 : Math.ceil(total / size);
  const from = Math.max(0, page - 1) * size;
  const dataPage = from >= total ? [] : data.slice(from, from + size);
  const first = page === 1;
  const raw = [date || "", page, size, type, cid, hot].join("|");
  return {
    total,
    totalPage,
    notopList: bodies(first ? notop : []),
    dataList: bodies(dataPage),
    overList: bodies(first ? over : []),
    topList: bodies(first ? top : []),
    live_type: liveType,
    starttime: date || "",
    currentPage: page,
    otherList: bodies(first ? other : []),
    token: createHash("md5").update(raw).digest("hex"),
    updatedAt: board.updatedAt || 0
  };
}

export function stamp(board, previous) {
  const marks = {};
  for (const row of board.rows) marks[String(row.id)] = markOf(row);
  const previousMarks = previous && previous.marks;
  const hasPrevious = Boolean(previousMarks && Object.keys(previousMarks).length);
  return {
    ...board,
    updatedAt: board.updatedAt || Date.now(),
    prevUpdatedAt: hasPrevious ? Number(previous.updatedAt) || 0 : 0,
    prevMarks: hasPrevious ? previousMarks : {},
    marks
  };
}

export function changes(board, since) {
  const now = Number(board.updatedAt) || 0;
  if (!now || !since) return null;
  if (since === now) return { updatedAt: now, changed: [], removed: [] };
  const prevMarks = board.prevMarks || {};
  if (since !== Number(board.prevUpdatedAt) || !Object.keys(prevMarks).length || !board.marks) return null;
  const marks = board.marks;
  const changed = [];
  for (const row of board.rows) {
    const id = String(row.id);
    if (marks[id] !== prevMarks[id]) changed.push(patch(row));
  }
  const removed = [];
  for (const id of Object.keys(prevMarks)) {
    if (marks[id] == null) removed.push(Number(id));
  }
  return { updatedAt: now, changed, removed };
}

export function counts(board) {
  const result = { matches: board.rows.length, notop: 0, top: 0, data: 0, over: 0, other: 0 };
  for (const row of board.rows) {
    if (result[row.section] != null) result[row.section] += 1;
  }
  return result;
}

function filtered(board, section, type, cid, hot, date) {
  return board.rows
    .filter((row) => row.section === section && matches(row, type, cid, hot, date))
    .sort((a, b) => a.order - b.order);
}

function matches(row, type, cid, hot, date) {
  if (type != null && type > 0 && row.type !== type) return false;
  if (cid != null && cid !== 0 && row.cid !== cid) return false;
  if (hot != null && hot >= 0 && row.hot !== hot) return false;
  if (date && !String(row.body.matchtime || "").startsWith(date)) return false;
  return true;
}

function bodies(rows) {
  return rows.map((row) => row.body);
}

function addAll(byId, next, items, section) {
  if (!Array.isArray(items)) return;
  for (const item of items) {
    const id = idOf(item);
    if (!id || byId.has(id)) continue;
    const order = next.get(section) || 0;
    next.set(section, order + 1);
    byId.set(id, toRow(item, section, order));
  }
}

function toRow(item, section, order) {
  return {
    id: idOf(item),
    type: Number(item.type) || 0,
    cid: Number(item.cid) || 0,
    hot: Number(item.isHot) === 1 ? 1 : 0,
    section,
    order,
    body: item
  };
}

function idOf(item) {
  if (!item || item.id == null || item.id === "") return 0;
  const id = Number(item.id);
  return Number.isSafeInteger(id) && id > 0 ? id : 0;
}

function markOf(row) {
  const body = row.body || {};
  const raw = row.section + "\n" + LIVE_FIELDS.map((key) => JSON.stringify(body[key] ?? null)).join("\n");
  return createHash("sha1").update(raw).digest("hex").slice(0, 12);
}

function patch(row) {
  const body = row.body || {};
  const item = { id: row.id };
  for (const key of LIVE_FIELDS) {
    if (body[key] != null) item[key] = body[key];
  }
  return item;
}
