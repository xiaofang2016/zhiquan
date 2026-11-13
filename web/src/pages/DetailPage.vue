<template>
  <div class="detail">
    <section :class="'screen' + (covered ? ' covered' : '')">
      <div class="stage" ref="stage">
        <div :class="'cover' + (covered ? '' : ' hide')">
          <div class="cover-side"><Logo :url="match.hteam_logo" :name="match.hteam_name" /><div class="name">{{ match.hteam_name || "" }}</div></div>
          <div class="cover-mid">
            <div class="cover-score">{{ coverScore }}</div>
            <div class="cover-vs">VS</div>
          </div>
          <div class="cover-side"><Logo :url="match.ateam_logo" :name="match.ateam_name" /><div class="name">{{ match.ateam_name || "" }}</div></div>
        </div>
        <iframe v-if="frameSrc" :src="frameSrc" allowfullscreen></iframe>
        <video v-show="videoOn" ref="video" controls autoplay playsinline @resize="reveal" @playing="reveal" @error="covered = true"></video>
        <div v-show="patch" class="wm" :style="patchStyle"></div>
      </div>
      <div class="screen-top">
        <button class="back" type="button" @click="closePlayer">返回</button>
        <span class="status-pill">{{ liveStatus(match) }}</span>
      </div>
      <div class="screen-bottom">
        <div class="side-team"><Logo :url="match.hteam_logo" :name="match.hteam_name" /><span>{{ match.hteam_name || "" }}</span></div>
        <div class="score-big">{{ scoreText(match).replace(/ - /g, " : ") }}</div>
        <div class="side-team away"><Logo :url="match.ateam_logo" :name="match.ateam_name" /><span>{{ match.ateam_name || "" }}</span></div>
      </div>
      <div class="screen-lines">
        <button v-for="(choice, index) in lines" :key="choice.url" type="button" :class="'line-btn' + (active === index ? ' on' : '')" @click="play(choice, index)">{{ choice.name }}</button>
      </div>
    </section>
    <aside class="room">
      <div v-if="sportType(match) !== 3" class="room-tabs">
        <button v-for="tab in tabs" :key="tab.id" type="button" :class="state.detailTab === tab.id ? 'on' : ''" @click="showDetailTab(match, tab.id)">{{ tab.label }}</button>
      </div>
      <div :class="'room-body' + (state.detailTab === 'chat' ? ' chat' : '')">
        <div v-if="state.detailTab === 'chat'" class="chat-list" ref="chatList" @scroll="onChatScroll">
          <div v-if="room.loading && !room.messages.length" class="closed">正在连接聊天室</div>
          <div v-else-if="room.error && !room.messages.length" class="closed">{{ room.error }}</div>
          <div v-else-if="!room.messages.length" class="closed">{{ room.closed ? "当前聊天室已关闭" : "还没有人说话" }}</div>
          <div v-for="message in room.messages" :key="message.id" class="chat-row">
            <img v-if="badgeOf(message)" class="badge" :src="badgeOf(message)" alt="" referrerpolicy="no-referrer">
            <b>{{ message.nick || "游客" }}</b>
            <span v-if="message.content" :class="message.bubble ? 'bubble' : ''" :style="bubbleStyle(message)">{{ message.content }}</span>
            <img v-for="(image, index) in safeImages(message)" :key="index" class="pic" :src="image.url" alt="" referrerpolicy="no-referrer">
          </div>
        </div>
        <form v-if="state.detailTab === 'chat'" class="chat-send" @submit.prevent="onSpeak">
          <input v-model="draft" maxlength="200" :disabled="room.closed" :placeholder="room.closed ? '当前聊天室已关闭' : (state.user ? '说点什么' : '登录后发言')">
          <button type="submit" :disabled="room.closed || sending">发送</button>
        </form>
        <div v-else class="detail-slot">
          <div v-if="state.detailLoading" class="closed">正在读取</div>
          <div v-else-if="state.detailError" class="closed">{{ state.detailError }}</div>
          <DetailFacts v-else :tab="state.detailTab" :data="state.detailData" :lines="lines" />
        </div>
      </div>
    </aside>
  </div>
</template>
<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import Logo from "../components/Logo.vue";
import DetailFacts from "./DetailFacts.vue";
import { state, toast } from "../state.js";
import { applyScore, liveStatus, phase, scoreText, sportType } from "../lib/match.js";
import { animationFrame, chatOlder, closePlayer, detailLines, loadDetail, openRoomChat, playLine, room, sendRoomChat, showDetailTab, stopPlayback, stopRoomChat, stopDetailRoom } from "../lib/detail.js";
import { videoContentBox, watermarkPatch } from "../lib/watermark.js";

const stage = ref(null);
const video = ref(null);
const chatList = ref(null);
const draft = ref("");
const sending = ref(false);
const covered = ref(true);
const videoOn = ref(false);
const patch = ref(null);
const frameSrc = ref("");
const active = ref(-1);
const tabs = [
  ["chat", "聊天"],
  ["live", "直播"],
  ["odds", "指数"],
  ["data", "数据"],
  ["tip", "预测"]
].map(([id, label]) => ({ id, label }));

const match = computed(() => applyScore(state.detail) || {});
const lines = computed(() => detailLines(state.detail || {}));
const coverScore = computed(() => {
  const current = match.value;
  return phase(current) === "wait" || !current.score ? "-" : String(current.score).replace("-", " : ");
});

const patchStyle = computed(() => {
  const box = patch.value;
  if (!box) return {};
  return {
    left: box.left + "px",
    top: box.top + "px",
    width: box.width + "px",
    height: box.height + "px"
  };
});

function placeWatermark() {
  const el = video.value;
  if (!el || covered.value || !videoOn.value) {
    patch.value = null;
    return;
  }
  const content = videoContentBox(el.clientWidth, el.clientHeight, el.videoWidth, el.videoHeight);
  patch.value = watermarkPatch(sportType(match.value), content);
}

function reveal() {
  if (video.value && video.value.videoWidth > 0) covered.value = false;
  placeWatermark();
}

async function play(choice, index) {
  active.value = index;
  frameSrc.value = "";
  videoOn.value = false;
  covered.value = true;
  stopPlayback();
  if (!choice) return;
  if (choice.animation) {
    frameSrc.value = animationFrame(choice);
    covered.value = false;
    return;
  }
  videoOn.value = true;
  await nextTick();
  const result = await playLine(choice, video.value);
  if (result !== "video") {
    videoOn.value = false;
    covered.value = true;
  }
}

function badgeOf(message) {
  return message.badge || room.badges[message.level] || "";
}
function bubbleStyle(message) {
  const style = {};
  if (message.bubble) style.borderImageSource = "url(\"" + String(message.bubble).replace(/["\\]/g, "") + "\")";
  if (message.color) style.color = message.color;
  return style;
}
function safeImages(message) {
  return (message.images || []).filter((image) => image && /^https?:\/\//.test(image.url || ""));
}
async function onSpeak() {
  if (sending.value || room.closed) return;
  const text = draft.value.trim();
  if (!text) return;
  sending.value = true;
  try {
    if (await sendRoomChat(text)) draft.value = "";
  } catch (error) {
    toast(error.message || "发送失败");
  } finally {
    sending.value = false;
  }
}

function onChatScroll() {
  const list = chatList.value;
  if (!list) return;
  room.stick = list.scrollHeight - list.scrollTop - list.clientHeight < 80;
  if (list.scrollTop > 40 || room.loading || room.exhausted) return;
  chatOlder(state.detail);
}

watch([covered, videoOn], () => placeWatermark());

watch(() => room.messages.length, async () => {
  await nextTick();
  const list = chatList.value;
  if (list && room.stick) list.scrollTop = list.scrollHeight;
});

function startRoom() {
  if (!state.detail) return;
  if (sportType(state.detail) === 3) state.detailTab = "chat";
  if (state.detailTab === "chat") openRoomChat(state.detail);
  else loadDetail(state.detail, state.detailTab, false);
  const choices = lines.value;
  const firstVideo = choices.findIndex((choice) => !choice.animation);
  if (firstVideo >= 0) play(choices[firstVideo], firstVideo);
}

let frameObserver;
onMounted(() => {
  startRoom();
  frameObserver = new ResizeObserver(() => placeWatermark());
  if (stage.value) frameObserver.observe(stage.value);
});
watch(() => state.detail && state.detail.id, (id, prev) => {
  if (!id || id === prev) return;
  stopPlayback();
  stopRoomChat();
  stopDetailRoom();
  covered.value = true;
  frameSrc.value = "";
  videoOn.value = false;
  active.value = -1;
  startRoom();
});
onUnmounted(() => {
  if (frameObserver) frameObserver.disconnect();
  stopPlayback();
  stopRoomChat();
  stopDetailRoom();
});
</script>
