package online.yudream.base.plugin.pointsmall.application.service;

import online.yudream.base.plugin.wallet.api.PluginWalletAsset;
import online.yudream.base.plugin.wallet.api.PluginWalletBalance;
import online.yudream.base.plugin.wallet.api.PluginWalletChangeRequest;
import online.yudream.base.plugin.wallet.api.PluginWalletService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 钱包能力的窄封装。
 *
 * <p>只暴露积分商城真正需要的四件事：列出结算资产、查余额、扣减、退款。所有调用都带业务单号，钱包按
 * 单号幂等，因此重试与重复提交不会重复扣分/退款。
 */
public class MallWalletPort {

    private final PluginWalletService wallet;

    public MallWalletPort(PluginWalletService wallet) {
        this.wallet = wallet;
    }

    public List<PluginWalletAsset> assets() {
        return wallet.assets() == null ? List.of() : wallet.assets();
    }

    public Optional<PluginWalletAsset> findAsset(String assetCode) {
        if (assetCode == null || assetCode.isBlank()) {
            return Optional.empty();
        }
        return wallet.findAsset(assetCode.trim());
    }

    public BigDecimal balance(String userId, String assetCode) {
        PluginWalletBalance balance = wallet.balance(userId, assetCode);
        return balance == null || balance.balance() == null ? BigDecimal.ZERO : balance.balance();
    }

    public void debit(String userId, String assetCode, long points, String businessNo, String remark) {
        wallet.debit(new PluginWalletChangeRequest(userId, assetCode, BigDecimal.valueOf(points), businessNo, remark));
    }

    public void credit(String userId, String assetCode, long points, String businessNo, String remark) {
        wallet.credit(new PluginWalletChangeRequest(userId, assetCode, BigDecimal.valueOf(points), businessNo, remark));
    }
}
