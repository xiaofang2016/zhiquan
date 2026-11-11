# 接口

页面对接以 [说球帝frontend.md](说球帝frontend.md) 为准。本文是网关、聊天和播放栏目的字段说明。

比赛、即时比分、登录和注册挂在同一个网关。只读接口不需要登录。聊天在另一个地址，见文末。资料库页面走公共赛事接口，不走下面的 `/prod-api/database/`。

基址：`https://dgtplaoir00la.cloudfront.net`

比赛、即时比分、资料库用 `GET`。登录、注册、验证码、恢复登录用 `POST`，路径仍然是 `/prod-api/...`。

| 路径 | 应答 |
|---|---|
| `POST /prod-api/user/sms` `/register` `/login` `/session` | 账号 |
| `POST /prod-api/user/gift`、`/predict`、`/predict/bet`、`/predict/rank`、`/wallet/sign`、`/phone/sms`、`/updatePhone` | 送礼、预测、现金签到、修改手机号 |
| `POST /prod-api/user/feedback`、`/user/feedback/list` | 意见反馈。要登录。列表最多 20 条，新的在前 |
| `POST /prod-api/applet/list` | 小程序目录。body `{ "client": "apk" }`，`client` 可以是 `apk`、`ipa`、`h5`，省略返回全部。不用登录 |
| `POST /prod-api/user/jxt/status` `/sms` `/bind` `/bind/password` `/unbind` `/withdraw` | 吉祥绑定和提现。短信见 [吉祥转账对接](jxt.md)。账号密码是 `/bind/password`，body `{ "token", "username", "password" }`，页面拿不到 `access_token` |
| `POST /prod-api/user/ued/status` `/sms` `/bind` `/bind/password` `/unbind` `/withdraw` | UED 绑定和提现，商户号 `10012`，其余与吉祥相同 |
| `POST /prod-api/user/bound-login` | 职圈取吉祥、UED 或说球帝直播令牌。浏览器不要调用，见 [吉祥转账对接](jxt.md) |
| `GET /prod-api/match/list/new2` | 即时比分 |
| `GET /prod-api/database/`、`/prod-api/team/`、`/prod-api/player/`、`/prod-api/manage/` | 资料库 |
| 其余 `GET /prod-api/`，例如联赛、日期、`/prod-api/match/list/new` | 比赛 |
| `GET /prod-api/chat/groups`、`/prod-api/chat/groups/{id}/messages` | 职圈群聊天，只读 |
| `GET /prod-api/goods/category/list`、`/goods/list`、`/goods/detail` | 红包商城只读，见 [说球帝frontend.md](说球帝frontend.md) |
| `POST /prod-api/chat/messages` | 播放页聊天发言（说球帝登录后） |
| `GET https://chat.kzbzcq.com/chat` | 播放页聊天室 |
| `GET https://live.kzbzcq.com/live` | 直播 |
| `GET https://index.kzbzcq.com/index` | 指数 |
| `GET https://data.kzbzcq.com/data` | 数据 |
| `GET https://tip.kzbzcq.com/tip` | 预测 |

浏览器跨域已放开。读接口允许 `GET`。账号接口允许 `POST` 和 `OPTIONS`，请求头要带 `content-type: application/json`。

读接口在这个域名上会压缩。请求带 `Accept-Encoding: gzip` 或 `br` 时，响应带对应的 `Content-Encoding`。即时比分第 1 页未压缩大约 0.6～0.9 MB，压缩后大约 120 KB。直接打原来的 Lambda 地址不会压缩。

播放页的直播、指数、数据和预测，以及聊天室，见文末。都走 `kzbzcq.com` 的子域名，经 CloudFront 再到对应函数。请用文档里的地址，不要直接请求函数地址。

## 通用约定

成功时 HTTP 200：

```json
{ "msg": "ok", "code": "0", "data": {} }
```

`code` 是字符串 `"0"`。先判断 `code`，再读 `data`。

参数或账号错误时 HTTP 400：

```json
{ "code": "400", "msg": "请输入正确的手机号", "data": null }
```

`msg` 是可以直接提示的中文。图片和直播地址都是完整的 `https://` 地址。队徽、联赛图标可能仍指向原站图床。

比赛列表里的 `token` 是原站留下的翻页字段，和登录令牌不是一回事。翻页不用把它传回去。

## 登录和注册

账号接口都是 `POST`，请求体是 JSON。没有退出接口，退出时把本地的 `token` 丢掉即可。只支持 11 位大陆手机号，不带 `+86`，不支持港澳台和海外号码。

手机号是 11 位大陆号码，`1` 开头，第二位是 `3` 到 `9`。不要带 `+86`。密码 6 到 20 位，前后空格也算在长度里。验证码是 6 位数字。

登录成功、注册成功、恢复登录，`data` 都是同一份：

```json
{
  "msg": "ok",
  "code": "0",
  "data": {
    "userId": "0c9a1e4b7d2f4a6e8b1c3d5f709182a4",
    "phone": "13800138000",
    "nickname": "138****8000",
    "avatar": "",
    "cityName": "",
    "token": "登录令牌"
  }
}
```

| 字段 | 说明 |
|---|---|
| `userId` | 用户 id，32 位，没有横线 |
| `phone` | 注册时的手机号 |
| `nickname` | 默认是打码后的手机号，例如 `138****8000` |
| `avatar` | 头像路径，可能是空串。相对路径拼在网关基址后面 |
| `cityName` | 当前城市，还没设置时是空串。评论定位用这个值 |
| `token` | 登录令牌。保存下来，恢复登录时原样传回。30 天后失效 |

设置城市是 `POST /prod-api/user/city`，body `{ "token", "cityName" }`。最多 20 个字。成功 `data` 仍是登录结果。发帖、发评论带了非空的 `cityName`，也会写进这里。这两处传空串只让这一条不带城市，不改账号。改帖带了城市时，帖子保存成功才写回账号。改资料仍按传入的 `cityName` 保存。

意见反馈 `POST /prod-api/user/feedback`，body `{ "token", "content", "contact", "images" }`。`content` 必填，最多 1000 字。`contact` 最多 64 字。`images` 最多 3 个地址。成功 `data` 是这一条：`content`、`contact`、`images`、`time`（`yyyy-MM-dd HH:mm:ss`，北京时间）。`POST /prod-api/user/feedback/list` 的 body 是 `{ "token" }`，`data.records` 最多 20 条，新的在前。

小程序 `POST /prod-api/applet/list`，body `{ "client": "h5" }`。`data` 是数组，每项 `appletId`、`appletName`、`appletTitle`、`appletImage`（空串，图标页面自备）、`appletUrl`、`program`、`idNum`。顺序是说球帝直播、H片、成人短剧、社球帝、UED体育官方网站、吉祥体育官方网站、活动红包、红包商城、ued专属网页。说球帝直播的 `appletTitle` 是「西甲赞助商，五大联赛，NBA，CBA 无延迟超高清观看」，其余标题和名字相同。`apk` 没有「说球帝直播」。`program` 为 `jxt`、`ued`、`wallet`、`mall` 时 `appletUrl` 是空串，走现有吉祥、UED、红包和商城入口，不要拼原站账号参数。`ued专属网页` 的地址是 `https://445d6dq.com`，这个站拒绝被嵌进页面，用新窗口打开。

### 发送验证码

`POST /prod-api/user/sms`

只用于还没注册的手机号。已注册再请求，返回「这个手机号已经注册」。

```json
{ "phone": "13800138000" }
```

成功时 `data` 为 `null`。验证码 5 分钟内有效，60 秒内不能重复获取。60 秒内再请求，返回「请稍后再获取验证码」。短信没发出去时返回「验证码发送失败」，后面可能带上简短原因。

### 注册

`POST /prod-api/user/register`

先发验证码，再用同一手机号提交。验证码只用一次。

```json
{
  "phone": "13800138000",
  "password": "123456",
  "confirm": "123456",
  "code": "483920"
}
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `phone` | 是 | 手机号。格式不对返回「请输入正确的手机号」 |
| `password` | 是 | 6 到 20 位。超出返回「密码需要 6 到 20 位」 |
| `confirm` | 是 | 再输入一次密码。不一致返回「两次输入密码不一致」 |
| `code` | 是 | 6 位验证码。没发过返回「请先获取验证码」，过期返回「验证码已过期」，不对返回「验证码不正确」 |

成功后直接是登录状态，`data` 里带 `token`。这个手机号已经注册时返回「这个手机号已经注册」。

### 登录

`POST /prod-api/user/login`

```json
{ "phone": "13800138000", "password": "123456" }
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `phone` | 是 | 手机号 |
| `password` | 是 | 密码，规则和注册相同 |

手机号没注册，或者密码不对，都返回「手机号或密码不正确」。成功时 `data` 带新的 `token`。

### 验证码登录

已注册的手机号可以用验证码登录。没注册返回「这个手机号还没有注册」。

1. `POST /prod-api/user/login/sms`，body `{ "phone" }`。成功 `data` 为 `null`。60 秒和 5 分钟规则与注册验证码相同。
2. `POST /prod-api/user/login/code`，body `{ "phone", "code" }`。成功 `data` 就是登录结果。

### 找回密码和修改密码

1. `POST /prod-api/user/password/sms`，body `{ "phone" }`。只发给已注册的手机号。
2. `POST /prod-api/user/password/reset`，body `{ "phone", "code", "password", "confirm" }`。成功 `data` 为 `null`，之后用新密码登录。
3. 已登录改密码：`POST /prod-api/user/password`，body `{ "token", "password", "confirm" }`。

### 账单、红包记录、提现记录

说球帝账号不要用职圈 `GET /wallet/records?merchantNo=`。余额流水在说球帝：

| 接口 | 请求体 |
|---|---|
| `POST /prod-api/user/wallet/records` | `{ "token", "kind", "page" }`。`kind` 为 `bill`、`redpacket` 或 `withdraw` |
| `POST /prod-api/user/withdraw/records` | `{ "token", "page" }`。等同 `kind=withdraw` |

`page` 从 1 起，每页 10 条。`data` 含 `page`、`size`、`total`、`records`。每条有 `tradeNo`、`kind`、`platform`（`jxt` / `ued`，红包为空）、`title`、`amount`（提现为负数）、`status`（`1` 成功，`2` 失败已退回）、`statusName`、`failReason`、`time`、`createdAt`。`kind` 为 `redpacket` 时另有全部成功记录的 `sentCount`、`sentAmount`、`receivedCount`、`receivedAmount`。扣款方向算发出，其余算收到。金额是元，两位小数。失败已退回的不计入。合计覆盖全部记录，不限当前页。

红包额度用 `POST /prod-api/user/redpack`，不要用职圈 `GET /redpacket/quota`。已登录时 `data` 另有 `dailyLimit`、`claimedAmount`、`remainAmount`、`canClaim`。`claims` 是这一轮的领取名单，先领的在前，每条有 `nick`、`avatar`、`amount`（元）、`time`。昵称和头像在领取时记下，更早的记录这两项可能是空串。没有锦鲤奖池、榜单和优惠券记录。每天这一份和帖子红包都会出现在 `kind=redpacket` 里，标题分别是「红包」和「帖子红包」。

帖子红包是说球帝自己发的，不走原站钱包。`POST /prod-api/community/posts/{postId}/redpacket`，请求体 `{ "token" }`。只出现在原站带红包标记的帖上，每帖 10 份、共 `10.00` 元，每个人每帖只能领一次。成功时 `data` 是这篇帖子，`redPacket.mine` 是领到的金额。领完返回「红包已领完」，这篇没有红包返回「这篇没有红包」。

### 现金签到

和任务中心的音浪签到分开。连续 7 天金额是 1、2、3、5、6、8、10 元，记入说球帝余额，账单 `kind=bill`、标题「签到」。断签或满 7 天后的下一次从第 1 天算。

| 接口 | 请求体 |
|---|---|
| `POST /prod-api/user/wallet/sign` | `{ "token" }`。只查询 |
| `POST /prod-api/user/wallet/sign/claim` | `{ "token", "day" }`。`day` 可不传。对不上当天那一格是「未到签到时间」。今天已签是「今日已签到」 |

`data` 有 `signDay`、`signed`、`amounts`、`today`（元，整数）、`canSign`、`balance`（元，两位小数）。领取成功另有 `amount`。

### 修改手机号

1. `POST /prod-api/user/phone/sms`，body `{ "token", "phone" }`。`phone` 是新号。验证码规则同注册。已注册返回「这个手机号已经注册」。与当前号码相同返回「新手机号不能和当前号码相同」。
2. `POST /prod-api/user/updatePhone`，body `{ "token", "phone", "code" }`。成功 `data` 是登录结果，`phone` 和 `token` 都是新的。旧令牌失效。余额、音浪、预测、吉祥和 UED 绑定跟着新号走。

### 送礼

`POST /prod-api/user/gift`，body `{ "token", "mid", "giftId", "count" }`。`count` 缺省 1，范围 1 到 99。单价取礼物目录的 `gift_yinlang`，从音浪余额扣除，明细标题以「送出」开头，`amount` 为负数。音浪不够是「音浪不足」。

成功 `data`：`balance`、`giftId`、`giftName`、`count`、`cost`、`content`。`content` 会作为这场聊天的一条普通消息，例如 `送出了「足球射门」×1`。聊天室已关闭时退回音浪，`msg` 为「当前聊天室已关闭」。

### 预测

下注扣音浪。盘口和赔率取下注当时 `tip` 的 `pankou`。大于等于 1 按欧洲赔率，返还为本金乘赔率，四舍五入到整数。小于 1 按水位，先加 1 再乘。让球、大小的四分之一盘把本金分两半。走水退本金。没猜中不退。完场用比赛详情的比分结算，打开下面两个查询时入账。推迟、取消、腰斩、待定、延期退回本金。

| 接口 | 请求体 |
|---|---|
| `POST /prod-api/user/predict/bet` | `{ "token", "mid", "type", "market", "betvalue", "stake", "clientId" }` |
| `POST /prod-api/user/predict` | `{ "token" }`。我的预测 |
| `POST /prod-api/user/predict/rank` | `{ "token", "period", "board" }` |

`market`：`rq` 让球（`betvalue` 1 主、2 客），`dx` 大小（1 大、2 小），`sf` 胜平负（1 胜、2 平、3 负）。`jq` 角球不开放，提交是「玩法不正确」。`type` 缺省 1。`stake` 为 1 到 100000 的音浪整数。`clientId` 可选，同一用户重复提交不重复扣款。

只有未开赛能下。进行中是「这场已经开始」，完场是「这场已经结束」，推迟、取消、腰斩、待定、延期是「这场不能投注」。赛况拿不到是「这场还不能投注」。

下注成功的 `data` 是一条预测，并带 `balance`。我的预测还有 `total`、`win`、`rate`、`profit`、`records`。`total` 不含未开奖和退回。`rate` 是胜场除以 `total`，百分数去掉小数。

一条预测：`id`、`mid`、`type`、`market`、`betvalue`、`label`、`line`、`odds`、`stake`、`status`、`statusName`、`returned`、`profit`、`placedAt`、`settledAt`、`league`、`home`、`away`、`kickoff`、`score`。`league` 是联赛名，`home`、`away` 是主客队，`kickoff` 是开赛时间。`score` 结算后是完场比分，例如 `1-1`，未开奖是空串。以前下的注这些字段是空串。`status` 为 `pending` 未开奖、`win` 赢、`lose` 输、`push` 走水、`half-win` 赢半、`half-lose` 输半、`refund` 退回。

收益榜 `period` 为 `day`、`week`、`month`，缺省 `day`，周从周一开始。`board` 为 `profit` 或 `roof`，缺省 `profit`。`profit` 按盈利从高到低，`roof` 按亏损从多到少。`records` 最多 50 条，字段 `rank`、`userId`、`nick`、`avatar`、`level`、`profit`、`win`、`total`。`avatar` 是账号头像，没有时是空串。`level` 是音浪等级：余额不足 100 为 1，否则是余额除以 100。这不是原站的等级名。`mine` 是自己的名次，没有结算记录时 `rank` 为 0，`nick` 是当前账号的昵称。

社球帝的评论、发帖、打赏、用户主页、通知和上传都在 [说球帝frontend.md](说球帝frontend.md) 的社球帝一节。评论和回复的 `likeCount` 不会小于原站。回复某一条回复时带 `replyToReplyId`。自己发的帖和评论会计入用户主页。公告从圈子详情和话题详情抓来；原站这两处目前是空的。发帖视频填已经能播的地址，不做转码。

### 恢复登录

`POST /prod-api/user/session`

打开页面时用上次保存的令牌换回用户资料。令牌不会因此续期。

```json
{ "token": "登录令牌" }
```

令牌缺失、对不上或用户已经不存在，返回「请先登录」。超过 30 天返回「登录已过期」。成功时 `data` 仍是登录结果，`token` 就是请求里的这一份。

## 比赛

给比赛首页用。路径、参数和成功返回与 kzb001.net 的 `/prod-api` 一致。只读，不需要登录。

今天的比赛每 2 分钟抓一次，顺带更新联赛和日期。上海时间 0 点到 6 点，同一轮再抓昨天的全部场次。未来日期每 30 分钟抓一次。过去 7 天每天 04:00（上海）再抓一遍全部场次。`total` 会跟着原站变。今天卡片上的实时比分、分钟、半场和角球用下面的即时比分，不要等这份列表刷新。昨天和更早的赛果用这一天的 `list/new`，里面是开赛日落在这一天的全部比赛。

`/prod-api/match/detail`、阵容、伤停、球员、指数这次没有提供。

### 联赛

`GET /prod-api/category/list?type=0&isoften=1&ishot=1&langtype=zh`

查询参数可以照原站带上，服务端返回同一份常看热门联赛。

```json
{
  "msg": "ok",
  "code": "0",
  "data": {
    "twoCategoryList": [
      {
        "id": 1,
        "type": 2,
        "name": "NBA",
        "name_zh": "NBA",
        "logo": "https://cdn.sportnanoapi.com/basketball/competition/061f5ab5b1a975fc1004529ec3f11fca.png"
      }
    ]
  }
}
```

| 字段 | 说明 |
|---|---|
| `id` | 联赛 id，对应比赛里的 `cid` |
| `type` | `1` 足球，`2` 篮球 |
| `name` / `name_zh` | 名称 |
| `logo` | 联赛图标 |

`id` 会重复。NBA 和世界杯都是 `id=1`，要用 `id` 加 `type` 才能区分。筛比赛时把这里的 `id` 传给列表的 `cid`，把 `type` 传给列表的 `type`。

### 日期

`GET /prod-api/match/date/count?type=-1&cid=0`

不带 `type`、`cid`、`teamId`，或 `type` 小于等于 0、`cid` 为 0、`teamId` 为 0，并且 `zhuboType` 不是 `1` 时，`totalmatch` 是上游日期接口的数字。带了 `type`、`cid`、`teamId`，或 `zhuboType=1` 时，按这一天已经抓到的场次重算。`cid` 可以逗号分隔多个，规则和比赛列表一样。`zhuboType=1` 只数有直播线路的场次。

```json
{
  "msg": "ok",
  "code": "0",
  "data": [
    { "matchdate": "2026-09-23", "totalmatch": 43 }
  ]
}
```

日期从早到晚。日历上的 `matchdate` 作为列表的 `starttime`。

### 比赛列表

`GET /prod-api/match/list/new`

```text
/prod-api/match/list/new?isfanye=1&type=0&cid=0&ishot=-1&langtype=zh&pid=4&zoneId=Asia/Shanghai&zhuboType=0&starttime=2026-10-23&pn=1&ps=20
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `starttime` | 是 | `yyyy-MM-dd`。缺失返回 400，「缺少必传请求参数」。格式不对返回「starttime 格式应为 yyyy-MM-dd」。和原站同一个 `starttime` 的那一份列表 |
| `pn` | 否 | 页码，从 1 开始。缺省或小于 1 时按 1。最大 500 |
| `ps` | 否 | 每页条数，默认 20，范围 1 到 200。超出返回 400，「ps 必须在 1 到 200 之间」 |
| `type` | 否 | `0` 或不传为全部。`1` 足球，`2` 篮球，`3` 娱乐 |
| `cid` | 否 | 联赛 id。`0` 或不传为全部。多个用逗号，例如 `82,120`，最多 20 个，重复的只留一个。要配合 `type`，因为不同运动的联赛 id 会重复。超过 20 个返回 400，「cid 最多 20 个」。格式不对返回「cid 格式不对」 |
| `teamId` | 否 | 球队 id。不传或 `0` 不筛。主队 `hteam_id` 或客队 `ateam_id` 对上就留下。格式不对返回 400，「teamId 格式不对」 |
| `ishot` | 否 | `-1` 或不传为全部。`1` 只留 `isHot` 为 `1` 的场次，置顶段 `topList` 例外，里面 `isHot` 为 `0` 的场次也返回。顺序仍是抓取时的位置 |
| `zhuboType` | 否 | `1` 只留有直播线路的场次。名字是「动画」，或地址里有 `nami.com`、`livetracker`，不算。`live_urls`、`mirror_live_urls`、`global_live_urls`、`live_cartoon_url` 里有一路直播就算。`0` 或不传不筛 |
| `live_type` | 否 | 原样回在 `data.live_type`，缺省为 `0` |
| `since` | 否 | 上次响应里的 `rev`。列表没变时只返回空的 `changed`。对不上时仍返回整页 |
| `isfanye` `langtype` `pid` `zoneId` `token` | 否 | 可以照原站传，服务端不使用 |

```json
{
  "msg": "ok",
  "code": "0",
  "data": {
    "total": 3398,
    "totalPage": 170,
    "notopList": [],
    "dataList": [],
    "topList": [],
    "live_type": 0,
    "starttime": "2026-10-23",
    "currentPage": 1,
    "token": "f475aa3c723da4152cb4a22885211a4e",
    "rev": "f475aa3c723da4152cb4a22885211a4e"
  }
}
```

| 字段 | 说明 |
|---|---|
| `total` | `dataList` 在该筛选下的总条数，不含置顶 |
| `totalPage` | 按 `ps` 算出的页数。没有比赛时为 `0` |
| `currentPage` | 当前 `pn` |
| `starttime` | 请求的日期 |
| `notopList` | 置顶，只在第 1 页返回。后面的页是空数组 |
| `topList` | 另一组置顶，同样只在第 1 页返回 |
| `dataList` | 当前页比赛，最多 `ps` 条 |
| `token` | 保留字段，不是登录令牌 |
| `rev` | 这一页列表的版本。轮询时作为 `since` 传回 |

带 `since` 并且和当前 `rev` 相同，不再返回三个列表：

```json
{
  "msg": "ok",
  "code": "0",
  "data": {
    "rev": "f475aa3c723da4152cb4a22885211a4e",
    "changed": [],
    "removed": []
  }
}
```

`changed` 和 `removed` 都空时，页面不用改。`since` 对不上时响应变回整页，记下新的 `rev` 再继续轮询。

翻页：第 1 页按 `topList`、`notopList`、`dataList` 的顺序接起来。从第 2 页起只把 `dataList` 接在后面，置顶不要重复画。`pn` 大于 `totalPage` 时三个列表都是空的，`total` 不变。

没有这场日期的数据时，`total` 为 `0`，三个列表为空。

每次抓取都会记下这场在原站列表里的位置。三个列表都按这个位置返回，接口不再按开赛时间、联赛或在线人数重排。多个 `cid` 也按这个位置拼在一起。今天抓取用的是上游带主播的名单，最多比原站晚约 2 分钟。

热门页在这个顺序上再做一次和原站相同的调整：`is_zd` 为 `1` 的场次抽到最前，按 `zd_level` 从高到低排，其余场次保持原顺序。足球、篮球分区不做这一步。开赛钟点按用户浏览器时区显示，接口里的 `matchtime` 仍是上海时间。

今天：原站后面几页会带上后面几天的场次，这里也保留，不按开赛日再切，也不把一场比赛从另一个日期的列表里拿走。从昨天往前，以及从明天起，这一天改抓全部场次，只留下开赛日就是这一天的。不传 `zhuboType` 或传 `0` 时，赛果按日期查 `list/new` 就是这一天的全部比赛，没有主播的场次也在里面。传 `zhuboType=1` 时只留有直播线路的。同一天过一会儿再查，`total` 会变，因为原站还在更新，抓取会跟着补。

球队 id 和有没有直播线路，在抓取时写到这场上。还没再次抓过的场次没有这两项：`teamId` 选不中；`zhuboType=1` 先留着，下一轮抓取后再按线路去掉。今天大约 2 分钟内会补上，昨天和过去 7 天在上海时间 04:00。更早的日期要等再次抓取。

### 比赛对象

三份列表里的元素都是原站比赛对象。字段随 `type` 变化，没有的字段不要当成空字符串去显示。即时比分里的场次也是这一份，另外多了分钟、牌、赔率，见即时比分。

| 字段 | 说明 |
|---|---|
| `id` | 比赛 id |
| `type` | `1` 足球，`2` 篮球，`3` 娱乐 |
| `cid` | 联赛 id |
| `name` | 联赛名 |
| `cname` | 联赛英文名，可能为空 |
| `clogo` | 联赛图标 |
| `matchtime` | `yyyy-MM-dd HH:mm:ss`，上海时区 |
| `status_up` | 状态码 |
| `status_up_name` | 状态文案，例如「未开赛」「上半场」「下半场」。个别篮球场次这里会是数字，界面优先用能显示的文案 |
| `hteam_name` / `ateam_name` | 主队、客队 |
| `hteam_logo` / `ateam_logo` | 队徽 |
| `hteam_id` / `ateam_id` | 球队 id |
| `score` | 比分，如 `1-0`。娱乐场次可能没有 |
| `isHot` | `1` 热门，`0` 不是。热门列表用这个字段筛未置顶和普通段。置顶段里 `isHot` 为 `0` 的场次仍会返回 |
| `is_zd` / `zd_level` | 原站热门页的置顶等级。`is_zd` 为 `1` 时按 `zd_level` 从高到低排到热门最前面。不要拿 `is_zd` 代替 `isHot` 做筛选 |
| `online_num` | 在线人数 |
| `live_urls` | 直播和动画地址 |
| `live_cartoon_url` | 与 `live_urls` 同类，按原站字段保留 |
| `mirror_live_urls` / `global_live_urls` | 镜像和全球线路，结构同 `live_urls` |

足球还会有 `banchang`、`jiaoqiu`、`time`、红黄牌。篮球还会有 `home_score_xiaojie`、`away_score_xiaojie`。娱乐场次用 `title`，经常没有 `score`。

`live_urls` 里每一路：

| 字段 | 说明 |
|---|---|
| `name` | 线路名。动画线路一般是「动画」 |
| `url` | 播放地址。直播目前给的是 `.flv`。同一地址把后缀换成 `.m3u8` 就是这一路的 HLS，iOS 用这个。动画是网页地址 |
| `status` | `1` 可播 |
| `index` | 排序 |
| `streamId` `live_user_name` `liveUserId` `room_num` | 直播间信息。动画线路通常没有这些字段。没有主播头像 |

播放时用 `url`。不要把 `.flv` 当成文件下载。`mirror_live_urls`、`global_live_urls` 现在和 `live_urls` 内容相同，还没有单独的镜像线路。

### 原站只读

下面这些路径原样转发 `kzb001.net` 的同一地址，查询参数照原站传。成功时正文就是上游的 JSON。上游失败时 HTTP 502，`msg` 为「上游暂时不可用」。成功响应缓存 60 秒。资料库不在这里。

| 路径 | 用途 |
|---|---|
| `GET /prod-api/gift/getGiftList` | 礼物目录 |
| `GET /prod-api/bobble/getBobbleList` | 气泡目录 |
| `GET /prod-api/match/news/information/list` | 比赛资讯列表 |
| `GET /prod-api/match/news/information/related/list` | 这场的相关资讯 |
| `GET /prod-api/match/detail/tabs` | 阵容页，含阵型、站位、评分 |
| `GET /prod-api/match/detail/lineupAnalysis` | 阵容分析 |
| `GET /prod-api/match/detail/playersStatSeason` | 本赛季球员数据 |
| `GET /prod-api/match/detail/seasonData` | 篮球常规赛关键数据。上游没有时 `data` 为 `null`，页面显示「暂无常规赛关键数据」，不要编字段 |
| `GET /prod-api/match/detail/player` | 阵容里点球员的本场数据。参数照原站：`type`、`mid`、`playerid`、`teamtype`（主队 1、客队 2） |
| `GET /prod-api/data/lanqiu/ranklist` | NBA 榜单。`ranktype=1` 球队、`2` 球员、`3` 伤病、`4` 日榜 |
| `GET /prod-api/search/hot` | 热门搜索词 |
| `GET /prod-api/search/content` | 搜索。查询参数照原站传，至少要 `key`、`pid`、`tab`。`zoneId` 会改回包里比赛时间的时区，页面固定传 `Asia/Shanghai`。带了说球帝 `token` 时，这次搜索词记在账号上。带令牌的这一次不缓存 |
| `GET /prod-api/get/search/history` | 热词和这个账号的搜索词。参数 `pid`、`tab`，登录再带 `token` |
| `GET` 或 `POST /prod-api/clear/search/history` | 清空这个账号的搜索词。参数同上一行 |
| `GET /prod-api/news/list` | 资料库资讯列表 |
| `GET /prod-api/news/detail` | 资料库资讯详情。`newsid` 用资料库列表里的 `id` |
| `GET /prod-api/news/information/detail` | 比赛资讯和相关资讯的详情。`id` 用比赛资讯列表里的 `id` |
| `GET /prod-api/at/list` | 原站比赛聊天的推广卡。不要用。里面是原站注册页和别的聊天 App。页面继续用现在写死的两张 |

搜索历史的 `data.hotSearchHistory` 是上游热词。`userSearchHistory` 是说球帝账号自己的词，新的在前，最多 20 个，每项只有 `key`。没带令牌或令牌无效时这一列是空的，热词照常。上游热词拿不到时，这两条仍是 HTTP 200，`hotSearchHistory` 为空数组，账号的词照常返回；清空也照样清掉账号的词。历史和清空不缓存。带令牌的 `/search/content` 也不缓存，记词失败仍返回搜索结果。令牌不会转给上游。

比赛搜索 `tab=1` 时，上游后面几页经常是空的，`total` 和 `totalPage` 仍写着还有。这时改成第一页的实际条数，`totalPage` 为 1。别的 `tab` 不动。

比赛资讯详情的正文在 `content`，封面是 `coverurl`。拿这个 `id` 去查 `/news/detail?newsid=` 会得到空对象。没有点赞和评论。列表里没有前瞻视频，这一排留空，不要编地址。`itemType` 为 1 的是动图，没有 `id`，`gif` 和 `cover` 就在这一项上。

## 即时比分

给比分页和比赛卡片上的实时比分用。只读，不需要登录。数据大约每分钟更新一次，请求进来时不再访问上游。

只有当天的即时比分，没有历史日期列表。联赛、日期和全量赛程用上面的比赛接口。

响应带 `cache-control: no-store`。这条路径在入口上不缓存，页面自己决定多久再请求一次。

### 即时比分列表

`GET /prod-api/match/list/new2`

```text
/prod-api/match/list/new2?type=1&cid=0&ishot=-1&pn=1&ps=20
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `pn` | 否 | 页码，从 1 开始。缺省或小于 1 时按 1。最大 500 |
| `ps` | 否 | 每页条数，默认 20，范围 1 到 200。超出返回 400，「ps 必须在 1 到 200 之间」 |
| `type` | 否 | `0` 或不传为全部。`1` 足球，`2` 篮球，`3` 娱乐 |
| `cid` | 否 | 联赛 id。`0` 或不传为全部。要配合 `type`，因为不同运动的联赛 id 会重复 |
| `ishot` | 否 | `-1` 或不传为全部。`1` 只看热门。`0` 只看非热门 |
| `starttime` | 否 | `yyyy-MM-dd`。传了就只留 `matchtime` 落在这一天的场次。格式不对返回 400，「starttime 格式应为 yyyy-MM-dd」。不传返回当前整板 |
| `live_type` | 否 | 原样回在 `data.live_type`，缺省为 `0` |
| `since` | 否 | 上次响应里的 `updatedAt`。相同就只返回空的 `changed`。比分有更新时只返回变过的场次。对不上时仍返回整页，用来重新对齐 |
| `isfanye` `langtype` `pid` `zoneId` `zhuboType` | 否 | 可以照原站传，服务端不使用 |

```json
{
  "msg": "ok",
  "code": "0",
  "data": {
    "total": 3043,
    "totalPage": 153,
    "notopList": [],
    "dataList": [],
    "overList": [],
    "topList": [],
    "live_type": 0,
    "starttime": "",
    "currentPage": 1,
    "otherList": [],
    "token": "a9809dfc2c2dd82265264a7b8566d40f",
    "updatedAt": 1792839202471
  }
}
```

| 字段 | 说明 |
|---|---|
| `total` | `dataList` 在该筛选下的总条数。进行中、完场、推迟不计入 |
| `totalPage` | 按 `ps` 算出的 `dataList` 页数。没有未开赛场次时为 `0` |
| `currentPage` | 当前 `pn` |
| `starttime` | 请求里的日期。没传是空字符串 |
| `notopList` | 进行中的一组，只在第 1 页返回。后面的页是空数组 |
| `topList` | 进行中的另一组，同样只在第 1 页返回 |
| `dataList` | 未开赛，当前页最多 `ps` 条 |
| `overList` | 完场，整份都在第 1 页，不随 `pn` 再翻 |
| `otherList` | 推迟、取消、待定，同样只在第 1 页 |
| `token` | 保留字段，不是登录令牌 |
| `updatedAt` | 这块比分板的更新时间，毫秒。轮询时作为 `since` 传回 |

带 `since` 并且还能对上时，不再返回上面五个列表：

```json
{
  "msg": "ok",
  "code": "0",
  "data": {
    "updatedAt": 1792839202471,
    "changed": [],
    "removed": []
  }
}
```

| 字段 | 说明 |
|---|---|
| `changed` | 比分、状态、分钟、半场、角球、红黄牌或即时赔率有变化的场次，只带这些字段和 `id` |
| `removed` | 这次从比分板上消失的比赛 id |

`changed` 和 `removed` 都空时，页面不用改。把 `changed` 按 `id` 盖到已有场次上，`removed` 从页面去掉。`since` 落后超过一块比分板时，响应变回整页，记下新的 `updatedAt` 再继续轮询。

第 1 页要同时画五个列表。从第 2 页起只把 `dataList` 接在未开赛后面，进行中、完场、推迟不要重复画。`pn` 大于 `totalPage` 时 `dataList` 为空，`total` 不变，另外四个列表也是空的。

| 栏目 | 用哪些列表 |
|---|---|
| 进行中 | `notopList` 和 `topList` |
| 未开赛 | `dataList`，需要翻页 |
| 完场 | `overList` |
| 推迟 / 取消 / 待定 | `otherList` |

场次字段见上面的比赛对象。即时比分还会用到：

| 字段 | 说明 |
|---|---|
| `time` | 进行到的分钟，如 `"52"`。未开赛经常是空的 |
| `banchang` | 半场比分，如 `0-1`。足球场次才有 |
| `jiaoqiu` | 角球，如 `1-8`。足球场次才有 |
| `hteam_red` / `ateam_red` | 主客红牌数 |
| `hteam_yellow` / `ateam_yellow` | 主客黄牌数 |
| `home_score_xiaojie` / `away_score_xiaojie` | 篮球各节比分数组 |
| `yazhi_jishi` | 即时亚盘，逗号分隔，如 `0.95,-1.25,0.85` |
| `ouzhi_jishi` | 即时欧赔，逗号分隔，如 `17,9,1.07` |
| `daxiao_jishi` | 即时大小，逗号分隔，如 `0.87,3.25,0.92` |

`status_up_name` 常见文案：

| 文案 | 含义 | 出现在 |
|---|---|---|
| 上半场、下半场、中场 | 进行中 | `notopList`、`topList` |
| 未开赛 | 还没开始 | `dataList` |
| 完场 | 已结束 | `overList` |
| 推迟、取消、待定 | 不会按正常时间打 | `otherList` |

个别篮球场次的 `status_up_name` 会是数字。界面优先用能直接显示的文案，数字可以改看 `status_up`。

`score` 形如 `1-0`。未开赛经常是 `0-0` 或空。界面上未开赛可以显示 VS，进行中和完场显示比分。

## 资料库

这一节是网关上的旧资料库。页面已经改接 [说球帝frontend.md](说球帝frontend.md) 里的公共赛事接口。

路径、参数和成功返回与 kzb001.net 的 `/prod-api` 一致。只读，不需要登录。请求进来时不再访问上游。联赛、日期和赛程仍用比赛接口。卡片上的实时比分用即时比分。

文档还没抓到时也是 HTTP 200，`data` 为 `null`，并带 `cache-control: no-store`。这不是 404，过一会儿再请求。已经落盘的响应缓存 60 秒。

```json
{ "msg": "ok", "code": "0", "data": null }
```

`langtype` 可以不传，不传按 `zh`。`token` 会被丢掉。参数顺序不影响结果。

联赛身份是 `id` 加运动类型。NBA 和世界杯都是 `id=1`，足球是 `sportsType=1` 或 `type=1`，篮球是 `2`。只拿 `id` 会串数据。

当前赛季不要自己猜年份。先读年份列表，用 `is_current` 为 `1` 的 `year`，例如 `2026-2027`。用错年份，积分榜会是空的。

下面这些由后台定时写入，页面打开不会触发补抓。每天 04:30（上海）刷目录、当前赛季和 FIFA 最新榜，每 15 分钟只刷新正在进行或刚结束的联赛。往年赛季抓过一次就不再更新。

球员、球队详情、某一轮赛程、球队赛程、最佳阵容、教练、FIFA 国家队某一期、荣誉列表，第一次打开可能是 `data: null`。这次访问会登记补抓。常见是一分钟内有正文，冷门的球队、球员页要一两分钟，之前慢的时候到过 3 分钟。一个页面会同时打好几个这样的接口，各 Tab 是陆续出来的。已经有的球队和球员页超过 24 小时会在后台更新，当次仍返回旧正文。

往年积分榜、NBA、CBA 往年排名不在这次打开时补抓。后台按联赛慢慢抓，抓到之前一直是 `data: null`。当季的积分榜和排名是有的。

### 资料库首页

`GET /prod-api/database/home`

热门、足球分区、篮球分区都在这一份里。

```json
{
  "msg": "ok",
  "code": "0",
  "data": {
    "hotList": [
      {
        "id": 1,
        "sportsType": 2,
        "name": "NBA",
        "logo": "https://cdn.sportnanoapi.com/basketball/competition/061f5ab5b1a975fc1004529ec3f11fca.png",
        "categoryName": "美洲",
        "ishot": 1
      }
    ],
    "fbList": [],
    "bbList": [],
    "bbNewList": []
  }
}
```

`hotList` 是顶部热门赛事。点进去用 `id` 和 `sportsType`。

`fbList`、`bbNewList` 是分区。每一区：

| 字段 | 说明 |
|---|---|
| `categoryName` | 分区名，例如「欧洲」「国际」 |
| `categoryId` | 分区 id |
| `importantCompetitionList` | 这一区靠前的联赛。项里有 `id`、`name`、`logo`、`sportsType` |
| `countryList` | 国家。里面的 `competitionList` 是该国家下的联赛，字段同上 |

`bbList` 是篮球联赛的平铺列表，项里有 `id`、`name`、`logo`、`sportsType`、`categoryName`。

### 赛季

足球：

```text
/prod-api/team/getFbYearListByCompetitionId?competitionId=82
```

篮球：

```text
/prod-api/team/getBbYearListByCompetitionId?competitionId=1
```

| 参数 | 必填 | 说明 |
|---|---|---|
| `competitionId` | 是 | 联赛 id |

```json
{
  "msg": "ok",
  "code": "0",
  "data": [
    { "year": "2026-2027", "is_current": 1, "start_time": 1791043200000, "end_time": 1807545600000 }
  ]
}
```

`is_current` 为 `1` 的那条就是当前赛季。后面的积分榜、对阵、轮次都用这个 `year`。

### 联赛资料

英超是 `type=1`、`cid=82` 或 `competitionId=82`。NBA 是 `type=2`、`cid=1`。

| 接口 | 参数 | 用途 |
|---|---|---|
| `/prod-api/team/getCompetitionInfo` | `type` `cid` | 联赛名和图标。`cname`、`logo`、`cid`、`type` |
| `/prod-api/team/getHonorHistory` | `type` `cid` | 冠军列表。`teamName`、`teamLogo`、`teamId`、`season_year`、`title_zh` |
| `/prod-api/team/getLatelyMatchInfo` | `type` `cid` | 最近一场。`homeTeamName`、`awayTeamName`、`homeTeamLogo`、`awayTeamLogo`、`matchTime`、`matchId` |
| `/prod-api/team/getCompetitionStageList` | `type` `cid` | 阶段。见下面 |
| `/prod-api/team/getTeamRankByTeamId` | `type` `competitionId` `year` | 当前或往年积分榜。主场再加 `isHome=1`，客场加 `isHome=0`。不传是总积分 |
| `/prod-api/team/getCompetitionBracket` | `type` `cid` `year` | 淘汰赛对阵。联赛可能没有对阵 |
| `/prod-api/team/getMatchListByCid` | `type` `cid` `year` `stageId` `roundNum` | 某一轮赛程 |
| `/prod-api/team/getFbCompetitionStats` | `type` `cid` `year` | 足球轮次名，在 `data.nameZh`。最佳阵容用这些名字 |
| `/prod-api/team/getFbCompetitionBestLineup` | `type` `cid` `year` `stats` | 最佳阵容。`stats` 是轮次名，例如 `第9轮`。缺了 `stats` 不会去补抓 |
| `/prod-api/team/getMatchList` | `type` `teamId` `year`，`competitionId` 可选 | 这支球队这一季的赛程。不传 `competitionId` 是全部赛事。第一次打开是 `null`，按需补抓后才有 |

阶段列表示例，英超当前只有一个阶段：

```json
{
  "stageId": 235026,
  "stageName": "联赛",
  "roundCount": 38,
  "curRound": 8,
  "isCurStage": 1
}
```

`isCurStage` 为 `1` 的是当前阶段。赛程用它的 `stageId` 和 `curRound` 作为 `roundNum`。

积分榜：

```text
/prod-api/team/getTeamRankByTeamId?type=1&competitionId=82&year=2026-2027
```

```json
{
  "msg": "ok",
  "code": "0",
  "data": {
    "isLiansai": 1,
    "rankList": [
      {
        "position": 1,
        "id": 10181,
        "teamName": "曼彻斯特城",
        "logo": "https://cdn.sportnanoapi.com/football/team/c4a6528a2ee147b99c9885ef24385a4e.png",
        "points": 21,
        "total": 7,
        "sdf": "7/0/0",
        "io": "19/7",
        "promotionName": "欧冠正赛"
      }
    ]
  }
}
```

`sdf` 是胜/平/负，`io` 是进/失球。杯赛的 `rankList` 可能是空的。足球当前赛季的总积分、主场、客场各抓一份。主场是 `isHome=1`，客场是 `isHome=0`，积分只算这一边。页面不读这里，主客场按公共赛事接口那一季的赛程自己算。

近期赛事：

```text
/prod-api/database/competition/notStarted?pid=4
```

每天抓一份。`data` 是联赛数组，有 `name`、`logo`、`matchCount`、`sportsType`、`shortName`、`cid`。

教练。第一次打开可能是 `data: null`，这次访问会登记补抓：

```text
/prod-api/manage/getManageInfoByManageId?type=1&manageId=8084
/prod-api/manage/queryFManageHistoryByManageId?type=1&manageId=8084
/prod-api/manage/queryFManageHonorByManageId?type=1&manageId=8084&orderType=1
```

荣誉缺了 `orderType` 不会去补抓。

NBA 不用上面这张积分榜，用东西部：

```text
/prod-api/team/getNbaTeamRank?rankType=1&year=2026-2027
/prod-api/team/getNbaTeamRank?rankType=3&year=2026-2027
```

`rankType=1` 的 `data` 里有 `east` 和 `west`。每一项：`position`、`teamId`、`teamName`、`logo`、`ressult`（胜/负）、`wonRate`、`streaks`、`gameBack`。

CBA（`id=3`、`type=2`）：

```text
/prod-api/team/getCbaTeamRank?year=2026-2027
```

### 热门赛事

```text
/prod-api/team/getFbHotCompetition
```

`data` 是联赛数组，不是球队排名。项里有 `competitionId`、`competitionName`。点进某一项后，仍走上面的年份和积分榜。

### FIFA

俱乐部榜每天更新，`rankType` 有 `0` 和 `1` 两份：

```text
/prod-api/team/getFifaRankClub?rankType=0
```

`data` 是数组：`teamId`、`teamName`、`teamLogo`、`points`、`marketValue`。

国家队先拿发布日期，再拿对应一期。男队、女队各一组。

```text
/prod-api/team/getFifaRankMemTime
/prod-api/team/getFifaRankWomenTime
```

```json
{
  "msg": "ok",
  "code": "0",
  "data": [
    { "pub_date": "2026-10-07", "pub_time": 1791302400 }
  ]
}
```

日期从新到旧。页面用第 0 条的 `pub_time`，参数名是 `pubTime`，再加 `regionId=0`：

```text
/prod-api/team/getFifaRankMem?pubTime=1791302400&regionId=0
/prod-api/team/getFifaRankWomen?pubTime=1791302400&regionId=0
```

`data` 是数组：`teamId`、`teamName`、`teamNameEn`、`countryLogo`、`points`、`positionChanged`。`positionChanged` 是名次变化，`0` 表示没变。

历史日期可以按同一个 `pubTime` 来取。最新一期每天会刷新，更早的期次抓过就不再变。

### 球队和球员

这些没有预抓。第一次请求会返回 `data: null`，同时登记补抓。

| 接口 | 说明 |
|---|---|
| `/prod-api/team/getTeamBase` `/prod-api/team/getTeamInfo` `/prod-api/team/getTeamData` | 球队资料 |
| `/prod-api/team/getTeamList` `/prod-api/team/getTeamAndPlayer` | 球队列表、球队和球员 |
| `/prod-api/team/getPlayer` 开头 | 球员 |
| `/prod-api/team/getCompetitionByTeam` `/prod-api/team/getCompetitionYearByTeam` | 球队参加过的赛事和赛季 |
| `/prod-api/player/` 开头 | 球员页 |

参数照原站传。`type` 或 `sportsType` 要和球队、球员所在的运动一致。

## 职圈群聊天

首页顶栏没有「聊天」。职圈群接口仍在，走上面的网关，不是播放页聊天室。只读，不需要登录。响应 `cache-control: no-store`。

```text
GET /prod-api/chat/groups
GET /prod-api/chat/groups/{groupId}/messages
GET /prod-api/chat/groups/{groupId}/messages?before={排序键}
```

`groupId` 是 1 到 18 位数字。`before` 必须是这个群自己的消息排序键，形如 `group#{gid}#msg#...`，否则当作从最新一页开始。

群列表 `data`：

| 字段 | 说明 |
|---|---|
| `partition` | 库里的分区，例如 `apk#5739998` |
| `imageBase` | 图片根地址。空或 `oss.imnono.net` 时页面改用 `https://imfile.dtthsp.com` |
| `groups` | 按最新一条消息的时间从新到旧 |

每个群有 `id`、`gid`、`name`、`ownerName`、`updatedAt`、`notice`、`avatar`、`latest`。`avatar` 是群头像文件名，没有则为空。`notice` 在原站 `enabled` 为 `0` 时是空字符串。`latest` 没有消息时为 `null`，否则和下面的消息行一样。

群名单在接口进程里缓存大约 45 秒。这期间群名、公告、头像可能还是上一份。消息正文不走这份缓存，点进群和翻页仍直接读库。不存在的群立刻返回空列表。

消息页 `data` 有 `id`、`gid`、`name`、`imageBase`、`messages`、`older`。`messages` 从旧到新，最多 100 条。扫到头时 `older` 为空；否则把 `older` 当作下一次的 `before`。单条内容正好是「领取了红包」「已领取了红包」或以此结尾的不返回。「等12/200人已领取了红包」这种汇总会返回。

一条消息：

| 字段 | 说明 |
|---|---|
| `time` | 毫秒 |
| `nick` | 发送人昵称 |
| `sendUid` | 发送人编号 |
| `type` | 字符串。`0` 文字，不要写成空字符串。`1` 单图文件名，`10` 红包 JSON，`11` 回复，`12` @，`13` 多图，`90` 系统提示，`91` 撤回 |
| `content` | 原文，最长 2000 字 |
| `id` | 排序键，翻页和去重都用它。推送里的 `groupId` 是群的数字 `id`，不是这条排序键 |

群头像：`{imageBase}/head/{avatar}?imageView2/1/w/256/h/256&imgType=group&imageMogr2/format/webp`。消息图片：`{imageBase}/message/{fileMd5}?imageView2/2/w/640/h/640&imageMogr2/format/webp`。文件名已经带 `?` 或本身是 `http` 地址时不再追加参数。

红包 `content` 仍是 JSON，字段有 `description`、`title`、`nickname`、`nickName`、`status`、`dbAmountPool`、`num`、`remainNum`、`walletId`。`status` 为 `1` 待领取、`2` 已领取、`3` 已领完。奖池把 `dbAmountPool` 的数字去掉末两位再补齐 6 位。`remainNum` 是抓到这条消息时的剩余份数，之后不再向原站重查。页面按这个数字画，不要按时间去估，也不要因此把还能领的画成已领完。没有按 `walletId` 查领取名单、锦鲤播报或优惠券记录的接口。

`status` 为 `1` 且 `remainNum` 不是 `0` 时可以领。登录后：

```text
POST /prod-api/chat/groups/{groupId}/redpacket
{ "token": "", "id": "group#{gid}#msg#..." }
```

`id` 必须是这个群自己的消息排序键。成功 `data` 有 `amount`（元）、`balance`、`status`（已领取）。金额在后台区间内随机，记入说球帝余额，流水标题「群红包」。同一个用户、同一个 `walletId` 只入账一次。原站已领完时 `msg` 是「红包已领完」。不调用原站领取接口。

区间默认 1 到 100 分。修改：

```text
POST /prod-api/admin/chat-redpack
X-Admin-Key
{ "minCent": 10, "maxCent": 200 }
```

`minCent` 至少 1，`maxCent` 至多 200000，且不能小于 `minCent`。

带 `origin_text`、又不是红包或图片的 JSON，`content` 改成那句正文再返回。`type` 仍是原来的值。多图是 `{ "text": "", "files": [{ "fileMd5": "", "fileName": "" }] }`。单图的 `content` 就是文件名。表情原文是 `[/微笑]` 这种，对照页里的 `web/src/lib/faces.js`。`[视频小程序]名字,链接` 原文原样返回。链接里的 `cover` 是封面路径，页面拼到 `imageBase` 上。路径里带 `_enc.` 的文件是加密图，由页面解开后显示，接口不解密。

撤回 `content` 含 `uid`、`nickName`、`beUid`、`beNickName`、`fpForMessage`。`beUid` 和发送人不同，显示 `{nickName}撤回了"{beNickName}"的一条消息`；否则 `"{nickName}" 撤回了一条消息`。

实时不走这条 HTTP。浏览器连接：

```text
wss://ix1ui9ljn6.execute-api.ap-northeast-1.amazonaws.com/prod
```

连上后发送 `{ "action": "subscribe", "groupId": "all" }`。保活发送 `{ "action": "ping" }`。新消息推送：

```json
{ "groupId": 8447, "message": { "time": 0, "nick": "", "sendUid": "", "type": "0", "content": "", "id": "" } }
```

推送里的 `content` 和上面的 HTTP 一样：带 `origin_text` 的 @ 已经换成正文，红包和图片仍是 JSON。

新出现的群推送 `{ "group": { "id": 0, "gid": "", "name": "", "ownerName": "", "notice": "", "avatar": "" } }`。未读数不在接口里，由页面按推送在本地累加，点开该群后清掉，没有上限。

数据在 DynamoDB `zhiquan_chat`。抓取挂着原站长连接，消息到达后由上面的函数推给已连接的浏览器，页面不用为了新消息轮询这两个 GET。抓取断线会先补离线再重连，补上的新行也会推。群列表大约每分钟重读，账号新加入的群在这一轮写入后推送。账号没加入的群不会出现。

## 播放页发言

说球帝登录后，在播放页发聊天：

```text
POST /prod-api/chat/messages
{ "token": "", "mid": "4498761", "nick": "", "content": "" }
```

字段以代码为准。前端已经在用，改路径或字段前先改本文。

## 聊天室

给播放页的聊天用。只读，不需要登录。地址和上面的网关不是同一个。

```text
GET https://chat.kzbzcq.com/chat?mid=4498761&type=1
```

请用这个地址。绕过它直接请求函数地址会返回「未授权」。

响应带 `cache-control: no-store`。同一场比赛 8 秒内的重复请求读已保存的记录，不会每次都去上游重拉。页面可以每几秒问一次新消息。

| 参数 | 必填 | 说明 |
|---|---|---|
| `mid` | 是 | 比赛 id，1 到 18 位数字。缺失或格式不对返回 HTTP 400，「mid 应为比赛编号」 |
| `type` | 否 | `1` 足球，`2` 篮球，`3` 娱乐。缺省或不在这三项里时按 `1` |
| `before` | 否 | 毫秒时间戳。往上翻时传当前最早一条的 `time`，返回比它更早的记录，最多 100 条 |
| `after` | 否 | 毫秒时间戳。轮询新消息时传当前最新一条的 `time`，只返回比它更新的记录，最多 100 条，不带 `badges`。没有新消息时 `messages` 为空 |

`before` 和 `after` 都传时按 `before` 处理。两个都不传，返回最新 100 条。

成功时 HTTP 200：

```json
{
  "code": "0",
  "matchId": "4498761",
  "roomId": "房间编号",
  "closed": false,
  "exhausted": false,
  "badges": {},
  "messages": []
}
```

先判断 `code`。`"0"` 再读 `messages`。没有人说话时 `messages` 是空数组，这不是错误。

| 字段 | 说明 |
|---|---|
| `matchId` | 请求的比赛 id |
| `roomId` | 聊天室编号。还没有房间时为 `null` |
| `closed` | 聊天室已关闭 |
| `exhausted` | 更早的历史已经取完。为 `true` 时不要再用 `before` 往上翻 |
| `badges` | 这次出现过的等级图标，键是等级，值是图片地址。同一等级只给一次 |
| `messages` | 按 `time` 从早到晚 |

暂时拿不到时仍是 HTTP 200，`code` 为 `"1"`，`messages` 为空数组，`msg` 是「聊天暂时没有拿到」。过一会儿再请求。

消息：

| 字段 | 说明 |
|---|---|
| `id` | 消息 id |
| `time` | 毫秒时间戳。翻页和轮询用这个值 |
| `nick` | 昵称。没有时页面可以显示「游客」 |
| `content` | 文字。纯图片消息可能是空字符串 |
| `images` | 图片，最多 4 张。没有图片时不返回这个字段 |
| `level` | 等级。没有等级时不返回。图标用响应里的 `badges[level]` |
| `bubble` | 气泡底图地址。没有时不返回 |
| `color` | 文字颜色，`#` 色值或 `rgb` / `rgba`。没有时不返回 |

`images` 里每一张：

| 字段 | 说明 |
|---|---|
| `url` | 图片地址 |
| `w` / `h` | 宽高。上游没给时不返回 |

打开聊天先不带 `before`、`after`，把 `badges` 按等级记下来。贴在底部时，用最新一条的 `time` 作为 `after` 问新消息，把返回的 `messages` 接在后面。`after` 不再带图标。往上滚时，用最早一条的 `time` 作为 `before`，把返回的记录插到前面，并补上这次带回的 `badges`。同一条 `id` 不要画两次。

送礼往这场文档追加一条普通消息，`content` 为 `送出了「礼物名」×数量`。聊天函数不用另发。

## 直播、指数、数据、预测

给播放页里这四个栏目用。只读，不需要登录。每个栏目一个地址，域名和路径都是栏目名。

```text
GET https://live.kzbzcq.com/live?mid=4498761&type=1
GET https://index.kzbzcq.com/index?mid=4498761&type=1
GET https://data.kzbzcq.com/data?mid=4498761&type=1
GET https://tip.kzbzcq.com/tip?mid=4498761&type=1
```

请用这些地址。绕过它们直接请求函数地址会返回「未授权」。路径不对返回 HTTP 400，「路径应为 /live、/index、/data 或 /tip」。

响应带 `cache-control: no-store`。直播和指数 15 秒内的重复请求读已保存的记录。数据和预测一旦有正文，就一直返回这份，直到后台再次写入。

| 参数 | 必填 | 说明 |
|---|---|---|
| `mid` | 是 | 比赛 id，1 到 18 位数字。缺失或格式不对返回 HTTP 400，「mid 应为比赛编号」 |
| `type` | 否 | `1` 足球，`2` 篮球，`3` 娱乐。缺省或不在这三项里时按 `1` |

成功时 HTTP 200：

```json
{
  "code": "0",
  "matchId": "4498761",
  "tab": "live",
  "data": {}
}
```

先判断 `code`。`"0"` 再按 `tab` 读 `data`。`tab` 与地址一致，是 `live`、`index`、`data` 或 `tip`。暂时拿不到时仍是 HTTP 200，`code` 为 `"1"`，`data` 为 `null`，`msg` 是「这场资料暂时没有拿到」。过一会儿再请求。已经有过一份旧数据时，上游失败仍返回那一份，`code` 仍是 `"0"`。

### 直播 `live.kzbzcq.com/live`

`data.environment` 是场地，可能没有。有的话用 `nameZh`、`city`、`temperature`、`humidity`。

`data.tlive` 是文字直播，按时间顺序。没有时是空数组。

| 字段 | 说明 |
|---|---|
| `time` | 事件时间，如 `45'` |
| `data` | 事件文字 |

### 指数 `index.kzbzcq.com/index`

四组都可能没有，没有就不要画。

| 字段 | 栏目 |
|---|---|
| `yazhi` | 让球 |
| `ouzhi` | 胜平负 |
| `daxiao` | 大小球 |
| `jiaoqiu` | 角球 |

每一行：

| 字段 | 说明 |
|---|---|
| `company_name` | 公司名 |
| `initial` | 初盘 |
| `realtime` | 即时盘 |

没有变化历史。只画这两行，不要从两行里插出走势，也不要另写几行。

### 数据 `data.kzbzcq.com/data`

`data.history` 是交锋和近期，里面的 `vs`、`home`、`away` 是数组。每一行也是数组，页面按位置取联赛、日期、比分和队伍。没有这场记录时对应数组为空或不存在。

`data.injury` 是伤停。`home`、`away` 是数组。每一项用 `player_name`（没有时看 `name`、`player`）和 `reason`。

### 预测 `tip.kzbzcq.com/tip`

`data.forecast` 里三份盘口，没有的就不画：

| 字段 | 栏目 | 选项顺序 |
|---|---|---|
| `rq` | 让球 | 主、客 |
| `dx` | 大小 | 大、小 |
| `sf` | 胜平负 | 胜、平、负 |

每一份里 `pankou` 是盘口数组，`betList` 是投票。`betvalue` 从 `1` 起对应上面的选项顺序，`numPeople` 是人数。

`data.intel` 是情报。`home_good`、`away_good`、`neutral`、`home_bad`、`away_bad` 是字符串数组，个别会是 JSON 字符串，解析后同样是字符串数组。
