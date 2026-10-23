import { GetObjectCommand, PutObjectCommand, S3Client } from "@aws-sdk/client-s3";
import { emptyBoard } from "./board.mjs";

const memory = { board: null, lockAt: 0 };
const bucket = process.env.SCORE_BUCKET || "sport-crawl-images-562904761216";
const prefix = (process.env.SCORE_PREFIX || "scores/").replace(/\/?$/, "/");
const s3 = process.env.SCORE_MEMORY === "1" ? null : new S3Client({ region: process.env.AWS_REGION || "ap-northeast-1" });
const LOCK_MS = 110_000;

let cache = { at: 0, board: null };

export async function loadBoard() {
  if (cache.board && Date.now() - cache.at < 5000) return cache.board;
  const board = await readBoard();
  cache = { at: Date.now(), board };
  return board;
}

export async function readBoard() {
  if (!s3) return memory.board || emptyBoard();
  try {
    const response = await s3.send(new GetObjectCommand({ Bucket: bucket, Key: prefix + "board.json" }));
    const board = JSON.parse(await response.Body.transformToString());
    if (!Array.isArray(board.rows)) board.rows = [];
    return board;
  } catch (error) {
    if (missing(error)) return emptyBoard();
    throw error;
  }
}

export async function writeBoard(board) {
  cache = { at: Date.now(), board };
  if (!s3) {
    memory.board = board;
    return;
  }
  await s3.send(new PutObjectCommand({
    Bucket: bucket,
    Key: prefix + "board.json",
    Body: JSON.stringify(board),
    ContentType: "application/json"
  }));
}

export async function claim(now = Date.now()) {
  if (!s3) {
    if (memory.lockAt && now - memory.lockAt < LOCK_MS) return false;
    memory.lockAt = now;
    return true;
  }
  const key = prefix + "lock.json";
  let etag = null;
  let at = 0;
  try {
    const response = await s3.send(new GetObjectCommand({ Bucket: bucket, Key: key }));
    const body = JSON.parse(await response.Body.transformToString());
    etag = response.ETag;
    at = Number(body.at) || 0;
  } catch (error) {
    if (!missing(error)) throw error;
  }
  if (at && now - at < LOCK_MS) return false;
  try {
    await s3.send(new PutObjectCommand({
      Bucket: bucket,
      Key: key,
      Body: JSON.stringify({ at: now }),
      ContentType: "application/json",
      ...(etag ? { IfMatch: etag } : { IfNoneMatch: "*" })
    }));
    return true;
  } catch (error) {
    if (error.$metadata?.httpStatusCode === 412) return false;
    throw error;
  }
}

export async function release() {
  if (!s3) {
    memory.lockAt = 0;
    return;
  }
  await s3.send(new PutObjectCommand({
    Bucket: bucket,
    Key: prefix + "lock.json",
    Body: JSON.stringify({ at: 0 }),
    ContentType: "application/json"
  }));
}

function missing(error) {
  return error.name === "NoSuchKey" || error.$metadata?.httpStatusCode === 404;
}
