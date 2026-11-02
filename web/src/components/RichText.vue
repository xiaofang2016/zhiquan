<template>
  <template v-for="(part, index) in shown" :key="index">
    <img v-if="part.kind === 'face'" class="face" :src="part.src" :alt="part.alt" />
    <a v-else-if="part.kind === 'link'" class="im-link" :href="part.href" :target="part.href.startsWith('/') ? null : '_blank'" rel="noopener noreferrer" @click="onLink($event, part.href)">{{ part.text }}</a>
    <template v-else>{{ part.text }}</template>
  </template>
</template>
<script setup>
import { computed } from "vue";
import { openPlayback } from "../lib/detail.js";

const props = defineProps({
  parts: { type: Array, default: null },
  text: { type: String, default: "" }
});

const shown = computed(() => (props.parts && props.parts.length ? props.parts : [{ kind: "text", text: props.text }]));

function onLink(event, href) {
  if (openPlayback(href)) event.preventDefault();
}
</script>
