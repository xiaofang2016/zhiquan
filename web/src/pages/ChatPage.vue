<template>
  <div class="im" :class="{ open: !!current }">
    <aside>
      <div v-if="state.chatLoading && !state.chatGroups.length" class="im-empty">正在读取群</div>
      <div v-else-if="state.chatError && !state.chatGroups.length" class="im-empty">{{ state.chatError }}</div>
      <div v-else-if="!state.chatGroups.length" class="im-empty">还没有群</div>
      <button v-for="group in state.chatGroups" :key="group.id" type="button" :class="{ on: state.chatGroupId === group.id }" @click="openChatGroup(group)">
        <i>
          <img v-if="showAvatar(group)" :src="groupAvatar(group.avatar, state.chatImageBase)" alt="" @error="hideAvatar(group.id)" />
          <template v-else>{{ mark(group.name) }}</template>
        </i>
        <span>
          <b>{{ group.name || group.gid }}</b>
          <em>{{ group.latest ? preview(group.latest) : "没有消息" }}</em>
        </span>
        <small v-if="group.latest">{{ chatTime(group.latest.time) }}</small>
      </button>
    </aside>
    <section>
      <header>
        <button v-if="current" class="im-back" type="button" aria-label="返回" @click="back">‹</button>
        <b>{{ current ? (current.name || current.gid) : "选择一个群" }}</b>
        <button type="button" @click="loadChatGroups">刷新</button>
      </header>
      <div v-if="noticeVisible" class="im-notice">
        <i aria-hidden="true"></i>
        <span>群公告：</span>
        <p @click="noticeOpen = true">{{ current.notice }}</p>
        <button type="button" aria-label="关闭公告" @click="hideNotice">×</button>
      </div>
      <div class="im-log">
        <div v-if="!current" class="im-empty">点左边的群看聊天</div>
        <div v-else-if="state.chatLoading && !state.chatMessages.length" class="im-empty">正在读取消息</div>
        <div v-else-if="state.chatError && !state.chatMessages.length" class="im-empty">{{ state.chatError }}</div>
        <div v-else-if="!messages.length" class="im-empty">这个群还没有消息</div>
        <article v-for="(message, index) in messages" :key="message.time + '-' + index" :class="message.view.kind">
          <p v-if="message.view.kind === 'revoke'">{{ message.view.text }}</p>
          <div v-else>
            <b>{{ message.nick || message.sendUid || "用户" }}</b>
            <template v-if="message.view.kind === 'image'">
              <a v-for="(src, imageIndex) in message.view.images" :key="imageIndex" class="shot" :href="src" target="_blank" rel="noopener">
                <img :src="src" alt="" @error="markMiss" />
                <em v-if="imageIndex === message.view.images.length - 1">{{ chatTime(message.time) }}</em>
              </a>
              <p v-if="message.view.text"><RichText :parts="message.view.parts" :text="message.view.text" /></p>
            </template>
            <div v-else-if="message.view.kind === 'pack'" class="im-pack" :class="{ done: message.view.done }">
              <div class="im-pack-top">
                <i>¥</i>
                <span>
                  <b>{{ message.view.title }}</b>
                  <small>{{ message.view.status }}</small>
                </span>
              </div>
              <div class="im-pack-bottom">
                <span>职圈3.0红包</span>
                <em>奖池 <strong>{{ message.view.pool }}</strong></em>
              </div>
            </div>
            <p v-else><RichText :parts="message.view.parts" :text="message.view.text" /></p>
            <em v-if="message.view.kind !== 'image'">{{ chatTime(message.time) }}</em>
          </div>
        </article>
      </div>
    </section>
    <div v-if="noticeOpen && current" class="im-dialog" @click.self="noticeOpen = false">
      <div>
        <b>群公告</b>
        <p>{{ current.notice }}</p>
        <button type="button" @click="noticeOpen = false">确定</button>
      </div>
    </div>
  </div>
</template>
<script setup>
import { computed, reactive, ref, watch } from "vue";
import { state } from "../state.js";
import { chatTime, loadChatGroups, openChatGroup } from "../lib/chat.js";
import { groupAvatar, present, previewText } from "../lib/chatView.js";
import RichText from "../components/RichText.vue";

const current = computed(() => state.chatGroups.find((group) => group.id === state.chatGroupId) || null);
const hiddenAvatars = reactive({});
const noticeOpen = ref(false);
const noticeTick = ref(0);

const messages = computed(() => state.chatMessages
  .filter((message) => !claimNotice(message.content))
  .map((message) => ({ ...message, view: present(message, state.chatImageBase) })));
const noticeVisible = computed(() => {
  noticeTick.value;
  const group = current.value;
  if (!group || !group.notice) return false;
  const until = Number(localStorage.getItem(noticeKey(group)) || 0);
  return !until || Date.now() >= until;
});

watch(() => current.value && current.value.id, () => {
  noticeOpen.value = false;
});

function markMiss(event) {
  const shot = event.target && event.target.closest(".shot");
  if (shot) shot.classList.add("miss");
}

function showAvatar(group) {
  return !!(group && group.avatar && !hiddenAvatars[group.id]);
}

function hideAvatar(id) {
  hiddenAvatars[id] = true;
}

function mark(name) {
  const letter = [...String(name || "")].find((ch) => /[\u4e00-\u9fffA-Za-z0-9]/.test(ch));
  return letter || "群";
}

function preview(latest) {
  const text = previewText(latest, state.chatImageBase);
  if (!text || claimNotice(text)) return " ";
  return latest.nick ? latest.nick + " " + text : text;
}

function back() {
  state.chatGroupId = 0;
  state.chatMessages = [];
}

function noticeKey(group) {
  return "kzb-notice:" + group.id + ":" + group.notice;
}

function hideNotice() {
  const group = current.value;
  if (!group || !group.notice) return;
  localStorage.setItem(noticeKey(group), String(Date.now() + 24 * 60 * 60 * 1000));
  noticeTick.value += 1;
}

function claimNotice(content) {
  const text = String(content || "").trim();
  if (!text || text.includes("人已领取")) return false;
  return text === "领取了红包" || text === "已领取了红包" || text.endsWith("已领取了红包");
}
</script>
