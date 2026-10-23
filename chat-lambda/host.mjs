import { readFileSync } from "node:fs";
import { fromNim } from "./doc.mjs";
import vm from "node:vm";
import { webcrypto } from "node:crypto";
import WebSocket from "ws";

const APP_KEY = "c653ab72468ed55c68a1a64ddd9bf57a";
const DEVICE = "sportcrawldevice0001";
const UPSTREAM = process.env.CHAT_UPSTREAM || "https://kzb001.net";
const headers = { Referer: "https://kzb001.net/", "User-Agent": "Mozilla/5.0" };

let sandboxPromise;
let accountCache;
let nim;
let roomClient;

function listeners() {
  const map = new Map();
  return {
    addEventListener(type, fn) {
      if (process.env.CHAT_DEBUG === "1" && type === "message") console.log("listen", String(fn).slice(0, 180));
      if (!map.has(type)) map.set(type, []);
      map.get(type).push(fn);
    },
    removeEventListener(type, fn) {
      const list = map.get(type) || [];
      const index = list.indexOf(fn);
      if (index >= 0) list.splice(index, 1);
    },
    emit(type, event) {
      const list = map.get(type) || [];
      if (process.env.CHAT_DEBUG === "1" && type === "message") {
        console.log("emit", list.length, String(event && event.data).slice(0, 48));
      }
      for (const fn of list) {
        try { fn(event); } catch (error) {
          if (process.env.CHAT_DEBUG === "1") console.log("listener", String(error && error.message || error).slice(0, 160));
        }
      }
    }
  };
}

function loadSdk() {
  if (sandboxPromise) return sandboxPromise;
  sandboxPromise = Promise.resolve().then(() => {
    const windowBus = listeners();
    const document = {
      createElement(tag) {
        const el = listeners();
        el.style = {};
        el.tagName = String(tag).toLowerCase();
        el.setAttribute = () => {};
        el.appendChild = () => {};
        el.contentWindow = {
          postMessage(data) {
            const request = typeof data === "string" ? JSON.parse(data) : data;
            if (!request || !request.url) return;
            const method = request.method || "GET";
            const init = { method, headers: request.headers || {} };
            if (method !== "GET" && request.data != null) {
              init.body = typeof request.data === "string" ? request.data : JSON.stringify(request.data);
            }
            fetch(request.url, init).then(async (res) => {
              const body = await res.text();
              if (process.env.CHAT_DEBUG === "1") console.log("proxy", res.status, String(request.url).slice(0, 80), body.slice(0, 100).replace(/token/gi, "t"));
              const payload = "NEJ-AJAX-DATA:" + JSON.stringify({
                key: request.key,
                status: res.status,
                result: encodeURIComponent(body)
              });
              windowBus.emit("message", { data: payload });
            }).catch(() => {});
          }
        };
        Object.defineProperty(el, "src", {
          set(value) {
            el._src = value;
            setTimeout(() => el.emit("load", { target: el }), 0);
          },
          get() { return el._src || ""; }
        });
        return el;
      },
      head: { appendChild() {} },
      body: { appendChild() {} },
      addEventListener() {},
      documentElement: { style: {} },
      cookie: "",
      querySelector() { return null },
      getElementById() { return null }
    };
    const store = new Map();
    const sandbox = {
      console,
      setTimeout, clearTimeout, setInterval, clearInterval,
      WebSocket: class extends WebSocket {
        constructor(url, proto) {
          if (process.env.CHAT_DEBUG === "1") console.log("ws", String(url).slice(0, 120));
          super(url, proto);
          this.addEventListener("open", () => { if (process.env.CHAT_DEBUG === "1") console.log("ws open"); });
          this.addEventListener("close", (event) => { if (process.env.CHAT_DEBUG === "1") console.log("ws close", event.code); });
          this.addEventListener("error", () => { if (process.env.CHAT_DEBUG === "1") console.log("ws error"); });
        }
      },
      fetch,
      URL, URLSearchParams, TextEncoder, TextDecoder, Promise, Date, Math, JSON,
      Object, Array, String, Number, Boolean, RegExp, Error, TypeError, Map, Set, Symbol,
      JSON: {
        parse(value, reviver) {
          if (process.env.CHAT_DEBUG === "1" && typeof value === "string" && value.includes("weblink")) {
            console.log("parsed link", value.slice(0, 70));
          }
          if (value && typeof value === "object") return value;
          return JSON.parse(value, reviver);
        },
        stringify: JSON.stringify.bind(JSON)
      },
      parseInt, parseFloat, isNaN, decodeURIComponent, encodeURIComponent, unescape, escape,
      crypto: webcrypto,
      atob: (value) => Buffer.from(value, "base64").toString("binary"),
      btoa: (value) => Buffer.from(value, "binary").toString("base64"),
      localStorage: {
        getItem: (key) => (store.has(key) ? store.get(key) : null),
        setItem: (key, value) => store.set(key, String(value)),
        removeItem: (key) => store.delete(key),
        clear: () => store.clear()
      },
      document,
      navigator: { userAgent: "Mozilla/5.0", language: "zh-CN", platform: "Linux", onLine: true },
      location: { protocol: "https:", host: "kzb001.net", href: "https://kzb001.net/", hostname: "kzb001.net" },
      performance: { now: () => Date.now() },
      addEventListener: windowBus.addEventListener.bind(windowBus),
      removeEventListener: windowBus.removeEventListener.bind(windowBus),
      postMessage() {},
      Image: class { set src(_) {} },
      __lbsFetch: (url) => fetch(url).then((res) => res.json())
    };
    sandbox.window = sandbox;
    sandbox.self = sandbox;
    sandbox.global = sandbox;
    sandbox.XMLHttpRequest = class {
      open(method, url) { this.method = method; this.url = url; this.headers = {}; }
      setRequestHeader(key, value) { this.headers[key] = value; }
      addEventListener(name, fn) { this["on" + name] = fn; }
      getAllResponseHeaders() { return ""; }
      send(body) {
        fetch(this.url, { method: this.method, headers: this.headers, body: body || undefined }).then(async (res) => {
          this.status = res.status;
          this.responseText = await res.text();
          this.readyState = 4;
          if (this.onreadystatechange) this.onreadystatechange();
          if (this.onload) this.onload();
        }).catch(() => {});
      }
    };
    vm.createContext(sandbox);
    vm.runInContext(readFileSync(new URL("./sdk.js", import.meta.url), "utf8"), sandbox, { filename: "sdk.js" });
    return sandbox;
  });
  return sandboxPromise;
}

async function guest() {
  if (accountCache) return accountCache;
  const url = UPSTREAM + "/prod-api/words/wangyi/user/add?apptype=0&deivceid=" + DEVICE + "&nickname=" + encodeURIComponent("观众") + "&uid=0&pid=1";
  const body = await (await fetch(url, { headers })).json();
  if (body.code !== "0" || !body.accid || !body.token) throw new Error("游客身份没有拿到");
  accountCache = body;
  return body;
}

export async function roomOf(matchId, type) {
  const url = UPSTREAM + "/prod-api/words/wangyi/match/chatroom?pid=4&apptype=0&type=" + type + "&mid=" + matchId + "&num=0";
  const body = await (await fetch(url, { headers })).json();
  return {
    open: body.code === "0" && Boolean(body.roomid),
    roomId: body.roomid ? String(body.roomid) : "",
    addresses: Array.isArray(body.addr) ? body.addr : []
  };
}

function disconnect(client) {
  if (!client) return;
  try { if (typeof client.disconnect === "function") client.disconnect(); } catch { /* already gone */ }
}

async function connectNim(sandbox, account) {
  if (nim) return nim;
  const client = await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("云信登录超时")), 12000);
    const created = sandbox.SDK.NIM.getInstance({
      debug: process.env.CHAT_DEBUG === "1",
      appKey: APP_KEY,
      account: account.accid,
      token: account.token,
      db: false,
      onconnect: () => { clearTimeout(timer); resolve(created); },
      onerror: (error) => { if (process.env.CHAT_DEBUG === "1") console.log("nim error", String(error && error.message || error).slice(0, 160)); },
      onwillreconnect: () => { if (process.env.CHAT_DEBUG === "1") console.log("nim reconnect"); },
      ondisconnect: (error) => { if (process.env.CHAT_DEBUG === "1") console.log("nim disc", JSON.stringify(error).slice(0, 180)); if (nim === created) nim = null; }
    });
  });
  nim = client;
  return client;
}

async function addressesFor(sandbox, account, roomId, fallback) {
  await connectNim(sandbox, account);
  const nimClient = sandbox.SDK.NIM.getInstance({
    appKey: APP_KEY,
    account: account.accid,
    token: account.token,
    db: false
  });
  const addresses = await new Promise((resolve) => {
    const timer = setTimeout(() => resolve(fallback), 8000);
    nimClient.getChatroomAddress({
      chatroomId: roomId,
      done: (error, obj) => {
        clearTimeout(timer);
        resolve(!error && obj && Array.isArray(obj.address) && obj.address.length ? obj.address : fallback);
      }
    });
  });
  return addresses;
}

async function connectRoom(sandbox, account, roomId, addresses) {
  if (roomClient && roomClient.roomId === roomId) return roomClient.chatroom;
  disconnect(roomClient && roomClient.chatroom);
  roomClient = null;
  const chatroom = await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("聊天室连接超时")), 12000);
    const client = sandbox.SDK.Chatroom.getInstance({
      debug: false,
      appKey: APP_KEY,
      account: account.accid,
      token: account.token,
      chatroomId: roomId,
      chatroomAddresses: addresses,
      chatroomNick: account.nickname || "观众",
      isAnonymous: false,
      db: false,
      onconnect: () => { clearTimeout(timer); resolve(client); },
      onerror: () => {},
      onwillreconnect: () => {},
      ondisconnect: () => { if (roomClient && roomClient.chatroom === client) roomClient = null; },
      onmsgs: () => {}
    });
  });
  roomClient = { roomId, chatroom };
  return chatroom;
}

export async function history(matchId, type, timetag) {
  const room = await roomOf(matchId, type);
  if (!room.open) return { roomId: "", messages: [], open: false };
  const sandbox = await loadSdk();
  const account = await guest();
  const addresses = await addressesFor(sandbox, account, room.roomId, room.addresses);
  const chatroom = await connectRoom(sandbox, account, room.roomId, addresses);
  const texts = await readHistory(chatroom, timetag, ["text"]);
  const pictures = await readHistory(chatroom, timetag, ["image", "custom"]).catch(() => []);
  const seen = new Set();
  const messages = [];
  for (const message of [...texts, ...pictures]) {
    if (seen.has(message.id)) continue;
    seen.add(message.id);
    messages.push(message);
  }
  return { roomId: room.roomId, open: true, messages };
}

function readHistory(chatroom, timetag, msgTypes) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("历史消息超时")), 10000);
    chatroom.getHistoryMsgs({
      timetag,
      limit: 100,
      reverse: false,
      msgTypes,
      done: (error, obj) => {
        clearTimeout(timer);
        if (error) {
          reject(new Error("历史消息没有拿到"));
          return;
        }
        const msgs = (obj && obj.msgs) || [];
        resolve(msgs.map(fromNim).filter(Boolean));
      }
    });
  });
}
