<template>
  <div class="sub-page">
    <div class="sub-col">
      <div class="match-bar"><button type="button" aria-label="返回" @click="openLevel">‹</button><b>等级规则</b></div>
      <div class="rule-table">
        <div v-for="(row, index) in LEVEL_RULES" :key="index" :class="index === 0 ? 'rule-head' : 'rule-row'">
          <span v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
<script setup>
import { LEVEL_RULES, openLevel } from "../lib/session.js";
</script>
