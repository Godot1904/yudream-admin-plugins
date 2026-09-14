package online.yudream.base.plugin.pointsmall.bootstrap;

import online.yudream.base.plugin.pointsmall.application.service.MallAppService;
import online.yudream.base.plugin.pointsmall.application.service.MallWalletPort;
import online.yudream.base.plugin.pointsmall.infrastructure.repository.MallDocumentRepository;
import online.yudream.base.plugin.pointsmall.interfaces.controller.MallAdminController;
import online.yudream.base.plugin.pointsmall.interfaces.controller.MallUserController;
import online.yudream.base.plugin.pointsmall.interfaces.http.MallHttpFacade;
import online.yudream.base.plugin.spi.annotation.PluginFrontend;
import online.yudream.base.plugin.spi.annotation.PluginPermission;
import online.yudream.base.plugin.spi.annotation.PluginPermissions;
import online.yudream.base.plugin.spi.annotation.PluginRoute;
import online.yudream.base.plugin.spi.annotation.PluginSpec;
import online.yudream.base.plugin.spi.core.PluginContext;
import online.yudream.base.plugin.spi.core.YuDreamPlugin;
import online.yudream.base.plugin.wallet.api.PluginWalletService;

/**
 * 积分商城入口：装配仓储、钱包端口与控制器。
 *
 * <p>本插件的核心能力（用积分兑换）离不开钱包，所以 {@code depend: yudream-wallet}：钱包未启用时本插件
 * 不会启用，也不存在「没有钱包却还能发商品」的半可用状态。
 */
@PluginSpec(
        code = PointsMallPlugin.CODE,
        name = "points-mall",
        version = "1.1.0",
        description = "上架积分商品，用户用钱包里的积分兑换，管理员发放或取消退款。",
        icon = "i-ri:store-3-line",
        dependencies = {"yudream-wallet"}
)
@PluginPermissions({
        @PluginPermission(code = PointsMallPlugin.VIEW_PERMISSION, name = "浏览积分商城", module = "平台插件",
                description = "查看在架商品、自己的积分余额与自己的兑换记录"),
        @PluginPermission(code = PointsMallPlugin.USE_PERMISSION, name = "兑换积分商品", module = "平台插件",
                description = "用钱包里的积分兑换商品，并取消自己尚未发放的兑换"),
        @PluginPermission(code = PointsMallPlugin.MANAGE_PERMISSION, name = "管理积分商城", module = "平台插件",
                description = "维护商品与库存、发放或取消退款、配置结算资产与兑换须知")
})
@PluginFrontend(
        moduleName = "pointsMall",
        menuTitle = "积分商城",
        menuIcon = "i-ri:store-3-line",
        menuSort = 35,
        parentCode = "plugin:yudream-wallet:module:yudreamWallet",
        styles = {"style.css"},
        routes = {
                @PluginRoute(
                        path = "/platform/plugins/points-mall",
                        name = "platform-plugin-points-mall",
                        title = "积分商城",
                        icon = "i-ri:store-3-line",
                        component = "points-mall/Mall",
                        permission = PointsMallPlugin.VIEW_PERMISSION,
                        sort = 27
                ),
                @PluginRoute(
                        path = "/platform/plugins/points-mall/redemptions",
                        name = "platform-plugin-points-mall-redemptions",
                        title = "我的兑换",
                        icon = "i-ri:gift-line",
                        component = "points-mall/MyRedemptions",
                        permission = PointsMallPlugin.VIEW_PERMISSION,
                        sort = 28
                ),
                @PluginRoute(
                        path = "/platform/plugins/points-mall/system/items",
                        name = "platform-plugin-points-mall-items",
                        title = "商品管理",
                        icon = "i-ri:apps-2-line",
                        parentPath = "/platform/plugins/points-mall/system",
                        parentTitle = "商城管理",
                        parentIcon = "i-ri:store-3-line",
                        parentSort = 45,
                        component = "points-mall/AdminItems",
                        permission = PointsMallPlugin.MANAGE_PERMISSION,
                        sort = 10
                ),
                @PluginRoute(
                        path = "/platform/plugins/points-mall/system/redemptions",
                        name = "platform-plugin-points-mall-system-redemptions",
                        title = "兑换记录",
                        icon = "i-ri:file-list-3-line",
                        parentPath = "/platform/plugins/points-mall/system",
                        parentTitle = "商城管理",
                        parentIcon = "i-ri:store-3-line",
                        parentSort = 45,
                        component = "points-mall/AdminRedemptions",
                        permission = PointsMallPlugin.MANAGE_PERMISSION,
                        sort = 11
                ),
                @PluginRoute(
                        path = "/platform/plugins/points-mall/system/settings",
                        name = "platform-plugin-points-mall-settings",
                        title = "商城设置",
                        icon = "i-ri:settings-3-line",
                        parentPath = "/platform/plugins/points-mall/system",
                        parentTitle = "商城管理",
                        parentIcon = "i-ri:store-3-line",
                        parentSort = 45,
                        component = "points-mall/AdminSettings",
                        permission = PointsMallPlugin.MANAGE_PERMISSION,
                        sort = 12
                )
        }
)
public class PointsMallPlugin implements YuDreamPlugin {

    public static final String CODE = "points-mall";
    public static final String VIEW_PERMISSION = "plugin:points-mall:view";
    public static final String USE_PERMISSION = "plugin:points-mall:use";
    public static final String MANAGE_PERMISSION = "plugin:points-mall:manage";

    @Override
    public void onEnable(PluginContext context) {
        PluginWalletService walletService = context.service("yudream-wallet", PluginWalletService.class)
                .orElseThrow(() -> new IllegalStateException("钱包插件未启用或未注册钱包服务"));
        MallAppService appService = new MallAppService(
                new MallDocumentRepository(context.documents()),
                new MallWalletPort(walletService),
                context.framework());
        MallHttpFacade http = new MallHttpFacade(appService);
        context.registerHttpController(new MallUserController(http));
        context.registerHttpController(new MallAdminController(http));
    }
}
