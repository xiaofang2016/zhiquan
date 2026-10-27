<template>
  <div class="sub-page">
    <div class="sub-col">
      <div class="match-bar task-bar with-side"><button type="button" aria-label="返回" @click="closeTask">‹</button><b>任务中心</b><button class="side" type="button" @click="openOrder('task')">音浪明细</button></div>
      <div class="task-user">
        <span class="mine-avatar">
          <img v-if="avatar" :src="avatar" alt="">
          <svg v-else viewBox="0 0 42 42" aria-hidden="true"><circle cx="21" cy="21" r="21" fill="#f6d7c4"/><circle cx="21" cy="17" r="7" fill="#f3b183"/><path d="M8 36c2-7 8-10 13-10s11 3 13 10" fill="#3d4a5c"/><path d="M12 16c1-6 6-9 9-9s8 3 9 9c-2 1-5 2-9 2s-7-1-9-2z" fill="#4a3428"/></svg>
        </span>
        <div>
          <p>已连续签到 <span>{{ data.signDay }}</span>天</p>
          <p class="sub">今日签到{{ data.signed ? "已" : "可" }}获得{{ shown }}音浪</p>
        </div>
        <span class="task-pill">{{ data.balance }}</span>
      </div>
      <section class="task-block">
        <h3>连续签到领好礼</h3>
        <div class="sign-grid">
          <button v-for="(amount, index) in SIGN_REWARDS" :key="index" type="button" :class="'sign-item' + (index === 6 ? ' wide' : '') + (index < data.signDay ? ' on' : '')" @click="handleSign(index + 1)">
            <template v-if="index === 6"><div><div>{{ SIGN_NAMES[index] }}</div><div>{{ amount }}音浪</div></div><i class="coin"></i></template>
            <template v-else><div>{{ SIGN_NAMES[index] }}</div><i class="coin"></i><div>{{ amount }} 已连续签到</div></template>
          </button>
        </div>
      </section>
      <button class="task-invite" type="button" @click="openInvite('task')"><img alt="邀好友 赚音浪" src="https://live.qiandongslim.com/static/img/invite_img.cefe6526.png"></button>
      <section class="task-block">
        <div class="watch-head"><h3>每日观看直播时长</h3><button class="watch-claim" type="button" @click="claimMission(4)">一键领取</button></div>
        <div class="watch-track">
          <div><b>10音浪</b><i></i><span>5分钟</span></div>
          <div><b>50音浪</b><i></i><span>30分钟</span></div>
          <div><b>100音浪</b><i></i><span>60分钟</span></div>
        </div>
      </section>
      <section class="task-block">
        <h3>新手任务</h3>
        <div class="mission-row"><i class="mission-ico"></i><dl><dt>首次注册成功</dt><dd>音浪+200</dd></dl><button :class="'mission-btn' + (data.gotReg ? ' off' : '')" type="button" @click="claimMission(1)">{{ data.gotReg ? "已领取" : "立即领取" }}</button></div>
        <div class="mission-row"><i class="mission-ico"></i><dl><dt>连续登录( {{ Math.min(data.signDay, 3) }}/3)</dt><dd>音浪+300</dd></dl><button :class="'mission-btn' + (data.gotLogin ? ' off' : '')" type="button" @click="claimMission(3)">{{ data.gotLogin ? "已领取" : "立即领取" }}</button></div>
      </section>
      <section class="task-block">
        <h3>日常任务</h3>
        <div class="mission-row"><i class="mission-ico"></i><dl><dt>分享直播间一次( 0/3)</dt><dd>音浪+50</dd></dl><button class="mission-btn" type="button" @click="state.shareOpen = true">去分享</button></div>
        <div class="mission-row"><i class="mission-ico"></i><dl><dt>邀请好友一起看球( 0/3)</dt><dd>音浪+500</dd></dl><button class="mission-btn" type="button" @click="openInvite('task')">去分享</button></div>
      </section>
    </div>
  </div>
</template>
<script setup>
import { computed } from "vue";
import { state } from "../state.js";
import { avatarSrc, claimMission, closeTask, handleSign, missionData, openInvite, openOrder, SIGN_NAMES, SIGN_REWARDS } from "../lib/session.js";
const data = computed(() => missionData());
const avatar = computed(() => state.user && state.user.avatar ? avatarSrc(state.user.avatar) : "");
const shown = computed(() => data.value.signDay > 0 ? SIGN_REWARDS[Math.min(data.value.signDay, SIGN_REWARDS.length) - 1] : "");
</script>
