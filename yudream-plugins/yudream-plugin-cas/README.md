# 塔里木大学统一身份认证插件

独立插件项目，不并入 `yudream-admin-plugins` 仓库，也不做 Git 提交。约定对齐插件仓 `AGENTS.md` 与宿主仓 SPI。

- 插件 code：`cas`
- 版本：`2.0.0`
- 依赖宿主 SPI：`2.29.0`（`PluginExternalLoginProvider`、`PluginGlobalWidget`、`PluginUserService.findByExternalIdentity`）
- 软依赖：`yudream-student-info`（只读，取学院 / 班级；未安装时相关列显示为空）
- 登录入口由宿主登录页 Tabs 渲染，插件贡献协议实现、管理设置页、学生信息页与绑定门禁挂件

## v2.0.0 破坏性变更

1. **移除「学生信息映射」**：不再把认证属性映射成学院 / 专业 / 年级 / 班级，也**不向学生档案插件写入或预填任何信息**：
   - 下线 `cas/Prefill`「完善学生档案」预填挂件与 `GET /api/plugins/cas/api/me/profile` 端点；
   - 认证账号档案（collection `cas-students`）只保留认证身份与登录统计：学号、姓名、邮箱、电话、协议、原始属性、首次/最近登录、登录次数；
   - 学院 / 班级改由「学生信息」页按学号从 `yudream-student-info` 插件**只读**获取（其 API `PluginStudentInfoService.findStudentInfoByStudentNo`），专业 / 年级列不再展示。
2. **访问控制简化**：原 `requireBinding` 开关保留，端点由 `/admin/mapping` 改为 `/admin/access-control`（配置聚合 `StudentMapping` → `AccessControl`，collection `student-mapping` → `access-control`；旧配置不迁移，开关默认关闭）。
3. **软依赖隔离**：provider 类型引用隔离在 `infrastructure/archive/StudentArchiveQueryFactory`，只在 `dependencyAvailable` 通过后实例化，未安装时不抛 `NoClassDefFoundError`。
4. **修复**：「学生信息」页「首次记录 / 最近登录」显示 `Invalid Date`——宿主把 `Long` 序列化成字符串，前端未转换。

## v1.1.0 新增

1. **管理端查看绑定信息**：「学生信息」页列表「绑定账号」列 + 详情「绑定信息」块（本站账号 / 用户 ID / 昵称 / 邮箱 / 手机 / 账号状态）。
   - 数据来源：宿主 SPI 2.29.0 的 `PluginUserService.findByExternalIdentity(providerCode, platformType, socialUid)`；`platformType` 取档案自身记录的协议（CAS/OIDC），切换协议后历史绑定仍能查到。
   - 降级：宿主不支持该能力、未提供用户服务或绑定用户已删除时，该列显示「无法查询」并给出原因，列表与其余功能照常（fail-open）。
   - 只能看「学工号 → 本站账号」这一方向：SPI 没有反向（按本站账号列绑定）的契约。

## v1.0.5 新增（v2.0.0 已移除预填挂件）

1. **学生档案预填（对接 `yudream-student-info` 插件）**：已绑定 CAS 且尚未填写学生档案的用户，右下角出现「完善学生档案」提醒；表单自动带入 CAS 属性（姓名/学号/学院，认证中心返回班级时一并带入），保存直接写入 `yudream-student-info` 插件的 `PUT /api/plugins/yudream-student-info/api/me`。**（v2.0.0 起本插件不再向学生档案插件写入任何信息，该挂件与 `/me/profile` 端点已下线）**
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
domain/           设置/访问控制/学生档案聚合、协议枚举、学生档案查询端口、仓储接口
application/      设置用例、访问控制用例、学生信息用例（含绑定与学院/班级读取）
infrastructure/   文档存储、密钥库、CAS XML、OIDC JWT、学生档案插件只读适配器
interfaces/       管理端 HTTP + 公开门禁端点
frontend/         Vite remote：设置页、学生信息页、Gate 绑定门禁挂件
```

注：`frontend/node_modules` 是指向 `../yudream-plugin-cas/frontend/node_modules` 的 Windows junction（复用依赖，删除不影响源码）。
