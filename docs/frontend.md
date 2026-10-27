# 前端接入

说球帝页面按屏幕接下面几处。浏览器只打这些地址，不打函数地址，也不打吉祥。

| 屏幕 | 基址 |
|---|---|
| 比赛、即时比分、社球帝、登录、音浪、红包、提现、提示音 | `https://dgtplaoir00la.cloudfront.net` |
| 资料库 | `https://sports-api.ued2sport.com` |
| 播放页聊天 | `https://chat.kzbzcq.com/chat` |
| 播放页直播、指数、数据、预测 | `https://live.kzbzcq.com/live`、`https://index.kzbzcq.com/index`、`https://data.kzbzcq.com/data`、`https://tip.kzbzcq.com/tip` |
| 动画 | `https://player.kzbzcq.com/anim` |
| 播放页比赛详情 | `https://kzb001.net/prod-api/match/detail` |

站点本身是 `https://www.kzbzcq.com`。播放页路径是 `/player/{type}/1/0/{id}`，`type` 为 `1` 足球、`2` 篮球、`3` 娱乐。

网关和播放栏目的字段说明仍在 [api.md](api.md)。吉祥、UED 入账在服务器完成，见 [jxt.md](jxt.md)。

## 两种应答

网关、聊天、四个栏目成功时 HTTP 200：

```json
{ "msg": "ok", "code": "0", "data": {} }
```

`code` 是字符串 `"0"`。先看 `code`，再读 `data`。参数或账号错误一般是 HTTP 400，`msg` 可以直接提示。

聊天和四个栏目暂时拿不到时仍是 HTTP 200，`code` 为 `"1"`，`msg` 可提示。

资料库是另一套。HTTP 成功后读 `data`，翻页读 `meta`。失败时用 `message`。

```json
{ "code": 0, "data": [], "meta": { "page": 1, "page_size": 1000, "total": 1949 } }
```

账号接口都是 `POST`，请求头 `content-type: application/json`。读接口是 `GET`。登录令牌放在 JSON 的 `token` 里，不放请求头。本地保存整份登录结果，键名 `sqd-account`。没有退出接口，退出时丢掉这份即可。

## 比赛

只读。卡片上的实时比分、分钟、红牌用即时比分，不要等这份列表。

打开页面：

```text
GET /prod-api/category/list?type=0&isoften=1&ishot=1&langtype=zh
GET /prod-api/match/date/count?type=-1&cid=0
```

联赛在 `data.twoCategoryList`。`id` 会重复，NBA 和世界杯都是 `1`，筛比赛时 `cid` 用这里的 `id`，`type` 用这里的 `type`。日期在 `data` 数组里，`matchdate` 作为列表的 `starttime`。

比赛列表：

```text
GET /prod-api/match/list/new?isfanye=1&type=0&cid=0&ishot=-1&langtype=zh&pid=4&zoneId=Asia/Shanghai&zhuboType=1&starttime=2026-10-27&pn=1&ps=30
```

| 参数 | 页面怎么传 |
|---|---|
| `starttime` | 上海日期 `yyyy-MM-dd`。必填 |
| `type` | `0` 全部，`1` 足球，`2` 篮球。点侧栏联赛时用该联赛的 `type` |
| `cid` | `0` 为全部。点联赛时用联赛 `id` |
| `ishot` | 热门为 `1`，其余为 `-1` |
| `pn` / `ps` | 页码从 1，每页 30 |
| `since` | 轮询时带上一次的 `rev`。列表没变时 `data.changed` 是空数组，页面不动 |
| `zoneId` | 浏览器时区，用来显示开赛钟点。接口里的 `matchtime` 仍是上海时间 |
| `isfanye` `langtype` `pid` `zhuboType` | 固定 `1`、`zh`、`4`、`1` |

第 1 页按 `topList`、`notopList`、`dataList` 接起来。第 2 页起只接 `dataList`。同一场 `id` 只留一条。热门页把 `is_zd` 为 `1` 的场次按 `zd_level` 从高到低抽到最前。

卡片用到的字段：

| 字段 | 说明 |
|---|---|
| `id` `type` `cid` | 比赛、运动、联赛 |
| `name` `clogo` | 联赛名、图标 |
| `matchtime` | `yyyy-MM-dd HH:mm:ss`，上海时间 |
| `status_up` `status_up_name` | 状态码和文案。篮球这里有时是数字，优先显示文案 |
| `hteam_name` `ateam_name` `hteam_logo` `ateam_logo` | 主客队 |
| `score` | 如 `1-0`。未开赛可以显示 VS |
| `time` `banchang` `jiaoqiu` | 分钟、半场、角球 |
| `isHot` `is_zd` `zd_level` | 热门筛选用 `isHot`。置顶排序用 `is_zd` 和 `zd_level` |
| `live_urls` `mirror_live_urls` `global_live_urls` `live_cartoon_url` | 线路，见播放 |

完整比赛对象见 [api.md](api.md) 的「比赛对象」。

## 即时比分

只读，只覆盖当天。大约每 2 秒拉一次，页面隐藏时停。

```text
GET /prod-api/match/list/new2?type=0&cid=0&ishot=-1&pn=1&ps=200
```

带上一次响应里的 `updatedAt` 作为 `since`。有变化时读 `changed` 和 `removed`，按比赛 `id` 盖到已有场次上。没有 `changed` 时，把 `notopList`、`topList`、`dataList`、`overList`、`otherList` 都收进来。

即时比分页自己翻页，`ps=200`，最多 3 页。第 1 页收 `notopList`、`topList`、`overList`、`otherList` 和 `dataList`，后面的页只收 `dataList`。

进球、红牌提示音：

```text
GET /prod-api/match/sound/{key}
```

`key` 为 `default`、`whistle`、`drum`、`broadcast`、`horn`、`victory`。响应是音频，不是 JSON。`mute` 不请求。

## 资料库

走公共赛事接口，不走网关的 `/prod-api/database/`。只读，不带 `competition_fame_level`。

列表一次取完再在页面里排序、分页。`page` 从 1，`page_size` 最大 1000，`lang=zh-hans`。

```text
GET /v1/competitions?lang=zh-hans&page=1&page_size=1000
GET /v1/competition-editions?lang=zh-hans&page=1&page_size=1000
```

两份都按 `meta.total` 翻完。赛事按下面的顺序排，然后每页 20 条：

1. 该赛事全部赛季的 `edition_standing_count` 之和，多的在前
2. 赛季份数，多的在前
3. `competition_fame_level_code`，小的在前
4. 中文名

热门条是排序后的前 20 个：`competition_kind_name` 为「联赛」、`competition_gender_name` 为「男」、赛季份数大于 0。足球、篮球用 `competition_sport_name_zh` 是否为「篮球」来分。

赛事用到的字段：

| 字段 | 说明 |
|---|---|
| `competition_id` | 赛事 id |
| `competition_name_zh` `competition_name_en` | 名称，中文优先 |
| `competition_logo_url` | 图标 |
| `competition_sport_name_zh` | 「足球」或「篮球」 |
| `competition_kind_name` | 「联赛」或「杯赛」 |
| `competition_gender_name` | 「男」「女」 |
| `competition_fame_level_code` | 知名度，数字越小越靠前 |
| `competition_league_tier` | 联赛级别 |

点开一个赛事，只取有积分的赛季，按 `edition_sort_order` 从新到旧：

```text
GET /v1/competition-editions?lang=zh-hans&page_size=100&competition_id={competition_id}&edition_sort=-edition_sort_order
```

赛季按钮用 `edition_season_label`，没有时用 `competition_edition_name_zh`。默认打开 `edition_status_name` 为「进行中」的那一季，没有就打开第一季。

积分榜：

```text
GET /v1/competition-editions/{competition_edition_id}?lang=zh-hans&with_ranking=true
```

读 `edition_rankings`，按 `rank` 从小到大。一行用到：

| 字段 | 列 |
|---|---|
| `rank` | 排名 |
| `team_logo_url` `team_name_zh` `team_name` | 球队，中文名优先 |
| `played` | 赛 |
| `won` `drawn` `lost` | 胜/平/负 |
| `score_for` `score_against` | 进/失 |
| `points` | 积分 |

短名用赛季上的 `competition_short_name_zh`。没有时，把中文名开头的「英格兰足球」「英格兰」去掉再显示。

## 社球帝

只读，不需要登录。数据是抓取后的副本，大约每 2 分钟更新。页面只显示最近 60 帖。

```text
GET /prod-api/community/feed
GET /prod-api/community/posts/{postId}/comments
```

`feed` 的 `data`：

| 字段 | 说明 |
|---|---|
| `circles` | 这 60 帖里出现过的圈子。`id`、`name`、`icon` |
| `posts` | 帖子，新的在前 |

帖子：

| 字段 | 说明 |
|---|---|
| `postId` | 帖子编号 |
| `title` `content` | 标题和正文 |
| `createTime` | ISO 时间，页面按上海时间显示 |
| `nickname` `head` `badgeName` | 作者、头像、徽章 |
| `likeCount` `commentCount` `viewCount` | 赞、评论、浏览 |
| `circleIds` `circleName` | 所属圈子。筛左侧圈子时用 `circleIds` |
| `images` | 图片地址。视频不在这里 |
| `redPacket` | 没有红包时为 `null`。有则含 `amount`、`num`、`remainNum`、`statusName` |

评论在 `data.comments`。每一条用 `commentId`、`nickname`、`head`、`badgeName`、`content`、`createTime`、`cityName`、`likeCount`。还没抓到时 `comments` 是空数组。

## 播放

详情先读原站，拿到 `data.matchinfo` 且 `id` 一致就用它。失败再从说球帝比赛列表里按 `id` 找今天、昨天、明天。

```text
GET https://kzb001.net/prod-api/match/detail?mid={id}&type={type}&isnew=1&pid=4&langtype=zh&test=1&zoneId=Asia/Shanghai
```

线路来自比赛对象的 `live_urls`、`mirror_live_urls`、`global_live_urls`、`live_cartoon_url`。同一 `url` 只留一条。名字是「动画」，或地址里有 `nami.com`、`livetracker`，就当动画。动画里 `https://widgets-livetracker.nami.com/` 开头的地址改去：

```text
GET https://player.kzbzcq.com/anim?src={编码后的原地址}
```

其余地址原样播放。`.flv` 用 flv.js 1.6.2。同一地址把后缀换成 `.m3u8` 给 iOS。

聊天、直播、指数、数据、预测都要 `mid` 和 `type`。`mid` 是比赛 id。`type` 缺省按 `1`。

```text
GET https://chat.kzbzcq.com/chat?mid=4498761&type=1
GET https://live.kzbzcq.com/live?mid=4498761&type=1
GET https://index.kzbzcq.com/index?mid=4498761&type=1
GET https://data.kzbzcq.com/data?mid=4498761&type=1
GET https://tip.kzbzcq.com/tip?mid=4498761&type=1
```

聊天先不带 `before`、`after`，记下 `badges`。贴在底部时用最新一条的 `time` 作为 `after`，约 4 秒问一次。往上翻用最早一条的 `time` 作为 `before`。`exhausted` 为真就停止上翻。同一条 `id` 只画一次。直播和指数约 8 秒刷新一次。

消息和四个栏目的字段见 [api.md](api.md) 文末。

## 账号

手机号 11 位，`1` 开头，第二位 `3` 到 `9`，不带 `+86`。密码 6 到 20 位。注册验证码 6 位，5 分钟内有效，60 秒内不能重复获取。

| 接口 | 请求体 | 成功时 `data` |
|---|---|---|
| `POST /prod-api/user/sms` | `{ "phone" }` | `null`。已注册返回「这个手机号已经注册」 |
| `POST /prod-api/user/register` | `{ "phone", "password", "confirm", "code" }` | 登录结果 |
| `POST /prod-api/user/login` | `{ "phone", "password" }` | 登录结果。手机号或密码不对都是「手机号或密码不正确」 |
| `POST /prod-api/user/session` | `{ "token" }` | 登录结果。令牌不续期。过期是「登录已过期」 |

登录结果：

| 字段 | 说明 |
|---|---|
| `userId` | 32 位，没有横线 |
| `phone` | 手机号 |
| `nickname` | 默认是打码手机号 |
| `avatar` | 头像路径，可能是空串。相对路径拼在网关基址后面 |
| `token` | 登录令牌，30 天后失效 |

头像：

```json
POST /prod-api/user/avatar
{ "token": "登录令牌", "image": "data:image/jpeg;base64,..." }
```

成功 `data` 仍是登录结果，`avatar` 形如 `/prod-api/user/avatar/{userId}?v=时间戳`。读图是 `GET` 这个路径，响应为 JPEG。

### 音浪

日期按上海。`data` 一直是这一份：

| 字段 | 说明 |
|---|---|
| `balance` | 音浪余额，整数 |
| `signDay` | 已连续签到天数 |
| `signed` | 今天是否已签 |
| `gotReg` | 注册奖励已领 |
| `gotLogin` | 连续登录奖励已领 |
| `records` | 明细。`title`、`time`（`yyyy-MM-dd HH:mm:ss`）、`amount` |

| 接口 | 请求体 |
|---|---|
| `POST /prod-api/user/mission` | `{ "token" }` |
| `POST /prod-api/user/mission/sign` | `{ "token", "day" }`。`day` 从 1 起，对应当天那一格 |
| `POST /prod-api/user/mission/claim` | `{ "token", "type" }`。`1` 注册 +200，`3` 连续登录满 3 天 +300 |

签到奖励按天是 30、50、70、90、110、130、150。今天已签返回「今日已签到」。还没轮到返回「未到签到时间」。观看时长、分享、邀请没有计数，对应领取会拒绝。已领过返回「已领取」。

等级用音浪余额本地算：不足 100 为 Lv.1，否则等级为余额除以 100 的整数部分。

### 红包

每天上海时间一轮。未登录也可以看进度，领取要带 `token`。

```json
POST /prod-api/user/redpack
{ "token": "登录令牌" }
```

| 字段 | 说明 |
|---|---|
| `sender` | 「说球帝」 |
| `description` | 「拼手气红包」 |
| `num` | 这一轮总数 |
| `drawNum` | 已领个数 |
| `amount` | 奖池，元，两位小数字符串 |
| `mine` | 当前用户领到的金额。没领是 `null` |
| `balance` | 已登录时带上说球帝余额，元 |

`POST /prod-api/user/redpack/claim`，请求体 `{ "token" }`。成功后的 `data` 同上，`mine` 有值。今天领过返回「今日已领取」。

### 吉祥和 UED

页面只打说球帝自己的账号接口。把路径里的 `jxt` 换成 `ued` 就是 UED，请求体相同。绑定验证码是 4 到 6 位。

| 接口 | 请求体 | 成功时 `data` |
|---|---|---|
| `POST /prod-api/user/{jxt\|ued}/status` | `{ "token" }` | 绑定状态 |
| `POST /prod-api/user/{jxt\|ued}/sms` | `{ "token" }` | `null`。手机号用当前登录账号，不要再传 |
| `POST /prod-api/user/{jxt\|ued}/bind` | `{ "token", "code" }` | 绑定状态 |
| `POST /prod-api/user/{jxt\|ued}/unbind` | `{ "token" }` | 绑定状态 |
| `POST /prod-api/user/{jxt\|ued}/withdraw` | `{ "token", "amount" }` | 绑定状态。`amount` 是元，最多两位小数，最低 10 |

绑定状态：

| 字段 | 说明 |
|---|---|
| `isBind` | `"1"` 已绑定，`"0"` 未绑定 |
| `account` | 对方用户名打码。未绑定是空串 |
| `balance` | 说球帝余额，元，两位小数 |

未登录是「请先登录」。提现还没绑定时，吉祥是「请先绑定吉祥账号」，UED 是同一句把名字换成 UED。金额和失败文案见 [jxt.md](jxt.md) 的「页面调用的接口」。
