<template>
  <div class="mine-page">
    <div class="mine-wrap">
      <div class="mine-bar">
        <button type="button" aria-label="返回" @click="onMine('back')">‹</button>
        <b>个人中心</b>
      </div>
      <button class="mine-user" type="button" @click="onMine('profile')">
        <span class="mine-avatar">
          <svg v-if="!avatar" viewBox="0 0 42 42" aria-hidden="true"><circle cx="21" cy="21" r="21" fill="#f6d7c4"/><circle cx="21" cy="17" r="7" fill="#f3b183"/><path d="M8 36c2-7 8-10 13-10s11 3 13 10" fill="#3d4a5c"/><path d="M12 16c1-6 6-9 9-9s8 3 9 9c-2 1-5 2-9 2s-7-1-9-2z" fill="#4a3428"/></svg>
          <img v-else :src="avatar" alt="">
        </span>
        <em>{{ name }}</em>
        <i v-if="state.user">›</i>
      </button>
      <section class="mine-card">
        <h3>福利中心</h3>
        <div class="mine-welfare">
          <button type="button" @click="onMine('mission', true)"><img alt="" src="https://live.qiandongslim.com/static/img/welfare_task_center.9ed4a01f.png">任务中心</button>
          <button type="button" @click="onMine('invite', true)"><img alt="" src="https://live.qiandongslim.com/static/img/welfare_invite_yinlang.f1221949.png">邀新领音浪</button>
          <button type="button" @click="onMine('yinlang', true)"><img alt="" src="https://live.qiandongslim.com/static/img/welfare_yinlang.44f44f50.png"><span>{{ yinlang }}音浪</span></button>
          <button type="button" @click="onMine('promo')"><img alt="" src="https://live.qiandongslim.com/static/img/welfare_cooperation.2d9d7bb8.png">推广合作</button>
          <button type="button" @click="onMine('agent')"><img alt="" src="https://live.qiandongslim.com/static/img/welfare_agent_recruit.8881610d.png">代理招募</button>
        </div>
      </section>
      <section v-if="state.user" class="mine-card mine-wallet">
        <h3>我的钱包余额（元）</h3>
        <div class="mine-balance">{{ walletText() }}</div>
        <input v-model="state.withdrawAmount" class="mine-amount" inputmode="decimal" placeholder="请输入提现金额">
        <button class="mine-withdraw" type="button" :disabled="state.withdrawBusy" @click="onMine('jxt-withdraw')">{{ brandLabel("jxt") }}</button>
        <button class="mine-withdraw" type="button" :disabled="state.withdrawBusy" @click="onMine('ued-withdraw')">{{ brandLabel("ued") }}</button>
        <p class="mine-tip">单笔最低10元。提现到已绑定的吉祥或 UED 主账户。</p>
      </section>
      <section class="mine-card">
        <button v-if="state.user" class="mine-row" type="button" @click="onMine('jxt-bind')">绑定吉祥<span>›</span></button>
        <button v-if="state.user" class="mine-row" type="button" @click="onMine('ued-bind')">绑定UED<span>›</span></button>
        <button class="mine-row" type="button" @click="onMine('avatar')">头像设置<span>›</span></button>
        <button class="mine-row" type="button" @click="onMine('match')">比赛设置<span>›</span></button>
        <button class="mine-row" type="button" @click="onMine('order', true)">我的订单<span>›</span></button>
        <button class="mine-row" type="button" @click="onMine('level', true)">我的等级<span>›</span></button>
        <button class="mine-row" type="button" @click="onMine('badge', true)">尊享徽章<span>›</span></button>
        <button class="mine-row" type="button" @click="onMine('forecast', true)">我的预测<span>›</span></button>
        <button class="mine-row" type="button" @click="onMine('feedback', true)">意见反馈<span>›</span></button>
        <button class="mine-row" type="button" @click="onMine('share')">分享应用<span>›</span></button>
        <button v-if="state.user" class="mine-row mine-logout" type="button" @click="onMine('logout')">退出登录</button>
      </section>
    </div>
  </div>
</template>
<script setup>
import { computed } from "vue";
import { state } from "../state.js";
import { avatarSrc, brandLabel, missionData, onMine, walletText } from "../lib/session.js";
const name = computed(() => state.user ? (state.user.nickname || state.user.phone || "已登录") : "未登录");
const avatar = computed(() => state.user && state.user.avatar ? avatarSrc(state.user.avatar) : "");
const yinlang = computed(() => state.user && state.mission ? missionData().balance : 0);
</script>
