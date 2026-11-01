# AWS 上的自动化

说球帝在 AWS 上自己跑的部分：请求怎么被拆开、抓取怎么自己续上、一次发布怎么换上 Java 服务。区域 `ap-northeast-1`。页面对接见 [说球帝frontend.md](说球帝frontend.md)，网关字段见 [api.md](api.md)。

聊天、直播、指数、数据、预测、动画不在下面这条发布里。那些是各自的函数或本机进程，改完要单独发。页面 `web/` 和三个 Java 包在同一次推送里换上。

## 一条请求怎么走

浏览器只看见两类地址。

统一入口是 `https://dgtplaoir00la.cloudfront.net`。它前面是 CloudFront，后面是 HTTP API `fff9lunsr8`。比赛、即时比分、资料库走 `GET`。登录、注册、验证码、恢复登录走 `POST`，路径仍是 `/prod-api/...`。CloudFront 在这一层做压缩，并放开浏览器跨域。即时比分这条路径不缓存。

网关按路径把请求交给两个后端：

| 网关路径 | 后端 |
|---|---|
| `/prod-api/match/list/new2` | Lambda `sport-crawl-score` |
| 其余 `/prod-api/{proxy+}`，含 `POST /prod-api/user/...` | 机器上的 `api.jar`，`172.31.5.227:8892` |

`api.jar` 自己再分：`/prod-api/database/`、`/team/`、`/player/` 读资料库对象，其余读赛程表，`/prod-api/user/` 读写账号。

播放页不进这个网关。每个栏目一个 `kzbzcq.com` 子域名，CloudFront 再转到对应函数或本机。函数要求的校验头由 CloudFront 补上，页面不带。直接打函数地址会得到「未授权」，也没有这层压缩。

| 域名 | 后端 |
|---|---|
| `chat.kzbzcq.com` | Lambda `sport-crawl-chat` |
| `live` / `index` / `data` / `tip`.kzbzcq.com | 同一个 Lambda `sport-crawl-detail`，用路径区分栏目 |
| `player.kzbzcq.com` 的 `/anim` | `3.112.56.84` 的 80 端口，再进本机动画桥 `127.0.0.1:8896` |
| `www.kzbzcq.com` | 同一台机器上的页面。Lambda `sport-crawl-web` 另有一份相同页面 |

## 四条自己会跑的循环

上游只有 `kzb001.net`。四条循环写进自己的存储，页面只读副本。时钟各算各的。

### 赛程：容器里的钟

`worker.jar` 在 Docker 里用 Spring 调度，不经过 EventBridge。只听 `127.0.0.1:8891`。

| 任务 | 节奏 | 写入 |
|---|---|---|
| 今天的比赛，顺带联赛和日期 | 每 2 分钟 | DynamoDB `sport_league`、`sport_match_date`、`sport_match`，队徽进 S3 |
| 未来日期 | 每 30 分钟 | 日期接口里晚于今天的每一天 |
| 过去 7 天 | 每天 04:00（上海） | 从昨天往前 |

请求间隔 200 毫秒。页大小先要 200，上游不理就退回 20，单日最多 500 页。这一天的页全部抓完，才删掉已经不在列表里的比赛。抓到一半停掉时，旧行留着。

线上把启动时的全量补抓关了。要补历史，打本机 `POST /crawl/run`。

### 即时比分：函数自己叫自己

`sport-crawl-score` 没有外部定时器。一轮做完这些事：

1. 抢 `scores/lock.json`。锁还在 110 秒内就跳过，避免两轮叠在一起。
2. 把 `/match/list/new2` 全部页拉完，再叠上足球、篮球的 `otherList`。
3. 写成 `scores/board.json`。
4. 等 15 秒，放开锁。
5. 异步调用自己。事件写成 `{ "source": "aws.events" }`，函数把这种事件当成「再抓一轮」，不当成页面请求。

页面来读时，进程里把板子留 5 秒。板子超过 90 秒没更新，这次读取会再踢一脚。自我调用失败时，循环停住，直到下一次读取把它踢起来。

### 资料库：另一套容器里的钟

`db.jar` 单独一套 compose，只听 `127.0.0.1:8893`。三件任务共用一把锁，忙的时候后来的直接跳过。

| 任务 | 节奏 | 范围 |
|---|---|---|
| 每日 | 04:30（上海） | 资料库首页、热门赛事、FIFA、每个联赛的当前赛季。历史赛季每轮最多补 400 份 |
| 赛中 | 每 15 分钟 | 今天足球和篮球里正在进行的，以及开球后 5 小时内结束的联赛 |
| 按需 | 每 15 秒 | 队列里到期的 20 条。失败推迟 5 分钟 |

正文在 S3 `database/...json`。进度写在旁边的 `.meta`。还没抓过的球队、球员页面，读接口返回空的 `data`，并把路径放进 `database/_queue/`。内容的 SHA-256 没变时只改时间，不重写正文。历史赛季一旦有过正文就不再请求上游。

### 社球帝：同一进程里的另一口钟

也在 `db.jar` 里，不跟上面三件任务共用那把锁，也不跟比赛抓取共用间隔。进程起来先抓一轮，之后每 2 分钟再拉 `kzb001.net` 的 `/comment-api/post/list`：社区最近 20 页、精华 3 页、每个圈子 2 页，外加圈子列表和圈子详情。帖子在最后回复时间、评论数、点赞数、浏览数或收藏数变了才重写正文和评论。评论最多翻 10 页。不在这些页里的帖，有人打开单帖时再补这一篇。

正文在同一个桶的 `community/feed.json`、`community/posts/{postId}.json`、`community/posts/{postId}/comments.json`。外站头像转存到图片桶，地址换成 `/prod-api/community/images/{hash}`。读接口见前端文档的社球帝一节。`/prod-api/` 的响应不论请求有没有 `Origin` 都带 `Access-Control-Allow-Origin: *`，预检直接返回 204，避免 CloudFront 把没带头的那份缓存住。

### 播放页：有人打开才拉

聊天和四个栏目没有定时任务。

| 线 | 存在 | 多久再去上游 |
|---|---|---|
| 聊天 `chat/{比赛}.json` | 同一场全部已合并消息 | 8 秒内直接回存档。另一个请求正在同步时，后来的读当前这份 |
| 直播、指数 | `detail/{比赛}.json` 里的两段 | 15 秒 |
| 数据、预测 | 同一份文件里的另外两段 | 有正文之后一直用这份 |
| 动画 | 不落文档 | 无头 Chrome 约每 400 毫秒截一张。地址两分钟没人看就关掉 |

聊天和资料用对象的 ETag 条件写入。两个人同时打开同一场，后写的会重读再合并。

动画只接受 `widgets-livetracker.nami.com` 和 `tracker.namitiyu.com`。Chrome 把 `player.chinajiushengjixie.com` 解析到本机，打开授权页再截图。桥只听本机 `8896`。

## 一次发布换上什么

Java 三个包打在同一个镜像 `sport-crawl:1.0.0` 里，用启动命令区分。CodeBuild 读 `buildspec.yml`：先在 `web/` 用 npm 打出页面，再用 Corretto 17 执行 `mvn package`，把 `worker.jar`、`api.jar`、`db.jar`、Dockerfile、两份 compose、`appspec.yml` 和 `scripts/` 打成制品。页面制品是构建后的 `web/dist`。

CodeDeploy 按 `appspec.yml` 盖到 `/var/local/sport-crawl`，钩子以 root 跑：

| 钩子 | 脚本 | 时限 | 做的事 |
|---|---|---|---|
| ApplicationStop | `scripts/stop.sh` | 120 秒 | 只停比赛这套 compose。资料库那套先留着 |
| ApplicationStart | `scripts/start.sh` | 900 秒 | 重新构建并拉起抓取和读接口，再用刚构建的镜像强制重建资料库 |
| ValidateService | `scripts/validate.sh` | 180 秒 | `172.31.5.227:8892/api/sport/health` 和 `127.0.0.1:8893/crawl/status` 都通才算成功。各等最多约 60 秒 |

机器上的限额：抓取 1.5 CPU / 2 GB，读接口 0.5 CPU / 1 GB，资料库 0.25 CPU / 1 GB。抓取和资料库的调度线程池都是 4。它们和比分函数可以同时打上游，彼此不共享那 200 毫秒的间隔。

存储都在这一个账号里：

| 东西 | 名字 |
|---|---|
| 桶 | `sport-crawl-images-562904761216` |
| 赛程 | `sport_league`、`sport_match_date`、`sport_match` |
| 图片去重 | `sport_image` |
| 账号 | `sport_user`，主键是手机号 |

资料库索引用的是桶里的 `.meta` 和 `_queue`，配置里的表名 `sport_db_doc` 没有参与读写。

## 不在这条发布里的

| 改了什么 | 怎么上 |
|---|---|
| `web/` 页面和三个 Java 包 | 推 `main`。CodeBuild 更新 Lambda `sport-crawl-web`，`scripts/start.sh` 把 `site/web/` 拷进站点目录 |
| `score-lambda`、`chat-lambda`、`detail-lambda` | 各自打 zip，更新对应函数。比分循环在新代码起来之后的下一轮自我调用里换上 |
| 动画桥 | 改 `3.112.56.84` 上的 `anim-bridge`，CloudFront 仍指这台机器的 80 端口 |
| 职圈群抓取 | `3.112.56.84` 上的 `zhiquan-chat-scraper`，代码在 `/var/local/zhiquan-chat-scraper/bin/scraper.py`。改完要单独拷上去再重启，不随 `main` 流水线 |
| 职圈群实时 | API Gateway WebSocket `ix1ui9ljn6`，函数 `sport-crawl-chat-live`。页面订阅和推送逻辑在仓库 `chat-live/`。改函数要单独更新代码，推 `main` 只换页面和 Java |

职圈群存在 DynamoDB `zhiquan_chat`，分区 `apk#5739998`。抓取挂着原站长连接，消息到达就写入并推给正在打开聊天页的浏览器。断线后先补一次离线再重新挂上。群列表大约每分钟重读，用来发现新加入的群。图片域名优先用原站返回的地址，`oss.imnono.net` 不用，改用 `https://imfile.dtthsp.com`。

读接口挂在内网 `172.31.5.227:8892`，给网关当源站。抓取、资料库、动画桥只听本机，CloudFront 和网关够不着它们的管理口。
