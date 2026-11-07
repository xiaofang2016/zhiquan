<template>
  <div class="login-page">
    <div class="login-wrap">
      <p class="login-note">注册账号可在以下所有平台登录</p>
      <img class="login-brands" alt="说球帝 职圈 UED体育 吉祥体育" src="https://live.qiandongslim.com/static/img/brand-row.b1ae556b.png">
      <img class="login-hero" alt="" src="https://live.qiandongslim.com/static/img/login-img.bfdddbea.png">
      <form @submit.prevent="submitAccount">
        <div class="login-field">
          <span class="login-prefix">+86 <i>▾</i></span>
          <input v-model="state.phone" type="tel" maxlength="11" placeholder="请输入你的手机号" autocomplete="username">
        </div>
        <div v-if="register" class="login-field">
          <span class="login-label">验证码</span>
          <input v-model="state.code" type="text" inputmode="numeric" maxlength="6" placeholder="请输入验证码" autocomplete="one-time-code">
          <button class="login-side" :class="state.smsWait ? 'wait' : ''" type="button" :disabled="state.smsWait > 0" @click="sendCode">{{ state.smsWait ? "重新获取 " + state.smsWait + "s" : "获取验证码" }}</button>
        </div>
        <div class="login-field">
          <span class="login-label">密码</span>
          <input v-model="state.password" type="password" maxlength="20" placeholder="请输入登录密码" :autocomplete="register ? 'new-password' : 'current-password'">
          <button v-if="!register" class="login-side" type="button" @click="toast('忘记密码暂未开放')">忘记密码?</button>
        </div>
        <div v-if="register" class="login-field">
          <span class="login-label">密码</span>
          <input v-model="state.confirm" type="password" maxlength="20" placeholder="请再次输入密码" autocomplete="new-password">
        </div>
        <button class="login-submit" type="submit" :disabled="state.authBusy">{{ register ? "注册并登录" : "登录" }}</button>
      </form>
      <div class="login-actions">
        <button type="button" @click="state.authMode = register ? 'login' : 'register'">{{ register ? "密码登录" : "验证码登录/注册" }}</button>
        <span>|</span>
        <button type="button" @click="openService">在线客服</button>
        <span>|</span>
        <button type="button" @click="closeAccount">随便逛逛</button>
      </div>
      <div class="login-sponsors">
        <a v-for="(src, index) in sponsors" :key="index" :href="sponsor" target="_blank" rel="noopener noreferrer"><img :alt="src.alt" :src="src.src"></a>
      </div>
      <p class="login-agree">登录/注册即代表您同意 <button type="button" @click="toast('使用协议暂未开放')">《使用协议》</button> 和 <button type="button" @click="toast('隐私政策暂未开放')">《隐私政策》</button></p>
      <p class="login-partner"><a :href="sponsor" target="_blank" rel="noopener noreferrer">《2026JX&amp;UED西甲官方区域合作伙伴》</a></p>
    </div>
  </div>
</template>
<script setup>
import { computed } from "vue";
import { state, toast } from "../state.js";
import { closeAccount, sendCode, submitAccount } from "../lib/session.js";
function openService() {
  window.open("https://zhiquan.78chat0.com/#/pages/cs/cs?merchantNo=sport", "_blank", "noopener,noreferrer");
}
const register = computed(() => state.authMode === "register");
const sponsor = "https://www.laliga.com/en-CN/news/jxsports-and-ued-sport-extend-sponsorship-agreement-with-laliga-through-2026-27?openBrowser=1";
const sponsors = [
  ["赞助西甲", "https://live.qiandongslim.com/static/img/login-icon1.dcc4d0bf.png"],
  ["皇家马德里", "https://live.qiandongslim.com/static/img/login-icon2.567ca5f7.png"],
  ["巴塞罗那", "https://live.qiandongslim.com/static/img/login-icon3.492dcf59.png"],
  ["马德里竞技", "https://live.qiandongslim.com/static/img/login-icon4.12506a14.png"]
].map(([alt, src]) => ({ alt, src }));
</script>
