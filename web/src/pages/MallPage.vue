<template>
  <div class="sub-page mall-page">
    <div class="sub-col">
      <div class="match-bar"><button type="button" aria-label="返回" @click="closeMall">‹</button><b>红包商城</b></div>
      <div class="yin-card mall-balance">
        <b>{{ balanceText }}</b>
        <span>我的余额</span>
      </div>
      <p class="mall-tip">仅展示最新商品，暂不支持兑换下单</p>
      <div class="mall-cats">
        <button type="button" :class="cid === 0 ? 'on' : ''" @click="pick(0)">全部</button>
        <button v-for="cat in categories" :key="cat.id" type="button" :class="cid === cat.id ? 'on' : ''" @click="pick(cat.id)">{{ cat.name }}</button>
      </div>
      <div v-if="loading" class="sub-empty">加载中…</div>
      <div v-else-if="!goods.length" class="sub-empty">当前暂无兑换商品</div>
      <div v-else class="mall-grid">
        <button v-for="item in goods" :key="item.id" type="button" class="mall-card" @click="openDetail(item)">
          <img :src="item.cover_url" :alt="item.title">
          <div>
            <b>{{ item.title }}</b>
            <em><i>{{ item.amount }}</i> 元</em>
            <span>{{ limitText(item.limit_type) }}</span>
            <p>已兑{{ item.sales || 0 }}件 · 剩余{{ item.stock || 0 }}件</p>
          </div>
        </button>
      </div>
      <button v-if="hasMore && !loading" class="mall-more" type="button" :disabled="loadingMore" @click="loadMore">{{ loadingMore ? "加载中…" : "加载更多" }}</button>
    </div>
    <div v-if="detail" class="sound-mask" @click.self="detail = null">
      <div class="sound-sheet mall-sheet" role="dialog" aria-label="商品详情">
        <img v-if="detail.cover_url" :src="detail.cover_url" :alt="detail.title">
        <b>{{ detail.title }}</b>
        <em><i>{{ detail.amount }}</i> 元 · {{ limitText(detail.limit_type) }}</em>
        <p>{{ detail.introduction || detail.description || "暂无介绍" }}</p>
        <p class="mall-stock">已兑{{ detail.sales || 0 }}件 · 剩余{{ detail.stock || 0 }}件</p>
        <button type="button" @click="detail = null">关闭</button>
      </div>
    </div>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from "vue";
import { get } from "../lib/api.js";
import { closeMall, missionData, refreshMission } from "../lib/session.js";
import { state } from "../state.js";

const LIMITS = { 0: "1次/每月", 1: "1次/终身", 2: "1次/每日", 3: "1次/每周", 4: "1次/半月" };
const categories = ref([]);
const goods = ref([]);
const cid = ref(0);
const page = ref(1);
const totalPage = ref(1);
const loading = ref(true);
const loadingMore = ref(false);
const detail = ref(null);

const hasMore = computed(() => page.value < totalPage.value);
const balanceText = computed(() => {
  if (!state.user) return "—";
  const wallet = state.user.wallet;
  if (wallet != null && wallet !== "") return Number(wallet).toFixed(2);
  return String(missionData().balance ?? 0);
});

function limitText(type) {
  return LIMITS[type] || "";
}

function pick(id) {
  cid.value = id;
  page.value = 1;
  load(true);
}

async function load(reset) {
  if (reset) loading.value = true;
  else loadingMore.value = true;
  try {
    const data = await get("/prod-api/goods/list?type=0&cid=" + cid.value + "&ishot=-1&pn=" + page.value + "&ps=10&langtype=zh");
    const list = data && data.dataList ? data.dataList : [];
    goods.value = reset ? list : goods.value.concat(list);
    totalPage.value = data && data.totalPage ? data.totalPage : 1;
  } catch (error) {
    if (reset) goods.value = [];
  } finally {
    loading.value = false;
    loadingMore.value = false;
  }
}

async function loadMore() {
  if (!hasMore.value || loadingMore.value) return;
  page.value += 1;
  await load(false);
}

async function openDetail(item) {
  try {
    detail.value = await get("/prod-api/goods/detail?id=" + item.id + "&langtype=zh") || item;
  } catch (error) {
    detail.value = item;
  }
}

onMounted(async () => {
  await refreshMission();
  try {
    const data = await get("/prod-api/goods/category/list?langtype=zh");
    categories.value = data && data.dataList ? data.dataList.filter((row) => row.has_goods !== 0) : [];
  } catch (error) {
    categories.value = [];
  }
  await load(true);
});
</script>
