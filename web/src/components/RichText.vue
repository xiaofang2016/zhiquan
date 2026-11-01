<template>
  <template v-for="(part, index) in shown" :key="index">
    <img v-if="part.kind === 'face'" class="face" :src="part.src" :alt="part.alt" />
    <template v-else>{{ part.text }}</template>
  </template>
</template>
<script setup>
import { computed } from "vue";

const props = defineProps({
  parts: { type: Array, default: null },
  text: { type: String, default: "" }
});

const shown = computed(() => (props.parts && props.parts.length ? props.parts : [{ kind: "text", text: props.text }]));
</script>
