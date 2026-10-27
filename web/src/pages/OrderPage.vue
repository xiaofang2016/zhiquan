<template>
  <div class="sub-page">
    <div class="sub-col">
      <div class="match-bar"><button type="button" aria-label="返回" @click="closeOrder">‹</button><b>音浪明细</b></div>
      <div class="yin-card"><b>{{ missionData().balance }}</b><span>我的音浪</span></div>
      <div>
        <div v-for="(row, index) in missionData().records" :key="index" class="yin-row">
          <div><b>{{ row.title }}</b><p>{{ row.time }}</p></div>
          <em>+{{ row.amount }}</em>
        </div>
      </div>
      <p class="sub-empty">没有更多了</p>
    </div>
  </div>
</template>
<script setup>
import { closeOrder, missionData } from "../lib/session.js";
</script>
