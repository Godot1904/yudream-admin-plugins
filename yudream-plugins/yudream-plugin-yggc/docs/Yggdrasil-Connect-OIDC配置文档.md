# Yggdrasil Connect（OAuth 2.0 / OIDC）配置文档

> 适用插件：`yudream-plugin-union-yggdrasil-connect`（yggc）
> 协议版本对齐 Yggdrasil Connect 规范：OAuth 2.0 授权码 + PKCE（RFC 7636）、设备授权（RFC 8628）、OIDC 风格 ID Token（RS256）与发现文档。
> 本文所有配置项与端点均来自当前代码实现，非通用规范描述。

---

## 1. 协议端点

所有 OIDC 端点与传统 Yggdrasil 协议同根，挂在 `/api/plugins/yggc/api/yggdrasil` 前缀下（含插件挂载前缀 `/api/plugins/yggc`，见 `YggcHttpFacade.API_LOCATION`）。设 `ISSUER` 为认证服务器完整地址（见第 3.1 节 issuer 配置），本站即 `https://hall.mc.taru.xj.cn/api/plugins/yggc/api/yggdrasil`：

| 端点 | 方法 | 地址 | 用途 |
|---|---|---|---|
| 发现文档 | GET | `{ISSUER}/.well-known/openid-configuration` | OIDC Discovery，启动器据此自动发现全部端点 |
| JWKS | GET | `{ISSUER}/.well-known/jwks.json` | ID Token 验签公钥（RS256） |
| 授权 | GET | `{ISSUER}/oauth/authorize` | 授权码端点（展示授权确认页） |
| 令牌 | POST | `{ISSUER}/oauth/token` | 换取 / 刷新访问令牌、设备码轮询 |
| 设备授权 | POST | `{ISSUER}/oauth/device` | 发起 RFC 8628 设备授权 |
| UserInfo | GET | `{ISSUER}/userinfo` | 携带 Bearer 访问令牌获取用户信息 |

发现文档声明的能力（与实现一致）：

```json
{
  "response_types_supported": ["code"],
  "grant_types_supported": [
    "authorization_code",
    "refresh_token",
    "urn:ietf:params:oauth:grant-type:device_code"
  ],
  "id_token_signing_alg_values_supported": ["RS256"],
  "token_endpoint_auth_methods_supported": ["client_secret_basic", "client_secret_post", "none"],
  "code_challenge_methods_supported": ["S256", "plain"],
  "subject_types_supported": ["public"]
}
```

配置了共享客户端时，发现文档会额外输出一行（未配置则完全不输出该字段，行为与 1.1.0 一致）：

```json
{
  "shared_client_id": "yggc_xxxxxxxxxxxxxxxxxxxxxx"
}
```

---

## 1.1 共享客户端（shared_client_id）

部分 Yggdrasil Connect 启动器不内置本站的 `client_id`，只从发现文档读取 `shared_client_id`（例如 PCL-CE 的 Yggdrasil Connect 登录：内置表只认识 LittleSkin，其余站点必须提供该字段，且启动器界面没有填写 client_id 的入口）。为这类启动器配置一个公共客户端即可：

1. **OAuth 应用管理** → 新建应用：客户端类型选「公共客户端」（`publicClient = true`），回调地址留空。设备码流不校验 `client_secret`，也不使用 `redirect_uri`；回调留空同时避免共享 id 被拿去跑授权码流。
2. **插件配置 → OAuth 2.0 / OIDC → 共享客户端（shared_client_id）**：下拉选择刚创建的应用（只列出启用中的公共客户端），保存。
3. 启动器侧无需任何配置，重新读取元数据 / 发现文档即可看到 `shared_client_id` 并用它走设备码流。

行为与边界：

- 该字段是**纯增量**：不配置时发现文档与旧版本完全相同；既有应用的注册信息、密钥、回调地址与已签发令牌都不受影响。
- 保存时会校验绑定的应用**存在、启用中、且是公共客户端**，否则拒绝保存并给出原因。
- 被绑定为共享客户端的应用不能删除、禁用、改为机密客户端或重置密钥（会先要求解除绑定），避免依赖它的启动器整体无法登录。
- 若绑定的应用后来在别处被禁用或删除（例如直接改了存储、或升级前遗留的绑定），发现文档仍会按配置输出该 id，但启动器登录会收到 `invalid_client`；此时在配置页会看到「已不在候选列表」的提示，改选其他应用或清空即可。
- 共享 id 会公开在发现文档里：更换它会使用它的启动器需要重新登录；设备确认页会展示应用名称，建议把应用名起得能被用户认出来（例如「PCL-CE（共享登录）」）。
- 若配置了 issuer（`ygg_connect_server_url`）为另一个域名，注意启动器会校验「发现文档地址与 issuer 同源」；PCL-CE 在两者 authority 不一致时会直接拒绝。

---


## 2. 支持的 Scope

| Scope | 授权页文案 | 说明 |
|---|---|---|
| `openid` | 获取你的基础身份信息 | **必选**，缺失直接返回 `invalid_scope` |
| `profile` | 获取你的详细资料（昵称、邮箱） | ID Token / UserInfo 输出 `name`、`preferred_username`、`nickname`、`email` |
| `offline_access` | 离线访问（颁发刷新令牌） | 令牌响应携带 `refresh_token` |
| `Yggdrasil.PlayerProfiles.Select` | 获取你选择的游戏角色 | 授权页强制选角色，令牌绑定 `selectedProfile` |
| `Yggdrasil.PlayerProfiles.Read` | 获取你的全部角色列表 | 输出 `availableProfiles` |
| `Yggdrasil.Server.Join` | 使用该角色加入 Minecraft 多人服务器 | 进服校验必需 |

**组合规则（服务端强校验）**：

1. `scope` 必须包含 `openid`，且不得出现上表之外的值；
2. `Select` 与 `Read` 互斥，不能同时申请；
3. 申请 `Join` 时必须同时申请 `Select`（进服令牌必须绑定单一角色）。

典型启动器全量 scope：

```
openid profile offline_access Yggdrasil.PlayerProfiles.Select Yggdrasil.Server.Join
```

只读工具（如网页查角色）可用：

```
openid profile Yggdrasil.PlayerProfiles.Read
```

---

## 3. 管理端配置

入口：**插件管理 → Yggdrasil Connect → 插件配置**（`/platform/plugins/yggc/admin/settings`，需管理权限）。

### 3.1 OAuth 2.0 / OIDC 卡片

| 配置项 | 对应配置键 | 默认值 | 合法区间 / 说明 |
|---|---|---|---|
| OpenID 提供者标识符（issuer） | `ygg_connect_server_url` | 空 | **留空即用本站 API 地址**。部署反向代理或独立 Connect 服务地址时填写，影响发现文档与 ID Token 的 `iss`；必须是 `http(s)://` 绝对地址，结尾斜杠会被去除，非法值按空处理。注意：配置的 issuer 只改写 `iss` 等声明，端点本身仍由本站提供服务，因此反代需将该地址的路径完整转发到本站 API 根 |
| 禁用 Auth Server | `ygg_disable_authserver` | 关 | 开启后传统用户名密码登录（`authenticate` / `refresh` / `validate` / `invalidate` / `signout`）全部返回 403，**仅允许 OAuth 登录** |
| 访问令牌有效期（秒） | `oauthAccessTtl` | 604800（7 天） | 300 秒 ～ 365 天 |
| 刷新令牌有效期（秒） | `oauthRefreshTtl` | 2592000（30 天） | 300 秒 ～ 365 天 |
| 设备授权码有效期（秒） | `oauthDeviceTtl` | 600（10 分钟） | 60 秒 ～ 1 天 |

### 3.2 相关的常规配置

| 配置项 | 说明 |
|---|---|
| 认证服务器名称（`serverName`） | Yggdrasil metadata 的 `meta.serverName`，展示于启动器；留空回退站点名，最长 64 字符 |
| 额外皮肤白名单（`ygg_skin_domain`） | 半角逗号分隔的域名，会写入 metadata 的 `skinDomains` |

### 3.3 签名密钥

设置页提供"重新生成密钥对"操作，用于轮换 ID Token 签名密钥（RS256）与材质签名密钥。**重新生成后所有已签发的 ID Token 立即失效**，启动器会通过 JWKS 重新拉取新公钥，一般无需干预。

---

## 4. OAuth 应用（客户端）管理

入口：**插件管理 → Yggdrasil Connect → OAuth 应用管理**（`/platform/plugins/yggc/admin/clients`，需管理权限）。

### 4.1 应用类型

| 类型 | 适用场景 | 鉴权方式 |
|---|---|---|
| 机密客户端（默认） | 有后端可安全保存密钥的 Web 应用 | `client_secret`（Basic 或 POST 均可） |
| 公开客户端 | 桌面启动器、移动端等无法保密的环境 | 无 secret，**必须**使用 PKCE |

### 4.2 管理操作

- **创建**：填写应用名称 + 回调地址（`redirect_uri` 白名单，可多个、自动去重）。机密客户端创建时**明文 secret 仅显示一次**，请立即保存；公开客户端不颁发 secret。
- **编辑**：可改名称、回调地址、类型、启用/禁用。禁用后该应用的所有授权请求立即返回 `invalid_client`。
- **重置密钥**：重新生成 secret（同时将客户端转为机密类型），旧 secret 立即失效。
- **删除**：级联删除该应用的所有访问令牌与刷新令牌。
- **令牌管理**：`/platform/plugins/yggc/admin/tokens` 可查看 / 吊销任意令牌；吊销访问令牌会连带吊销其刷新令牌。

### 4.3 客户端 ID / Secret 形态

- `client_id`：`yggc_` + 22 位随机串（管理员可见）；
- `client_secret`：32 位随机串，**服务端只存 SHA-256 哈希**，遗失只能重置。

---

## 5. 授权码 + PKCE 流程（授权码模式）

以 `https://hall.mc.taru.xj.cn/api/plugins/yggc/api/yggdrasil` 为 `ISSUER`、公开客户端为例：

1. 客户端生成 `code_verifier`（43–128 字符）与 `code_challenge = BASE64URL(SHA256(verifier))`；
2. 打开授权页（浏览器 / 内嵌 WebView）：

```
GET {ISSUER}/oauth/authorize?
    response_type=code
    &client_id=yggc_xxxxxxxxxxxxxxxxxxxxxx
    &redirect_uri=http://127.0.0.1/callback
    &scope=openid%20profile%20offline_access%20Yggdrasil.PlayerProfiles.Select%20Yggdrasil.Server.Join
    &code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
    &code_challenge_method=S256
    &state=abc123
    &nonce=n-0S6_WzA2Mj
```

3. 用户登录后在授权确认页勾选角色（含 `Select` 时强制）并同意；
4. 站点 302 回跳：

```
http://127.0.0.1/callback?code=yggc_ac_xxx...&state=abc123
```

5. 客户端换取令牌：

```
POST {ISSUER}/oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code
&code=yggc_ac_xxx...
&redirect_uri=http://127.0.0.1/callback
&client_id=yggc_xxxxxxxxxxxxxxxxxxxxxx
&code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
```

机密客户端可用 `Authorization: Basic base64(client_id:client_secret)`，或附加 `client_secret` 表单字段。**带 PKCE 时不校验 secret；不带 PKCE 的请求（仅机密客户端允许）必须提供 secret。**

6. 令牌响应：

```json
{
  "access_token": "yggc_at_...",
  "token_type": "Bearer",
  "expires_in": 604800,
  "scope": "openid profile offline_access Yggdrasil.PlayerProfiles.Select Yggdrasil.Server.Join",
  "refresh_token": "yggc_rt_...",
  "id_token": "eyJhbGciOiJSUzI1NiIs..."
}
```

固定规则：授权码 5 分钟有效且**一次性**（用后即焚）；`redirect_uri` 必须与授权请求逐字一致；`code_challenge` 长度须为 43–128，仅支持 S256（写 `plain` 会被拒绝，不带 method 时按 S256 处理）。

---

## 6. 刷新令牌（旋转策略）

```
POST {ISSUER}/oauth/token
grant_type=refresh_token
&refresh_token=yggc_rt_...
&client_id=...
(&client_secret=...)   # 机密客户端必填
```

刷新令牌为**一次性**：每次刷新会旋转出新令牌并吊销旧访问令牌，旧刷新令牌立即失效。客户端必须以"最后一次收到的刷新令牌"为准，收到 `invalid_grant` 时应重新走授权流程。

---

## 7. 设备授权流程（RFC 8628）

适用于电视、主机等无输入界面的客户端：

1. 发起：

```
POST {ISSUER}/oauth/device
client_id=yggc_...&scope=openid%20Yggdrasil.PlayerProfiles.Select%20Yggdrasil.Server.Join
```

2. 响应：

```json
{
  "device_code": "yggc_dc_...",
  "user_code": "XXXX-XXXX",
  "verification_uri": "https://站点地址/platform/plugins/yggc/device",
  "verification_uri_complete": "https://站点地址/platform/plugins/yggc/device?user_code=XXXX-XXXX",
  "expires_in": 600,
  "interval": 5
}
```

3. 用户在任意设备打开 `verification_uri`，登录、选角色、确认或拒绝；
4. 客户端按 `interval` 秒轮询：

```
POST {ISSUER}/oauth/token
grant_type=urn:ietf:params:oauth:grant-type:device_code
&device_code=yggc_dc_...
&client_id=yggc_...
```

轮询结果：`authorization_pending`（等待）、`slow_down`（轮询过快）、`access_denied`（用户拒绝）、`expired_token`（过期）、或正常令牌响应（结构与第 5 节一致）。公开客户端轮询设备码**无需** secret。

---

## 8. ID Token 与 UserInfo

ID Token 为 RS256 JWT，有效期 10 分钟，公钥在 JWKS 端点发布。Claims 按申请的 scope 输出：

| Claim | 条件 | 内容 |
|---|---|---|
| `iss` / `sub` / `aud` / `iat` / `exp` | 总是 | issuer / 用户 ID / client_id / 时间戳 |
| `nonce` | 授权请求携带时 | 原样回传，防重放 |
| `selectedProfile` | 申请了 `Select` | `{id: 角色UUID, name: 角色名}` |
| `availableProfiles` | 申请了 `Read` | 全部角色数组 |
| `name` / `preferred_username` / `nickname` / `email` | 申请了 `profile` | 昵称 / 用户名 / 邮箱 |

UserInfo 端点（`GET {ISSUER}/userinfo`，`Authorization: Bearer <access_token>`）返回与上表相同的 claim 集合，另含 `aud`（client_id）。访问令牌过期返回 401 `invalid_token`。

**进服校验**：访问令牌携带 `Yggdrasil.Server.Join` 时可直接用于 `POST {ISSUER}/sessionserver/session/minecraft/join`，服务端校验 scope、角色绑定一致性与令牌有效性；会话记录保留 5 分钟。

---

## 9. 用户侧页面

| 页面 | 路径 | 权限 | 用途 |
|---|---|---|---|
| 认证服务器地址 | `/platform/plugins/yggc/me/endpoint` | 登录用户 | 查看地址、拖拽导入启动器、复制、添加到 SJMCL |
| 我的授权 | `/platform/plugins/yggc/me/grants` | 登录用户 | 查看各应用的令牌，可单吊销或整应用吊销 |
| 授权确认 | `/platform/plugins/yggc/authorize` | 登录用户 | OAuth 授权确认页（隐藏菜单） |
| 设备授权 | `/platform/plugins/yggc/device` | 登录用户 | RFC 8628 设备码确认页（隐藏菜单） |

---

## 10. 启动器接入

1. 启动器填写认证服务器地址 = `ISSUER`（即传统 Yggdrasil API 根地址，本站为 `https://hall.mc.taru.xj.cn/api/plugins/yggc/api/yggdrasil`，注意**必须包含插件挂载前缀** `/api/plugins/yggc`）；
2. 启动器读取 `{ISSUER}/.well-known/openid-configuration` 自动发现 OIDC 端点（metadata 中亦提供 `feature.openid_configuration_url`）；
3. 支持"认证服务器地址指示（ALI）"的启动器可从首页卡片 / 地址页直接**拖拽导入**；
4. 本站支持 SJMCL 深链：`sjmcl://add-auth-server?url=<URL 编码后的 ISSUER>`；
5. 用户在启动器中完成 OAuth 登录后，即可用绑定角色进服。

---

## 11. 安全注意事项

- `client_secret` 明文只在创建 / 重置时显示一次，服务端仅存哈希；
- 公开客户端强制 PKCE（S256），机密客户端未带 PKCE 时强制 secret；
- 授权码、刷新令牌均为一次性使用，刷新即旋转并吊销旧访问令牌；
- 删除 OAuth 应用会级联吊销其全部令牌；
- 生产环境建议： issuer 走 HTTPS、开启"禁用 Auth Server"以收敛攻击面（仅当全部客户端均已支持 OAuth 时）；
- 设备码轮询有 5 秒节流，过快返回 `slow_down`。
