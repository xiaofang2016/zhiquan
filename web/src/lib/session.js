import { API, state, toast } from "../state.js";
import { postAccount } from "./api.js";
import { MATCH_SOUNDS, matchFlag, playMatchSound, soundChoice, soundLabel } from "./scores.js";

const ACCOUNT_KEY = "sqd-account";
export const SIGN_REWARDS = [30, 50, 70, 90, 110, 130, 150];
export const SIGN_NAMES = ["第一天", "第二天", "第三天", "第四天", "第五天", "第六天", "第七天"];
export const SHARE_CHANNELS = ["微信", "朋友圈", "QQ", "QQ空间", "微博", "复制链接"];
export const LEVEL_RULES = [
  ["用户等级", "用户特权", "专属头衔"],
  ["Lv.1~Lv.3", "球技100~300", "身份标识"],
  ["Lv.4~Lv.6", "球技450~750", "身份标识、投屏体验"],
  ["Lv.7~Lv.9", "球技950~1350", "身份标识、投屏体验"],
  ["Lv.10~Lv.12", "球技1600~2100", "身份标识、投屏体验、酷炫礼物"],
  ["Lv.13~Lv.15", "球技2400~3000", "身份标识、投屏体验、酷炫礼物"],
  ["Lv.16~Lv.18", "球技3350~4050", "身份标识、投屏体验、酷炫礼物"],
  ["Lv.19~Lv.21", "球技4450~5250", "身份标识、投屏体验、酷炫礼物、登场弹幕"],
  ["Lv.22~Lv.24", "球技5700~6600", "身份标识、投屏体验、酷炫礼物、登场弹幕"],
  ["Lv.25~Lv.27", "球技7100~8100", "身份标识、投屏体验、酷炫礼物、登场弹幕"],
  ["Lv.28~Lv.30", "球技8650~9750", "身份标识、投屏体验、酷炫礼物、登场弹幕"],
  ["Lv.31~Lv.33", "球技10350~11550", "身份标识、投屏体验、酷炫礼物、登场弹幕"],
  ["Lv.34~Lv.36", "球技12200~13500", "身份标识、投屏体验、酷炫礼物、登场弹幕"],
  ["Lv.37~Lv.39", "球技14200~15600", "身份标识、投屏体验、酷炫礼物、登场弹幕"],
  ["Lv.40~Lv.42", "球技16350~17850", "身份标识、投屏体验、酷炫礼物、登场弹幕"],
  ["Lv.43~Lv.45", "球技18650~20250", "身份标识、投屏体验、酷炫礼物、登场弹幕"],
  ["Lv.46~Lv.48", "球技21100~22800", "身份标识、投屏体验、酷炫礼物、登场弹幕"],
  ["Lv.49~Lv.50", "球技23700~24600", "身份标识、投屏体验、酷炫礼物、登场弹幕"]
];

let avatarUpload = null;
let smsTimer = 0;
let bindTimer = 0;

export function savedAccount() {
  try { return JSON.parse(localStorage.getItem(ACCOUNT_KEY) || "null"); }
  catch (error) { return null; }
}

export function blankMission() {
  return { balance: 0, signDay: 0, signed: false, gotReg: false, gotLogin: false, records: [] };
}

function normalizeMission(data) {
  const blank = blankMission();
  if (!data || typeof data !== "object") return blank;
  return {
    balance: Number(data.balance) || 0,
    signDay: Number(data.signDay) || 0,
    signed: !!data.signed,
    gotReg: !!data.gotReg,
    gotLogin: !!data.gotLogin,
    records: Array.isArray(data.records) ? data.records : []
  };
}

export function missionData() {
  return state.mission || blankMission();
}

export function avatarSrc(value) {
  if (!value) return "";
  if (value.startsWith("data:") || value.startsWith("http://") || value.startsWith("https://")) return value;
  return API + value;
}

export function walletText() {
  if (state.ued && state.ued.balance) return state.ued.balance;
  return (state.jxt && state.jxt.balance) || "0.00";
}

export function brandBound(brand) {
  const info = brand === "ued" ? state.ued : state.jxt;
  return info && info.isBind === "1";
}

export function brandLabel(brand) {
  const info = brand === "ued" ? state.ued : state.jxt;
  const bound = info && info.isBind === "1";
  const name = brand === "ued" ? "UED" : "吉祥";
  return "提现到" + name + "｜" + (bound ? (info.account || "已绑定") : "未绑定");
}

function hideOverlays() {
  state.soundOpen = false;
  state.shareOpen = false;
  state.avatarOpen = false;
  state.avatarPick = false;
  state.accountOpen = false;
}

export function openMine() {
  hideOverlays();
  state.screen = "mine";
  state.returnMine = false;
  loadJxt();
  refreshMission();
}

export function closeMine() {
  state.screen = "";
  state.returnMine = false;
}

export function toggleMine() {
  if (state.screen === "mine") closeMine();
  else openMine();
}

export function openAccount(mode, backToMine) {
  state.returnMine = !!backToMine;
  if (mode) state.authMode = mode;
  hideOverlays();
  state.screen = "login";
}

export function closeAccount() {
  state.screen = "";
  if (!state.returnMine) return;
  state.returnMine = false;
  openMine();
}

export function rememberAccount(user) {
  state.user = user;
  if (user) localStorage.setItem(ACCOUNT_KEY, JSON.stringify(user));
  else localStorage.removeItem(ACCOUNT_KEY);
  refreshMission();
}

export async function restoreAccount() {
  const saved = savedAccount();
  if (!saved || !saved.token) return;
  try {
    rememberAccount(await postAccount("/prod-api/user/session", { token: saved.token }));
  } catch (error) {
    rememberAccount(null);
  }
}

function startWait(field, seconds) {
  state[field] = seconds;
  const timer = setInterval(() => {
    state[field] -= 1;
    if (state[field] <= 0) {
      state[field] = 0;
      clearInterval(timer);
    }
  }, 1000);
  return timer;
}

export async function sendCode() {
  const phone = state.phone.trim();
  if (!/^1[3-9]\d{9}$/.test(phone)) { toast("请输入正确的手机号"); return; }
  if (state.smsWait > 0) return;
  try {
    await postAccount("/prod-api/user/sms", { phone });
    toast("验证码已发送");
    if (smsTimer) clearInterval(smsTimer);
    smsTimer = startWait("smsWait", 60);
  } catch (error) {
    toast(error.message || "验证码发送失败");
  }
}

export async function submitAccount() {
  const phone = state.phone.trim();
  const password = state.password;
  const confirm = state.confirm;
  const code = state.code.trim();
  if (!/^1[3-9]\d{9}$/.test(phone)) { toast("请输入正确的手机号"); return; }
  if (password.length < 6 || password.length > 20) { toast("密码需要 6 到 20 位"); return; }
  if (state.authMode === "register" && !/^\d{6}$/.test(code)) { toast("请输入6位验证码"); return; }
  if (state.authMode === "register" && password !== confirm) { toast("两次输入密码不一致"); return; }
  state.authBusy = true;
  try {
    const path = state.authMode === "register" ? "/prod-api/user/register" : "/prod-api/user/login";
    const user = await postAccount(path, { phone, password, confirm, code });
    rememberAccount(user);
    state.password = "";
    state.confirm = "";
    state.code = "";
    closeAccount();
    toast(state.authMode === "register" ? "注册成功" : "登录成功");
  } catch (error) {
    toast(error.message || "登录失败");
  } finally {
    state.authBusy = false;
  }
}

export function logout() {
  rememberAccount(null);
  state.jxt = { isBind: "0", account: "", balance: "0.00" };
  state.ued = { isBind: "0", account: "", balance: "0.00" };
  toast("已退出");
}

export async function loadJxt() {
  if (!state.user || !state.user.token) {
    state.jxt = { isBind: "0", account: "", balance: "0.00" };
    state.ued = { isBind: "0", account: "", balance: "0.00" };
    return;
  }
  try {
    state.jxt = await postAccount("/prod-api/user/jxt/status", { token: state.user.token });
  } catch (error) {
    state.jxt = { isBind: "0", account: "", balance: "0.00" };
  }
  try {
    state.ued = await postAccount("/prod-api/user/ued/status", { token: state.user.token });
  } catch (error) {
    state.ued = { isBind: "0", account: "", balance: "0.00" };
  }
}

export function openBrand(brand) {
  if (!state.user) { openAccount("login", true); return; }
  state.bindBrand = brand;
  state.bindCode = "";
  hideOverlays();
  state.screen = "bind";
}

function brandPath() {
  return state.bindBrand === "ued" ? "/prod-api/user/ued" : "/prod-api/user/jxt";
}

export async function sendBindCode() {
  if (!state.user) { openAccount("login", true); return; }
  if (state.bindSmsWait > 0) return;
  try {
    await postAccount(brandPath() + "/sms", { token: state.user.token });
    toast("验证码已发送");
    if (bindTimer) clearInterval(bindTimer);
    bindTimer = startWait("bindSmsWait", 60);
  } catch (error) {
    toast(error.message || "验证码发送失败");
  }
}

export async function submitBind() {
  if (!state.user) return;
  const code = state.bindCode.trim();
  if (!/^\d{4,6}$/.test(code)) { toast("请输入验证码"); return; }
  state.bindBusy = true;
  try {
    const info = await postAccount(brandPath() + "/bind", { token: state.user.token, code });
    if (state.bindBrand === "ued") state.ued = info;
    else state.jxt = info;
    state.bindCode = "";
    openMine();
    toast("绑定成功");
  } catch (error) {
    toast(error.message || "绑定失败");
  } finally {
    state.bindBusy = false;
  }
}

export async function unbindBrand() {
  if (!state.user) return;
  const name = state.bindBrand === "ued" ? "UED" : "吉祥";
  if (!window.confirm("是否确认解除绑定" + name + "账号？解绑后将无法进行提现操作")) return;
  try {
    const info = await postAccount(brandPath() + "/unbind", { token: state.user.token });
    if (state.bindBrand === "ued") state.ued = info;
    else state.jxt = info;
    toast("已解除绑定");
  } catch (error) {
    toast(error.message || "解除绑定失败");
  }
}

export async function withdrawBrand(brand) {
  if (!state.user) { openAccount("login", true); return; }
  const info = brand === "ued" ? state.ued : state.jxt;
  if (!(info && info.isBind === "1")) { openBrand(brand); return; }
  state.withdrawBusy = true;
  const path = brand === "ued" ? "/prod-api/user/ued/withdraw" : "/prod-api/user/jxt/withdraw";
  try {
    const next = await postAccount(path, { token: state.user.token, amount: state.withdrawAmount.trim() });
    if (brand === "ued") state.ued = next;
    else state.jxt = next;
    state.withdrawAmount = "";
    toast("提现成功");
  } catch (error) {
    toast(error.message || "提现失败");
  } finally {
    state.withdrawBusy = false;
  }
}

export function openAvatar() {
  if (!state.user) { toast("未登录，请登录后使用！"); return; }
  state.avatarPreview = "";
  state.avatarReady = false;
  state.avatarPick = false;
  state.avatarOpen = true;
}

export function closeAvatar() {
  state.avatarPreview = "";
  state.avatarReady = false;
  state.avatarOpen = false;
  state.avatarPick = false;
}

function compressAvatar(file) {
  return new Promise((resolve, reject) => {
    const image = new Image();
    const url = URL.createObjectURL(file);
    image.onload = () => {
      URL.revokeObjectURL(url);
      const max = 800;
      const scale = Math.min(1, max / image.width, max / image.height);
      const width = Math.max(1, Math.round(image.width * scale));
      const height = Math.max(1, Math.round(image.height * scale));
      const canvas = document.createElement("canvas");
      canvas.width = width;
      canvas.height = height;
      const ctx = canvas.getContext("2d");
      if (!ctx) { reject(new Error("图片压缩失败")); return; }
      ctx.drawImage(image, 0, 0, width, height);
      const encode = (quality) => {
        canvas.toBlob((blob) => {
          if (!blob) { reject(new Error("图片压缩失败")); return; }
          if (blob.size > 180000 && quality > 0.45) { encode(Math.round((quality - 0.15) * 100) / 100); return; }
          const reader = new FileReader();
          reader.onload = () => resolve(String(reader.result || ""));
          reader.onerror = () => reject(new Error("图片压缩失败"));
          reader.readAsDataURL(blob);
        }, "image/jpeg", quality);
      };
      encode(0.82);
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("图片加载失败"));
    };
    image.src = url;
  });
}

export function uploadAvatar(dataUrl) {
  if (!state.user) { toast("未登录，请登录后使用！"); return Promise.resolve(); }
  if (avatarUpload) return avatarUpload;
  state.avatarBusy = true;
  avatarUpload = postAccount("/prod-api/user/avatar", { token: state.user.token, image: dataUrl })
    .then((user) => {
      state.avatarReady = true;
      state.avatarPreview = avatarSrc(user && user.avatar) || dataUrl;
      rememberAccount(user);
    })
    .catch((error) => {
      state.avatarReady = false;
      toast(error.message || "上传失败");
    })
    .finally(() => {
      avatarUpload = null;
      state.avatarBusy = false;
    });
  return avatarUpload;
}

export async function takeAvatar(file) {
  if (!file) return;
  if (file.type && !file.type.startsWith("image/")) { toast("请选择图片文件"); return; }
  state.avatarPick = false;
  try {
    const dataUrl = await compressAvatar(file);
    state.avatarPreview = dataUrl;
    state.avatarReady = false;
    await uploadAvatar(dataUrl);
  } catch (error) {
    toast(error.message || "上传失败");
  }
}

export async function saveAvatar() {
  if (!state.user || !state.avatarPreview) return;
  if (!state.avatarReady) await uploadAvatar(state.avatarPreview);
  if (!state.avatarReady) return;
  closeAvatar();
  toast("修改成功");
}

export function toggleMatch(key, fallback) {
  localStorage.setItem(key, JSON.stringify(!matchFlag(key, fallback)));
  state.settingsRev += 1;
}

export function chooseSound(key) {
  localStorage.setItem(state.soundSide === "away" ? "SetUpCom-awaySound" : "SetUpCom-homeSound", key);
  state.soundOpen = false;
  state.settingsRev += 1;
  playMatchSound(key);
}

export function openSound(side) {
  state.soundSide = side;
  state.soundOpen = true;
}

export async function refreshMission() {
  if (!state.user || !state.user.token) {
    state.mission = blankMission();
    return state.mission;
  }
  try {
    state.mission = normalizeMission(await postAccount("/prod-api/user/mission", { token: state.user.token }));
  } catch (error) {
    state.mission = state.mission || blankMission();
  }
  return state.mission;
}

export async function openOrder(from) {
  state.orderFrom = from || "mine";
  state.screen = "order";
  await refreshMission();
}

export function closeOrder() {
  if (state.orderFrom === "task") openTask(state.taskFrom);
  else if (state.orderFrom === "forecast") openForecast();
  else openMine();
}

export async function openLevel() {
  state.screen = "level";
  await refreshMission();
}

export function levelView() {
  const skill = state.user ? missionData().balance : 0;
  const left = skill < 100 ? 0 : Math.floor(skill / 100) * 100;
  const right = left + 100;
  const level = skill < 100 ? 1 : Math.floor(skill / 100);
  return {
    title: "球迷Lv." + level,
    left,
    right,
    need: "差 " + (right - skill) + "球技 升至Lv." + (level + 1),
    width: ((skill - left) / (right - left) * 100) + "%"
  };
}

export async function openTask(from) {
  state.taskFrom = from || "mine";
  state.screen = "task";
  await refreshMission();
}

export function closeTask() {
  if (state.taskFrom === "forecast") openForecast();
  else openMine();
}

export async function handleSign(day) {
  if (!state.user) return;
  try {
    state.mission = normalizeMission(await postAccount("/prod-api/user/mission/sign", { token: state.user.token, day }));
    toast("^-^ 签到成功");
  } catch (error) {
    const message = error.message || "签到失败";
    toast(message === "未到签到时间" || message === "今日已签到" ? "^-^ " + message : message);
  }
}

export async function claimMission(type) {
  if (!state.user) return;
  try {
    state.mission = normalizeMission(await postAccount("/prod-api/user/mission/claim", { token: state.user.token, type }));
    toast("领取成功");
  } catch (error) {
    if (error.message === "已领取") return;
    toast(error.message || "领取失败");
  }
}

export async function openInvite(from) {
  state.inviteFrom = from || "mine";
  state.screen = "invite";
  await refreshMission();
}

export function closeInvite() {
  if (state.inviteFrom === "task") openTask(state.taskFrom);
  else openMine();
}

export async function chooseShare(name) {
  state.shareOpen = false;
  if (name === "复制链接") {
    try {
      await navigator.clipboard.writeText("https://www.kzbzcq.com");
      toast("复制成功");
    } catch (error) {
      toast("复制失败");
    }
    return;
  }
  toast("可在浏览器工具栏选择分享");
}

export function openForecast() {
  state.forecastTab = "predict";
  state.screen = "forecast";
}

export function submitFeedback() {
  const text = state.feedback.trim();
  if (!text) { toast("请输入你的反馈"); return; }
  let saved = [];
  try { saved = JSON.parse(localStorage.getItem("sqd-feedback") || "[]"); }
  catch (error) { saved = []; }
  saved.push({ text, at: Date.now() });
  localStorage.setItem("sqd-feedback", JSON.stringify(saved.slice(-20)));
  state.feedback = "";
  toast("提交成功");
}

export async function copyShare() {
  try {
    await navigator.clipboard.writeText("https://www.kzbzcq.com");
    toast("复制成功");
  } catch (error) {
    toast("复制失败");
  }
}

export function onMine(action, needsLogin) {
  if (action === "back") { closeMine(); return; }
  if (action === "logout") { logout(); return; }
  if (action === "profile") {
    if (!state.user) openAccount("login", true);
    return;
  }
  if (action === "avatar") { openAvatar(); return; }
  if (action === "match") { state.screen = "settings"; return; }
  if (action === "jxt-bind") { openBrand("jxt"); return; }
  if (action === "ued-bind") { openBrand("ued"); return; }
  if (action === "jxt-withdraw") { withdrawBrand("jxt"); return; }
  if (action === "ued-withdraw") { withdrawBrand("ued"); return; }
  if (needsLogin && !state.user) { openAccount("login", true); return; }
  if (action === "order") { openOrder("mine"); return; }
  if (action === "level") { openLevel(); return; }
  if (action === "badge") { state.screen = "badge"; return; }
  if (action === "forecast") { openForecast(); return; }
  if (action === "feedback") { state.screen = "feedback"; return; }
  if (action === "share") { state.screen = "share"; return; }
  if (action === "mission" || action === "yinlang") { openTask("mine"); return; }
  if (action === "invite") { openInvite("mine"); return; }
  if (action === "promo") { state.screen = "promo"; return; }
  if (action === "agent") { state.screen = "agent"; return; }
  toast("暂未开放");
}

export function bindFromBadge() {
  if (brandBound("jxt") && brandBound("ued")) return;
  openBrand(brandBound("jxt") ? "ued" : "jxt");
}

export async function loadPack() {
  try {
    const body = state.user && state.user.token ? { token: state.user.token } : {};
    state.pack = await postAccount("/prod-api/user/redpack", body);
  } catch (error) {
    state.pack = null;
  }
}

export function openPack() {
  if (!state.pack || !Number(state.pack.num)) {
    toast("暂无红包");
    return;
  }
  state.screen = "pack";
}

export function closePack() {
  state.screen = "";
  loadPack();
}

export async function claimPack() {
  if (!state.user || !state.user.token) { openAccount("login"); return; }
  if (state.pack && state.pack.mine) return;
  if (state.claimBusy) return;
  state.claimBusy = true;
  try {
    state.pack = await postAccount("/prod-api/user/redpack/claim", { token: state.user.token });
  } catch (error) {
    toast(error.message || "领取失败");
    loadPack();
  } finally {
    state.claimBusy = false;
  }
}

export function packView() {
  const packet = state.pack;
  const total = Number(packet && packet.num);
  if (!packet || !Number.isFinite(total) || total <= 0) return null;
  const drawn = Number(packet.drawNum) || 0;
  const sold = drawn >= total;
  return {
    count: drawn + "/" + total,
    from: (packet.sender || "说球帝") + "的红包",
    desc: packet.description || "拼手气红包",
    showOpen: !packet.mine && !sold,
    result: packet.mine ? "¥" + packet.mine : (sold ? "已领完" : "")
  };
}

export function homeSoundLabel() {
  state.settingsRev;
  return soundLabel(soundChoice("home"));
}

export function awaySoundLabel() {
  state.settingsRev;
  return soundLabel(soundChoice("away"));
}

export function goalOn() {
  state.settingsRev;
  return matchFlag("SetUpCom-checked", true);
}

export function redOn() {
  state.settingsRev;
  return matchFlag("SetUpCom-checkedRed", false);
}

export { MATCH_SOUNDS, soundChoice };
