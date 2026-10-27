<template>
  <div class="sub-page invite-page">
    <div class="sub-col">
      <div class="match-bar"><button type="button" aria-label="返回" @click="closeInvite">‹</button><b>我的邀请</b></div>
      <p class="invite-tip">邀好友观看直播 领音浪</p>
      <div class="invite-card">
        <div class="invite-face"><span>音浪</span><b>{{ missionData().balance }}</b></div>
        <button type="button" @click="state.shareOpen = true">立即邀请</button>
      </div>
      <div class="invite-stats"><div><b>0人</b><span>累计邀请</span></div><div><b>500音浪</b><span>邀请奖励</span></div></div>
    </div>
  </div>
</template>
<script setup>
import { state } from "../state.js";
import { closeInvite, missionData } from "../lib/session.js";
</script>
