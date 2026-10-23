import { GetObjectCommand, PutObjectCommand, S3Client } from "@aws-sdk/client-s3";
import { empty } from "./doc.mjs";

const memory = new Map();
const bucket = process.env.DETAIL_BUCKET || "sport-crawl-images-562904761216";
const prefix = (process.env.DETAIL_PREFIX || "detail/").replace(/\/?$/, "/");
const s3 = process.env.DETAIL_MEMORY === "1" ? null : new S3Client({ region: process.env.AWS_REGION || "ap-northeast-1" });

export async function read(matchId) {
  if (!s3) return { document: memory.get(matchId) || empty(matchId), etag: memory.has(matchId) ? "mem" : null };
  try {
    const response = await s3.send(new GetObjectCommand({ Bucket: bucket, Key: prefix + matchId + ".json" }));
    const document = JSON.parse(await response.Body.transformToString());
    if (!document.sections) document.sections = {};
    return { document, etag: response.ETag };
  } catch (error) {
    if (error.name === "NoSuchKey" || error.$metadata?.httpStatusCode === 404) return { document: empty(matchId), etag: null };
    throw error;
  }
}

export async function write(matchId, document, etag) {
  if (!s3) {
    memory.set(matchId, document);
    return;
  }
  await s3.send(new PutObjectCommand({
    Bucket: bucket,
    Key: prefix + matchId + ".json",
    Body: JSON.stringify(document),
    ContentType: "application/json",
    ...(etag ? { IfMatch: etag } : { IfNoneMatch: "*" })
  }));
}
