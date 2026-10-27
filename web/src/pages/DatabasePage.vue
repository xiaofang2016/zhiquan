<template>
  <div v-if="!state.dbCatalog && !state.dbError" class="empty">正在读取资料库</div>
  <div v-else-if="state.dbError && !state.dbCatalog" class="empty">{{ state.dbError }}</div>
  <template v-else-if="state.dbLeague">
    <div class="dbbar">
      <button type="button" @click="closeDbLeague">‹</button>
      <b>{{ state.dbLeague.name || dbLabel(state.dbLeague) }}</b>
    </div>
    <div v-if="state.dbSeasons.length" class="dbseasons">
      <button v-for="season in state.dbSeasons" :key="season.competition_edition_id" type="button" :class="season.competition_edition_id === state.dbEdition ? 'on' : ''" @click="openDbSeason(season.competition_edition_id)">{{ season.edition_season_label || season.competition_edition_name_zh || "" }}</button>
    </div>
    <div v-if="state.dbRows == null" class="empty">正在读取积分榜</div>
    <div v-else-if="!state.dbRows.length" class="empty">{{ state.dbLeague.empty || state.dbError || "暂无积分榜" }}</div>
    <table v-else class="dbtable">
      <tr><th v-for="label in heads" :key="label">{{ label }}</th></tr>
      <tr v-for="(row, index) in state.dbRows" :key="index">
        <td>{{ row.rank || "" }}</td>
        <td><div class="dbteam"><Logo :url="row.team_logo_url" :name="row.team_name_zh || row.team_name" />{{ row.team_name_zh || row.team_name || "" }}</div></td>
        <td>{{ row.played ?? "" }}</td>
        <td>{{ [row.won, row.drawn, row.lost].map((value) => value ?? 0).join("/") }}</td>
        <td>{{ (row.score_for ?? "-") + "/" + (row.score_against ?? "-") }}</td>
        <td>{{ row.points ?? "" }}</td>
      </tr>
    </table>
  </template>
  <template v-else>
    <div class="dbhead">热门赛事</div>
    <div class="hotrow">
      <div v-if="!hot.length" class="empty">暂无热门赛事</div>
      <button v-for="league in hot" :key="league.id" class="hot" type="button" @click="openDbLeague(league)"><Logo :url="league.logo" :name="dbLabel(league)" /><div>{{ dbLabel(league) }}</div></button>
    </div>
    <div class="dbhead">积分排名</div>
    <div class="rankrow">
      <button class="rank" type="button" @click="showFifa">FIFA排名</button>
      <button class="rank" type="button" @click="setDbType(1)">热门足球</button>
      <button class="rank" type="button" @click="setDbType(2)">热门篮球</button>
    </div>
    <div class="switch">
      <button type="button" :class="state.dbType === 1 ? 'on' : ''" @click="setDbType(1)">足球</button>
      <button type="button" :class="state.dbType === 2 ? 'on' : ''" @click="setDbType(2)">篮球</button>
    </div>
    <div class="zones"><button class="on" type="button">全部</button></div>
    <div class="leaguegrid">
      <div v-if="!windowed.total" class="empty">{{ state.dbType === 2 ? "暂无篮球赛事" : "暂无赛事" }}</div>
      <button v-for="league in windowed.leagues" :key="league.id" type="button" @click="openDbLeague(league)"><Logo :url="league.logo" :name="dbLabel(league)" /><div>{{ dbLabel(league) }}</div></button>
    </div>
    <div v-if="windowed.pages > 1" class="dbpager">
      <button type="button" :disabled="windowed.page <= 1" @click="state.dbPage = windowed.page - 1">上一页</button>
      <span>{{ windowed.page }} / {{ windowed.pages }}</span>
      <button type="button" :disabled="windowed.page >= windowed.pages" @click="state.dbPage = windowed.page + 1">下一页</button>
    </div>
  </template>
</template>
<script setup>
import { computed, onMounted } from "vue";
import Logo from "../components/Logo.vue";
import { state } from "../state.js";
import { closeDbLeague, dbLabel, dbPageWindow, hotLeagues, loadDb, openDbLeague, openDbSeason, setDbType } from "../lib/database.js";

const heads = ["排名", "球队", "赛", "胜/平/负", "进/失", "积分"];
const hot = computed(() => hotLeagues());
const windowed = computed(() => dbPageWindow());
function showFifa() {
  state.dbType = 1;
  state.dbLeague = { name: "FIFA排名", empty: "暂无 FIFA 排名" };
  state.dbRows = [];
  state.dbSeasons = [];
}
onMounted(() => { if (!state.dbCatalog && !state.dbError) loadDb(); });
</script>
