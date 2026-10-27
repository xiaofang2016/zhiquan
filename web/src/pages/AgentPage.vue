<template>
  <div class="sub-page poster-page">
    <div class="sub-col">
      <div class="match-bar"><button type="button" aria-label="返回" @click="openMine">‹</button><b>代理招募</b></div>
      <div class="poster">
        <img src="/agent-poster.jpg" alt="">
        <div class="poster-copy agent-copy">
          <b class="a1">平台实力代理</b>
          <b class="a2">合作加盟中</b>
          <p class="poster-note">安全加密聊天，和重要的人畅聊重要的事</p>
          <div class="poster-link">https://ya.cn</div>
          <img class="poster-qr" alt="" src="/recruit-qr.jpg">
        </div>
      </div>
    </div>
  </div>
</template>
<script setup>
import { openMine } from "../lib/session.js";
</script>
