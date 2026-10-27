import { existsSync, readFileSync } from "node:fs";
import { extname } from "node:path";

const types = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".jpg": "image/jpeg",
  ".jpeg": "image/jpeg",
  ".png": "image/png",
  ".gif": "image/gif",
  ".webp": "image/webp",
  ".ico": "image/x-icon",
  ".woff2": "font/woff2"
};
const binary = new Set([".jpg", ".jpeg", ".png", ".gif", ".webp", ".ico", ".woff2"]);

function requestPath(event) {
  const raw = event.rawPath || event.path || "/";
  let path = "/";
  try { path = decodeURIComponent(raw.split("?")[0]); }
  catch (error) { path = "/"; }
  if (!path.startsWith("/") || path.includes("..") || path.endsWith(".mjs")) return "/index.html";
  if (path === "/" || path.startsWith("/player/")) return "/index.html";
  return path;
}

export const handler = async (event) => {
  let name = requestPath(event);
  let file = new URL("." + name, import.meta.url);
  if (!existsSync(file)) {
    name = "/index.html";
    file = new URL("./index.html", import.meta.url);
  }
  const ext = extname(name);
  const bytes = readFileSync(file);
  const encoded = binary.has(ext);
  return {
    statusCode: 200,
    headers: {
      "content-type": types[ext] || "application/octet-stream",
      "cache-control": name.endsWith(".html") ? "no-cache" : "public, max-age=300",
      "x-backend": "lambda"
    },
    isBase64Encoded: encoded,
    body: encoded ? bytes.toString("base64") : bytes.toString("utf8")
  };
};
