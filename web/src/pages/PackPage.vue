<template>
  <div class="sub-page pack-page">
    <div class="sub-col">
      <div class="match-bar"><button type="button" aria-label="返回" @click="closePack">‹</button><b>领取红包</b></div>
      <div class="pack-stage">
        <div class="pack-card">
          <div class="pack-face">说</div>
          <p>{{ view.from }}</p>
          <b>{{ view.desc }}</b>
          <button v-if="view.showOpen" class="pack-chai" type="button" :disabled="state.claimBusy" @click="claimPack">拆</button>
          <p v-if="view.result" class="pack-result">{{ view.result }}</p>
        </div>
        <p class="pack-foot">领到的金额记入说球帝余额，可提现到吉祥或 UED</p>
      </div>
    </div>
  </div>
</template>
<script setup>
import { computed } from "vue";
import { state } from "../state.js";
import { claimPack, closePack, packView } from "../lib/session.js";
const view = computed(() => packView() || { from: "说球帝的红包", desc: "拼手气红包", showOpen: false, result: "" });
</script>
