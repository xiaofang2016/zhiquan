# 说球帝转到吉祥

页面要调的账号接口在 [说球帝frontend.md](说球帝frontend.md)。入账由说球帝服务器再调吉祥，浏览器不接触吉祥的令牌和签名。

说球帝基址：`https://dgtplaoir00la.cloudfront.net`

吉祥基址：`https://api.jixsports.com`

商户号 `10011`。流水在吉祥账单里的类型是转账，显示名称是「跨平台转账」。

UED 用同一套路径，把 `jxt` 换成 `ued`，商户号是 `10012`。验证码、登录和入账地址仍是 `https://api.jixsports.com`，签名密钥仍是 `prod/jxt-boxim` 的 `BOXIM_OUTBOUND_SECRET`。流水同样显示「跨平台转账」。验证码也是 4 到 6 位，没有吉祥或 UED 账号时同样自动建号，令牌同样 30 天，频控和提现单号与吉祥相同。失败文案把「吉祥」换成「UED」。

## 页面调用的接口

都是 `POST`，请求头 `content-type: application/json`。成功时 HTTP 200，`code` 为字符串 `"0"`。业务失败时 HTTP 400，`msg` 可直接提示。

成功：

```json
{ "msg": "ok", "code": "0", "data": {} }
```

失败：

```json
{ "code": "400", "msg": "请先登录", "data": null }
```

登录令牌就是 `POST /prod-api/user/login` 返回的 `token`。没带、过期或对不上时，`msg` 是「请先登录」。

绑定状态、发验证码、确认绑定、解除绑定、提现成功时，`data` 都是这一份：

```json
{
  "isBind": "1",
  "account": "a***8",
  "balance": "100.00",
  "withdrawn": "20.00"
}
```

| 字段 | 说明 |
|---|---|
| `isBind` | `"1"` 已绑定，`"0"` 未绑定 |
| `account` | 吉祥用户名打码。空串是未绑定；长度不超过 2 时原样；否则首字 + `***` + 末字 |
| `balance` | 说球帝余额，单位元，两位小数 |
| `withdrawn` | 这个平台累计提现成功的金额，单位元，两位小数。失败退回的不计入。吉祥和 UED 各自累计 |

发验证码成功时 `data` 为 `null`。

### 查询绑定

`POST /prod-api/user/jxt/status`

```json
{ "token": "登录令牌" }
```

### 发送吉祥验证码

`POST /prod-api/user/jxt/sms`

```json
{ "token": "登录令牌" }
```

手机号用当前登录账号，请求体不要再传手机号。说球帝服务器转调吉祥 `POST /smscode`，字段是 `merchant_no=10011`、`mobile`、`phone_area_code=86`。验证码 5 分钟内有效。说球帝按手机号限制 60 秒一次，没到时间返回「当前操作太频繁请您1分钟后再尝试」。吉祥对 `User-Agent` 以 `sport-crawl` 开头的请求也按手机号限制 60 秒，不再把说球帝所有用户算成同一次。

### 确认绑定

`POST /prod-api/user/jxt/bind`

```json
{ "token": "登录令牌", "code": "1234" }
```

`code` 是 4 到 6 位数字。说球帝转调吉祥 `POST /mobilelogin`。手机号没有吉祥账号时，吉祥会自动建号并登录。成功响应里的 `id`、`username`、`access_token`、`expire_time` 记在说球帝，页面拿不到 `access_token`。`expire_time` 是秒级时间戳，有效期 30 天。

已经绑定过、但还没有令牌或令牌过期的账号，要再走一次验证码。

### 解除绑定

`POST /prod-api/user/jxt/unbind`

```json
{ "token": "登录令牌" }
```

只清说球帝本地的绑定标记和吉祥令牌，不调用吉祥，也不删除吉祥账号。未绑定时 `msg` 是「还没有绑定吉祥账号」。

### 把已绑定令牌交给职圈

`POST /prod-api/user/bound-login`

说球帝页面不要调用。浏览器拿不到签名密钥，也拿不到吉祥令牌。职圈服务器在用户打开吉祥体育时来取，再用返回的 `accessToken` 打开吉祥登录页。说球帝自己的页面不会因此变成吉祥的登录态。UED 把 `brand` 换成 `ued`。打开说球帝直播时 `brand` 是 `live`，返回的是这个说球帝账号新签发的登录令牌，不看吉祥或 UED 有没有绑定。

```json
{
  "userId": "32位小写编号",
  "phone": "13800138000",
  "brand": "jxt",
  "timestamp": "1700000000",
  "nonce": "16到64位字母或数字",
  "signature": "64位小写十六进制"
}
```

`userId` 是说球帝登录结果里的 32 位编号，必须小写。`phone` 是这个账号的手机号。`brand` 是 `jxt`、`ued` 或 `live`。`timestamp` 是当前秒级时间戳，10 位；13 位毫秒也可以，按秒比较。和服务器相差不能超过 300 秒。`nonce` 是 16 到 64 位字母或数字。请求体最长 2048 字节。

签名和入账用同一套 HMAC-SHA256，密钥仍是 `prod/jxt-boxim` 的 `BOXIM_OUTBOUND_SECRET`。待签名字符串用换行接上，最后三段是用户编号、手机号、品牌，不是整段 JSON：

```text
{timestamp}
{nonce}
POST
/prod-api/user/bound-login
{userId}
{phone}
{brand}
```

签名放在 JSON 的 `signature` 里，小写十六进制。不放请求头。

成功时 `data` 只有 `accessToken`。`jxt` 和 `ued` 是绑定时记下的令牌。`live` 是刚签发的说球帝登录令牌，页面用它走 `POST /prod-api/user/session`。手机号对不上这个用户编号时，`live` 的 `msg` 是「请先登录」。

| `msg` | 含义 |
|---|---|
| 签名不正确 | 字段、时间、随机串或签名对不上。签名失败一律是这一句 |
| 请先绑定吉祥账号 | 还没绑定，或手机号对不上这个用户编号。UED 把「吉祥」换成「UED」 |
| 请重新验证吉祥账号 | 没有令牌，或令牌已过 30 天。UED 把「吉祥」换成「UED」 |
| 请先登录 | 这一接口上只在 `brand` 为 `live` 时出现。手机号对不上这个用户编号。职圈把它收成「说球帝账号暂不能打开吉祥或UED」 |

### 提现

`POST /prod-api/user/jxt/withdraw`

```json
{ "token": "登录令牌", "amount": "10.00" }
```

`amount` 是元，最多两位小数。最低 10 元，最高 1000000 元。说球帝先扣自己的余额，再把同一金额转入吉祥主钱包。吉祥入账失败会用同一单号再试一次；仍失败则把说球帝余额加回去，`msg` 为「提现失败，余额已退回」，吉祥有具体原因时接在冒号后面。成功时 `data` 在绑定状态之外多一个 `tradeNo`，就是这次入账用的单号。

| `msg` | 含义 |
|---|---|
| 请先绑定吉祥账号 | 还没有绑定 |
| 请重新验证吉祥账号 | 没有吉祥令牌，或令牌已过 30 天 |
| 提现金额不正确 | 不是金额，或超过上限，或小数超过两位 |
| 提现金额不能小于10元 | 低于 10 元 |
| 余额不足 | 说球帝余额不够 |
| 吉祥接口未配置 | 说球帝读不到签名密钥 |
| 吉祥接口失败 | 吉祥没有返回可识别的 JSON |

其余 `msg` 是吉祥原文，例如「登录已失效，请重新登录」。

## 说球帝调用吉祥入账

`POST https://api.jixsports.com/external/transfer/cross-platform`

这条和职圈领取红包用同一套中间件：HMAC、来源 IP 白名单、用户 `access_token`。商户 `10011`（吉祥）和 `10012`（UED）可以入账。

### 签名

请求头：

| 头 | 值 |
|---|---|
| `Content-Type` | `application/json` |
| `User-Agent` | 非空。说球帝发送 `sport-crawl` |
| `X-Boxim-Timestamp` | 当前毫秒时间戳，与吉祥服务器相差不超过 300 秒 |
| `X-Boxim-Nonce` | 随机串。600 秒内不能重复 |
| `X-Boxim-Signature` | HMAC-SHA256，小写十六进制 |

待签名字符串五行，用换行 `\n` 连接，最后一行是原始 body 字节：

```text
{timestamp}
{nonce}
POST
/external/transfer/cross-platform
{body}
```

密钥和吉祥环境变量 `BOXIM_OUTBOUND_SECRET` 是同一个值。说球帝不把密钥放在环境变量里，启动后从 Secrets Manager 读取：

| 项 | 值 |
|---|---|
| 区域 | `ap-northeast-1` |
| 密钥名 | `prod/jxt-boxim` |
| JSON 字段 | `BOXIM_OUTBOUND_SECRET` |

跑说球帝接口的角色需要 `secretsmanager:GetSecretValue`。说球帝服务器的出口 IP 要在吉祥 `BOXIM_IP_WHITELIST` 里，否则签名通过后仍返回 403。

### 请求体

```json
{
  "accessToken": "吉祥登录返回的 access_token",
  "userId": "吉祥用户 id",
  "amount": "10.00",
  "tradeNo": "32位无横线单号"
}
```

`userId` 必须和 `accessToken` 对应的用户一致。`amount` 是元，两位小数字符串。`tradeNo` 最长 64 字符，同一用户重复提交不会入账两次。

### 响应

入账成功或重复单，HTTP 200：

```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "tradeNo": "32位无横线单号",
    "amount": 10,
    "balance": 110,
    "duplicated": false
  }
}
```

`duplicated` 为 `true` 表示这张单号已经入过账，余额不再增加。`code` 是数字 `0`。说球帝把数字 `0` 和字符串 `"10000"` 都当成成功。

业务拒绝也是 HTTP 200，`message` 为原因：

| `code` | `message` |
|---|---|
| 40006 | 登录已失效，请重新登录 |
| 40006 | userId 与登录身份不一致 |
| 40001 | 不是可入账账号 |
| 40001 | tradeNo 非法 |
| 40001 | 金额非法 |
| 40904 | 入账失败 |
| 500 | 钱包记账失败 |

签名层失败时 body 同样是 `{ "code", "message", "data": null }`，HTTP 状态不同：

| HTTP | `code` | `message` |
|---|---|---|
| 401 | 40001 | missing signature headers，或 invalid signature |
| 401 | 40002 | 时间戳超出窗口 |
| 409 | 40003 | nonce replayed |
| 403 | 40301 | forbidden: source ip not allowed |
| 500 | 50001 | box-im outbound secret not configured |

吉祥钱包记一笔 `type=6`（额度转账）。`second_type_name` 和 `log` 都是「跨平台转账」。账单接口用 `second_type_name` 作为 `showName`。
