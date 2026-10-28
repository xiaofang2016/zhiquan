<template>
  <div v-if="post" class="cfeed">
    <div class="dbbar">
      <button type="button" @click="closeCommunityPost">‹</button>
      <b>{{ post.circleName || "社球帝" }}</b>
    </div>
    <article class="cpost">
      <header>
        <Logo :url="post.head" :name="post.nickname" />
        <b>{{ post.nickname || "用户" }}</b>
        <span v-if="post.badgeName">{{ post.badgeName }}</span>
        <em>{{ communityTime(post.createTime) }}</em>
      </header>
      <h3 v-if="post.title">{{ post.title }}</h3>
      <p>{{ post.content }}</p>
      <div v-if="post.images && post.images.length" class="cphotos">
        <img v-for="url in post.images" :key="url" :src="url" alt="">
      </div>
      <div class="cmeta">
        <span v-if="post.redPacket" class="cpacket">红包 {{ post.redPacket.remainNum }}/{{ post.redPacket.num }} · {{ post.redPacket.amount }}</span>
        <span>{{ post.likeCount || 0 }} 赞</span>
        <span>{{ post.commentCount || 0 }} 评论</span>
      </div>
    </article>
    <form class="composer" @submit.prevent="onComment">
      <textarea v-model="reply" maxlength="200" :placeholder="state.user ? '回复这条帖子' : '登录后回复'"></textarea>
      <button type="submit" :disabled="sending">回复</button>
    </form>
    <div v-if="state.communityCommentsLoading" class="empty">正在读取评论</div>
    <div v-else-if="!state.communityComments.length" class="empty">暂无评论</div>
    <div v-else class="ccomments">
      <article v-for="comment in state.communityComments" :key="comment.commentId" class="ccomment">
        <header>
          <Logo :url="comment.head" :name="comment.nickname" />
          <b>{{ comment.nickname || "用户" }}</b>
          <span v-if="comment.badgeName">{{ comment.badgeName }}</span>
          <em>{{ communityTime(comment.createTime) }}<template v-if="comment.cityName"> · {{ comment.cityName }}</template></em>
        </header>
        <p>{{ comment.content }}</p>
      </article>
    </div>
  </div>
  <div v-else>
    <form class="composer" @submit.prevent="onPost">
      <input v-model="title" maxlength="40" :placeholder="state.user ? '标题，可以不填' : '登录后发帖'">
      <textarea v-model="content" maxlength="500" placeholder="说点什么"></textarea>
      <button type="submit" :disabled="sending">发帖</button>
    </form>
    <div v-if="state.communityLoading && !state.communityPosts.length" class="empty">正在读取社球帝</div>
    <div v-else-if="state.communityError && !state.communityPosts.length" class="empty">{{ state.communityError }}</div>
    <div v-else-if="!windowed.total" class="empty">暂无帖子</div>
    <div v-else>
      <button v-for="item in windowed.posts" :key="item.postId" class="cpost" type="button" @click="openCommunityPost(item)">
        <header>
          <Logo :url="item.head" :name="item.nickname" />
          <b>{{ item.nickname || "用户" }}</b>
          <span v-if="item.circleName">{{ item.circleName }}</span>
          <em>{{ communityTime(item.createTime) }}</em>
        </header>
        <h3 v-if="item.title">{{ item.title }}</h3>
        <p>{{ item.content }}</p>
        <div class="cmeta">
          <span v-if="item.redPacket" class="cpacket">红包</span>
          <span>{{ item.likeCount || 0 }} 赞</span>
          <span>{{ item.commentCount || 0 }} 评论</span>
        </div>
      </button>
      <div v-if="windowed.pages > 1" class="dbpager">
        <button type="button" :disabled="windowed.page <= 1" @click="state.communityPage = windowed.page - 1">上一页</button>
        <span>{{ windowed.page }} / {{ windowed.pages }}</span>
        <button type="button" :disabled="windowed.page >= windowed.pages" @click="state.communityPage = windowed.page + 1">下一页</button>
      </div>
    </div>
  </div>
</template>
<script setup>
import { computed, ref } from "vue";
import Logo from "../components/Logo.vue";
import { state, toast } from "../state.js";
import { closeCommunityPost, communityPosts, communityTime, openCommunityPost, publishCommunityComment, publishCommunityPost } from "../lib/community.js";

const title = ref("");
const content = ref("");
const reply = ref("");
const sending = ref(false);

async function onPost() {
  const text = content.value.trim();
  if (!text || sending.value) return;
  sending.value = true;
  try {
    if (await publishCommunityPost(title.value, text)) {
      title.value = "";
      content.value = "";
    }
  } catch (error) {
    toast(error.message || "发帖失败");
  } finally {
    sending.value = false;
  }
}

async function onComment() {
  const text = reply.value.trim();
  if (!text || sending.value) return;
  sending.value = true;
  try {
    if (await publishCommunityComment(text)) reply.value = "";
  } catch (error) {
    toast(error.message || "回复失败");
  } finally {
    sending.value = false;
  }
}

const windowed = computed(() => communityPosts());
const post = computed(() => state.communityPost);
</script>
