import assert from "node:assert/strict";
import test from "node:test";
import { collect, overlay, pageCount, pageSizeIgnored, query } from "./board.mjs";

test("finished matches stay on the first page only", () => {
  const board = collect([
    {
      notopList: [{ id: 1, type: 1, cid: 8, isHot: 1, score: "1-0", status_up_name: "上半场", matchtime: "2026-10-23 21:00:00" }],
      topList: [],
      dataList: [{ id: 2, type: 1, cid: 8, isHot: 0, score: "0-0", status_up_name: "未开赛", matchtime: "2026-10-23 23:00:00" }],
      overList: [{ id: 3, type: 1, cid: 9, isHot: 0, score: "2-1", status_up_name: "完场", matchtime: "2026-10-23 18:00:00" }],
      otherList: []
    },
    {
      dataList: [{ id: 4, type: 2, cid: 1, isHot: 1, score: "0-0", matchtime: "2026-10-23 23:30:00" }],
      overList: [{ id: 3, type: 1, cid: 9, isHot: 0, score: "2-2", status_up_name: "完场", matchtime: "2026-10-23 18:00:00" }]
    }
  ]);
  assert.equal(board.rows.length, 4);
  const first = query(board, { type: null, cid: null, hot: null, date: null, page: 1, size: 20, liveType: 0 });
  assert.equal(first.overList.length, 1);
  assert.equal(first.overList[0].score, "2-1");
  assert.equal(first.total, 2);
});

test("later pages hide the pinned and finished lists", () => {
  const board = collect([{
    notopList: [{ id: 9, type: 1, cid: 1, isHot: 0, score: "0-0", matchtime: "2026-10-23 21:00:00" }],
    dataList: [
      { id: 1, type: 1, cid: 1, isHot: 1, score: "0-0", matchtime: "2026-10-23 22:00:00" },
      { id: 2, type: 1, cid: 2, isHot: 0, score: "0-0", matchtime: "2026-10-23 22:30:00" }
    ],
    overList: [{ id: 8, type: 1, cid: 1, isHot: 0, score: "1-0", matchtime: "2026-10-23 16:00:00" }]
  }]);
  const first = query(board, { type: 1, cid: null, hot: null, date: "2026-10-23", page: 1, size: 1, liveType: 0 });
  assert.equal(first.total, 2);
  assert.equal(first.totalPage, 2);
  assert.equal(first.notopList.length, 1);
  assert.equal(first.dataList[0].id, 1);
  const second = query(board, { type: 1, cid: null, hot: null, date: null, page: 2, size: 1, liveType: 0 });
  assert.equal(second.dataList[0].id, 2);
  assert.equal(second.overList.length, 0);
  assert.equal(second.notopList.length, 0);
  assert.equal(query(board, { type: 1, cid: 2, hot: null, date: null, page: 1, size: 20, liveType: 0 }).dataList.length, 1);
  assert.equal(query(board, { type: null, cid: null, hot: 1, date: null, page: 1, size: 20, liveType: 0 }).dataList.length, 1);
});

test("postponed matches leave the upcoming list", () => {
  const board = overlay(collect([{
    dataList: [{ id: 5, type: 1, cid: 3, isHot: 0, score: "0-0", status_up_name: "未开赛" }]
  }]), [{ id: 5, type: 1, cid: 3, isHot: 0, score: "0-0", status_up_name: "推迟" }]);
  const view = query(board, { type: 1, cid: null, hot: null, date: null, page: 1, size: 20, liveType: 0 });
  assert.equal(view.total, 0);
  assert.equal(view.otherList[0].status_up_name, "推迟");
});

test("page count follows the upstream total", () => {
  assert.equal(pageSizeIgnored(3553, 3553, 1, 200), true);
  assert.equal(pageCount(3570, 18, 200, 200, 250), 18);
  assert.equal(pageCount(3570, 179, 20, 20, 250), 179);
});
