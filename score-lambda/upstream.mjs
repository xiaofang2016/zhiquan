import { collect, overlay, pageCount, pageSizeIgnored } from "./board.mjs";

const UPSTREAM = process.env.SCORE_UPSTREAM || "https://kzb001.net/prod-api";
const PACE_MS = Number(process.env.SCORE_PACE_MS || 200);
const MAX_PAGES = Number(process.env.SCORE_MAX_PAGES || 250);
const headers = {
  Referer: "https://kzb001.net/",
  "User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
  Accept: "application/json"
};

let lastRequestAt = 0;

export async function pull() {
  let pageSize = 200;
  let first = await page(0, 1, pageSize);
  let total = Number(first.total) || 0;
  let totalPage = Number(first.totalPage) || 0;
  let dataSize = Array.isArray(first.dataList) ? first.dataList.length : 0;
  if (pageSizeIgnored(total, totalPage, dataSize, pageSize)) {
    pageSize = 20;
    first = await page(0, 1, pageSize);
    total = Number(first.total) || 0;
    totalPage = Number(first.totalPage) || 0;
    dataSize = Array.isArray(first.dataList) ? first.dataList.length : 0;
  }
  const pages = [first];
  const count = pageCount(total, totalPage, dataSize, pageSize, MAX_PAGES);
  for (let number = 2; number <= count; number += 1) {
    pages.push(await page(0, number, pageSize));
  }
  let board = collect(pages);
  for (const type of [1, 2]) {
    try {
      const extra = await page(type, 1, pageSize);
      board = overlay(board, extra.otherList || []);
    } catch (error) {
      console.error("score otherList", type, error && error.message);
    }
  }
  board.updatedAt = Date.now();
  return board;
}

async function page(type, number, pageSize) {
  const query = new URLSearchParams({
    isfanye: "1",
    type: String(type),
    cid: "0",
    ishot: "-1",
    pn: String(number),
    ps: String(pageSize),
    langtype: "zh",
    pid: "4",
    zoneId: "Asia/Shanghai",
    zhuboType: "0"
  });
  await pace();
  const response = await fetch(UPSTREAM + "/match/list/new2?" + query, { headers });
  const body = await response.json();
  if (!response.ok || !body || String(body.code) !== "0") {
    throw new Error((body && body.msg) || "上游没有拿到");
  }
  return body.data || {};
}

async function pace() {
  const wait = PACE_MS - (Date.now() - lastRequestAt);
  if (lastRequestAt && wait > 0) await new Promise((resolve) => setTimeout(resolve, wait));
  lastRequestAt = Date.now();
}
