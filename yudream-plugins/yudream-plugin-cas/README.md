# 塔里木大学统一身份认证插件

独立插件项目，不并入 `yudream-admin-plugins` 仓库，也不做 Git 提交。约定对齐插件仓 `AGENTS.md` 与宿主仓 SPI。

- 插件 code：`cas`
- 版本：`1.1.0`（v1.0.3 基线保留在 `../yudream-plugin-cas/`，已交付网信中心，不再改动）
- 依赖宿主 SPI：`2.29.0`（`PluginExternalLoginProvider`、`PluginGlobalWidget`、`PluginUserService.findByExternalIdentity`）
- 登录入口由宿主登录页 Tabs 渲染，插件贡献协议实现、管理设置页、学生信息页、绑定门禁挂件与学生档案预填挂件

## v1.1.0 新增

1. **管理端查看绑定信息**：「学生信息」页列表新增「绑定账号」列，详情抽屉新增绑定信息块（本站账号 / 用户 ID / 昵称 / 邮箱 / 手机 / 账号状态），管理员可直接核对「学工号 → 本站账号」。
   - 数据来源：宿主 SPI 2.29.0 的 `PluginUserService.findByExternalIdentity(providerCode, platformType, socialUid)`，即宿主 external account 表里 (登录通道, 协议, 学工号) 命中的那条绑定；`platformType` 取学生档案自身记录的协议（CAS/OIDC），因此管理员中途切换协议后历史绑定仍能查到。
   - 降级：宿主版本不支持该能力（默认实现抛 `UnsupportedOperationException`）、宿主未提供用户服务、或绑定指向的用户已不存在时，只把该列/该块显示为「无法查询」并附原因，列表、搜索、其他字段与预填功能照常可用（fail-open）。
   - 只能看「学工号 → 本站账号」这一方向：SPI 没有「按本站账号列绑定」的契约，反向列表需宿主先发布对应SPI。
2. **映射字段对照说明**：「认证设置 → 学生信息映射」新增字段对照，直接对齐学生档案插件的四个字段：
   - `姓名` ← 姓名字段键（留空自动探测 `name` / `cn` / `displayName`）
   - `学号` ← 认证中心返回的账号（CAS 的 `<cas:user>`），无需配置
   - `学院` / `班级` ← 对应字段键；认证中心未返回该属性时留空，由成员在预填表单里补填
   - `专业` / `年级` ← 仅归档到「学生信息」页，学生档案插件不使用

## v1.0.5 新增

1. **学生档案预填（对接 `yudream-student-info` 插件）**：已绑定 CAS 且尚未填写学生档案的用户，右下角出现「完善学生档案」提醒；表单自动带入 CAS 属性（姓名/学号/学院，认证中心返回班级时一并带入），保存直接写入 `yudream-student-info` 插件的 `PUT /api/plugins/yudream-student-info/api/me`。
   - 预填数据来自本插件新公开端点 `GET /api/plugins/cas/api/me/profile?socialUid=`（要求已登录，返回最小字段集——姓名/班级/学院/专业/年级，不含邮箱/电话/原始属性）。
   - 学工号取自宿主绑定记录（`/api/user/me/external-accounts` 的 `socialUid`），后端不做 userId↔学工号映射。
   - 已填写过档案（`studentNo` 非空）不再提醒；用户点「暂不填写」按账号记忆，不再打扰；查询失败静默放弃。
   - 依赖 `yudream-student-info` 插件已安装启用；未安装时挂件自动沉默。
2. **班级字段**：`StudentMapping` 增 `classKey`（探测候选 className/class/clazz/banji/studentClass），学生档案聚合增 `className`。
3. **collection 更名**：本插件自建学生档案 collection 由 `yudream-student-info` 更名为 `cas-students`（避免与同名插件混淆；旧 collection 数据不迁移，用户重新登录即重建）。

## v1.0.4 新增

1. **学生信息映射**：CAS/OIDC 登录交换时把认证属性全量收集（CAS attributes 节点 / OIDC userinfo 文本字段），按「显式配置键 > 常见键名自动探测 > 旧值兜底」三级回退 upsert 成学生档案（以学工号为主键，存 `cas-students` collection）。记录动作 best-effort，失败不影响登录。
   - 管理端「学生信息」页：分页 + 关键词搜索（学号/姓名/学院/专业）+ 详情（含原始属性 JSON，用于核对字段键名后回填映射配置）。
   - 映射配置存独立聚合 `StudentMapping`（collection `student-mapping`），含姓名/学院/专业/年级/班级 5 个键。
2. **未绑定门禁开关**：`requireBinding` 开启后，全站挂件 `cas/Gate`（`registerGlobalWidget`）对未绑定 CAS 的登录用户显示全屏引导遮罩并引导跳转绑定。
   - 管理员（持 `plugin:cas:manage`）豁免；CAS 登录入口未就绪时开关自动失效，防止锁死。
   - 绑定状态以宿主 `GET /api/user/me/external-accounts` 为唯一权威来源（前端原生 fetch + localStorage token，匹配 `providerCode==='cas'` 且 `platformType` 等于当前协议 typeCode）。
   - **边界**：门禁是前端 UI 层强制——绕过界面直接调 API 不受限；宿主若开启「API 接口加密」（默认关闭），原生 fetch 会被宿主过滤器拒绝（400），挂件按 fail-open 放行，门禁静默失效但不会锁死任何人。

## 通道标识与管理标识

`PluginExternalLoginDescriptor.providerCode()` 直接使用插件管理标识 `cas`（即 `TaruSsoPlugin.CODE`）。
宿主 SPI 2.27.0 起 `login.vue` / `profile.vue` 不再硬编码 "wwoyun"，登录入口按
`/api/external-login/providers` 返回的 enabled 提供方渲染并直接把 `providerCode` 拼到
`/api/external-login/{providerCode}/{type}/authorize`；宿主后端按 `descriptor.providerCode()`
精确匹配插件扩展点。插件启用后登录页会出现「CAS 统一身份认证」入口。

## 能力

把塔里木大学 `https://auth.taru.edu.cn` 的 Apereo CAS 5/6 + pac4j-oidc 接到站点第三方登录：

- **CAS 3.0**（默认）：`/authserver/login` + `/cas/p3/serviceValidate`。CAS 没有独立 `state` 参数，插件把宿主签发的 state 编码进 `service` URL，校验时必须用完全相同的 service 回放。
- **OIDC 授权码**：`/authserver/oidc/authorize` → `accessToken`（`client_secret_basic`）→ JWKS RS256 校验 `id_token` → `/profile` 取用户信息。`client_secret` 写入 `context.secrets()`，不进文档存储。
- 首次登录走宿主绑定流程（`BIND_REQUIRED`）：登录已有账号或注册新账号后再绑定，不自动开户。

## 部署前必做

1. 在宿主发布并安装 SPI `2.29.0`，部署含 `PluginExternalLoginProvider` 分发、`AuthEventListener` 与 `PluginUserService.findByExternalIdentity` 的宿主后端与登录页改动。
2. 把本站回调地址登记到学校网信中心的 CAS service 白名单。回调必须填**前端**回调路由（浏览器回跳到这里，页面再调后端完成登录）：

   `https://你的站点/external-login/callback`

   不要填 `https://你的站点/api/external-login/callback`：那是后端接口，浏览器直接回跳到接口上只会显示一段 JSON，既不完成登录也不会跳转。
   也不要写成 `https://其他域名@你的站点/...` 这种带 `user@` 的形式：浏览器会把 `@` 前的内容当成用户名、实际访问 `@` 后面的域名（曾因此在新标签页首次打开时报 404）。
   CAS 会追加 `ticket` 与已编码的 `state`；OIDC 会带回 `code` 与 `state`。该域名目前探测到的白名单只有 `oa / jwxt / mail / i.taru.edu.cn`，未登记会被 CAS 拒绝。
3. 管理员打开「CAS 单点登录 › 认证设置」，填写回调地址，选择协议并启用。角色需授予 `plugin:cas:manage` 才能看到菜单。

## 构建

需要 JDK 21。本机默认 JAVA_HOME 若是 17，请覆盖：

```powershell
$env:JAVA_HOME = "C:\Users\SiberianHusky\.jdks\ms-21.0.10"
cd D:\code\yudream-plugin-cas\frontend
pnpm install
pnpm run typecheck
pnpm run build
cd ..
& "C:\Users\SiberianHusky\dev\ide\IntelliJ IDEA\plugins\maven\lib\maven3\bin\mvn.cmd" test
& "C:\Users\SiberianHusky\dev\ide\IntelliJ IDEA\plugins\maven\lib\maven3\bin\mvn.cmd" package -DskipTests
```

JAR 必须包含：

```
plugin.yml
META-INF/yudream-plugin/frontend/cas/remoteEntry.js
META-INF/yudream-plugin/frontend/cas/manifest.json
```

把 JAR 放到宿主 `plugins/` 后启用。开发模式可把本目录登记进宿主插件开发源码加载。

## 目录

```
bootstrap/        插件入口，注册扩展、管理端点与全局挂件
domain/           设置/学生映射/学生档案聚合、协议枚举、仓储接口
application/      设置用例、学生信息用例（含绑定查询）、PluginExternalLoginProvider 实现
infrastructure/   文档存储、密钥库、CAS XML、OIDC JWT
interfaces/       管理端 HTTP + 公开门禁/预填端点
frontend/         Vite remote：设置页、学生信息页、Gate 绑定门禁挂件、Prefill 档案预填挂件
```

注：`frontend/node_modules` 是指向 `../yudream-plugin-cas/frontend/node_modules` 的 Windows junction（复用依赖，删除不影响源码）。
