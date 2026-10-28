<template>
  <img v-if="shown && !failed" class="logo" :src="shown" alt="" @error="failed = true">
  <span v-else class="ph">{{ (name || "?").slice(0, 1) }}</span>
</template>
<script setup>
import { ref, watch } from "vue";
import { API } from "../state.js";
const props = defineProps({ url: String, name: String });
const failed = ref(false);
function resolve(url) {
  if (!url) return "";
  if (url.startsWith("data:") || url.startsWith("http://") || url.startsWith("https://")) return url;
  if (url.startsWith("/")) return API + url;
  return url;
}
const shown = ref(resolve(props.url));
watch(() => props.url, (url) => { failed.value = false; shown.value = resolve(url); });
</script>
