<template>
  <div class="sub-page">
    <div class="sub-col">
      <div class="match-bar"><button type="button" aria-label="返回" @click="state.screen = 'badge'">‹</button><b>徽章权益</b></div>
      <div class="badge-card">
        <h3>当前徽章等级 Lv0</h3>
        <p>累计充值 ¥0</p>
        <p>再充值 ¥1 可升级至 Lv1</p>
        <p>Lv0当前享有5项权益</p>
      </div>
      <div class="badge-card"><h3>连续签到累计可得红包奖励 ¥22</h3><p>连续签到可得更高金额</p></div>
      <div class="badge-card"><h3>专属徽章标识</h3><p>展示专属等级徽章</p></div>
      <div class="badge-card"><h3>钱包提现次数提升</h3><p>每日可提现 1 次</p></div>
      <div class="badge-card"><h3>钱包提现额度提升</h3><p>单月可提现 ¥50</p></div>
      <div class="badge-card"><h3>周等级福利 0 元</h3><p>等级周福利每周仅限领取 1 次</p></div>
      <div class="badge-card"><h3>其他等级权益预览</h3><p>Lv1 权益 未达成</p><p>Lv2 权益 未达成</p><p>* 升级后，新权益将在次周周一北京时间 0 点生效</p></div>
    </div>
  </div>
</template>
<script setup>
import { state } from "../state.js";
</script>
