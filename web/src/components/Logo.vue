<template>
  <img v-if="shown && !failed" class="logo" :src="shown" alt="" @error="failed = true">
  <span v-else class="ph">{{ (name || "?").slice(0, 1) }}</span>
</template>
<script setup>
import { ref, watch } from "vue";
const props = defineProps({ url: String, name: String });
const failed = ref(false);
const shown = ref(props.url || "");
watch(() => props.url, (url) => { failed.value = false; shown.value = url || ""; });
</script>
