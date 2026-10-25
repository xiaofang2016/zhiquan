import { spawn } from "node:child_process";
import http from "node:http";

const CHROME = process.env.CHROME || "/usr/bin/google-chrome";
const PORT = Number(process.env.PORT || 8896);
const DEBUG_PORT = Number(process.env.DEBUG_PORT || 9223);
const ALLOWED = /^https:\/\/(widgets-livetracker\.nami\.com|tracker\.namitiyu\.com)\//;

let browserWs = null;
let messageId = 0;
const pending = new Map();
const pages = new Map();

function send(method, params, sessionId) {
  const id = ++messageId;
  const payload = { id, method, params: params || {} };
  if (sessionId) payload.sessionId = sessionId;
  browserWs.send(JSON.stringify(payload));
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      pending.delete(id);
      reject(new Error(method + " 超时"));
    }, 20000);
    pending.set(id, { resolve, reject, timer });
  });
}

function connectBrowser() {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("浏览器连接超时")), 10000);
    fetch("http://127.0.0.1:" + DEBUG_PORT + "/json/version").then((response) => response.json()).then((version) => {
      const ws = new WebSocket(version.webSocketDebuggerUrl);
      ws.addEventListener("open", () => {
        clearTimeout(timer);
        browserWs = ws;
        resolve();
      });
      ws.addEventListener("error", () => {
        clearTimeout(timer);
        reject(new Error("浏览器连接失败"));
      });
      ws.addEventListener("message", (event) => {
        const message = JSON.parse(event.data);
        if (message.id && pending.has(message.id)) {
          const waiter = pending.get(message.id);
          pending.delete(message.id);
          clearTimeout(waiter.timer);
          if (message.error) waiter.reject(new Error(message.error.message || "浏览器错误"));
          else waiter.resolve(message.result || {});
        }
      });
    }).catch((error) => {
      clearTimeout(timer);
      reject(error);
    });
  });
}

function launchChrome() {
  const chrome = spawn(CHROME, [
    "--headless=new",
    "--remote-debugging-port=" + DEBUG_PORT,
    "--user-data-dir=/tmp/anim-chrome",
    "--no-sandbox",
    "--disable-dev-shm-usage",
    "--ignore-certificate-errors",
    "--allow-insecure-localhost",
    "--host-resolver-rules=MAP player.chinajiushengjixie.com 127.0.0.1",
    "--window-size=960,540",
    "--no-first-run",
    "--disable-gpu",
    "about:blank"
  ], { stdio: ["ignore", "ignore", "pipe"] });
  chrome.stderr.on("data", (chunk) => process.stderr.write(chunk));
  chrome.on("exit", () => {
    browserWs = null;
    pages.clear();
  });
  return chrome;
}

async function ensureBrowser() {
  if (browserWs && browserWs.readyState === WebSocket.OPEN) return;
  launchChrome();
  for (let attempt = 0; attempt < 20; attempt++) {
    try {
      await connectBrowser();
      return;
    } catch (error) {
      await new Promise((resolve) => setTimeout(resolve, 300));
    }
  }
  throw new Error("无头浏览器没有起来");
}

async function pageFor(src) {
  await ensureBrowser();
  const cached = pages.get(src);
  if (cached) {
    cached.used = Date.now();
    return cached.sessionId;
  }
  const target = await send("Target.createTarget", { url: "about:blank" });
  const attached = await send("Target.attachToTarget", { targetId: target.targetId, flatten: true });
  const sessionId = attached.sessionId;
  await send("Page.enable", {}, sessionId);
  await send("Emulation.setDeviceMetricsOverride", {
    width: 960,
    height: 540,
    deviceScaleFactor: 1,
    mobile: false
  }, sessionId);
  const embed = "https://player.chinajiushengjixie.com/embed?src=" + encodeURIComponent(src);
  await send("Page.navigate", { url: embed }, sessionId);
  await new Promise((resolve) => setTimeout(resolve, 4000));
  pages.set(src, { sessionId, targetId: target.targetId, used: Date.now() });
  return sessionId;
}

async function frame(src) {
  const sessionId = await pageFor(src);
  const shot = await send("Page.captureScreenshot", { format: "jpeg", quality: 70 }, sessionId);
  pages.get(src).used = Date.now();
  return Buffer.from(shot.data, "base64");
}

setInterval(() => {
  const now = Date.now();
  for (const [src, page] of pages) {
    if (now - page.used < 120000) continue;
    send("Target.closeTarget", { targetId: page.targetId }).catch(() => {});
    pages.delete(src);
  }
}, 30000);

function html(src) {
  return `<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>动画</title>
<style>html,body{margin:0;height:100%;background:#111}img{width:100%;height:100%;object-fit:contain;background:#111}</style>
</head><body><img id="v" alt="">
<script>
const src = ${JSON.stringify(src)};
const img = document.getElementById("v");
function tick(){ img.src = "/anim-frame?src=" + encodeURIComponent(src) + "&t=" + Date.now(); }
img.onload = () => setTimeout(tick, 400);
img.onerror = () => setTimeout(tick, 1000);
tick();
</script></body></html>`;
}

const server = http.createServer(async (request, response) => {
  const url = new URL(request.url, "http://127.0.0.1");
  const src = url.searchParams.get("src") || "";
  if (!ALLOWED.test(src)) {
    response.writeHead(400, { "content-type": "text/plain; charset=utf-8" });
    response.end("动画地址无效");
    return;
  }
  try {
    if (url.pathname === "/anim-frame") {
      const body = await frame(src);
      response.writeHead(200, { "content-type": "image/jpeg", "cache-control": "no-store" });
      response.end(body);
      return;
    }
    response.writeHead(200, { "content-type": "text/html; charset=utf-8", "cache-control": "no-store" });
    response.end(html(src));
  } catch (error) {
    response.writeHead(502, { "content-type": "text/plain; charset=utf-8" });
    response.end(error.message || "播放失败");
  }
});

server.listen(PORT, "127.0.0.1");
