<template>
  <div class="sub-page">
    <div class="sub-col">
      <div class="match-bar"><button type="button" aria-label="返回" @click="openMine">‹</button><b>意见反馈</b></div>
      <div class="feedback-box">
        <textarea v-model="state.feedback" maxlength="300" placeholder="请输入你的反馈"></textarea>
        <div class="count">{{ state.feedback.length }}/300</div>
      </div>
      <button class="feedback-submit" type="button" @click="submitFeedback">提交反馈</button>
    </div>
  </div>
</template>
<script setup>
import { state } from "../state.js";
import { openMine, submitFeedback } from "../lib/session.js";
</script>
