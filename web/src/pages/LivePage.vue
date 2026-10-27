<template>
  <div class="pills">
    <button v-for="item in modes" :key="item.id" type="button" :class="'pill' + (state.liveMode === item.id ? ' on' : '')" @click="state.liveMode = item.id">{{ item.label }}</button>
  </div>
  <div class="board">
    <div class="chips">
      <button type="button" :class="state.liveLeague ? '' : 'on'" @click="state.liveLeague = 0">全部</button>
      <button v-for="league in footballLeagues" :key="league.id" type="button" :class="state.liveLeague === league.id ? 'on' : ''" @click="state.liveLeague = league.id">{{ league.name_zh || league.name }}</button>
    </div>
    <table>
      <tr>
        <th v-for="label in heads" :key="label">{{ label }}</th>
      </tr>
      <tr v-if="state.loading && !state.liveRows.length"><td colspan="8">加载即时比分…</td></tr>
      <tr v-else-if="!sections.length"><td colspan="8">暂无比赛</td></tr>
      <template v-else>
        <template v-for="(section, index) in sections" :key="index">
          <tr class="sec"><td colspan="8">{{ section.title }}</td></tr>
          <tr v-for="match in section.rows" :key="match.id" @click="openPlayer(match)">
            <td><Logo :url="match.clogo" :name="match.name" /> {{ match.name || "" }}</td>
            <td>{{ localClock(match) }} <span :class="'st ' + phase(match)">{{ statusText(match) }}</span> <span v-if="match.cur_round" class="tag">{{ match.cur_round }}</span></td>
            <td>{{ match.hteam_name || "" }}</td>
            <td :class="phase(match) === 'live' ? 'sred' : ''">{{ scoreText(match) }}</td>
            <td>{{ match.ateam_name || "" }}</td>
            <td>{{ odds(match.yazhi_jishi).join(" ") }}</td>
            <td>{{ extras(match) }}</td>
            <td>{{ anchor(match) }}</td>
          </tr>
        </template>
      </template>
    </table>
  </div>
</template>
<script setup>
import { computed } from "vue";
import Logo from "../components/Logo.vue";
import { state } from "../state.js";
import { anchor, localClock, matchExtras, odds, phase, scoreText, statusText } from "../lib/match.js";
import { liveRows } from "../lib/scores.js";
import { openPlayer } from "../lib/detail.js";

const modes = [
  ["live", "比分"],
  ["result", "赛果"],
  ["schedule", "赛程"],
  ["all", "完整"]
].map(([id, label]) => ({ id, label }));
const heads = ["赛事", "时间状态", "主队", "比分", "客队", "即时指数", "半场角球", "直播"];
const footballLeagues = computed(() => state.leagues.filter((item) => item.type === 1).slice(0, 8));
const sections = computed(() => {
  const blocks = [];
  let current = null;
  for (const match of liveRows()) {
    const title = phase(match) === "wait" ? "未开始的比赛" : phase(match) === "done" ? "已结束的比赛" : "正在进行中的比赛";
    if (!current || current.title !== title) {
      current = { title, rows: [] };
      blocks.push(current);
    }
    current.rows.push(match);
  }
  return blocks;
});
function extras(match) { return matchExtras(match); }
</script>
