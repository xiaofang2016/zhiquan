<template>
  <div class="sub-page">
    <div class="sub-col">
      <div class="match-bar with-side"><button type="button" aria-label="返回" @click="openMine">‹</button><b>我的预测</b><button class="side" type="button" @click="openOrder('forecast')">获取音浪</button></div>
      <div class="forecast-banners">
        <button type="button" @click="openMall('forecast')"><b>红包商城</b><span>领音浪 兑实物</span></button>
        <button type="button" @click="state.screen = 'rank'"><b>预测收益榜</b><span>预测大神就是你</span></button>
      </div>
      <div class="forecast-tabs">
        <button type="button" :class="state.forecastTab === 'predict' ? 'on' : ''" @click="state.forecastTab = 'predict'">预测</button>
        <button type="button" :class="state.forecastTab === 'record' ? 'on' : ''" @click="state.forecastTab = 'record'">记录</button>
      </div>
      <div class="sub-empty">
        <svg width="120" height="90" viewBox="0 0 120 90" aria-hidden="true"><path d="M18 62h84v8H18zM30 38h60v24H30zM38 46h44v4H38zm0 8h28v4H38z" fill="#e5e6eb"/><path d="M24 28h18v10H24zM78 22h16v12H78z" fill="#f2f3f5"/></svg>
        <span>{{ state.forecastTab === "record" ? "暂无记录" : "暂无预测数据" }}</span>
      </div>
    </div>
  </div>
</template>
<script setup>
import { state } from "../state.js";
import { openMall, openMine, openOrder } from "../lib/session.js";
</script>
