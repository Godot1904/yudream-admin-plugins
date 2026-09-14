package online.yudream.base.plugin.pointsmall.infrastructure;

import online.yudream.base.plugin.wallet.api.PluginWalletAsset;
import online.yudream.base.plugin.wallet.api.PluginWalletBalance;
import online.yudream.base.plugin.wallet.api.PluginWalletChangeRequest;
import online.yudream.base.plugin.wallet.api.PluginWalletService;
import online.yudream.base.plugin.wallet.api.PluginWalletTransaction;
import online.yudream.base.plugin.wallet.api.PluginWalletTransferRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 测试用的钱包：复刻真实实现对业务单号的幂等语义，并允许制造扣分失败。
 *
 * <p>幂等是这里最关键的行为——积分商城的「不重复扣分」完全依赖它，测试里必须能验证同一个单号调两次
 * 只动一次账。
 */
public class FakeWalletService implements PluginWalletService {

    private final Map<String, PluginWalletAsset> assets = new LinkedHashMap<>();
    private final Map<String, BigDecimal> balances = new HashMap<>();
    private final Set<String> appliedBusinessNos = new LinkedHashSet<>();
    private final List<String> debits = new ArrayList<>();
    private final List<String> credits = new ArrayList<>();
    private boolean failDebits;

    public FakeWalletService() {
        assets.put("POINT", new PluginWalletAsset("POINT", "积分", "积分", 0, false, true, false, BigDecimal.ONE));
        assets.put("CNY", new PluginWalletAsset("CNY", "人民币", "元", 2, true, true, true, new BigDecimal("0.01")));
    }

    public FakeWalletService setBalance(String userId, String assetCode, String value) {
        balances.put(key(userId, assetCode), new BigDecimal(value));
        return this;
    }

    public BigDecimal balanceOf(String userId, String assetCode) {
        return balances.getOrDefault(key(userId, assetCode), BigDecimal.ZERO);
    }

    public List<String> debitBusinessNos() {
        return List.copyOf(debits);
    }

    public List<String> creditBusinessNos() {
        return List.copyOf(credits);
    }

    public void disableAsset(String assetCode) {
        PluginWalletAsset asset = assets.get(assetCode);
        if (asset != null) {
            assets.put(assetCode, new PluginWalletAsset(asset.code(), asset.name(), asset.symbol(), asset.scale(),
                    asset.money(), false, asset.transferEnabled(), asset.minTransferAmount()));
        }
    }

    /** 让接下来的扣分直接抛错，用来验证「扣分失败要回滚记录」。 */
    public void failDebits() {
        this.failDebits = true;
    }

    @Override
    public List<PluginWalletAsset> assets() {
        return List.copyOf(assets.values());
    }

    @Override
    public Optional<PluginWalletAsset> findAsset(String assetCode) {
        return Optional.ofNullable(assets.get(assetCode));
    }

    @Override
    public PluginWalletAsset ensureAsset(PluginWalletAsset asset) {
        assets.put(asset.code(), asset);
        return asset;
    }

    @Override
    public List<PluginWalletBalance> balances(String userId) {
        return balances.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(userId + "|"))
                .map(entry -> new PluginWalletBalance(userId, entry.getKey().substring(entry.getKey().indexOf('|') + 1),
                        entry.getValue(), System.currentTimeMillis()))
                .toList();
    }

    @Override
    public PluginWalletBalance balance(String userId, String assetCode) {
        return new PluginWalletBalance(userId, assetCode, balanceOf(userId, assetCode), System.currentTimeMillis());
    }

    @Override
    public PluginWalletTransaction credit(PluginWalletChangeRequest request) {
        if (request.businessNo() != null && !appliedBusinessNos.add("credit:" + request.businessNo())) {
            return transaction(request, "CREDIT");
        }
        BigDecimal next = balanceOf(request.userId(), request.assetCode()).add(request.amount());
        balances.put(key(request.userId(), request.assetCode()), next);
        credits.add(String.valueOf(request.businessNo()));
        return transaction(request, "CREDIT");
    }

    @Override
    public PluginWalletTransaction debit(PluginWalletChangeRequest request) {
        if (request.businessNo() != null && appliedBusinessNos.contains("debit:" + request.businessNo())) {
            return transaction(request, "DEBIT");
        }
        if (failDebits) {
            throw new IllegalStateException("钱包暂时不可用");
        }
        BigDecimal current = balanceOf(request.userId(), request.assetCode());
        BigDecimal next = current.subtract(request.amount());
        if (next.signum() < 0) {
            throw new IllegalArgumentException("余额不足");
        }
        if (request.businessNo() != null) {
            appliedBusinessNos.add("debit:" + request.businessNo());
        }
        balances.put(key(request.userId(), request.assetCode()), next);
        debits.add(String.valueOf(request.businessNo()));
        return transaction(request, "DEBIT");
    }

    @Override
    public PluginWalletTransaction transfer(PluginWalletTransferRequest request) {
        throw new UnsupportedOperationException("积分商城不使用转账");
    }

    @Override
    public Optional<PluginWalletTransaction> findTransactionByBusinessNo(String businessNo) {
        return Optional.empty();
    }

    private PluginWalletTransaction transaction(PluginWalletChangeRequest request, String type) {
        return new PluginWalletTransaction("tx-" + request.businessNo(), request.businessNo(), type, "points-mall",
                request.assetCode(), request.userId(), request.userId(), request.amount(),
                balanceOf(request.userId(), request.assetCode()), balanceOf(request.userId(), request.assetCode()),
                request.remark(), System.currentTimeMillis());
    }

    private static String key(String userId, String assetCode) {
        return userId + "|" + assetCode;
    }
}
