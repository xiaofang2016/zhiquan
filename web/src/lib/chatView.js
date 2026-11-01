const FALLBACK_IMAGE_BASE = "https://oss.imnono.net";

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
      status: status === "2" ? "已领取" : status === "3" ? "已领完" : "待领取",
      done: status === "2" || status === "3",
      pool: poolDigits(data.dbAmountPool)
    };
  }
  if (type === "13" || (data && Array.isArray(data.files) && data.files.length)) {
    const images = data.files.map((file) => imageSrc(file, imageBase)).filter(Boolean);
    if (images.length) {
      return { kind: "image", text: String(data.text || "").trim(), images };
    }
  }
  if (type === "1" && content && !content.startsWith("{") && !content.startsWith("[")) {
    const src = imageSrc({ fileMd5: content }, imageBase);
    if (src) return { kind: "image", text: "", images: [src] };
  }
  return { kind: "text", text: textOf(type, content, data) };
}

export function previewText(message, imageBase) {
  const view = present(message, imageBase);
  if (view.kind === "image") return view.text || "[图片]";
  if (view.kind === "pack") return view.title || "[红包]";
  return String(view.text || "").replace(/\s+/g, " ").trim();
}

export function poolDigits(raw) {
  const text = String(raw || "").replace(/\D/g, "");
  const cut = text.length > 2 ? text.slice(0, -2) : "0";
  return cut.padStart(6, "0");
}

export function imageSrc(file, imageBase) {
  const host = String(imageBase || FALLBACK_IMAGE_BASE).replace(/\/+$/, "");
  let name = String((file && (file.fileMd5 || file.fileName)) || "").trim();
  if (!name) return "";
  if (/^https?:\/\//i.test(name)) return name;
  if (name.includes("?")) return host + "/message/" + name;
  return host + "/message/" + name + "?imageView2/2/w/640/h/640&imageMogr2/format/webp";
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
