<template>
  <header class="top">
    <div class="brand">
      <svg width="36" height="36" viewBox="0 0 36 36" aria-hidden="true"><rect rx="8" width="36" height="36" fill="#e23b3b"/><text x="18" y="24" text-anchor="middle" fill="#fff" font-size="16" font-family="PingFang SC, Microsoft YaHei">说</text></svg>
      <div><b>说球帝</b><small></small></div>
    </div>
    <nav class="tabs">
      <button v-for="item in tabs" :key="item.id" type="button" :class="'tab' + (state.tab === item.id ? ' on' : '')" @click="openTab(item.id)">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path :d="item.icon" /></svg>
        {{ item.label }}
      </button>
    </nav>
    <div class="spacer"></div>
    <div class="account">
      <button class="ghost" type="button" @click="toggleMine">{{ state.user ? (state.user.nickname || "已登录") + " ▾" : "未登录 ▾" }}</button>
    </div>
    <div class="split"></div>
    <button class="ghost" type="button" @click="toast('客户端暂未开放')">APP客户端</button>
  </header>
  <div class="layout">
    <aside v-show="showSide" class="side">
      <h2>{{ state.tab === 'community' ? '圈子' : '全部分类' }}</h2>
      <button v-if="state.tab === 'db'" class="cat on" type="button">资料库</button>
      <template v-else-if="state.tab === 'community'">
        <button type="button" :class="'cat' + (state.communityCircle ? '' : ' on')" @click="pickCircle(0)"><span class="ph">全</span>全部</button>
        <button v-for="circle in state.communityCircles" :key="circle.id" type="button" :class="'league' + (state.communityCircle === circle.id ? ' on' : '')" @click="pickCircle(circle.id)">
          <Logo :url="circle.icon" :name="circle.name" />{{ circle.name }}
        </button>
      </template>
      <template v-else>
        <button v-for="item in fixed" :key="item.id" type="button" :class="'cat' + (item.on ? ' on' : '')" @click="selectFixed(item.id)"><span class="ph">{{ item.label.slice(0, 1) }}</span>{{ item.label }}</button>
        <button v-for="league in state.leagues" :key="league.type + '-' + league.id" type="button" :class="'league' + (state.cid === league.id && state.type === league.type ? ' on' : '')" @click="selectLeague(league)">
          <Logo :url="league.logo" :name="league.name_zh || league.name" />{{ league.name_zh || league.name }}
        </button>
      </template>
    </aside>
    <main :class="mainClass">
      <DetailPage v-if="state.detail && state.tab === 'match'" />
      <MatchPage v-else-if="state.tab === 'match'" />
      <LivePage v-else-if="state.tab === 'live'" />
      <CommunityPage v-else-if="state.tab === 'community'" />
      <ChatPage v-else-if="state.tab === 'chat'" />
      <DatabasePage v-else />
    </main>
  </div>
  <LoginPage v-if="state.screen === 'login'" />
  <MinePage v-else-if="state.screen === 'mine'" />
  <BindPage v-else-if="state.screen === 'bind'" />
  <MatchSettingsPage v-else-if="state.screen === 'settings'" />
  <OrderPage v-else-if="state.screen === 'order'" />
  <MallPage v-else-if="state.screen === 'mall'" />
  <LevelPage v-else-if="state.screen === 'level'" />
  <LevelRulePage v-else-if="state.screen === 'level-rule'" />
  <BadgePage v-else-if="state.screen === 'badge'" />
  <BadgeRightsPage v-else-if="state.screen === 'badge-rights'" />
  <ForecastPage v-else-if="state.screen === 'forecast'" />
  <TaskPage v-else-if="state.screen === 'task'" />
  <InvitePage v-else-if="state.screen === 'invite'" />
  <PromoPage v-else-if="state.screen === 'promo'" />
  <AgentPage v-else-if="state.screen === 'agent'" />
  <RankPage v-else-if="state.screen === 'rank'" />
  <FeedbackPage v-else-if="state.screen === 'feedback'" />
  <SharePage v-else-if="state.screen === 'share'" />
  <PackPage v-else-if="state.screen === 'pack'" />
  <AvatarSheet v-if="state.avatarOpen" />
  <div v-if="state.shareOpen" class="sound-mask" @click.self="state.shareOpen = false">
    <div class="sound-sheet share-sheet" role="dialog" aria-label="分享">
      <b>分享</b>
      <div>
        <button v-for="name in SHARE_CHANNELS" :key="name" type="button" @click="chooseShare(name)">{{ name }}</button>
      </div>
      <div class="avatar-gap"></div>
      <button type="button" @click="state.shareOpen = false">取消</button>
    </div>
  </div>
  <div v-if="state.toast" class="toast" style="display:block">{{ state.toast }}</div>
  <div class="dock">
    <button v-if="pack" class="dock-pack" type="button" aria-label="领取红包" @click="openPack">
      <img alt="" src="https://live.qiandongslim.com/static/img/redpack-nomal.a0186118.gif">
      <span>{{ pack.count }}</span>
    </button>
    <button class="dock-top" type="button" aria-label="回到顶部" @click="toTop">
      <svg width="22" height="22" viewBox="0 0 22 22" aria-hidden="true"><path d="M11 4l7 8h-4v6H8v-6H4z" fill="#2ec8c4"/></svg>
    </button>
    <button class="dock-app" type="button" aria-label="APP客户端" @click="toast('客户端暂未开放')">
      <svg width="22" height="22" viewBox="0 0 22 22" aria-hidden="true"><rect x="2" y="2" width="8" height="8" rx="2" fill="#fff"/><rect x="12" y="2" width="8" height="8" rx="2" fill="#fff"/><rect x="2" y="12" width="8" height="8" rx="2" fill="#fff"/><rect x="12" y="12" width="8" height="8" rx="2" fill="#fff"/></svg>
    </button>
    <button class="refresh" type="button" title="刷新" @click="refresh">↻</button>
  </div>
</template>
<script setup>
import { computed } from "vue";
import { state, toast } from "./state.js";
import Logo from "./components/Logo.vue";
import MatchPage from "./pages/MatchPage.vue";
import LivePage from "./pages/LivePage.vue";
import DatabasePage from "./pages/DatabasePage.vue";
import CommunityPage from "./pages/CommunityPage.vue";
import ChatPage from "./pages/ChatPage.vue";
import DetailPage from "./pages/DetailPage.vue";
import LoginPage from "./pages/LoginPage.vue";
import MinePage from "./pages/MinePage.vue";
import BindPage from "./pages/BindPage.vue";
import MatchSettingsPage from "./pages/MatchSettingsPage.vue";
import OrderPage from "./pages/OrderPage.vue";
import MallPage from "./pages/MallPage.vue";
import LevelPage from "./pages/LevelPage.vue";
import LevelRulePage from "./pages/LevelRulePage.vue";
import BadgePage from "./pages/BadgePage.vue";
import BadgeRightsPage from "./pages/BadgeRightsPage.vue";
import ForecastPage from "./pages/ForecastPage.vue";
import TaskPage from "./pages/TaskPage.vue";
import InvitePage from "./pages/InvitePage.vue";
import PromoPage from "./pages/PromoPage.vue";
import AgentPage from "./pages/AgentPage.vue";
import RankPage from "./pages/RankPage.vue";
import FeedbackPage from "./pages/FeedbackPage.vue";
import SharePage from "./pages/SharePage.vue";
import PackPage from "./pages/PackPage.vue";
import AvatarSheet from "./components/AvatarSheet.vue";
import { selectFixed, selectLeague, resetMatches } from "./lib/list.js";
import { loadLive, selectTab } from "./lib/scores.js";
import { chooseShare, openPack, packView, SHARE_CHANNELS, toggleMine } from "./lib/session.js";
import { loadCommunity, pickCircle } from "./lib/community.js";
import { loadChatGroups } from "./lib/chat.js";

const tabs = [
  ["match", "比赛", "M7 4h10v2a5 5 0 0 1-4 4.9V13h3v2H8v-2h3V10.9A5 5 0 0 1 7 6V4zm-2 1H3v2a4 4 0 0 0 3.2 3.9M19 5h2v2a4 4 0 0 1-3.2 3.9"],
  ["live", "即时比分", "M6 3v2M18 3v2M4 8h16M5 5h14a1 1 0 0 1 1 1v13a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z"],
  ["db", "资料库", "M4 18V8M10 18V4M16 18v-6M20 18H4"],
  ["community", "社球帝", "M5 6h14a2 2 0 0 1 2 2v7a2 2 0 0 1-2 2H9l-4 3v-3H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2z"]
].map(([id, label, icon]) => ({ id, label, icon }));

const showSide = computed(() => state.tab !== "live" && state.tab !== "chat" && !(state.detail && state.tab === "match"));
const mainClass = computed(() => {
  if (state.detail && state.tab === "match") return "main detail-main";
  if (state.tab === "chat") return "main main-chat";
  return "main";
});
const fixed = computed(() => [
  ["hot", "热门", state.hot && !state.resultOnly],
  ["football", "足球", state.type === 1 && !state.cid && !state.hot && !state.resultOnly],
  ["basketball", "篮球", state.type === 2 && !state.cid && !state.hot && !state.resultOnly],
  ["result", "赛果", state.resultOnly]
].map(([id, label, on]) => ({ id, label, on })));
const pack = computed(() => packView());

function openTab(id) {
  if (id !== state.tab) state.communityPost = null;
  selectTab(id);
  if (id === "community" && !state.communityPosts.length && !state.communityLoading) loadCommunity();
  if (id === "chat" && !state.chatGroups.length && !state.chatLoading) loadChatGroups();
}
function refresh() {
  if (state.tab === "live") {
    state.liveLoaded = false;
    state.liveRows = [];
    loadLive();
  } else if (state.tab === "community") loadCommunity();
  else if (state.tab === "chat") loadChatGroups();
  else if (state.tab === "match") resetMatches();
}
function toTop() {
  window.scrollTo({ top: 0, behavior: "smooth" });
}
</script>
