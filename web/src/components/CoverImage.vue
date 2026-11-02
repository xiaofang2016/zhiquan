<script setup>
import { ref, watch } from "vue";
import { openCover } from "../lib/coverImage.js";

const props = defineProps({ src: String });
const emit = defineEmits(["error"]);
const shown = ref("");
const hold = ref(null);
let ticket = 0;

watch(() => props.src, (src) => {
  const mine = ++ticket;
  shown.value = "";
  openCover(src).then((url) => {
    if (mine === ticket) shown.value = url;
  }).catch(() => {
    if (mine !== ticket) return;
    const shot = hold.value && hold.value.closest(".shot");
    if (shot) shot.classList.add("miss");
  });
}, { immediate: true });

function onError(event) {
  emit("error", event);
}
</script>

<template>
  <img v-if="shown" :src="shown" alt="" @error="onError" />
  <i v-else ref="hold" class="cover-hold"></i>
</template>
