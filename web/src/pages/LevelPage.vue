<template>
  <div class="sub-page">
    <div class="sub-col">
      <div class="match-bar with-side"><button type="button" aria-label="返回" @click="openMine">‹</button><b>我的等级</b><button class="side" type="button" @click="state.screen = 'level-rule'">等级规则</button></div>
      <div class="level-head"><i></i><span>{{ name }}</span></div>
      <div class="level-badge">
        <svg width="54" height="54" viewBox="0 0 54 54" aria-hidden="true"><path d="M27 4l16 10v16L27 50 11 30V14z" fill="#d9dde3"/><path d="M27 14l8 5v8l-8 10-8-10v-8z" fill="#9aa3ad"/></svg>
        <span>{{ view.title }}</span>
      </div>
      <div class="level-track">
        <div class="cap"><span>用户等级<br><span>{{ view.title }}</span></span><span>注：1音浪=1球技</span></div>
        <div class="level-bar"><i :style="{ width: view.width }"></i></div>
        <div class="cap"><span>{{ view.left }}</span><span class="need">{{ view.need }}</span><span>{{ view.right }}</span></div>
      </div>
    </div>
  </div>
</template>
<script setup>
import { computed } from "vue";
import { state } from "../state.js";
import { levelView, openMine } from "../lib/session.js";
const name = computed(() => state.user ? (state.user.nickname || state.user.phone || "已登录") : "未登录");
const view = computed(() => levelView());
</script>
