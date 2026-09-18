package online.yudream.base.plugin.yggc.bootstrap;

import online.yudream.base.plugin.spi.annotation.PluginDashboardCard;
import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginPermissions;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.yggc.application.service.YggcAppService;
import online.yudream.base.plugin.yggc.application.service.YggcOAuthService;
import online.yudream.base.plugin.yggc.application.service.YggcProfileSyncService;
import online.yudream.base.plugin.yggc.application.service.YggcSettingsService;
import online.yudream.base.plugin.yggc.application.service.YggcUnionService;
import online.yudream.base.plugin.yggc.infrastructure.repository.YggcRepository;
import online.yudream.base.plugin.yggc.infrastructure.service.YggcCryptoService;
import online.yudream.base.plugin.yggc.infrastructure.service.YggcUnionClient;
import online.yudream.base.plugin.yggc.infrastructure.service.YggcUnionProfileSyncScheduler;
import online.yudream.base.plugin.yggc.infrastructure.support.YggcUnionHostVerifier;
import online.yudream.base.plugin.yggc.interfaces.controller.YggcAdminController;
import online.yudream.base.plugin.yggc.interfaces.controller.YggcOAuthController;
import online.yudream.base.plugin.yggc.interfaces.controller.YggcProtocolController;
import online.yudream.base.plugin.yggc.interfaces.controller.YggcUnionController;
import online.yudream.base.plugin.yggc.interfaces.controller.YggcUserController;
import online.yudream.base.plugin.yggc.interfaces.http.YggcHttpFacade;

@PluginSpec(
        code = YggcPlugin.CODE,
        name = "Union Yggdrasil Connect",
        version = "1.3.0",
        description = "传统 Yggdrasil 协议 + Yggdrasil Connect（OAuth 2.0 / OIDC，Janus 能力内置）：授权码 + PKCE、设备授权、刷新令牌旋转、RS256 ID Token。",
        dependencies = {"yudream-skin"}
)
@PluginPermissions({
        @PluginPermission(code = YggcPlugin.USE_PERMISSION, name = "使用 Yggdrasil Connect", module = "平台插件", description = "登录启动器并管理个人 OAuth 授权"),
        @PluginPermission(code = YggcPlugin.MANAGE_PERMISSION, name = "管理 Yggdrasil Connect", module = "平台插件", description = "管理 OAuth 应用、令牌与插件状态")
})
@PluginDashboardCard(
        code = "yggc-endpoints",
        title = "Yggdrasil Connect",
        description = "启动器验证与 OAuth 登录服务地址。",
        icon = "i-ri:shield-keyhole-line",
        category = "认证服务",
        component = "yggc/EndpointCard",
        actionPath = "/platform/plugins/yggc/admin/status",
        dragPayloadTemplate = "authlib-injector:yggdrasil-server:{encodedUrl}",
        tone = "violet",
        defaultW = 4,
        defaultH = 2,
        minW = 3,
        minH = 2,
        sort = 36
)
@PluginFrontend(
        moduleName = "yggc",
        menuTitle = "Yggdrasil Connect",
        menuIcon = "i-ri:shield-keyhole-line",
        menuSort = 37,
        styles = {"style.css"},
        routes = {
                @PluginRoute(
                        path = "/platform/plugins/yggc/admin/status",
                        name = "platform-plugin-yggc-admin-status",
                        title = "Yggdrasil Connect 状态",
                        icon = "i-ri:shield-keyhole-line",
                        component = "yggc/AdminStatus",
                        permission = YggcPlugin.MANAGE_PERMISSION,
                        sort = 10
                ),
                @PluginRoute(
                        path = "/platform/plugins/yggc/admin/clients",
                        name = "platform-plugin-yggc-admin-clients",
                        title = "OAuth 应用管理",
                        icon = "i-ri:app-store-2-line",
                        component = "yggc/AdminClients",
                        permission = YggcPlugin.MANAGE_PERMISSION,
                        sort = 20
                ),
                @PluginRoute(
                        path = "/platform/plugins/yggc/admin/settings",
                        name = "platform-plugin-yggc-admin-settings",
                        title = "插件配置",
                        icon = "i-ri:settings-3-line",
                        component = "yggc/AdminSettings",
                        permission = YggcPlugin.MANAGE_PERMISSION,
                        sort = 25
                ),
                @PluginRoute(
                        path = "/platform/plugins/yggc/admin/union",
                        name = "platform-plugin-yggc-admin-union",
                        title = "MUA 状态",
                        icon = "i-ri:global-line",
                        component = "yggc/AdminUnion",
                        permission = YggcPlugin.MANAGE_PERMISSION,
                        sort = 27
                ),
                @PluginRoute(
                        path = "/platform/plugins/yggc/admin/blacklist",
                        name = "platform-plugin-yggc-admin-blacklist",
                        title = "联合黑名单",
                        icon = "i-ri:forbid-line",
                        component = "yggc/AdminBlacklist",
                        permission = YggcPlugin.MANAGE_PERMISSION,
                        sort = 28
                ),
                @PluginRoute(
                        path = "/platform/plugins/yggc/me/endpoint",
                        name = "platform-plugin-yggc-me-endpoint",
                        title = "认证服务器地址",
                        icon = "i-ri:link",
                        component = "yggc/MyEndpoint",
                        permission = YggcPlugin.USE_PERMISSION,
                        sort = 35
                ),
                @PluginRoute(
                        path = "/platform/plugins/yggc/me/union",
                        name = "platform-plugin-yggc-me-union",
                        title = "跨站角色",
                        icon = "i-ri:exchange-line",
                        component = "yggc/MyUnion",
                        permission = YggcPlugin.USE_PERMISSION,
                        sort = 45
                ),
                @PluginRoute(
                        path = "/platform/plugins/yggc/admin/tokens",
                        name = "platform-plugin-yggc-admin-tokens",
                        title = "OAuth 令牌管理",
                        icon = "i-ri:key-line",
                        component = "yggc/AdminTokens",
                        permission = YggcPlugin.MANAGE_PERMISSION,
                        sort = 30
                ),
                @PluginRoute(
                        path = "/platform/plugins/yggc/me/grants",
                        name = "platform-plugin-yggc-me-grants",
                        title = "我的授权",
                        icon = "i-ri:user-shared-line",
                        component = "yggc/MyGrants",
                        permission = YggcPlugin.USE_PERMISSION,
                        sort = 40
                ),
                @PluginRoute(
                        path = "/platform/plugins/yggc/authorize",
                        name = "platform-plugin-yggc-authorize",
                        title = "授权确认",
                        component = "yggc/Authorize",
                        permission = YggcPlugin.USE_PERMISSION,
                        hideInMenu = true,
                        sort = 50
                ),
                @PluginRoute(
                        path = "/platform/plugins/yggc/device",
                        name = "platform-plugin-yggc-device",
                        title = "设备授权",
                        component = "yggc/Device",
                        permission = YggcPlugin.USE_PERMISSION,
                        hideInMenu = true,
                        sort = 60
                )
        }
)
public class YggcPlugin implements YuDreamPlugin {

    public static final String CODE = "yggc";
    public static final String USE_PERMISSION = "plugin:yggc:use";
    public static final String MANAGE_PERMISSION = "plugin:yggc:manage";

    @Override
    public void onEnable(PluginContext context) {
        YggcRepository repository = new YggcRepository(context.documents());
        YggcCryptoService cryptoService = new YggcCryptoService(repository);
        YggcSettingsService settingsService = new YggcSettingsService(repository);
        YggcUnionClient unionClient = new YggcUnionClient();
        // 角色同步：定时对账 + 玩家登录时定向补推；关闭插件时线程必须一起释放。
        YggcProfileSyncService profileSyncService = new YggcProfileSyncService(
                context, repository, settingsService, unionClient);
        YggcUnionProfileSyncScheduler profileSyncScheduler = new YggcUnionProfileSyncScheduler(
                settingsService, profileSyncService);
        context.onDispose(profileSyncScheduler);
        profileSyncScheduler.start();
        YggcAppService appService = new YggcAppService(context, repository, cryptoService, settingsService,
                profileSyncScheduler);
        YggcOAuthService oauthService = new YggcOAuthService(context, repository, cryptoService, appService, settingsService);
        YggcUnionService unionService = new YggcUnionService(context, repository, settingsService,
                cryptoService, unionClient);
        YggcUnionHostVerifier unionHostVerifier = new YggcUnionHostVerifier(unionClient, cryptoService);
        YggcHttpFacade http = new YggcHttpFacade(appService, oauthService, context.framework(),
                settingsService, unionClient, cryptoService, unionService, unionHostVerifier, profileSyncService);
        context.registerHttpController(new YggcProtocolController(http));
        context.registerHttpController(new YggcOAuthController(http));
        context.registerHttpController(new YggcAdminController(http));
        context.registerHttpController(new YggcUserController(http));
        context.registerHttpController(new YggcUnionController(http));
    }
}
