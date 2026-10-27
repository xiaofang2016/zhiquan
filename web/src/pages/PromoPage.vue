<template>
  <div class="sub-page poster-page">
    <div class="sub-col">
      <div class="match-bar"><button type="button" aria-label="返回" @click="openMine">‹</button><b>推广合作</b></div>
      <div class="poster">
        <img src="/promo-poster.jpg" alt="">
        <div class="poster-copy promo-copy">
          <b class="poster-title">尽在说球帝</b>
          <img class="poster-logo" alt="说球帝" src="https://live.qiandongslim.com/static/img/logo.12360f74.png">
          <div class="poster-flow">大量收流量</div>
          <div class="poster-ad">广告勿扰</div>
          <p class="poster-note">安全加密聊天，和重要的人畅聊重要的事</p>
          <div class="poster-link">https://ya.cn</div>
          <img class="poster-qr" alt="" src="/recruit-qr.jpg">
          <p class="poster-foot">扫码下载丝瓜添加账号dty888加我好友</p>
          <p class="poster-id">Bat ID:dty888</p>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup>
import { openMine } from "../lib/session.js";
</script>
