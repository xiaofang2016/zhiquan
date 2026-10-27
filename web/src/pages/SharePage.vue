<template>
  <div class="sub-page">
    <div class="sub-col">
      <div class="match-bar"><button type="button" aria-label="返回" @click="openMine">‹</button><b>分享应用</b></div>
      <div class="share-card">
        <b>说球帝</b>
        <p>把网页版发给好友一起看比赛</p>
        <p>https://www.kzbzcq.com</p>
        <button type="button" @click="copyShare">复制链接</button>
      </div>
    </div>
  </div>
</template>
<script setup>
import { copyShare, openMine } from "../lib/session.js";
</script>
