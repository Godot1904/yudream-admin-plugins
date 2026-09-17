package online.yudream.base.plugin.yggc.interfaces.controller;

import online.yudream.base.plugin.spi.annotation.PluginHttpEndpoint;
import online.yudream.base.plugin.spi.http.PluginHttpRequest;
import online.yudream.base.plugin.spi.http.PluginHttpResponse;
import online.yudream.base.plugin.yggc.bootstrap.YggcPlugin;
import online.yudream.base.plugin.yggc.interfaces.http.YggcHttpFacade;

/**
 * 管理端点：状态 / OAuth 客户端 / 令牌管理。
 */
public class YggcAdminController {
    private final YggcHttpFacade http;

    public YggcAdminController(YggcHttpFacade http) {
        this.http = http;
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/status", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse status(PluginHttpRequest request) {
        return http.status(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/clients", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse listClients(PluginHttpRequest request) {
        return http.listClients(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/shared-clients", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse eligibleSharedClients(PluginHttpRequest request) {
        return http.eligibleSharedClients(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/clients", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse createClient(PluginHttpRequest request) {
        return http.createClient(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/clients/{id}", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse updateClient(PluginHttpRequest request) {
        return http.updateClient(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/admin/clients/{id}", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse deleteClient(PluginHttpRequest request) {
        return http.deleteClient(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/clients/{id}/secret", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse resetClientSecret(PluginHttpRequest request) {
        return http.resetClientSecret(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/tokens", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse listTokens(PluginHttpRequest request) {
        return http.listTokens(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/admin/tokens/{token}", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse revokeToken(PluginHttpRequest request) {
        return http.revokeToken(request);
    }

    // ---- 插件配置（对应原 yggdrasil-connect Option 配置）----

    @PluginHttpEndpoint(method = "GET", path = "/admin/config", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse getConfig(PluginHttpRequest request) {
        return http.getConfig(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/config", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse updateConfig(PluginHttpRequest request) {
        return http.updateConfig(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/config/reset", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse resetConfig(PluginHttpRequest request) {
        return http.resetConfig(request);
    }

    /** usage = texture（材质签名密钥）| token（JWT 签名密钥）。 */
    @PluginHttpEndpoint(method = "POST", path = "/admin/config/keypair/{usage}", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse regenerateKeyPair(PluginHttpRequest request) {
        return http.regenerateKeyPair(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/config/union/diagnose", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse diagnoseUnion(PluginHttpRequest request) {
        return http.diagnoseUnion(request);
    }

    // ---- 管理端：Union 联邦同步与黑名单 ----

    @PluginHttpEndpoint(method = "GET", path = "/admin/union/status", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse unionStatus(PluginHttpRequest request) {
        return http.unionStatus(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/union/sync-privatekey", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse syncUnionPrivateKey(PluginHttpRequest request) {
        return http.syncUnionPrivateKey(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/union/sync-serverlist", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse syncUnionServerList(PluginHttpRequest request) {
        return http.syncUnionServerList(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/union/sync-profiles", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse syncUnionProfiles(PluginHttpRequest request) {
        return http.syncUnionProfiles(request);
    }

    /** 增量对账：与定时任务同一条路径，只补推有差异的角色。 */
    @PluginHttpEndpoint(method = "POST", path = "/admin/union/reconcile-profiles", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse reconcileUnionProfiles(PluginHttpRequest request) {
        return http.reconcileUnionProfiles(request);
    }

    @PluginHttpEndpoint(method = "GET", path = "/admin/union/blacklist", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse blacklistQuery(PluginHttpRequest request) {
        return http.blacklistQuery(request);
    }

    @PluginHttpEndpoint(method = "POST", path = "/admin/union/blacklist", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse blacklistCreate(PluginHttpRequest request) {
        return http.blacklistCreate(request);
    }

    @PluginHttpEndpoint(method = "PUT", path = "/admin/union/blacklist/invalidate/{id}", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse blacklistInvalidate(PluginHttpRequest request) {
        return http.blacklistInvalidate(request);
    }

    @PluginHttpEndpoint(method = "DELETE", path = "/admin/union/blacklist/{id}", permission = YggcPlugin.MANAGE_PERMISSION)
    public PluginHttpResponse blacklistDelete(PluginHttpRequest request) {
        return http.blacklistDelete(request);
    }
}
