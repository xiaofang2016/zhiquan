import { API } from "../state.js";

export async function get(path) {
  const response = await fetch(API + path, { cache: "no-store" });
  const body = await response.json();
  if (!response.ok || body.code !== "0") throw new Error(body.msg || "加载失败");
  return body.data;
}

export async function postAccount(path, body) {
  const response = await fetch(API + path, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body)
  });
  const payload = await response.json().catch(() => ({}));
  if (!response.ok || payload.code !== "0") throw new Error(payload.msg || "请求失败");
  return payload.data;
}
