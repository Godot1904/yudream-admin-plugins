package online.yudream.base.plugin.eduroam.bootstrap;

import online.yudream.base.plugin.eduroam.application.service.EduroamAppService;
import online.yudream.base.plugin.eduroam.application.service.EduroamLoginProvider;
import online.yudream.base.plugin.eduroam.domain.repo.EduroamAccountRepository;
import online.yudream.base.plugin.eduroam.domain.repo.EduroamAttemptRepository;
import online.yudream.base.plugin.eduroam.domain.repo.EduroamLoginTicketRepository;
import online.yudream.base.plugin.eduroam.domain.repo.EduroamSettingsRepository;
import online.yudream.base.plugin.eduroam.infrastructure.repository.EduroamAccountDocumentRepository;
import online.yudream.base.plugin.eduroam.infrastructure.repository.EduroamAttemptDocumentRepository;
import online.yudream.base.plugin.eduroam.infrastructure.repository.EduroamLoginTicketDocumentRepository;
import online.yudream.base.plugin.eduroam.infrastructure.repository.EduroamSettingsDocumentRepository;
import online.yudream.base.plugin.eduroam.infrastructure.service.FrameworkLocalUserDirectory;
import online.yudream.base.plugin.eduroam.infrastructure.service.HttpEduroamProbe;
import online.yudream.base.plugin.eduroam.infrastructure.support.AttemptRateLimiter;
import online.yudream.base.plugin.eduroam.interfaces.controller.EduroamAdminController;
import online.yudream.base.plugin.eduroam.interfaces.controller.EduroamPublicController;
import online.yudream.base.plugin.eduroam.interfaces.http.EduroamHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginPermissions;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginProvider;

/**
 * Eduroam 第三方登录插件。
 *
 * <p>与 CAS 插件同构：向宿主注册一个 {@link PluginExternalLoginProvider}，登录页据此渲染
 * 「Eduroam 认证」入口；凭据校验、账号台账与封禁由插件自己承载。
 *
 * <p>与 CAS 的唯一差别是「授权方」：CAS 会把浏览器跳到学校统一认证服务器，而 Eduroam 校验的是
 * 校园网账号密码本身，所以插件公开页 {@code /eduroam} 就是凭据页——它认证成功后拿一次性票据
 * 回宿主回调端点，由宿主完成绑定与会话签发。
 */
@PluginSpec(
        code = EduroamPlugin.CODE,
        name = "Eduroam 认证",
        version = EduroamPlugin.VERSION,
        description = "用校园网 / Eduroam 账号密码作为第三方登录：登录页一键进入凭据页，校验通过后由宿主完成绑定与会话签发。",
        icon = "i-ri:wifi-line"
)
@PluginPermissions({
        @PluginPermission(code = EduroamPlugin.MANAGE_PERMISSION, name = "管理 Eduroam 登录", module = "Eduroam 认证",
                description = "查看登录过的校园网账号与尝试审计、封禁或解封账号、维护渠道配置")
})
@PluginFrontend(
        moduleName = "eduroam",
        menuTitle = "Eduroam 认证",
        menuIcon = "i-ri:wifi-line",
        menuSort = 49,
        styles = {"style.css"},
        routes = {
                @PluginRoute(
                        path = "/eduroam",
                        name = "eduroam-login",
                        title = "Eduroam 登录",
                        icon = "i-ri:wifi-line",
                        component = "eduroam/Public",
                        hideInMenu = true,
                        publicAccess = true
                ),
                @PluginRoute(
                        path = "/platform/plugins/eduroam/admin/accounts",
                        name = "platform-plugin-eduroam-admin-accounts",
                        title = "登录账号",
                        icon = "i-ri:user-shared-line",
                        component = "eduroam/AdminAccounts",
                        permission = EduroamPlugin.MANAGE_PERMISSION,
                        sort = 90
                ),
                @PluginRoute(
                        path = "/platform/plugins/eduroam/admin/attempts",
                        name = "platform-plugin-eduroam-admin-attempts",
                        title = "尝试审计",
                        icon = "i-ri:history-line",
                        component = "eduroam/AdminAttempts",
                        permission = EduroamPlugin.MANAGE_PERMISSION,
                        sort = 91
                ),
                @PluginRoute(
                        path = "/platform/plugins/eduroam/admin/settings",
                        name = "platform-plugin-eduroam-admin-settings",
                        title = "认证设置",
                        icon = "i-ri:settings-3-line",
                        component = "eduroam/AdminSettings",
                        permission = EduroamPlugin.MANAGE_PERMISSION,
                        sort = 92
                )
        }
)
public final class EduroamPlugin implements YuDreamPlugin {

    public static final String CODE = "eduroam";
    public static final String VERSION = "1.1.0";
    public static final String MANAGE_PERMISSION = "plugin:eduroam:manage";

    @Override
    public void onEnable(PluginContext context) {
        EduroamAccountRepository accounts = new EduroamAccountDocumentRepository(context.documents());
        EduroamAttemptRepository attempts = new EduroamAttemptDocumentRepository(context.documents());
        EduroamLoginTicketRepository tickets = new EduroamLoginTicketDocumentRepository(context.documents());
        EduroamSettingsRepository settings = new EduroamSettingsDocumentRepository(context.documents());
        EduroamAppService app = new EduroamAppService(accounts, attempts, tickets, settings,
                new HttpEduroamProbe(), new FrameworkLocalUserDirectory(context.framework()),
                new AttemptRateLimiter());
        app.seedDefaults();

        EduroamHttpFacade http = new EduroamHttpFacade(app);
        context.registerHttpController(new EduroamPublicController(http));
        context.registerHttpController(new EduroamAdminController(http));
        context.registerExtension(PluginExternalLoginProvider.class, new EduroamLoginProvider(app));
    }
}
