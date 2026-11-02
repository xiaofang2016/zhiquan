import { decryptCover } from "./aesCfb.js";

const cache = new Map();
const pending = new Map();

export function encryptedCover(url) {
  return /_enc\./i.test(String(url || ""));
}

export function imageType(bytes) {
  if (bytes.length >= 3 && bytes[0] === 0xff && bytes[1] === 0xd8 && bytes[2] === 0xff) return "image/jpeg";
  if (bytes.length >= 8 && bytes[0] === 0x89 && bytes[1] === 0x50 && bytes[2] === 0x4e && bytes[3] === 0x47) return "image/png";
  if (bytes.length >= 6 && bytes[0] === 0x47 && bytes[1] === 0x49 && bytes[2] === 0x46) return "image/gif";
  if (bytes.length >= 12 && bytes[0] === 0x52 && bytes[1] === 0x49 && bytes[2] === 0x46 && bytes[3] === 0x46 && bytes[8] === 0x57 && bytes[9] === 0x45 && bytes[10] === 0x42 && bytes[11] === 0x50) return "image/webp";
  return "";
}

export function decodeCover(data) {
  const bytes = data instanceof Uint8Array ? data : new Uint8Array(data);
  const plain = imageType(bytes) ? bytes : decryptCover(bytes);
  if (!imageType(plain)) throw new Error("cover");
  return plain;
}

export function openCover(src) {
  if (!src) return Promise.resolve("");
  if (!encryptedCover(src)) return Promise.resolve(src);
  if (cache.has(src)) return Promise.resolve(cache.get(src));
  if (pending.has(src)) return pending.get(src);
  const job = fetch(src).then(async (response) => {
    if (!response.ok) throw new Error("cover");
    const plain = decodeCover(new Uint8Array(await response.arrayBuffer()));
    const url = URL.createObjectURL(new Blob([plain], { type: imageType(plain) }));
    cache.set(src, url);
    return url;
  }).finally(() => pending.delete(src));
  pending.set(src, job);
  return job;
}
