# 前端接入

说球帝页面按屏幕接下面几处。浏览器只打这些地址，不打函数地址，也不打吉祥。

| 屏幕 | 基址 |
|---|---|
| 比赛、即时比分、社球帝、登录、音浪、红包、提现、提示音 | `https://dgtplaoir00la.cloudfront.net` |
| 资料库 | `https://sports-api.ued2sport.com` |
| 职圈群聊天 | `https://dgtplaoir00la.cloudfront.net`，实时用 `wss://ix1ui9ljn6.execute-api.ap-northeast-1.amazonaws.com/prod` |
| 播放页聊天 | `https://chat.kzbzcq.com/chat` |
| 播放页直播、指数、数据、预测 | `https://live.kzbzcq.com/live`、`https://index.kzbzcq.com/index`、`https://data.kzbzcq.com/data`、`https://tip.kzbzcq.com/tip` |
| 动画 | `https://player.kzbzcq.com/anim` |
| 播放页比赛详情 | `https://kzb001.net/prod-api/match/detail` |

站点本身是 `https://www.kzbzcq.com`。播放页路径是 `/player/{type}/1/0/{id}`，`type` 为 `1` 足球、`2` 篮球、`3` 娱乐。

前端只看这一份。网关内部字段和吉祥入账签名不在这里。

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

足球还可能有红黄牌 `hteam_red`、`hteam_yellow`、`ateam_red`、`ateam_yellow`。篮球还可能有 `home_score_xiaojie`、`away_score_xiaojie`。娱乐场次用 `title`，经常没有 `score`。没有的字段不要当成空字符串去显示。

`live_urls` 里每一路：`name` 线路名，动画一般叫「动画」；`url` 播放地址，直播是 `.flv`；`status` 为 `1` 可播；`index` 排序。

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

抓取后的副本，大约每 2 分钟更新。社区流默认按发布时间从新到旧，不是另一套推荐序。`postId` 是原站编号，同一帖不会改号。

```text
GET /prod-api/community/feed?page=1&ps=60&circleId=14&type=essence&sortRule=comment&before=2026-10-29T02:00:00.000+00:00&token=
GET /prod-api/community/posts/{postId}?token=
GET /prod-api/community/posts/{postId}/comments?page=1&ps=50&sortRule=like&token=
GET /prod-api/community/posts/{postId}/highlights?token=
GET /prod-api/community/comments/{commentId}/replies?postId={postId}&page=1&ps=50&sortRule=newest&token=
GET /prod-api/community/circles?token=
GET /prod-api/community/circles?type=essence&token=
GET /prod-api/community/circles/{circleId}?token=
```

读接口的 `token` 无效或过期时按未登录处理，仍然返回数据。写接口仍要有效登录。社区 JSON 带 `Cache-Control: no-store`。视频清单、密钥和分片仍可缓存。

`page` 从 1。`ps` 不传时流是 60、评论和回复是 50，最大也是这两个数。`circleId` 只留这个圈子。`type=essence` 只留精华。`type=follow` 是头部「关注」。只留当前登录用户在说球帝点过关注的作者或圈子，不使用原站的关注列表。要带 `token`，没登录或还没关注任何人时这一页是空的。`type=fans` 只留关注了当前用户的人发的帖，没登录或还没有粉丝发帖时是空的。原站被关注的人新发的帖，下一轮抓取写进副本后会出现在这里，大约 2 分钟。`sortRule=publish` 按发布时间，`sortRule=comment` 按 `lastReplyTime`，没有回复的用发布时间。不传按发布时间。`before` 只留这条时间之前的帖；按发布时间排时用 `createTime`，按最新回复排时用 `lastReplyTime`。筛完再分页。`total` 是筛完的条数，`pages` 是页数。拉黑的作者不会出现在流和评论里。

`GET /circles?type=essence` 的 `data` 仍是圈子数组，只留这份副本里有精华帖的圈子，顺序和全部圈子一致。带 `token` 时每个圈子有 `followed`，表示当前用户有没有在说球帝关注这个圈子。`followCount` 是原站人数，再加上说球帝本地关注人数。圈子详情多一个 `announces`。抓取会读圈子详情，再读这个圈子下每个话题的详情，把 `announces` 合到一起。每条有 `id`、`title`、`type`、`createTime`、`content`，有外链时还有 `url`。上游这两处都没有时是空数组。

帖子不在已抓到的范围里时，单帖 `data` 为 `null`。评论还没抓到，或帖子不存在，`comments` 是空数组。说球帝账号发出的帖、评论和回复，`userId` 是登录接口返回的 32 位编号，用来对上当前用户。原站内容仍是数字编号。社区里的 `head`、`ownerHead` 若是站内相对路径，会补成 `https://dgtplaoir00la.cloudfront.net` 开头的绝对地址。

`feed` 的 `data`：

| 字段 | 说明 |
|---|---|
| `circles` | 全部圈子，不随分页变少 |
| `posts` | 当前页。默认新发的在前；`sortRule=comment` 时最后回复的在前 |
| `total` `page` `pages` | 分页 |

圈子：`id`、`name`、`icon`、`cover`、`followCount`、`postCount`、`ownerName`、`ownerHead`、`topics`、`followed`。`topics` 里是 `id`、`name`，圈子上的话题还有 `cover`。`GET /circles` 的 `data` 就是这个数组。单个圈子没有时 `data` 为 `null`。没带有效 `token` 时 `followed` 是 `false`。

社区流抓最近 20 页，精华 3 页，每个圈子 2 页。更早的帖第一次打开单帖或评论时再向上游要这一篇，之后就留在副本里。

帖子：

| 字段 | 说明 |
|---|---|
| `postId` | 原站编号 |
| `title` `content` | 标题和正文 |
| `createTime` `lastReplyTime` | ISO 时间。页面按上海时间显示。没有回复时 `lastReplyTime` 可能为空 |
| `nickname` `head` `userId` | 作者。原站是数字编号。说球帝账号是登录返回的 32 位编号 |
| `badgeName` `badgeLevel` `badgeStyle` `vipLevel` `jxfLevel` `uedLevel` | 徽章名和等级。`badgeStyle` 是 `1` 或 `2`，没有时为 `null`。`jxfLevel`、`uedLevel` 是两个平台的 V 级，没有或是 `-1` 时为 `null`。`vipLevel` 仍是 UED 的等级，页面要两套图标时用前两个，取高的那个 |
| `cityName` | 没有时为空字符串 |
| `likeCount` `commentCount` `viewCount` `collectCount` | `commentCount` 是这份副本里实际带上的顶层评论和回复，和列表对得上。`liked`、`collected`、`followed` 要带有效 `token` 才知道是不是当前用户，否则是 `false`。`followed` 只表示当前用户有没有关注这篇的作者，和原站无关 |
| `essence` | 精华是 `true` |
| `circleIds` `circleName` | 所属圈子 |
| `topics` | `{ id, name }`。没有时是空数组 |
| `mentionUsers` | 正文里 @ 的人，`{ userId, nickname }`。没有时是空数组 |
| `highlightCommentCount` | 亮评条数。点赞不少于 2 的顶层评论 |
| `rewardCount` `rewardEnabled` | 打赏人数，以及这篇能不能打赏。不能打赏时 `rewardEnabled` 是 `false` |
| `images` | 图片地址，给已经按字符串用的页面 |
| `pictures` | 同一批图，带 `url`、`thumb`、`w`、`h`。COS 的 `thumb` 已带缩略图参数。没有宽高时 `w`、`h` 为 `null` |
| `videos` | `url`、`cover`、`w`、`h`、`duration`。`duration` 是秒，播放列表没拿到时为 `null`。原站 `m3u8` 会换成网关地址 `/prod-api/community/video/{taskId}/index.m3u8`，清单、密钥和分片都由网关转发并带上跨域头。分片只转发 `commentfile.yc12315.com` 上 `/community/video/` 开头的地址 |
| `match` | 没有关联比赛时为 `null`。有则含 `id`、`type`、`home`、`away`、`homeLogo`、`awayLogo`、`score`、`status`。多场只给第一场。`score` 如 `1-0`，`status` 如「上半场」。近三天的赛程里没有这场时这两项为 `null` |
| `hotComment` | 没有亮评时为 `null`。有则含 `commentId`、`userId`、`nickname`、`head`、`createTime`、`content`、`likeCount`、`cityName`。头像没有时是空字符串 |
| `redPacket` | 没有红包时为 `null`。有则是说球帝自己发的，不使用原站金额。每帖 10 份、共 `10.00` 元。字段有 `amount`、`num`、`remainNum`、`status`、`statusName`、`description`、`mine`。`mine` 是当前用户领到的金额，没领或没带 `token` 时为 `null`。领完 `statusName` 是「已领完」 |

评论在 `data.comments`。`sortRule=newest` 新的在前，`oldest` 早的在前，`like` 按点赞从高到低，点赞相同再按时间新的在前。不传按最新。`total` 是顶层条数，不含回复。一条评论：`commentId`、`userId`、`nickname`、`head`、`badgeName`、`badgeLevel`、`badgeStyle`、`vipLevel`、`jxfLevel`、`uedLevel`、`content`、`imageUrls`、`createTime`、`cityName`、`likeCount`、`replyCount`、`replies`。`imageUrls` 是配图地址，没有时是空数组。`replyCount` 等于这条评论里实际带上的 `replies` 条数。回复里用 `replyId`。直接回复这条评论时不带 `replyToUserId`、`replyToNickname`、`replyToReplyId`。回复某一条回复时带 `replyToReplyId`，并保留 `replyToUserId` 和 `replyToNickname`，即使被回复的人就是这条评论的作者。其余字段和评论相同，没有再嵌一层。头像若在外站，会转成 `https://dgtplaoir00la.cloudfront.net/prod-api/community/images/{hash}` 的小图；COS 地址会先补上缺失的连字符。说球帝账号的 `head` 是绝对地址。

亮评是 `GET /posts/{postId}/highlights`。`data.comments` 一次给全，字段和评论相同，按点赞从高到低。点赞少于 2 的不算。`data.total` 是条数。

回复列表是 `GET /comments/{commentId}/replies`。`data.replies` 是当前页，`total`、`page`、`pages` 和评论一样。`sortRule` 是 `newest` 或 `oldest`。`postId` 用来找到这篇帖已经存下的回复；抓取记下评论所在帖之后可以不传。嵌在评论里的回复和原站另页的回复会合成一份，同一条 `replyId` 只出现一次。

```text
POST /prod-api/community/posts/{postId}/comments
{ "token": "", "content": "", "commentId": 9603, "replyToReplyId": 6054, "replyToUserId": 1050718, "imageUrls": ["https://img.example/a.jpg"] }
```

`commentId` 不传就是评这篇帖子。传了就是回复这条评论。`replyToReplyId` 是要回复的那一条回复。不传就是直接回复这条评论，返回里不带回复对象。传了就带回 `replyToReplyId`、`replyToUserId` 和 `replyToNickname`，哪怕对方就是评论作者。编号可以是数字，也可以是说球帝登录返回的 32 位编号。配图最多 4 张，地址要以 `http://` 或 `https://` 开头。评论不存在时返回「这条评论不存在」。说球帝账号发出的评论和回复带同一个 32 位 `userId`。

发帖和评论要登录。令牌放在 JSON 的 `token` 里。写进去的只在说球帝这份副本上，所有打开这个接口的人都能看到，不会同步回原站。

```text
POST /prod-api/community/posts
{ "token": "", "title": "", "content": "" }
```

点赞、收藏、关注、拉黑、举报要登录。再点一次点赞、收藏、关注、拉黑就是取消。这些都写在说球帝这份副本上，所有打开这个接口的人都能看到计数，不会同步回原站。

```text
POST /prod-api/community/posts/{postId}/like
POST /prod-api/community/posts/{postId}/collect
{ "token": "" }

POST /prod-api/community/users/{userId}/follow
POST /prod-api/community/users/{userId}/block
{ "token": "" }

POST /prod-api/community/circles/{circleId}/follow
{ "token": "" }

POST /prod-api/community/reports
{ "token": "", "targetType": "post", "targetId": "5939", "reason": "" }
```

`targetType` 是 `post`、`comment`、`reply` 或 `user`。`reason` 是 1 到 100 个字。同一个人重复举报同一对象，保留第一次。

评论和回复可以点赞、取消，再点一次就是取消。返回 `{ liked, likeCount }`。`likeCount` 是原站已有的赞，再加上说球帝点过的次数，不会小于原站。列表里的 `liked` 要带有效 `token`。删除只能删自己在说球帝发的评论或回复，原站那条删不了。

```text
POST /prod-api/community/comments/{commentId}/like
POST /prod-api/community/comments/{commentId}/remove
{ "token": "", "postId": "" }

POST /prod-api/community/replies/{replyId}/like
POST /prod-api/community/replies/{replyId}/remove
{ "token": "", "postId": "", "commentId": "" }
```

发评论可以带 `cityName`，不传就是空。发帖可以带 `circleIds`、`pictures`、`videos`、`topicIds`、`mentionUserIds`、`matchId`、`matchType`。`circleIds` 不传时不进具体圈子，仍出现在全部帖子里。图片和视频先走上传，视频这里只收已经能播的地址，不做转码。

```text
POST /prod-api/community/upload
{ "token": "", "image": "" }
```

`image` 和头像一样是压好的 JPEG。返回 `url`、`w`、`h`。`url` 是 `https://dgtplaoir00la.cloudfront.net/prod-api/community/media/{id}`。

自己的帖子可以改、删、改查看权限、开关打赏。`visibility` 是 `public` 或 `self`。`self` 只有作者自己的流和详情看得到。

```text
POST /prod-api/community/posts/{postId}/edit
POST /prod-api/community/posts/{postId}/remove
POST /prod-api/community/posts/{postId}/visibility
{ "token": "", "visibility": "self" }

POST /prod-api/community/posts/{postId}/reward-switch
{ "token": "" }
```

打赏从说球帝余额扣到作者余额，流水标题是「帖子打赏」。金额 `0.01` 到 `200` 元。帖子要先打开打赏。作者是原站用户时返回「暂时不能打赏原站作者」，不会扣款。不能打赏自己的帖子。帖子上有 `rewardUsers`，最近几个人的头像。记录是 `GET /posts/{postId}/rewards`。

用户主页是 `GET /users/{userId}`，`userId` 可以是原站数字或说球帝 32 位编号。昵称、头像和徽章从他发过的帖、评论里取，说球帝自己发的也算。带 `token` 时有 `followed`、`blocked`。资料里有 `cityName`。他的帖用 `GET /feed?userId=`。关注和粉丝是 `GET /users/{userId}/follows`、`/fans`。改自己的简介、背景和城市是 `POST /me/profile`，`intro` 最多 100 字，`bg` 用上传得到的地址，`cityName` 最多 20 个字。

```text
GET /prod-api/community/me/collects?token=&page=1&ps=20
GET /prod-api/community/me/likes?token=
GET /prod-api/community/me/comments?token=
GET /prod-api/community/me/circles?token=
GET /prod-api/community/me/blocks?token=
POST /prod-api/community/me/collects/clear
POST /prod-api/community/me/likes/clear
{ "token": "" }
```

单条取消收藏、取消点赞仍用原来的 `/collect`、`/like`。

通知是说球帝账号之间的评论、回复、点赞、关注。原站用户不产生、也收不到。

```text
GET /prod-api/community/me/messages?token=&type=comment&page=1&ps=20
GET /prod-api/community/me/messages/unread?token=
POST /prod-api/community/me/messages/read
{ "token": "", "ids": [] }
```

`type` 可以是 `comment`、`reply`、`like`、`follow`、`system`。不传 `ids` 时全部标成已读。未读数按这几类分开，另有 `total`。

话题是 `GET /topics/{topicId}`，这个话题下的帖用 `GET /feed?topicId=`。公告从圈子详情和话题详情的 `announces` 抓来，正文在 `content`。详情是 `GET /announces/{id}`，只有外链时用 `url`。粉丝发的帖是 `GET /feed?type=fans`。

原站帖子里的视频已经是 `m3u8`，网关只转发清单、密钥和分片，不再转一次。自己发帖时 `videos` 填已经能播的地址，可以是 `mp4`，不做转码。

点赞和收藏成功时，`data` 仍是这篇帖子，带上新的计数和 `liked`、`collected`。关注作者是 `POST /users/{userId}/follow`，`userId` 用帖子上的原站用户编号。成功时 `data` 是 `{ userId, followed }`。再点一次取消。取消后，头部「关注」不再出现这个人的帖。这个人之后在原站发的新帖，仍要等下一轮抓取；只要关注还在，就会进「关注」。拉黑返回 `blocked`。

帖子红包要登录后领。每个人每帖只能领一次，再领不会再入账，`mine` 仍是上次的金额。领到的钱记进说球帝余额，流水标题是「帖子红包」。份数领完返回「红包已领完」。这篇没有红包时返回「这篇没有红包」。

```text
POST /prod-api/community/posts/{postId}/redpacket
{ "token": "" }
```

成功时 `data` 仍是这篇帖子，`redPacket.mine` 有值。每天那份 588 元红包是另一条接口，和帖子红包互不相干。

职圈群里发出去的红包也记在这份余额里。流水标题是「发红包」「群红包」「红包退回」。

## 职圈群聊天

站点「聊天」这一屏，看的是职圈群，不是比赛播放页的聊天室。只读，不登录也能看。点开红包只在本页展示，不领取，不把抓取账号的钱从奖池里划走。

新消息不要轮询 HTTP。打开页面拉一次列表和当前群的最新 100 条，之后只接 WebSocket。抓取挂着原站长连接，消息到达就会推过来。页面这条 socket 断了，1 秒后重连，之后加倍，最长 30 秒，连上再从 1 秒算。抓取自己断线会先补离线再推。新加入的群大约一分钟内出现。

列表是 `GET /prod-api/chat/groups`，按 `latest.time` 从新到旧。每群有 `id`、`gid`、`name`、`ownerName`、`notice`、`avatar`、`latest`。路由和未读都用数字 `id`，不要用可能补零的 `gid`。`avatar` 是文件名，拼在 `imageBase` 上：`{imageBase}/head/{文件名}?imageView2/1/w/256/h/256&imgType=group&imageMogr2/format/webp`。`imageBase` 为空，或是打不开的 `oss.imnono.net` 时，用 `https://imfile.dtthsp.com`。文件名已经是 `http` 地址就直接用；已经带 `?` 就不要再追加参数。没有文件名，或这一页里图片加载失败，显示群名里第一个汉字、字母或数字，没有就显示「群」。刷新页面后再试一次图片。

列表右侧预览：图片没有配文时是 `[图片]`，红包用标题，撤回用那句系统字。谁领了红包的单条预览留空。有昵称时写成 `{昵称} {预览}`。时间用 `latest.time`，上海时区，格式 `MM-DD HH:mm`。没有消息显示「没有消息」。

公告 `enabled` 为 `0` 时接口给的就是空字符串，不要展示。有公告时黄条可关，关掉后 24 小时内不再出现，键名 `kzb-notice:{群id}:{公告}`。公告文字变了，键对不上，会再出现。

点进群取 `GET /prod-api/chat/groups/{id}/messages`，最新 100 条，从旧到新，停在底部。请求还没回来时如果 socket 已经推了更新的，接在这 100 条后面，同一 `id` 只留一条。往上滑到顶（距顶 8px 内）再请求 `?before={上一条的 id}`，`id` 是库里的排序键。新的一页插到上面，滚动位置不动。连着几页都是已经有的，就停。`older` 为空就到头了。距底部 120px 以内，新消息才把列表滚下去；人在看上面的历史时不要拽回底部。

单条内容正好是「领取了红包」「已领取了红包」或以此结尾的不显示。正文里含有「人已领取」的留下，例如「等12/200人已领取了红包」。

`type` 是字符串。`0` 就是文字，不要当成空。

| `type` | 页面 |
|---|---|
| `0` 文字、`90` 系统提示 | 普通气泡。`90` 不居中 |
| `11` 回复、`12` @ | 普通气泡，不另画引用条。正文按 `origin_text`、`to.text`、`text`、回复的 `from.text` 取第一段非空文字 |
| `1` | 一张图。`content` 本身就是文件名，不是 JSON |
| `13` | `content` 是 `{ "text": "", "files": [{ "fileMd5": "", "fileName": "" }] }`。每张图用 `fileMd5`，没有再用 `fileName`。`text` 画在图下面，里面的表情同样要换 |
| `10` | 红包卡片。见下面 |
| `91` 或撤回 JSON | 居中灰字，没有头像昵称。撤回别人的是 `{昵称}撤回了"{对方}"的一条消息`，撤回自己的是 `"{昵称}" 撤回了一条消息` |

`[/发呆]` 这类括号对照 `web/src/lib/faces.js` 换成 `/face/` 里的图。表里没有的整段留在文字里。`[视频小程序] 名字，后面还有别的` 只显示名字。

图片地址 `{imageBase}/message/{文件名}?imageView2/2/w/640/h/640&imageMogr2/format/webp`。打不开时那一块显示「图片」。

红包 `content` 是 JSON。这一页不调用领取接口。

| 字段 | 页面 |
|---|---|
| `description`，没有就用 `title` | 卡片标题，去掉 🧧。都没有就写「红包」 |
| `nickname` 或 `nickName`，再没有用消息的 `nick` | 封面第一行 `{名字}的红包` |
| `status` | `1` 待领取，`2` 已领取，`3` 已领完。后两种直接看详情 |
| `dbAmountPool` | 只留数字，去掉末两位，左侧补 0 到 6 位。这是奖池，不是领到的金额 |
| `num`、`remainNum` | 两个都有写 `剩余 {remainNum}/{num}`。只有 `num` 写 `共 {num} 个` |
| 卡片底栏 | 固定文案「职圈3.0红包」，不是接口字段 |

待领取先出封面，点「开」才看奖池和剩余个数。已领取、已领完点开就是详情。

打开聊天页就连 WebSocket，发送 `{ "action": "subscribe", "groupId": "all" }`。每 4 分钟发 `{ "action": "ping" }`。推送：

```json
{ "groupId": 8447, "message": { "time": 0, "nick": "", "sendUid": "", "type": "0", "content": "", "id": "" } }
```

`groupId` 等于列表里的数字 `id`。`message` 和 HTTP 那一行一样，按 `id` 去重。当前群接进底部；别的群只改列表上的最后一条，并给未读加 1。新群是另一条 `{ "group": { "id": 0, "gid": "", "name": "", "ownerName": "", "notice": "", "avatar": "" } }`，列表里没有就加进去。

未读只在本机。`localStorage` 键名 `kzb-chat-unread`，值是 `{ "8447": 3 }` 这种，群 `id` 对条数。没有 99 的上限，数字原样标在头像右上角，红底 `#f74c31`、白字。点进该群后删掉这一项。刷新后还在，直到点开。窄屏点进群只留消息，返回再出列表。

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

聊天成功时 HTTP 200，`code` 为 `"0"`。没有人说话时 `messages` 是空数组。暂时拿不到时 `code` 为 `"1"`，`msg` 是「聊天暂时没有拿到」。

| 参数 | 说明 |
|---|---|
| `mid` | 必填，比赛 id |
| `type` | `1` 足球，`2` 篮球，`3` 娱乐。缺省按 `1` |
| `before` | 往上翻。传当前最早一条的 `time`，最多 100 条 |
| `after` | 问新消息。传当前最新一条的 `time`，不带 `badges` |

`before` 和 `after` 都传时按 `before`。两个都不传，返回最新 100 条。

| 字段 | 说明 |
|---|---|
| `closed` | 聊天室已关闭 |
| `exhausted` | 更早的历史已经取完 |
| `badges` | 等级图标。键是等级，值是图片地址 |
| `messages` | 按 `time` 从早到晚 |

一条消息：`id`、`time`（毫秒）、`nick`（没有时显示「游客」）、`content`、`images`（最多 4 张，每张有 `url`，可能有 `w`、`h`）、`level`（图标用 `badges[level]`）、`bubble`、`color`。后面三项没有时不返回。

四个栏目成功时 `data` 按地址读。暂时拿不到时 `code` 为 `"1"`，`msg` 是「这场资料暂时没有拿到」。

| 栏目 | `data` 里用什么 |
|---|---|
| 直播 | `environment` 场地，可能没有。有则用 `nameZh`、`city`、`temperature`、`humidity`。`tlive` 是文字直播，按时间顺序，每条 `time`、`data` |
| 指数 | `yazhi` 让球，`ouzhi` 胜平负，`daxiao` 大小球，`jiaoqiu` 角球。一行有 `company_name`、`initial` 初盘、`realtime` 即时盘。没有的组不画 |
| 数据 | `history` 里 `vs`、`home`、`away` 是数组，一行也是数组，按位置取联赛、日期、比分和队伍。`injury.home`、`injury.away` 用 `player_name`（没有时看 `name`、`player`）和 `reason` |
| 预测 | `forecast.rq` 让球（主、客），`dx` 大小（大、小），`sf` 胜平负（胜、平、负）。每份有 `pankou` 和 `betList`。`betvalue` 从 `1` 起对应选项顺序，`numPeople` 是人数。`intel` 是情报：`home_good`、`away_good`、`neutral`、`home_bad`、`away_bad` |

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
| `cityName` | 当前城市，还没设置时是空串。评论定位用这个值 |
| `token` | 登录令牌，30 天后失效 |

设置城市：

```json
POST /prod-api/user/city
{ "token": "登录令牌", "cityName": "上海" }
```

成功 `data` 仍是登录结果。最多 20 个字。发评论或改社区资料时如果带了 `cityName`，也会写进这里。

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
| `dailyLimit` | 这一轮奖池，元 |
| `claimedAmount` | 当前用户今天领到的金额，没领是 `0.00` |
| `remainAmount` | 奖池剩余，元 |
| `canClaim` | 已登录、今天还没领、奖池还有剩余时为 `true` |

`POST /prod-api/user/redpack/claim`，请求体 `{ "token" }`。成功后的 `data` 同上，`mine` 有值。今天领过返回「今日已领取」。

### 吉祥和 UED

页面只打说球帝自己的账号接口。把路径里的 `jxt` 换成 `ued` 就是 UED，请求体相同。绑定验证码是 4 到 6 位。

| 接口 | 请求体 | 成功时 `data` |
|---|---|---|
| `POST /prod-api/user/{jxt\|ued}/status` | `{ "token" }` | 绑定状态 |
| `POST /prod-api/user/{jxt\|ued}/sms` | `{ "token" }` | `null`。手机号用当前登录账号，不要再传 |
| `POST /prod-api/user/{jxt\|ued}/bind` | `{ "token", "code" }` | 绑定状态 |
| `POST /prod-api/user/{jxt\|ued}/unbind` | `{ "token" }` | 绑定状态 |
| `POST /prod-api/user/{jxt\|ued}/withdraw` | `{ "token", "amount" }` | 绑定状态，另有 `tradeNo`。`amount` 是元，最多两位小数，最低 10 |

绑定状态：

| 字段 | 说明 |
|---|---|
| `isBind` | `"1"` 已绑定，`"0"` 未绑定 |
| `account` | 对方用户名打码。未绑定是空串 |
| `balance` | 说球帝余额，元，两位小数 |
| `withdrawn` | 这个平台累计提现成功的金额，元，两位小数 |

未登录是「请先登录」。提现还没绑定时，吉祥是「请先绑定吉祥账号」，UED 把「吉祥」换成「UED」。

说球帝页面不要调用 `POST /prod-api/user/bound-login`。绑定成功后，吉祥令牌只留在说球帝。职圈打开吉祥体育时，由职圈服务器来取这个令牌。令牌过期要重新验证。页面不会因此变成吉祥的登录态。细节在 [吉祥转账对接](jxt.md)。

提现 `msg` 可以直接提示：

| `msg` | 含义 |
|---|---|
| 请重新验证吉祥账号 | 没有对方令牌，或令牌已过 30 天。UED 把「吉祥」换成「UED」 |
| 提现金额不正确 | 不是金额，或超过上限，或小数超过两位 |
| 提现金额不能小于10元 | 低于 10 元 |
| 余额不足 | 说球帝余额不够 |
| 还没有绑定吉祥账号 | 解除绑定时还没绑。UED 同样把名字换成 UED |
