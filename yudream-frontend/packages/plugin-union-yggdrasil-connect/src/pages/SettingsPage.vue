<script setup lang="ts">
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import type { YuDreamPluginSdk } from '@yudream/plugin-sdk'
import { FaButton, FaCard, FaPageHeader, FaPageMain, FaSwitch, FaTag, FaTextarea, useFaModal } from '@yudream/components'
import { onMounted } from 'vue'
import { useYggcSettings } from '../composables/useYggcSettings'

const props = defineProps<{
  sdk: YuDreamPluginSdk
  route?: RouteLocationNormalizedLoaded
}>()

void props.route

const model = useYggcSettings(props.sdk)
const confirm = useFaModal()

function askRegenerate(usage: 'texture' | 'token') {
  confirm.confirm({
    title: '重新生成密钥对',
    content: usage === 'texture'
      ? '确认重新生成材质签名密钥吗？已分发的旧签名将立即失效，在线启动器需重新获取元数据。'
      : '确认重新生成 ID Token 签名密钥吗？已签发的 ID Token 将立即失效，客户端需重新授权。',
    onConfirm: () => model.regenerateKeyPair(usage),
  })
}

onMounted(model.load)
</script>

<template>
  <section class="yggc-home">
    <FaPageHeader title="Yggdrasil Connect 设置" description="令牌、OAuth 2.0 / OIDC、MUA Union 与签名密钥配置。">
      <FaButton variant="outline" :loading="model.loading" @click="model.load">
        刷新
      </FaButton>
      <FaButton variant="outline" @click="model.reset">
        恢复默认值
      </FaButton>
      <FaButton :loading="model.saving" @click="model.save">
        保存配置
      </FaButton>
    </FaPageHeader>

    <FaPageMain>
      <div class="yggc-settings-grid">
        <FaCard title="常规配置" description="令牌 / 频率 / 皮肤白名单" content-class="yggc-card-content">
          <label>
            <span>认证服务器名称</span>
            <input v-model="model.form.serverName" type="text" maxlength="64" class="yggc-input" placeholder="留空则使用站点名称">
            <p class="yggc-help">写入 Yggdrasil 元数据的 meta.serverName，展示于启动器添加认证服务器时的名称。留空回退到站点 app.name。</p>
          </label>
          <div class="yggc-form-grid two">
            <label>
              <span>令牌暂时失效时间（秒）</span>
              <input v-model.number="model.form.tokenExpire" type="number" min="60" class="yggc-input">
              <p class="yggc-help">对应 ygg_token_expire_1，超过后刷新令牌需要重新登录。默认 259200（3 天）。</p>
            </label>
            <label>
              <span>令牌完全失效时间（秒）</span>
              <input v-model.number="model.form.tokenRefreshExpire" type="number" min="60" class="yggc-input">
              <p class="yggc-help">对应 ygg_token_expire_2，超过后必须重新登录。默认 604800（7 天）。</p>
            </label>
          </div>
          <div class="yggc-form-grid two">
            <label>
              <span>令牌数量限制（每用户）</span>
              <input v-model.number="model.form.tokensLimit" type="number" min="1" max="1000" class="yggc-input">
              <p class="yggc-help">对应 ygg_tokens_limit，超出后最早的会话将被吊销。默认 10。</p>
            </label>
            <label>
              <span>登录 / 登出频率限制（毫秒）</span>
              <input v-model.number="model.form.rateLimit" type="number" min="0" max="600000" class="yggc-input">
              <p class="yggc-help">对应 ygg_rate_limit，0 表示不限制。默认 1000。</p>
            </label>
          </div>
          <div class="yggc-form-grid two">
            <label>
              <span>批量查询角色数量上限</span>
              <input v-model.number="model.form.searchProfileMax" type="number" min="1" max="100" class="yggc-input">
              <p class="yggc-help">对应 ygg_search_profile_max。默认 5。</p>
            </label>
            <label>
              <span>UUID 生成算法</span>
              <select v-model="model.form.uuidAlgorithm" class="yggc-input">
                <option value="v3">v3（离线 UUID）</option>
                <option value="v4">v4（随机 UUID）</option>
              </select>
              <p class="yggc-help">角色 UUID 由 yudream-skin 插件生成，此项作为站点规范保留，供接入方对齐。</p>
            </label>
          </div>
          <label>
            <span>额外皮肤白名单域名</span>
            <FaTextarea v-model="model.form.skinDomain" class="w-full" placeholder="半角逗号分隔，例如 cdn.example.com,example.org" />
            <p class="yggc-help">对应 ygg_skin_domain，材质服务器域名会自动加入白名单，此处追加自定义域名。</p>
          </label>
          <div class="yggc-form-grid two">
            <label>
              <span>API 地址指示（ALI）</span>
              <FaSwitch v-model="model.form.enableAli" />
              <p class="yggc-help">对应 ygg_enable_ali，开启后在协议响应中注入 X-Authlib-Injector-API-Location 头。</p>
            </label>
            <label>
              <span>启用 Restore API</span>
              <FaSwitch v-model="model.form.restoreApi" />
              <p class="yggc-help">对应 ygg_restore_api，允许第三方后端提交角色资料并由本站私钥重签名，谨慎开启。</p>
            </label>
          </div>
          <label>
            <span>用户中心显示快速配置板块</span>
            <FaSwitch v-model="model.form.showConfigSection" />
            <p class="yggc-help">对应 ygg_show_config_section。当前站点登录地址统一由本页与状态页展示，此项保留开关语义。</p>
          </label>
        </FaCard>

        <FaCard title="OAuth 2.0 / OIDC" description="Yggdrasil Connect" content-class="yggc-card-content">
          <label>
            <span>OpenID 提供者标识符（issuer）</span>
            <input v-model="model.form.connectServerUrl" class="yggc-input" placeholder="留空则使用本站 API 地址，例如 https://ygg.example.com/api/plugins/yggc/api/yggdrasil">
            <p class="yggc-help">对应 ygg_connect_server_url。部署反向代理或独立 Connect 服务地址时填写，影响发现文档与 ID Token 的 iss。</p>
          </label>
          <label>
            <span>禁用 Auth Server（传统用户名密码登录）</span>
            <FaSwitch v-model="model.form.disableAuthserver" />
            <p class="yggc-help">对应 ygg_disable_authserver。开启后 authenticate / refresh / validate / invalidate / signout 全部返回 403，仅允许 OAuth 登录。</p>
          </label>
          <div class="yggc-form-grid two">
            <label>
              <span>访问令牌有效期（秒）</span>
              <input v-model.number="model.form.oauthAccessTtl" type="number" min="300" class="yggc-input">
              <p class="yggc-help">默认 604800（7 天）。</p>
            </label>
            <label>
              <span>刷新令牌有效期（秒）</span>
              <input v-model.number="model.form.oauthRefreshTtl" type="number" min="300" class="yggc-input">
              <p class="yggc-help">默认 2592000（30 天），不能小于访问令牌有效期。</p>
            </label>
          </div>
          <label>
            <span>设备授权码有效期（秒）</span>
            <input v-model.number="model.form.oauthDeviceTtl" type="number" min="60" class="yggc-input">
            <p class="yggc-help">RFC 8628 设备码轮询有效期。默认 600（10 分钟）。</p>
          </label>
          <label>
            <span>共享客户端（shared_client_id）</span>
            <select v-model="model.form.sharedClientId" class="yggc-input">
              <option value="">不启用（发现文档不输出 shared_client_id）</option>
              <option v-for="client in model.sharedCandidates" :key="client.id" :value="client.id">
                {{ client.name }}（{{ client.id }}）
              </option>
            </select>
            <p class="yggc-help">
              写入 OIDC 发现文档的 <code>shared_client_id</code>，让没有内置 client_id 的启动器（如 PCL-CE）直接使用该应用登录。
              只能选启用中的公共客户端；回调地址留空即可（设备流不需要 redirect_uri，留空也能避免共享 id 被用于授权码流）。
              留空或未绑定时行为与旧版本完全一致。
            </p>
            <p v-if="model.form.sharedClientId && !model.sharedCandidates.some(item => item.id === model.form.sharedClientId)" class="yggc-help">
              注意：当前绑定的应用已不在候选列表（被禁用、改为机密客户端或已删除），保存时会被拒绝；请改选其他应用或清空。
            </p>
          </label>
        </FaCard>

        <FaCard content-class="yggc-card-content">
          <template #header>
            <div class="yggc-card-head">
              <div>
                <h3 class="yggc-card-head__title">MUA 相关配置</h3>
                <p class="yggc-card-head__desc">跨站联邦 · Minecraft 高校联盟（MUA）</p>
              </div>
              <FaButton size="sm" variant="outline" :loading="model.diagnosing" @click="model.diagnoseUnion">
                自助诊断
              </FaButton>
            </div>
          </template>
          <label>
            <span>MUA API Root</span>
            <input v-model="model.form.unionApiRoot" class="yggc-input" placeholder="https://skin.mualliance.ltd/api/union">
          </label>
          <label>
            <span>MUA Member Key</span>
            <input v-model="model.form.unionMemberKey" type="password" class="yggc-input" placeholder="未设置">
            <p class="yggc-help">由 MUA 主服务器分配的成员密钥，仅保存在本站。</p>
          </label>
          <label>
            <span>允许 MUA 数据自动更新</span>
            <FaSwitch v-model="model.form.unionEnableUpdate" />
            <p class="yggc-help">对应 union_enable_update。开启后接受主服务器下发的成员回调（列表更新 / 私钥轮换 / 密钥轮换 / UUID 重映射 / 插件更新通知）。插件更新包仍需管理员手动安装。</p>
          </label>
          <label>
            <span>启用 MUA OAuth2</span>
            <FaSwitch v-model="model.form.unionEnableOauth2" />
            <p class="yggc-help">对应 union_enable_oauth2。开启后 MUA 主服务器可通过 OAuth2 流程使用本站账号登录（暴露 /union/member/oauth2 公钥与 grant 端点）。</p>
          </label>
          <label>
            <span>定期同步角色到 MUA 主服务器</span>
            <FaSwitch v-model="model.form.unionSyncEnabled" />
            <p class="yggc-help">
              开启后按下面的间隔做一次增量对账：新建的角色推送（POST /profile）、改名的更新（PUT /profile/&#123;uuid&#125;）、
              已删除的从主服务器移除（DELETE /profile/&#123;uuid&#125;）。每次只推送有差异的条目，角色没有变化时不打扰主服务器。
            </p>
          </label>
          <label>
            <span>角色同步间隔（分钟）</span>
            <input
              v-model.number="model.form.unionSyncIntervalMinutes"
              class="yggc-input"
              type="number"
              min="1"
              max="1440"
              step="1"
            >
            <p class="yggc-help">1 - 1440 分钟，默认 10。间隔越短，新角色越快到主服务器，但对本站与主服务器的压力也越大。</p>
          </label>
          <label>
            <span>玩家登录时补推新角色</span>
            <FaSwitch v-model="model.form.unionSyncOnLogin" />
            <p class="yggc-help">
              角色是在皮肤站创建的，插件无法在「点下创建」的那一刻收到通知。开启后，玩家用启动器登录或进入服务器时会立即补推他本人的新角色，
              相当于把「创建后第一次使用」当作推送时机；不想额外产生上游请求时可以关闭，只依赖定时对账。
            </p>
          </label>
          <div v-if="model.diagnosis" class="yggc-diagnosis">
            <div class="yggc-status-line">
              <span>连通性</span>
              <FaTag :variant="model.diagnosis.reachable ? 'default' : 'destructive'">
                {{ model.diagnosis.reachable ? '可访问' : '不可访问' }}
              </FaTag>
            </div>
            <div class="yggc-status-line"><span>HTTP 状态码</span><strong>{{ model.diagnosis.status ?? '-' }}</strong></div>
            <div class="yggc-status-line"><span>延迟</span><strong>{{ model.diagnosis.latencyMs ?? '-' }} ms</strong></div>
            <div class="yggc-status-line"><span>Member Key</span><strong>{{ model.diagnosis.memberKeyConfigured ? '已配置' : '未配置' }}</strong></div>
            <p class="yggc-help">{{ model.diagnosis.message }}</p>
            <pre v-if="model.diagnosis.body" class="yggc-diagnosis-body">{{ model.diagnosis.body }}</pre>
          </div>
        </FaCard>

        <FaCard title="签名密钥对" description="RSA" content-class="yggc-card-content">
          <label>
            <span>
              材质签名公钥（SHA1withRSA）
              <FaTag :variant="model.unionState?.privateKeySynced ? 'default' : 'secondary'">
                {{ model.unionState?.privateKeySynced
                  ? `MUA 主服务器分发（版本 ${model.unionState?.privateKey?.version ?? '-'}）`
                  : '本地生成' }}
              </FaTag>
            </span>
            <p v-if="model.unionState?.privateKeySynced" class="yggc-help">
              私钥由 MUA 主服务器生成并经 GET /privatekey 分发，同步时间
              {{ model.unionState?.privateKey?.syncedAt ? new Date(model.unionState.privateKey.syncedAt).toLocaleString() : '-' }}。
              上游轮换私钥时会自动回调本站更新；本地重新生成已被禁用。
            </p>
            <p v-else class="yggc-help">
              当前材质签名密钥为本站生成。配置 MUA API Root 与 Member Key 后点击下方按钮，改用主服务器分发的签名私钥（跨站兼容要求）。
            </p>
            <pre class="yggc-keybox">{{ model.keyPairs.texture?.publicKey || '尚未生成' }}</pre>
            <div class="yggc-actions">
              <FaButton size="sm" :loading="model.syncingKey" @click="model.syncUnionPrivateKey">从上游同步签名私钥</FaButton>
              <FaButton v-if="!model.unionState?.privateKeySynced" size="sm" variant="destructive" @click="askRegenerate('texture')">
                重新生成材质密钥
              </FaButton>
            </div>
          </label>
          <label>
            <span>ID Token 签名密钥（RS256，2048 位）</span>
            <div class="yggc-status-line"><span>密钥标识（kid）</span><code>{{ model.keyPairs.token?.kid || '-' }}</code></div>
            <div class="yggc-actions">
              <FaButton size="sm" variant="destructive" @click="askRegenerate('token')">重新生成 JWT 密钥</FaButton>
            </div>
          </label>
          <label>
            <span>MUA OAuth2 站点签名密钥（RSA-SHA256，2048 位）</span>
            <p class="yggc-help">用于向 MUA 主服务器签发 userInfoToken（启用 MUA OAuth2 时自动生成本地密钥对）。</p>
            <pre class="yggc-keybox">{{ model.keyPairs['union-oauth2']?.publicKey || '尚未生成，启用 MUA OAuth2 后自动生成' }}</pre>
          </label>
        </FaCard>
      </div>
    </FaPageMain>
  </section>
</template>
