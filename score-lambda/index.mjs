import { InvokeCommand, LambdaClient } from "@aws-sdk/client-lambda";
import { changes, counts, query, stamp } from "./board.mjs";
import { claim, loadBoard, readBoard, release, writeBoard } from "./store.mjs";
import { pull } from "./upstream.mjs";

const GAP_MS = Number(process.env.SCORE_GAP_MS || 15000);
const STALE_MS = Number(process.env.SCORE_STALE_MS || 90000);
const lambda = new LambdaClient({ region: process.env.AWS_REGION || "ap-northeast-1" });

function json(status, body, event) {
  const headers = {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store"
  };
  const host = (event && event.requestContext && event.requestContext.domainName) || "";
  if (!host.includes("lambda-url")) {
    headers["access-control-allow-origin"] = "*";
  }
  return {
    statusCode: status,
    headers,
    body: JSON.stringify(body)
  };
}

export async function crawl() {
  if (!await claim()) return { skipped: true };
  let result;
  try {
    const previous = await readBoard();
    const board = stamp(await pull(), previous);
    await writeBoard(board);
    result = counts(board);
    console.log("score crawl", JSON.stringify(result));
  } catch (error) {
    console.error(error && error.name, error && error.message);
    result = { error: error && error.message ? error.message : "抓取失败" };
  }
  await new Promise((resolve) => setTimeout(resolve, GAP_MS));
  await release();
  try {
    await kick();
    result.rescheduled = true;
  } catch (error) {
    console.error("score reschedule", error && error.message);
    result.rescheduled = false;
  }
  return result;
}

async function kick() {
  const name = process.env.AWS_LAMBDA_FUNCTION_NAME;
  if (!name || process.env.SCORE_MEMORY === "1") return;
  await lambda.send(new InvokeCommand({
    FunctionName: name,
    InvocationType: "Event",
    Payload: Buffer.from(JSON.stringify({ source: "aws.events" }))
  }));
}

function serve(event) {
  const path = event.rawPath || "/";
  const params = event.queryStringParameters || {};
  if (path === "/crawl/status") {
    return loadBoard().then(async (board) => {
      if (stale(board)) await kick().catch((error) => console.error("score kick", error && error.message));
      return json(200, { updatedAt: board.updatedAt || 0, ...counts(board) }, event);
    });
  }
  if (path !== "/" && path !== "/prod-api/match/list/new2") {
    return Promise.resolve(json(404, { code: "404", msg: "没有这个接口", data: null }, event));
  }
  let date = null;
  if (params.starttime) {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(params.starttime)) {
      return Promise.resolve(json(400, { code: "400", msg: "starttime 格式应为 yyyy-MM-dd", data: null }, event));
    }
    date = params.starttime;
  }
  const page = Math.min(Math.max(Number(params.pn) || 1, 1), 500);
  const size = params.ps == null || params.ps === "" ? 20 : Number(params.ps);
  if (!Number.isInteger(size) || size < 1 || size > 200) {
    return Promise.resolve(json(400, { code: "400", msg: "ps 必须在 1 到 200 之间", data: null }, event));
  }
  const type = params.type == null || params.type === "" ? null : Number(params.type);
  const cid = params.cid == null || params.cid === "" ? null : Number(params.cid);
  const hot = params.ishot == null || params.ishot === "" ? null : Number(params.ishot);
  const liveType = params.live_type == null || params.live_type === "" ? 0 : Number(params.live_type);
  const since = params.since && /^\d+$/.test(params.since) ? Number(params.since) : 0;
  return loadBoard().then(async (board) => {
    if (stale(board)) await kick().catch((error) => console.error("score kick", error && error.message));
    const delta = since ? changes(board, since) : null;
    return json(200, {
      msg: "ok",
      code: "0",
      data: delta || query(board, { type, cid, hot, date, page, size, liveType })
    }, event);
  });
}

function stale(board) {
  return !board.updatedAt || Date.now() - board.updatedAt > STALE_MS;
}

export async function handler(event) {
  if (event && event.source === "aws.events") return crawl();
  return serve(event || {});
}
