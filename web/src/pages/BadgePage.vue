<template>
  <div class="sub-page">
    <div class="sub-col">
      <div class="match-bar with-side"><button type="button" aria-label="返回" @click="openMine">‹</button><b>尊享徽章</b><button class="side" type="button" @click="state.screen = 'badge-rights'">徽章权益</button></div>
      <div class="badge-hero">
        <b>Lv0</b>
        <p>经验值 ¥0/¥1</p>
        <div class="bar"><i></i></div>
        <p>再充值1元即可升级Lv1，解锁更多权益！</p>
      </div>
      <div class="badge-card">
        <div class="badge-line"><h3>三方游戏平台周累计充值</h3><span>¥0/¥1</span></div>
        <p>UED 周累计充值 ¥0</p>
        <p>吉祥 周累计充值 ¥0</p>
      </div>
      <div class="badge-card"><h3>等级特权</h3><p>绑定账号后解锁更多特权</p></div>
      <div class="badge-card badge-line">
        <div><h3>平台账号绑定</h3><p>绑定UED/吉祥账号，解锁聊天权限等级与专属等级</p></div>
        <button type="button" @click="bindFromBadge">{{ both ? "已绑定" : "立即绑定" }}</button>
      </div>
    </div>
  </div>
</template>
<script setup>
import { computed } from "vue";
import { state } from "../state.js";
import { bindFromBadge, brandBound, openMine } from "../lib/session.js";
const both = computed(() => brandBound("jxt") && brandBound("ued"));
</script>
