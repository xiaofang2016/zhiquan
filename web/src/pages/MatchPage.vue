<template>
  <div class="rows">
    <div v-if="state.loading && !state.rows.length" class="loading">加载比赛…</div>
    <div v-else-if="!groups.length" class="empty">这一天没有比赛</div>
    <template v-for="item in groups" :key="item.key">
      <div v-if="item.day" class="day">{{ item.day }}</div>
      <div v-else class="match" @click="openPlayer(item.match)">
        <div class="when">{{ localClock(item.match) }} {{ item.match.name || "" }} <span :class="'st ' + phase(item.match)">{{ liveStatus(item.match) }}</span></div>
        <div class="odds"><span v-for="(part, index) in odds(item.match.yazhi_jishi)" :key="index">{{ part }}</span></div>
        <div class="teams">
          <div class="team"><Logo :url="item.match.hteam_logo" :name="item.match.hteam_name" /><span>{{ item.match.hteam_name || "" }}</span></div>
          <div :class="'score' + (scoreText(item.match) === 'VS' ? ' vs' : '')">{{ scoreText(item.match) }}</div>
          <div class="team away"><Logo :url="item.match.ateam_logo" :name="item.match.ateam_name" /><span>{{ item.match.ateam_name || "" }}</span></div>
        </div>
        <div class="meta">
          <div v-if="anchor(item.match)" class="anchor">{{ anchor(item.match) }}</div>
          <div v-if="extras(item.match)" class="extra">{{ extras(item.match) }}</div>
          <div v-if="odds(item.match.ouzhi_jishi).length">{{ odds(item.match.ouzhi_jishi).join(" ") }}</div>
        </div>
      </div>
    </template>
  </div>
  <button v-if="state.page < state.totalPage" class="more" type="button" @click="loadMatches(false)">{{ state.loading ? "加载中…" : "加载更多" }}</button>
</template>
<script setup>
import { computed } from "vue";
import Logo from "../components/Logo.vue";
import { state } from "../state.js";
import { anchor, dayHeading, liveStatus, localClock, localDayKey, localMatchDay, matchExtras, odds, phase, scoreText, visibleMatches } from "../lib/match.js";
import { loadMatches } from "../lib/list.js";
import { openPlayer } from "../lib/detail.js";

const groups = computed(() => {
  const today = localDayKey(new Date());
  const items = [];
  let last = "";
  visibleMatches().forEach((match, index) => {
    const day = localMatchDay(match);
    if (day && day !== last) {
      last = day;
      if (day !== today) items.push({ key: "day-" + day, day: dayHeading(day) });
    }
    items.push({ key: "m-" + match.id + "-" + index, match });
  });
  return items;
});
function extras(match) { return matchExtras(match); }
</script>
