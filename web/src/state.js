import { reactive } from "vue";

export const API = "https://dgtplaoir00la.cloudfront.net";
export const SPORTS_API = "https://sports-api.ued2sport.com";
export const DB_PAGE_SIZE = 20;
export const WEEK = ["星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"];
export const SCORE_KEYS = ["score", "status", "status_up", "status_up_name", "time", "banchang", "jiaoqiu", "hteam_red", "hteam_yellow", "ateam_red", "ateam_yellow", "home_score_xiaojie", "away_score_xiaojie", "yazhi_jishi", "ouzhi_jishi", "daxiao_jishi"];

export const state = reactive({
  tab: "match",
  dates: [],
  leagues: [],
  date: "",
  type: 0,
  cid: 0,
  hot: true,
  resultOnly: false,
  page: 1,
  totalPage: 1,
  rows: [],
  loading: false,
  liveMode: "live",
  liveLeague: 0,
  liveRows: [],
  liveLoaded: false,
  scoreById: new Map(),
  dbType: 1,
  dbPage: 1,
  dbCatalog: null,
  dbLoading: false,
  dbError: "",
  dbLeague: null,
  dbSeasons: [],
  dbEdition: "",
  dbRows: null,
  communityCircle: 0,
  communityPage: 1,
  communityPosts: [],
  communityCircles: [],
  communityPost: null,
  communityComments: [],
  communityLoading: false,
  communityCommentsLoading: false,
  communityError: "",
  chatPartition: "",
  chatGroups: [],
  chatGroupId: 0,
  chatMessages: [],
  chatLoading: false,
  chatError: "",
  detail: null,
  detailTab: "live",
  detailLoading: false,
  detailError: "",
  detailData: null,
  user: null,
  authMode: "login",
  returnMine: false,
  jxt: null,
  ued: null,
  bindBrand: "jxt",
  avatarPreview: "",
  avatarReady: false,
  avatarOpen: false,
  avatarPick: false,
  avatarBusy: false,
  mission: null,
  pack: null,
  screen: "",
  toast: "",
  accountOpen: false,
  phone: "",
  password: "",
  confirm: "",
  code: "",
  authBusy: false,
  smsWait: 0,
  bindCode: "",
  bindBusy: false,
  bindSmsWait: 0,
  withdrawAmount: "",
  withdrawBusy: false,
  claimBusy: false,
  soundOpen: false,
  soundSide: "home",
  settingsRev: 0,
  taskFrom: "mine",
  orderFrom: "mine",
  inviteFrom: "mine",
  forecastTab: "predict",
  feedback: "",
  shareOpen: false
});

let toastTimer = 0;
export function toast(text) {
  state.toast = text;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    if (state.toast === text) state.toast = "";
  }, 1600);
}
