<template>
  <div class="jxt-page">
    <div class="jxt-bar">
      <button type="button" aria-label="返回" @click="openMine">‹</button>
      <b>账号验证</b>
    </div>
    <div class="jxt-card">
      <h2>账号验证</h2>
      <div class="jxt-brands"><span>说球帝</span><b>⇒</b><span>{{ ued ? "UED体育" : "吉祥体育" }}</span></div>
      <p class="jxt-note">{{ ued ? "首次使用需验证，未注册的手机号将自动创建为UED账号" : "首次使用需验证，未注册的手机号将自动创建为吉祥账号" }}</p>
      <div class="jxt-line"><em>手机号</em><b>{{ state.user && state.user.phone || "" }}</b></div>
      <div class="jxt-line"><em>验证码</em><input v-model="state.bindCode" inputmode="numeric" maxlength="6" placeholder="请输入验证码"><button type="button" :disabled="state.bindSmsWait > 0" @click="sendBindCode">{{ state.bindSmsWait ? "重新获取 " + state.bindSmsWait + "s" : "获取验证码" }}</button></div>
      <button class="jxt-submit" type="button" :disabled="state.bindBusy" @click="submitBind">一键登录</button>
      <button v-if="bound" class="jxt-unbind" type="button" @click="unbindBrand">解除绑定</button>
    </div>
  </div>
</template>
<script setup>
import { computed } from "vue";
import { state } from "../state.js";
import { brandBound, openMine, sendBindCode, submitBind, unbindBrand } from "../lib/session.js";
const ued = computed(() => state.bindBrand === "ued");
const bound = computed(() => brandBound(state.bindBrand));
</script>
