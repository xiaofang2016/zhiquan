<template>
  <div class="match-page sub-page">
    <div class="sub-col">
      <div class="match-bar"><button type="button" aria-label="返回" @click="openMine">‹</button><b>比赛设置</b></div>
      <div class="match-row strong"><span>进球提示</span><button :class="'match-switch' + (goal ? ' on' : '')" type="button" role="switch" :aria-checked="goal" @click="toggleMatch('SetUpCom-checked', true)"><i></i></button></div>
      <button class="match-row" type="button" @click="openSound('home')"><span>主队声音</span><em>{{ home }}</em></button>
      <button class="match-row" type="button" @click="openSound('away')"><span>客队声音</span><em>{{ away }}</em></button>
      <div class="match-row"><span>红牌声音</span><button :class="'match-switch small' + (red ? ' on' : '')" type="button" role="switch" :aria-checked="red" @click="toggleMatch('SetUpCom-checkedRed', false)"><i></i></button></div>
    </div>
  </div>
  <div v-if="state.soundOpen" class="sound-mask" @click.self="state.soundOpen = false">
    <div class="sound-sheet" role="dialog" aria-label="选择声音">
      <div>
        <button v-for="item in MATCH_SOUNDS" :key="item[0]" type="button" :class="item[0] === current ? 'on' : ''" @click="chooseSound(item[0])">{{ item[1] }}</button>
      </div>
      <div class="avatar-gap"></div>
      <button type="button" @click="state.soundOpen = false">取消</button>
    </div>
  </div>
</template>
<script setup>
import { computed } from "vue";
import { state } from "../state.js";
import { awaySoundLabel, chooseSound, goalOn, homeSoundLabel, MATCH_SOUNDS, openMine, openSound, redOn, soundChoice, toggleMatch } from "../lib/session.js";
const goal = computed(() => goalOn());
const red = computed(() => redOn());
const home = computed(() => homeSoundLabel());
const away = computed(() => awaySoundLabel());
const current = computed(() => { state.settingsRev; return soundChoice(state.soundSide); });
</script>
