<template>
  <div class="chatdbg">
    <aside>
      <div class="dbbar">
        <b>群</b>
        <span>{{ state.chatGroups.length }}</span>
        <button type="button" @click="loadChatGroups">刷新</button>
      </div>
      <div v-if="state.chatLoading && !state.chatGroups.length" class="empty">正在读取群</div>
      <div v-else-if="state.chatError && !state.chatGroups.length" class="empty">{{ state.chatError }}</div>
      <div v-else-if="!state.chatGroups.length" class="empty">还没有群</div>
      <button v-for="group in state.chatGroups" :key="group.id" class="chatroom" type="button" :class="{ on: state.chatGroupId === group.id }" @click="openChatGroup(group)">
        <b>{{ group.name || group.gid }}</b>
        <small>{{ group.latest ? chatTime(group.latest.time) : "没有消息" }}</small>
        <em v-if="group.latest">{{ group.latest.nick }} {{ group.latest.content }}</em>
      </button>
    </aside>
    <section>
      <div class="dbbar">
        <b>{{ current ? (current.name || current.gid) : "选择一个群" }}</b>
        <span v-if="current">最新 {{ state.chatMessages.length }} 条</span>
      </div>
      <div v-if="!current" class="empty">点左边的群，看这个群最新的 100 条</div>
      <div v-else-if="state.chatLoading && !state.chatMessages.length" class="empty">正在读取消息</div>
      <div v-else-if="state.chatError && !state.chatMessages.length" class="empty">{{ state.chatError }}</div>
      <div v-else-if="!state.chatMessages.length" class="empty">这个群还没有消息</div>
      <article v-for="(message, index) in state.chatMessages" :key="message.time + '-' + index">
        <header>
          <b>{{ message.nick || message.sendUid || "用户" }}</b>
          <span v-if="message.type">{{ message.type }}</span>
          <em>{{ chatTime(message.time) }}</em>
        </header>
        <p>{{ message.content }}</p>
      </article>
    </section>
  </div>
</template>
<script setup>
import { computed } from "vue";
import { state } from "../state.js";
import { chatTime, loadChatGroups, openChatGroup } from "../lib/chat.js";

const current = computed(() => state.chatGroups.find((group) => group.id === state.chatGroupId) || null);
</script>
