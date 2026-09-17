package online.yudream.base.plugin.tarusso.bootstrap;

import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginPermissions;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.spi.system.auth.PluginExternalLoginProvider;
import online.yudream.base.plugin.spi.widget.PluginGlobalWidget;
import online.yudream.base.plugin.tarusso.application.service.SettingsService;
import online.yudream.base.plugin.tarusso.application.service.StudentInfoService;
import online.yudream.base.plugin.tarusso.application.service.TaruSsoLoginProvider;
import online.yudream.base.plugin.tarusso.infrastructure.cas.CasProtocolClient;
import online.yudream.base.plugin.tarusso.infrastructure.oidc.OidcProtocolClient;
import online.yudream.base.plugin.tarusso.infrastructure.repository.RelayTicketDocumentRepository;
import online.yudream.base.plugin.tarusso.infrastructure.repository.SsoSettingsDocumentRepository;
import online.yudream.base.plugin.tarusso.infrastructure.repository.StudentMappingDocumentRepository;
import online.yudream.base.plugin.tarusso.infrastructure.repository.StudentProfileDocumentRepository;
import online.yudream.base.plugin.tarusso.infrastructure.secret.ClientSecretStore;
import online.yudream.base.plugin.tarusso.interfaces.controller.TaruSsoAdminController;
import online.yudream.base.plugin.tarusso.interfaces.http.TaruSsoHttpFacade;

@PluginSpec(
        code = TaruSsoPlugin.CODE,
        name = "CAS 统一身份认证",
        version = TaruSsoPlugin.VERSION,
        description = "接入 Apereo CAS / OIDC 统一身份认证，作为站点第三方登录提供方；支持学生信息映射与未绑定访问门禁",
        icon = "i-ri:graduation-cap-line"
)
@PluginPermissions({
        @PluginPermission(
                code = TaruSsoPlugin.MANAGE_PERMISSION,
                name = "管理 CAS 单点登录",
                module = "CAS 单点登录",
                description = "维护 CAS/OIDC 协议、回调地址、客户端凭据与学生信息映射"
        )
})
@PluginFrontend(
        moduleName = "cas",
        menuTitle = "CAS 单点登录",
        menuIcon = "i-ri:graduation-cap-line",
        menuSort = 48,
        styles = {"style.css"},
        routes = {
                @PluginRoute(
                        path = "/platform/plugins/cas/settings",
                        name = "platform-plugin-cas-settings",
                        title = "认证设置",
                        icon = "i-ri:settings-3-line",
                        component = "cas/Settings",
                        permission = TaruSsoPlugin.MANAGE_PERMISSION,
                        sort = 10
                ),
                @PluginRoute(
                        path = "/platform/plugins/cas/students",
                        name = "platform-plugin-cas-students",
                        title = "学生信息",
                        icon = "i-ri:group-line",
                        component = "cas/Students",
                        permission = TaruSsoPlugin.MANAGE_PERMISSION,
                        sort = 20
                )
        }
)
public final class TaruSsoPlugin implements YuDreamPlugin {

    public static final String CODE = "cas";
    public static final String VERSION = "1.1.0";
    public static final String MANAGE_PERMISSION = "plugin:cas:manage";

    @Override
    public void onEnable(PluginContext context) {
        ClientSecretStore secrets = new ClientSecretStore(context.secrets());
        SsoSettingsDocumentRepository repository = new SsoSettingsDocumentRepository(context.documents());
        // 兜底模式（state 不进 service 查询串）的待回调记录仓储，由 CAS 客户端与中转端点共用。
        RelayTicketDocumentRepository relayTickets = new RelayTicketDocumentRepository(context.documents());
        SettingsService settings = new SettingsService(
                repository,
                secrets,
                new CasProtocolClient(relayTickets),
                new OidcProtocolClient()
        );
        StudentInfoService studentInfo = new StudentInfoService(
                new StudentMappingDocumentRepository(context.documents()),
                new StudentProfileDocumentRepository(context.documents())
        );
        TaruSsoHttpFacade http = new TaruSsoHttpFacade(settings, studentInfo);
        context.registerHttpController(new TaruSsoAdminController(http));
        context.registerExtension(PluginExternalLoginProvider.class, new TaruSsoLoginProvider(settings, studentInfo));
        // 全站绑定门禁挂件：未绑定 CAS 的用户在开启开关后被引导完成绑定（组件 key = cas/Gate）
        context.registerGlobalWidget(new PluginGlobalWidget("cas-binding-gate", "cas/Gate", "", 900));
        // 学生档案预填挂件：已绑定且尚未填写学生档案时，用 CAS 属性预填 yudream-student-info 的表单（组件 key = cas/Prefill）
        context.registerGlobalWidget(new PluginGlobalWidget("cas-student-prefill", "cas/Prefill", "", 901));
    }
}
