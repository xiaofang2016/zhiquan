import { createApp } from "vue";
import App from "./App.vue";
import "./styles.css";
import { state, toast } from "./state.js";
import { get } from "./lib/api.js";
import { shanghaiDay } from "./lib/match.js";
import { loadMatches, pollList } from "./lib/list.js";
import { loadLive, refreshScores } from "./lib/scores.js";
import { onRoute } from "./lib/detail.js";
import { loadPack, restoreAccount, savedAccount } from "./lib/session.js";

state.user = savedAccount();
createApp(App).mount("#app");

window.addEventListener("popstate", onRoute);
restoreAccount().then(() => loadPack());
loadPack();

Promise.all([
  get("/prod-api/match/date/count?type=-1&cid=0"),
  get("/prod-api/category/list?type=0&isoften=1&ishot=1&langtype=zh")
]).then(([dates, categories]) => {
  state.dates = dates || [];
  state.leagues = (categories && categories.twoCategoryList) || [];
  state.date = shanghaiDay(0);
}).catch((error) => {
  toast(error.message || "接口加载失败");
});

if (location.pathname.startsWith("/player/")) onRoute();
else if (state.tab === "match") loadMatches(true);

setInterval(() => {
  refreshScores().catch(() => {});
  pollList().catch(() => {});
}, 2000);
setInterval(() => {
  if (document.visibilityState === "hidden") return;
  if (state.tab === "live" && !state.loading) loadLive();
  loadPack();
}, 15000);
refreshScores().catch(() => {});
