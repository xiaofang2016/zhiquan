<template>
  <div class="avatar-mask" @click.self="closeAvatar">
    <div class="avatar-sheet" role="dialog" aria-label="头像特权">
      <div class="avatar-head">
        <button type="button" @click="closeAvatar">取消</button>
        <b>头像特权</b>
        <button class="avatar-save" type="button" :disabled="state.avatarBusy" @click="saveAvatar">设置</button>
      </div>
      <button class="avatar-photo" type="button" aria-label="上传图片" @click="state.avatarPick = true">
        <img v-if="!shown" alt="" src="https://live.qiandongslim.com/static/img/UploadAvatarsUps.b48d3217.png">
        <img v-else :src="shown" alt="">
      </button>
      <p :class="'avatar-caption' + (state.avatarPreview ? ' again' : '')" @click="state.avatarPreview && (state.avatarPick = true)">{{ state.avatarPreview ? "重新上传头像" : "设置您的专属头像" }}</p>
      <p class="avatar-note">·请选择清晰、非纯白色背景(容易与背景融合)<br>·不可以选用二维码等其他敏感图片，图片像素建议200X200<br>·大小小于2MB，可以用jpg，png等格式～</p>
    </div>
  </div>
  <div v-if="state.avatarPick" class="avatar-pick" @click.self="state.avatarPick = false">
    <div class="avatar-pick-sheet" role="dialog" aria-label="选择图片">
      <label>拍摄<input class="avatar-file" type="file" accept="image/*" capture="environment" @change="pick"></label>
      <label>相册<input class="avatar-file" type="file" accept="image/*" @change="pick"></label>
      <div class="avatar-gap"></div>
      <button type="button" @click="state.avatarPick = false">取消</button>
    </div>
  </div>
</template>
<script setup>
import { computed } from "vue";
import { state } from "../state.js";
import { avatarSrc, closeAvatar, saveAvatar, takeAvatar } from "../lib/session.js";
const shown = computed(() => state.avatarPreview || (state.user && state.user.avatar ? avatarSrc(state.user.avatar) : ""));
function pick(event) {
  const file = event.target.files && event.target.files[0];
  event.target.value = "";
  takeAvatar(file);
}
</script>
