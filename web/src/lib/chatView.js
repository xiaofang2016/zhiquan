import { FACE_SRC } from "./faces.js";

const FALLBACK_IMAGE_BASE = "https://oss.imnono.net";
const FACE_TOKEN = /\[\/[^\]]+\]/g;

export function present(message, imageBase) {
  const type = String((message && message.type) || "");
  const content = String((message && message.content) || "").trim();
  const data = parse(content);
  if (type === "10" || (data && data.walletId && (data.description || data.title))) {
    const status = String(data.status || "1");
    const title = String(data.description || data.title || "红包").replace(/🧧/g, "").trim() || "红包";
    return {
      kind: "pack",
      title,
      from: String(data.nickname || data.nickName || (message && message.nick) || "").trim(),
      status: status === "2" ? "已领取" : status === "3" ? "已领完" : "待领取",
      done: status === "2" || status === "3",
      pool: poolDigits(data.dbAmountPool),
      count: packCount(data)
    };
  }
  if (type === "13" || (data && Array.isArray(data.files) && data.files.length)) {
    const images = data.files.map((file) => imageSrc(file, imageBase)).filter(Boolean);
    if (images.length) {
      const text = String(data.text || "").trim();
      return { kind: "image", text, parts: textParts(text), images };
    }
  }
  if (type === "1" && content && !content.startsWith("{") && !content.startsWith("[")) {
    const src = imageSrc({ fileMd5: content }, imageBase);
    if (src) return { kind: "image", text: "", images: [src] };
  }
  if (type === "91" || isRevoke(data)) {
    return { kind: "revoke", text: revokeLine(data, message) };
  }
  const text = textOf(type, content, data);
  return { kind: "text", text, parts: textParts(text) };
}

export function previewText(message, imageBase) {
  const view = present(message, imageBase);
  if (view.kind === "image") return view.text || "[图片]";
  if (view.kind === "pack") return view.title || "[红包]";
  if (view.kind === "revoke") return view.text || "撤回了一条消息";
  return String(view.text || "").replace(/\s+/g, " ").trim();
}

export function textParts(text) {
  const value = String(text || "");
  const parts = [];
  let last = 0;
  for (const match of value.matchAll(FACE_TOKEN)) {
    const token = match[0];
    const src = FACE_SRC[token];
    if (!src) continue;
    if (match.index > last) parts.push({ kind: "text", text: value.slice(last, match.index) });
    parts.push({ kind: "face", src, alt: token });
    last = match.index + token.length;
  }
  if (!parts.length) return [{ kind: "text", text: value }];
  if (last < value.length) parts.push({ kind: "text", text: value.slice(last) });
  return parts;
}

function packCount(data) {
  const num = String(data.num || "").replace(/\D/g, "");
  const remain = String(data.remainNum == null ? "" : data.remainNum).replace(/\D/g, "");
  if (num && remain) return "剩余 " + remain + "/" + num;
  if (num) return "共 " + num + " 个";
  return "";
}

export function poolDigits(raw) {
  const text = String(raw || "").replace(/\D/g, "");
  const cut = text.length > 2 ? text.slice(0, -2) : "0";
  return cut.padStart(6, "0");
}

export function groupAvatar(file, imageBase) {
  const name = String(file || "").trim();
  if (!name) return "";
  if (/^https?:\/\//i.test(name)) return name;
  const host = String(imageBase || FALLBACK_IMAGE_BASE).replace(/\/+$/, "");
  if (name.includes("?")) return host + "/head/" + name;
  return host + "/head/" + name + "?imageView2/1/w/256/h/256&imgType=group&imageMogr2/format/webp";
}

export function imageSrc(file, imageBase) {
  const host = String(imageBase || FALLBACK_IMAGE_BASE).replace(/\/+$/, "");
  let name = String((file && (file.fileMd5 || file.fileName)) || "").trim();
  if (!name) return "";
  if (/^https?:\/\//i.test(name)) return name;
  if (name.includes("?")) return host + "/message/" + name;
  return host + "/message/" + name + "?imageView2/2/w/640/h/640&imageMogr2/format/webp";
}

function isRevoke(data) {
  return !!(data && data.fpForMessage && (data.uid || data.nickName) && !data.walletId && !data.files);
}

function revokeLine(data, message) {
  const nick = String((data && data.nickName) || (message && message.nick) || "").trim();
  const beNick = String((data && data.beNickName) || "").trim();
  const beUid = String((data && data.beUid) || "").trim();
  const uid = String((data && data.uid) || "").trim();
  if (nick && beNick && beUid && beUid !== uid) return nick + "撤回了\"" + beNick + "\"的一条消息";
  if (nick) return "\"" + nick + "\" 撤回了一条消息";
  return "撤回了一条消息";
}

function parse(content) {
  if (!content.startsWith("{")) return null;
  try {
    const data = JSON.parse(content);
    return data && typeof data === "object" ? data : null;
  } catch (error) {
    return null;
  }
}

function textOf(type, content, data) {
  if (data) {
    if (typeof data.origin_text === "string" && data.origin_text.trim()) return data.origin_text.trim();
    if (data.to && typeof data.to.text === "string" && data.to.text.trim()) return data.to.text.trim();
    if (typeof data.text === "string" && data.text.trim() && !Array.isArray(data.files)) return data.text.trim();
    if (type === "11" && data.from && typeof data.from.text === "string") return data.from.text.trim();
  }
  const mini = content.match(/^\[视频小程序\]\s*([^,，]+)/);
  if (mini) return mini[1].trim();
  return content;
}
