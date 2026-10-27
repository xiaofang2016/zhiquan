import { get } from "./api.js";
import { state, toast } from "../state.js";

const PAGE_SIZE = 20;

export function communityTime(value) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  return new Intl.DateTimeFormat("zh-CN", {
    timeZone: "Asia/Shanghai",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hourCycle: "h23"
  }).format(date);
}

export function communityPosts() {
  const rows = state.communityPosts || [];
  const picked = state.communityCircle
    ? rows.filter((post) => (post.circleIds || []).some((id) => Number(id) === Number(state.communityCircle)))
    : rows;
  const pages = Math.max(1, Math.ceil(picked.length / PAGE_SIZE));
  const page = Math.min(Math.max(state.communityPage || 1, 1), pages);
  const start = (page - 1) * PAGE_SIZE;
  return { posts: picked.slice(start, start + PAGE_SIZE), page, pages, total: picked.length };
}

export function pickCircle(id) {
  state.communityCircle = id;
  state.communityPage = 1;
  state.communityPost = null;
}

export async function loadCommunity() {
  if (state.communityLoading) return;
  state.communityLoading = true;
  state.communityError = "";
  try {
    const data = await get("/prod-api/community/feed");
    state.communityCircles = (data && data.circles) || [];
    state.communityPosts = (data && data.posts) || [];
  } catch (error) {
    state.communityError = error.message || "社球帝没有拿到";
    toast(state.communityError);
  } finally {
    state.communityLoading = false;
  }
}

export async function openCommunityPost(post) {
  state.communityPost = post;
  state.communityComments = [];
  state.communityCommentsLoading = true;
  try {
    const data = await get("/prod-api/community/posts/" + post.postId + "/comments");
    if (state.communityPost && state.communityPost.postId === post.postId) {
      state.communityComments = (data && data.comments) || [];
    }
  } catch (error) {
    if (state.communityPost && state.communityPost.postId === post.postId) {
      state.communityError = error.message || "评论没有拿到";
    }
  } finally {
    if (state.communityPost && state.communityPost.postId === post.postId) {
      state.communityCommentsLoading = false;
    }
  }
}

export function closeCommunityPost() {
  state.communityPost = null;
  state.communityComments = [];
  state.communityError = "";
}
