<template>
  <template v-if="tab === 'live'">
    <div v-if="lines.length" class="line-list">
      <div v-for="choice in lines" :key="choice.url" class="detail-line">{{ choice.name }}{{ choice.animation ? " · 动画" : " · 直播" }}</div>
    </div>
    <div v-if="envLine" class="detail-line">{{ envLine }}</div>
    <div v-if="!events.length" class="closed">这场还没有文字直播</div>
    <div v-for="(line, index) in events" :key="index" class="live-line"><b v-if="line.time">{{ line.time }}</b>{{ line.data || "" }}</div>
  </template>
  <template v-else-if="tab === 'odds'">
    <div v-if="!oddsBlocks.length" class="closed">暂无指数</div>
    <template v-for="block in oddsBlocks" :key="block.label">
      <h3>{{ block.label }}</h3>
      <div v-for="(row, index) in block.rows" :key="index" class="detail-line"><b>{{ row.company_name || "公司" }}</b>初 {{ row.initial || "-" }}    即 {{ row.realtime || "-" }}</div>
    </template>
  </template>
  <template v-else-if="tab === 'data'">
    <div v-if="!dataBlocks.length && !injuries.length" class="closed">这场还没有数据和交锋</div>
    <template v-for="block in dataBlocks" :key="block.label">
      <h3>{{ block.label }}</h3>
      <div v-for="(row, index) in block.rows" :key="index" class="hist">{{ historyLine(row) }}</div>
    </template>
    <template v-for="block in injuries" :key="block.label">
      <h3>{{ block.label }}</h3>
      <div v-for="(row, index) in block.rows" :key="index" class="detail-line">{{ playerName(row) }}{{ row.reason ? "  " + row.reason : "" }}</div>
    </template>
  </template>
  <template v-else>
    <div v-if="!markets.length && !notes.length" class="closed">暂无预测</div>
    <template v-for="market in markets" :key="market.label">
      <h3>{{ market.label }}</h3>
      <div v-if="market.line" class="detail-line">盘口  {{ market.line }}</div>
      <div v-for="(bet, index) in market.bets" :key="index" class="detail-line">{{ bet.name }}  {{ bet.numPeople || 0 }}人</div>
    </template>
    <template v-if="notes.length">
      <h3>情报</h3>
      <div v-for="(note, index) in notes" :key="index" class="detail-line">{{ note }}</div>
    </template>
  </template>
</template>
<script setup>
import { computed } from "vue";
const props = defineProps({ tab: String, data: Object, lines: Array });
const events = computed(() => (props.data && props.data.tlive) || []);
const envLine = computed(() => {
  const env = props.data && props.data.environment;
  if (!env) return "";
  return [env.nameZh, env.city, env.temperature, env.humidity].filter(Boolean).join("  ");
});
const oddsBlocks = computed(() => {
  const data = props.data || {};
  return [["让球", data.yazhi], ["胜平负", data.ouzhi], ["大小球", data.daxiao], ["角球", data.jiaoqiu]]
    .filter(([, rows]) => Array.isArray(rows) && rows.length)
    .map(([label, rows]) => ({ label, rows }));
});
const dataBlocks = computed(() => {
  const history = (props.data && props.data.history) || {};
  return [["历史交锋", history.vs], ["主队近期", history.home], ["客队近期", history.away]]
    .filter(([, rows]) => Array.isArray(rows) && rows.length)
    .map(([label, rows]) => ({ label, rows: rows.slice(0, 12).filter((row) => Array.isArray(row)) }));
});
const injuries = computed(() => {
  const injury = (props.data && props.data.injury) || {};
  return [["主队伤停", injury.home], ["客队伤停", injury.away]]
    .filter(([, rows]) => Array.isArray(rows) && rows.length)
    .map(([label, rows]) => ({ label, rows: rows.filter((row) => playerName(row)) }));
});
const markets = computed(() => {
  const forecast = (props.data && props.data.forecast) || {};
  const spec = [["让球", forecast.rq, ["主", "客"]], ["大小", forecast.dx, ["大", "小"]], ["胜平负", forecast.sf, ["胜", "平", "负"]]];
  return spec.filter(([, market]) => market).map(([label, market, names]) => ({
    label,
    line: (market.pankou || []).slice(0, 3).join(" / "),
    bets: (market.betList || []).map((bet) => ({ name: names[(Number(bet.betvalue) || 1) - 1] || "选项", numPeople: bet.numPeople }))
  }));
});
const notes = computed(() => {
  const intel = (props.data && props.data.intel) || {};
  const list = [];
  for (const key of ["home_good", "away_good", "neutral", "home_bad", "away_bad"]) {
    let rows = intel[key];
    if (typeof rows === "string") {
      try { rows = JSON.parse(rows); } catch { rows = []; }
    }
    if (Array.isArray(rows)) list.push(...rows.filter((item) => typeof item === "string" && item));
  }
  return list.slice(0, 12);
});
function historyLine(row) {
  return [row[2], row[7], row[10], row[9]].filter((part) => part != null && part !== "").join("  ");
}
function playerName(row) {
  return row && (row.player_name || row.name || row.player);
}
</script>
