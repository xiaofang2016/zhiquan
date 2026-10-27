<template>
  <div class="sub-page">
    <div class="sub-col">
      <div class="match-bar"><button type="button" aria-label="返回" @click="openForecast">‹</button><b>预测收益榜</b></div>
      <p class="sub-empty">暂无数据</p>
    </div>
  </div>
</template>
<script setup>
import { openForecast } from "../lib/session.js";
</script>
