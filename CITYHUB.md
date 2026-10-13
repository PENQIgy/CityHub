# CityHub · 城市生活内容与交易平台

CityHub 是在原「黑马点评（hmdp）」项目基础上升级改造而来的一站式城市生活平台，
定位对标 **小红书**（探店内容 + Feed 流）与 **大众点评**（商户 + 代金券交易）。

改造覆盖 **后端全量重命名与功能补全** + **前端小红书风格重做**，两侧已联调打通。

---

## 1. 改造概览

| 维度 | 改造前（hmdp） | 改造后（CityHub） |
|---|---|---|
| Java 包名 | `com.hmdp` | `com.cityhub` |
| 主类 | `HmDianPingApplication` | `CityHubApplication` |
| Maven 坐标 | `com.hmdp:hm-dianping` | `com.cityhub:cityhub` |
| 数据库 | `hmdp` | `cityhub` |
| 前端目录 | `html/hmdp` | `html/cityhub` |
| 评论模块 | 控制器/服务为空壳 | 完整一二级评论 + 评论点赞 |
| Feed 流 | 仅「关注流」，翻页 offset 计算有 bug | 推荐 / 关注 / 附近 三路 Feed，scroll 分页修复 |
| 内容能力 | 只有标题、正文、图片 | 话题、评分、定位、浏览量、收藏、编辑、删除、搜索 |
| 用户体系 | 无登出、无统计、isFollow 判断错误 | 登出、资料编辑、主页统计、关注/粉丝列表、他人主页 |
| 交易闭环 | 只有秒杀下单 | 下单 → 订单列表 → 支付 → 核销 / 取消，前端有订单页 |
| 源码编码 | 未声明，Maven 按 GBK 编译导致中文乱码 | 统一 UTF-8 |
| 启动预热 | GEO / 秒杀库存需手工跑测试用例 | `DataWarmUpRunner` 启动自动预热 |

原 `hmdp` 库与 `front/html/hmdp` 目录在迁移时会被重命名，**原库数据保留在 `hmdp` 中作为备份**。

---

## 2. 目录结构

```
CityHub/
├── CITYHUB.md                     ← 本文件
├── .gitignore                     ← Git 忽略规则
├── pom.xml                        ← Maven 构建文件（后端入口）
├── cityhub.sql                    ← CityHub 完整建库脚本（含表结构 + 演示数据，可直接导入）
├── tools/                         ← 自检 / 回归脚本
│   ├── frontend-static-check.js        前端页面语法与引用自检
│   ├── api-smoke-test.ps1              接口冒烟测试（63 项）
│   ├── verify-utf8-and-follow.ps1      中文往返 + 关注列表回归
│   ├── verify-feed-scroll.ps1          关注流跨页翻页回归
│   └── verify-feed-scroll-same-score.ps1  同 score 冲突下的 offset 回归
├── front/                         ← 前端（部署到 nginx）
│   ├── conf/nginx.conf            ← nginx 配置（root 指向 html/cityhub）
│   └── html/cityhub/
│       ├── API.md                 ← 前后端接口契约
│       ├── FRONTEND_GUIDE.md      ← 前端开发规范
│       ├── css/  js/  imgs/
│       └── *.html                 ← 页面
└── src/                           ← 后端 Spring Boot 源码
    └── main/
        ├── java/com/cityhub/
        │   ├── CityHubApplication.java
        │   ├── config/      配置 + GeoIndex 预热 + 全局异常
        │   ├── controller/  9 个控制器
        │   ├── dto/         Result / ScrollResult / UserDTO / VoucherOrderVO ...
        │   ├── entity/      Blog / BlogComments / Topic / BlogCollect ...
        │   ├── mapper/      MyBatis-Plus Mapper
        │   ├── service/     接口 + impl
        │   └── utils/       拦截器 / Redis key / 分布式 ID / 缓存工具
        └── resources/
            ├── application.yaml
            ├── seckill.lua / unlock.lua
            ├── mapper/*.xml
            └── db/
                ├── cityhub_upgrade.sql     ← 表结构升级（幂等）
                ├── cityhub_seed.sql        ← 演示数据 + 数据润色（幂等）
                └── migrate-to-cityhub.ps1  ← 一键迁移脚本
```

### 前端页面一览

| 页面 | 说明 |
|---|---|
| `index.html` | 首页 Feed 流：推荐 / 关注 / 附近 三个分段 + 话题胶囊 + 搜索入口 |
| `nearby.html` | 附近：附近笔记 / 热门笔记（带定位与兜底定位） |
| `blog-detail.html` | 笔记详情：图片轮播、关注、点赞、收藏、一二级评论、删除 |
| `blog-edit.html` | 发布 / 编辑笔记：多图上传、话题、评分、定位、关联商户 |
| `topic.html` | 话题详情：话题信息 + 话题下笔记 |
| `search.html` | 搜索：笔记 / 商户双 Tab + 搜索发现 + 搜索历史 |
| `shop-detail.html` | 商户详情：商户信息、代金券抢购、该商户的探店笔记 |
| `shop-list.html` | 分类商户列表（支持定位距离排序） |
| `orders.html` | 我的订单：按状态筛选、支付、核销、取消 |
| `info.html` | 我的主页：资料、统计、笔记 / 收藏 / 赞过 / 关注 / 粉丝 |
| `other-info.html` | 他人主页：资料、统计、关注按钮、笔记 / 关注 / 粉丝 |
| `info-edit.html` | 编辑资料：头像上传、昵称、城市、简介、性别 |
| `login.html` | 登录：验证码登录（开发模式自动回填验证码 + 60s 倒计时） |

### 自检工具用法

```powershell
# 前端静态自检（语法、util/chAuth 方法引用、本地资源是否存在）
& "D:\NODE anzhung\node.exe" tools\frontend-static-check.js

# 接口冒烟测试（需后端 8081 已启动）
Invoke-Expression (Get-Content tools\api-smoke-test.ps1 -Raw)

# 关注流翻页回归（需后端已启动）
Invoke-Expression (Get-Content tools\verify-feed-scroll.ps1 -Raw)
```

> 这些脚本只读接口、不改代码；`verify-*` 系列会创建并清理自己的测试数据。


---

## 3. 环境要求

| 组件 | 版本 | 本机路径示例 |
|---|---|---|
| JDK | 17 | `C:\Users\<user>\.jdks\ms-17.0.19` |
| Maven | 3.9+ | `D:\apache-maven-3.9.12\apache-maven-3.9.12` |
| MySQL | 8.0 | `C:\Program Files\MySQL\MySQL Server 8.0` |
| Redis | 3.2+（需密码） | `D:\Redis-x64-3.2.100` |
| nginx | 1.18 | `D:\nginx-1.18.0` |

默认连接信息（可在 `application.yaml` 修改）：

- MySQL `127.0.0.1:3306`，库 `cityhub`，账号 `root / 123456`
- Redis `127.0.0.1:6379`，密码 `123456`
- 后端端口 `8081`，nginx 端口 `8080`

---

## 4. 快速开始

### 4.1 数据库迁移

方式一（推荐，从现有 `hmdp` 库迁移，保留原库作备份）：

```powershell
cd src\main\resources\db
powershell -ExecutionPolicy Bypass -File migrate-to-cityhub.ps1
```

脚本会：导出 `hmdp` → 重建 `cityhub` → 导入 → 执行 `cityhub_upgrade.sql` → 执行 `cityhub_seed.sql`。
两个 SQL 都是幂等的，可重复运行。

方式二（直接用完整 dump 建库，不需要旧的 `hmdp` 库）：

```powershell
mysql -uroot -p --default-character-set=utf8mb4 < cityhub.sql
```

`cityhub.sql` 是最终状态的完整 dump（表结构 + 演示数据），已包含 6 个话题、
9 篇探店笔记、12 条评论、收藏与订单数据。

### 4.2 启动 Redis 与 MySQL

```powershell
Start-Service MYSQL80
Start-Process "D:\Redis-x64-3.2.100\redis-server.exe" -ArgumentList "D:\Redis-x64-3.2.100\redis.windows.conf" -WorkingDirectory "D:\Redis-x64-3.2.100"
```

### 4.3 构建并启动后端

```powershell
$env:JAVA_HOME = "C:\Users\<user>\.jdks\ms-17.0.19"
& "D:\apache-maven-3.9.12\apache-maven-3.9.12\bin\mvn.cmd" -B -DskipTests package
& "$env:JAVA_HOME\bin\java.exe" -jar "target\cityhub-0.0.1-SNAPSHOT.jar"
```

启动日志里会看到 GEO 索引与秒杀库存的预热结果。

### 4.4 部署前端并启动 nginx

```powershell
$repo  = "front"
$nginx = "D:\nginx-1.18.0"
Copy-Item "$repo\conf\nginx.conf" "$nginx\conf\nginx.conf" -Force
Copy-Item "$repo\html\cityhub\*.html" "$nginx\html\cityhub\" -Force
Copy-Item "$repo\html\cityhub\css\*"  "$nginx\html\cityhub\css\" -Force
Copy-Item "$repo\html\cityhub\js\*"   "$nginx\html\cityhub\js\"  -Force
Start-Process "$nginx\nginx.exe" -WorkingDirectory $nginx
```

> 注意：`$nginx\html\cityhub\imgs` 是上传图片的落盘目录，**同步前端时不要覆盖/删除它**。
> 后端 `cityhub.image-upload-dir` 指向这里，nginx 通过 `/imgs/**` 直接对外提供访问。

访问 <http://127.0.0.1:8080> 即可。

### 4.5 登录（开发模式）

项目未接入真实短信通道，`POST /user/code` 会**把验证码放在响应 `data` 里返回**，
登录页会自动填入验证码，一键即可登录。可用已有测试账号：

| 手机号 | 用户 id | 昵称 |
|---|---|---|
| `19999999999` | 1011 | user_i9hc1g |
| `18888888888` | 1012 | user_je32qk |
| `19877777777` | 1013 | user_a6r4rb |
| `13686869696` | 1 | 小鱼同学 |
| `13838411438` | 2 | 可可今天不吃肉 |

---

## 5. 功能清单

### 内容侧（探店笔记）

- **发布 / 编辑 / 删除**笔记：多图（最多 9 张）、标题、正文、话题、探店评分、定位、关联商户
- **图片上传**：落盘到 nginx 静态目录，由 nginx 直接提供访问（带 7 天缓存）
- **互动**：点赞、收藏、评论（一二级）、评论点赞，全部有计数一致性保证
- **浏览量**：同一访客同一天只计一次（Redis Set 去重）
- **话题**：话题列表 / 热门话题 / 话题详情 / 话题下的笔记，话题笔记数自动维护
- **搜索**：标题 + 正文 + 定位模糊搜索，返回总数

### Feed 流

| Feed | 接口 | 排序依据 |
|---|---|---|
| 推荐 | `GET /blog/feed/recommend` | `评论×4 + 点赞×3 + 收藏×2 + 浏览量/10` 加权 |
| 关注 | `GET /blog/of/follow` | Redis ZSet 收件箱，发布时间倒序，scroll 分页 |
| 附近 | `GET /blog/feed/nearby` | Redis GEO 取 10km 内商户，再取这些商户的笔记 |
| 热门 | `GET /blog/hot` | 点赞数倒序 |

关注流采用**写扩散**：发布笔记时推送到所有粉丝的收件箱 ZSet，并做长度裁剪（1000）与 7 天过期。

### 交易侧

- 商户详情 / 分类列表（支持按经纬度 GEO 排序并返回距离）/ 名称与关键字搜索
- 商户代金券列表（区分秒杀券与普通券，含库存与生效时间）
- 秒杀下单：Lua 脚本在 Redis 内原子完成「库存判断 + 一人一单 + 扣减」，
  再经阻塞队列异步落库，落库时用 Redisson 分布式锁 + 唯一性校验兜底
- 订单闭环：订单列表（按状态筛选）→ 模拟支付 → 核销 / 取消（取消回补库存与 Redis 标记）

### 用户体系

- 验证码登录 / 登出（删除 Redis 登录态，并刷新登录缓存中的昵称头像）
- 个人资料编辑：头像上传、昵称、城市、简介、性别、生日
- 主页统计：笔记数、关注数、粉丝数、获赞数、被收藏数（实时统计，不依赖冗余字段）
- 关注 / 取关 / 是否关注 / 共同关注 / 关注列表 / 粉丝列表
- 他人主页

---

## 6. 本次修复的缺陷

改造过程中修复了原项目里的若干真实缺陷：

| # | 位置 | 问题 | 影响 |
|---|---|---|---|
| 1 | `FollowServiceImpl.isFollow` | 用全表 `count()` 当作自己的关注数，查询结果被丢弃 | 只要库里有任意一条关注记录就永远返回「已关注」 |
| 2 | `BlogServiceImpl.queryBlogOrFollow` | 计算 `offset` 时循环内无条件 `os = 1` 重置，且完全忽略入参 `offset` | 关注流翻页重复 / 丢数据 |
| 3 | `seckill.lua` | 把 `tonumber` 误写成 `tonum`（Lua 中不存在该函数） | 秒杀脚本**必然报错**，秒杀功能完全不可用 |
| 4 | `VoucherOrderServiceImpl` 订单线程 | `run()` 只 `take()` 一次，处理完一笔订单线程即退出 | 只能处理第一笔订单 |
| 5 | `VoucherOrderServiceImpl.createVoucherOrder` | `eq("voucher_id", voucherOrder)` 把整个实体当作条件值 | 扣减库存 SQL 条件错误 |
| 6 | `VoucherOrderServiceImpl` 事务代理 | `proxy` 在每个请求里赋值给实例字段，存在竞态；`@PostConstruct` 中取 `AopContext` / `getBean(self)` 会触发循环依赖 | 事务失效或启动失败 |
| 7 | `pom.xml` | 未声明源码编码，Maven 按平台默认 GBK 编译 | 所有中文字符串字面量运行时乱码 |
| 8 | `Seckill.lua` | 文件名大写 S，而代码引用 `seckill.lua` | 仅因 Windows 大小写不敏感才没暴露，Linux 部署会失败 |
| 9 | `common.js` | `paramsSerializer` 用 `if(params[k])` 过滤参数 | `offset=0`、`lastId=0` 被丢弃 |
| 10 | `common.js` | `axios` 超时 2000ms | 首次请求 / 冷启动频繁超时 |
| 11 | `common.js` | 错误分支直接读 `error.response.status` | 网络异常时抛 TypeError，页面白屏 |
| 12 | `LoginInterceptor` + `MvcConfig` | 用 `excludePathPatterns` 无法区分读写，且自定义拦截器用 `new` 创建 | `GET /user/{id}` 与 `/user/me` 冲突；`DELETE /blog/{id}` 会随 `/blog/*` 一起被放行 |
| 13 | `IndexController` 前身 `index.html` | 分类图标拼接成 `/imgs//types/ms.png` | 图标全部 404 |
| 14 | `SystemConstants.IMAGE_UPLOAD_DIR` | 硬编码 nginx 路径 | 改为 `cityhub.image-upload-dir` 配置项 |
| 15 | 前端各页 | `images.split(',')` 后未做地址归一化 | 相对路径图片 404 |
| 16 | `WebExceptionAdvice` | 只处理 `RuntimeException`，参数类型错误返回「服务器异常」 | 排查困难，改为分类处理 |

---

## 7. 接口文档

完整的接口契约（含字段说明、登录要求、分页协议）见：

- 前端目录：[`front/html/cityhub/API.md`](front/html/cityhub/API.md)
- 前端开发规范：[`front/html/cityhub/FRONTEND_GUIDE.md`](front/html/cityhub/FRONTEND_GUIDE.md)

### 关键约定

统一响应体：

```json
{ "success": true, "errorMsg": null, "data": {}, "total": null }
```

- 前端 axios 拦截器已把 `success=false` 转成 `Promise.reject(中文错误文案)`
- 未登录访问受保护接口返回 **HTTP 401**
- 图片上传返回 `/blogs/x/y/uuid.jpg` 形式的相对路径，前端展示时拼 `/imgs` 前缀

关注流 scroll 分页协议：

```
首次：GET /blog/of/follow?lastId=0&offset=0
返回：data = { list: [...], minTime, offset }
下一页：lastId=<上次 minTime>&offset=<上次 offset>
list 为空数组 => 没有更多
```

---

## 8. 已知限制

- 未接入真实短信通道，验证码直接在接口响应中返回（仅适用于开发/演示环境）
- 支付为**模拟支付**，不接入真实支付渠道；核销由用户自行点击完成
- 项目未加鉴权分级（没有后台管理端），`POST /shop`、`PUT /shop`、`POST /voucher` 等写接口
  仅受登录拦截器保护，生产环境需要额外收紧
- 笔记「浏览量」按 IP / 账号 + 自然日去重，属于近似值
- 附近 Feed 依赖商户经纬度，`tb_shop` 中坐标为空的商户不会进入 GEO 索引
- 关注流为写扩散（fan-out on write），粉丝量极大时需要改为拉模式或推拉结合
- `tb_user_info` 的 `fans/followee/blog_count/liked_count` 是冗余统计字段：
  读接口一律实时统计，冗余字段只在关注/统计时顺带同步，以接口返回为准
