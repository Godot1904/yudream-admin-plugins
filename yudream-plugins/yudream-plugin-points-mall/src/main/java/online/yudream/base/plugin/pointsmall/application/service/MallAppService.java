package online.yudream.base.plugin.pointsmall.application.service;

import online.yudream.base.plugin.pointsmall.application.assembler.MallAppAssembler;
import online.yudream.base.plugin.pointsmall.application.cmd.MallDeliverCmd;
import online.yudream.base.plugin.pointsmall.application.cmd.MallItemSaveCmd;
import online.yudream.base.plugin.pointsmall.application.cmd.MallRedeemCmd;
import online.yudream.base.plugin.pointsmall.application.cmd.MallSettingsSaveCmd;
import online.yudream.base.plugin.pointsmall.application.dto.MallAssetOptionDTO;
import online.yudream.base.plugin.pointsmall.application.dto.MallItemDTO;
import online.yudream.base.plugin.pointsmall.application.dto.MallOverviewDTO;
import online.yudream.base.plugin.pointsmall.application.dto.MallRedemptionDTO;
import online.yudream.base.plugin.pointsmall.application.dto.MallSettingsDTO;
import online.yudream.base.plugin.pointsmall.domain.aggregate.MallItem;
import online.yudream.base.plugin.pointsmall.domain.aggregate.MallRedemption;
import online.yudream.base.plugin.pointsmall.domain.enumerate.MallRedemptionStatus;
import online.yudream.base.plugin.pointsmall.domain.repo.MallRepository;
import online.yudream.base.plugin.pointsmall.domain.valobj.MallDeliveryProof;
import online.yudream.base.plugin.pointsmall.domain.valobj.MallSettings;
import online.yudream.base.plugin.spi.system.FrameworkServices;
import online.yudream.base.plugin.spi.system.user.PluginUserProfile;
import online.yudream.base.plugin.wallet.api.PluginWalletAsset;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 积分商城的用例编排。
 *
 * <p>用户端方法的 {@code userId} 一律由接口层从 {@code request.principal()} 传入，方法内部再把它当作
 * 数据范围使用；管理员端方法才接受显式的筛选条件。两条路径共用下面这些私有步骤，但入口各自独立，
 * 不会出现「因为调用人是管理员所以放宽用户端范围」的分支。
 *
 * <p>兑换与退款的顺序都是「先落记录，再动钱」：兑换先写一条待发放记录，再用兑换单号去扣分（钱包按单号
 * 幂等），扣分失败就把这条记录落成已取消，绝不会出现「扣了分却没有记录」；取消先落状态，再用退款单号
 * 退款，重复取消因为状态守卫与单号幂等也不会二次退款。
 *
 * <p>发放闭环：管理员发放必须带凭证（图片，存宿主对象存储），记录转为「待确认收货」；用户确认后才变成
 * 「已完成」。取消只允许发生在发放之前，避免货已交付还把钱退回去。
 */
public class MallAppService {

    /** 系统自动回滚（扣分失败）时记录的操作人。 */
    private static final String SYSTEM_OPERATOR = "system";
    private static final int MAX_PAGE_SIZE = 100;
    private static final String REDEMPTION_NOT_FOUND = "兑换记录不存在";

    private final MallRepository repository;
    private final MallWalletPort wallet;
    private final FrameworkServices framework;
    private final MallAppAssembler assembler = new MallAppAssembler();

    public MallAppService(MallRepository repository, MallWalletPort wallet, FrameworkServices framework) {
        this.repository = repository;
        this.wallet = wallet;
        this.framework = framework;
    }

    // ------------------------------------------------------------------ 设置与概览

    public MallSettingsDTO settings() {
        return assembler.toSettingsDTO(repository.findSettings());
    }

    public List<MallAssetOptionDTO> assetOptions() {
        return wallet.assets().stream().map(assembler::toAssetOptionDTO).toList();
    }

    public MallSettingsDTO saveSettings(MallSettingsSaveCmd cmd) {
        MallSettings current = repository.findSettings();
        MallSettingsSaveCmd safe = cmd == null ? new MallSettingsSaveCmd(null, null) : cmd;
        boolean enabled = safe.enabled() == null ? current.enabled() : safe.enabled();
        String notice = safe.notice() == null ? current.notice() : safe.notice();
        return assembler.toSettingsDTO(repository.saveSettings(new MallSettings(enabled, notice)));
    }

    /** 当前登录人的商城概览：在架商品用到的每种资产各一行余额，只含自己的数据。 */
    public MallOverviewDTO overview(String userId) {
        String owner = requireUser(userId);
        MallSettings settings = repository.findSettings();
        Map<String, PluginWalletAsset> index = assetIndex();
        List<online.yudream.base.plugin.pointsmall.application.dto.MallAssetBalanceDTO> assets =
                repository.listOpenItemAssetCodes().stream()
                        .map(code -> assembler.toAssetBalanceDTO(code, index.get(code),
                                wallet.balance(owner, code)))
                        .toList();
        return new MallOverviewDTO(settings.enabled(), assets,
                repository.countItems("", Boolean.TRUE),
                repository.countRedemptions(owner, null, MallRedemptionStatus.PENDING),
                repository.countRedemptions(owner, null, MallRedemptionStatus.DELIVERED),
                repository.countRedemptions(owner, null, null), settings.notice());
    }

    // ------------------------------------------------------------------ 用户端

    /** 商城在架商品；已兑完的仍然列出（前端显示为已兑完并禁用兑换），避免分页总数与列表对不上。 */
    public List<MallItemDTO> listOpenItems(String keyword, int page, int size) {
        return toItemDTOs(repository.listItems(keyword, Boolean.TRUE, safePage(page), safeSize(size)));
    }

    public long countOpenItems(String keyword) {
        return repository.countItems(keyword, Boolean.TRUE);
    }

    public MallRedemptionDTO redeem(String userId, MallRedeemCmd cmd) {
        String owner = requireUser(userId);
        MallSettings settings = repository.findSettings();
        if (!settings.enabled()) {
            throw new IllegalArgumentException("积分商城当前未开放");
        }
        MallRedeemCmd safe = cmd == null ? new MallRedeemCmd(null, null, null) : cmd;
        MallItem item = requireItem(requireText(safe.itemId(), "请选择要兑换的商品"));
        int quantity = safe.quantity() == null ? 1 : safe.quantity();
        item.requireAvailable(quantity);
        PluginWalletAsset asset = requireAsset(item.assetCode(), "该商品还没有设置结算资产，请联系管理员");
        requirePerUserLimit(item, owner, quantity);
        long totalPoints = item.totalPoints(quantity);
        requireBalance(owner, asset, totalPoints);
        MallRedemption redemption = repository.saveRedemption(
                MallRedemption.create(item, owner, quantity, safe.remark()));
        try {
            wallet.debit(owner, asset.code(), totalPoints, redemption.debitBusinessNo(),
                    "积分商城兑换：" + item.name() + " ×" + quantity);
        } catch (RuntimeException failure) {
            // 扣分失败（余额不足、资产被停用、钱包故障）：把刚落的记录收成已取消，别留下「待发放但没扣分」的单子。
            repository.saveRedemption(redemption.cancel(SYSTEM_OPERATOR, "扣减积分失败：" + failure.getMessage()));
            throw failure;
        }
        repository.saveItem(item.deduct(quantity));
        return toRedemptionDTO(redemption);
    }

    public List<MallRedemptionDTO> listMyRedemptions(String userId, int page, int size) {
        String owner = requireUser(userId);
        return repository.listRedemptions(owner, null, null, safePage(page), safeSize(size)).stream()
                .map(redemption -> assembler.toRedemptionDTO(redemption, displayName(owner)))
                .toList();
    }

    public long countMyRedemptions(String userId) {
        return repository.countRedemptions(requireUser(userId), null, null);
    }

    /** 用户取消自己尚未发放的兑换；别人的记录一律按「不存在」处理，不泄露它是否存在。 */
    public MallRedemptionDTO cancelMyRedemption(String userId, String redemptionId, String reason) {
        String owner = requireUser(userId);
        MallRedemption redemption = requireRedemption(redemptionId);
        if (!redemption.belongsTo(owner)) {
            throw new IllegalArgumentException(REDEMPTION_NOT_FOUND);
        }
        return cancel(redemption, owner, reason);
    }

    /** 用户确认收到已发放的兑换；只有本人能确认。 */
    public MallRedemptionDTO confirmReceipt(String userId, String redemptionId) {
        String owner = requireUser(userId);
        MallRedemption redemption = requireRedemption(redemptionId);
        if (!redemption.belongsTo(owner)) {
            throw new IllegalArgumentException(REDEMPTION_NOT_FOUND);
        }
        return toRedemptionDTO(repository.saveRedemption(redemption.confirmReceipt(owner)));
    }

    // ------------------------------------------------------------------ 管理员端

    public List<MallItemDTO> adminItems(String keyword, Boolean enabled, int page, int size) {
        return toItemDTOs(repository.listItems(keyword, enabled, safePage(page), safeSize(size)));
    }

    public long adminItemCount(String keyword, Boolean enabled) {
        return repository.countItems(keyword, enabled);
    }

    public MallItemDTO createItem(MallItemSaveCmd cmd) {
        MallItemSaveCmd safe = cmd == null
                ? new MallItemSaveCmd(null, null, null, null, null, null, null, null, null)
                : cmd;
        PluginWalletAsset asset = requireAsset(safe.assetCode());
        MallItem item = MallItem.create(safe.name(), safe.description(), safe.imageUrl(), asset.code(),
                requirePricePoints(safe.pricePoints()),
                safe.stock() == null ? MallItem.UNLIMITED_STOCK : safe.stock(),
                safe.perUserLimit() == null ? MallItem.UNLIMITED_PER_USER : safe.perUserLimit(),
                safe.enabled() == null || safe.enabled(),
                safe.sort() == null ? 0 : safe.sort());
        return toItemDTO(repository.saveItem(item));
    }

    public MallItemDTO updateItem(String itemId, MallItemSaveCmd cmd) {
        MallItem item = requireItem(itemId);
        MallItemSaveCmd safe = cmd == null
                ? new MallItemSaveCmd(null, null, null, null, null, null, null, null, null)
                : cmd;
        String assetCode = safe.assetCode() == null || safe.assetCode().isBlank() ? item.assetCode() : safe.assetCode();
        PluginWalletAsset asset = requireAsset(assetCode);
        long pricePoints = safe.pricePoints() == null ? item.pricePoints() : safe.pricePoints();
        MallItem saved = item.update(
                safe.name() == null ? item.name() : safe.name(),
                safe.description() == null ? item.description() : safe.description(),
                safe.imageUrl() == null ? item.imageUrl() : safe.imageUrl(),
                asset.code(),
                pricePoints,
                safe.stock() == null ? item.stock() : safe.stock(),
                safe.perUserLimit() == null ? item.perUserLimit() : safe.perUserLimit(),
                safe.enabled() == null ? item.enabled() : safe.enabled(),
                safe.sort() == null ? item.sort() : safe.sort());
        return toItemDTO(repository.saveItem(saved));
    }

    public MallItemDTO setItemEnabled(String itemId, boolean enabled) {
        MallItem item = requireItem(itemId);
        return toItemDTO(repository.saveItem(item.withEnabled(enabled)));
    }

    /** 删除商品；已有兑换记录的商品只能下架，避免历史记录指向一个不存在的商品。 */
    public void deleteItem(String itemId) {
        MallItem item = requireItem(itemId);
        long redemptions = repository.countItemRedemptions(item.id());
        if (redemptions > 0) {
            throw new IllegalArgumentException("该商品已有 " + redemptions + " 条兑换记录，不能删除；请改为下架");
        }
        repository.deleteItem(item.id());
    }

    public List<MallRedemptionDTO> adminRedemptions(String userId, String itemId, String status, int page, int size) {
        return repository
                .listRedemptions(blankToNull(userId), blankToNull(itemId), parseStatus(status), safePage(page), safeSize(size))
                .stream()
                .map(redemption -> assembler.toRedemptionDTO(redemption, displayName(redemption.userId())))
                .toList();
    }

    public long adminRedemptionCount(String userId, String itemId, String status) {
        return repository.countRedemptions(blankToNull(userId), blankToNull(itemId), parseStatus(status));
    }

    /** 管理员发放：必须带凭证；发放后等用户确认收货。 */
    public MallRedemptionDTO deliverRedemption(String redemptionId, String operatorUserId, MallDeliverCmd cmd) {
        MallRedemption redemption = requireRedemption(redemptionId);
        MallDeliverCmd safe = cmd == null ? new MallDeliverCmd(null, List.of()) : cmd;
        List<MallDeliveryProof> proofs = safe.proofsOrEmpty().stream()
                .filter(proof -> proof != null)
                .map(proof -> new MallDeliveryProof(proof.url(), proof.filename(), proof.contentType(),
                        proof.size() == null ? 0L : proof.size()))
                .toList();
        MallRedemption delivered = repository.saveRedemption(
                redemption.deliver(requireUser(operatorUserId), safe.note(), proofs));
        return toRedemptionDTO(delivered);
    }

    public MallRedemptionDTO cancelRedemption(String redemptionId, String operatorUserId, String reason) {
        MallRedemption redemption = requireRedemption(redemptionId);
        return cancel(redemption, requireUser(operatorUserId), reason);
    }

    // ------------------------------------------------------------------ 私有步骤

    /** 取消并退款：先落取消状态，再按原单号把积分退回原资产，最后归还库存。 */
    private MallRedemptionDTO cancel(MallRedemption redemption, String operatorUserId, String reason) {
        MallRedemption cancelled = repository.saveRedemption(redemption.cancel(operatorUserId, reason));
        wallet.credit(cancelled.userId(), cancelled.assetCode(), cancelled.totalPoints(),
                cancelled.refundBusinessNo(), "积分商城取消退款：" + cancelled.itemName() + " ×" + cancelled.quantity());
        repository.findItem(cancelled.itemId())
                .ifPresent(item -> repository.saveItem(item.restore(cancelled.quantity())));
        return toRedemptionDTO(cancelled);
    }

    private void requirePerUserLimit(MallItem item, String userId, int quantity) {
        if (item.perUserLimit() <= 0) {
            return;
        }
        long owned = repository.sumUserItemQuantity(userId, item.id());
        if (owned + quantity > item.perUserLimit()) {
            throw new IllegalArgumentException(
                    "每人限兑 " + item.perUserLimit() + " 件，你已兑换 " + owned + " 件");
        }
    }

    private void requireBalance(String userId, PluginWalletAsset asset, long totalPoints) {
        BigDecimal balance = wallet.balance(userId, asset.code());
        if (balance.compareTo(BigDecimal.valueOf(totalPoints)) < 0) {
            throw new IllegalArgumentException(asset.name() + "不足，当前 " + plain(balance) + "，需要 " + totalPoints);
        }
    }

    private MallItem requireItem(String itemId) {
        return repository.findItem(requireText(itemId, "商品 ID 不能为空"))
                .orElseThrow(() -> new IllegalArgumentException("商品不存在"));
    }

    private MallRedemption requireRedemption(String redemptionId) {
        return repository.findRedemption(requireText(redemptionId, "兑换记录 ID 不能为空"))
                .orElseThrow(() -> new IllegalArgumentException(REDEMPTION_NOT_FOUND));
    }

    /** 结算资产必须真实存在且启用；停用的资产不给出新的兑换入口。 */
    private PluginWalletAsset requireAsset(String assetCode) {
        return requireAsset(assetCode, "请选择结算资产");
    }

    private PluginWalletAsset requireAsset(String assetCode, String blankMessage) {
        String code = assetCode == null ? "" : assetCode.trim();
        if (code.isEmpty()) {
            throw new IllegalArgumentException(blankMessage);
        }
        return wallet.findAsset(code)
                .filter(PluginWalletAsset::enabled)
                .orElseThrow(() -> new IllegalArgumentException(
                        "结算资产「" + code + "」在钱包里不存在或已停用，请重新选择"));
    }

    private Map<String, PluginWalletAsset> assetIndex() {
        Map<String, PluginWalletAsset> index = new LinkedHashMap<>();
        for (PluginWalletAsset asset : wallet.assets()) {
            index.put(asset.code(), asset);
        }
        return index;
    }

    private List<MallItemDTO> toItemDTOs(List<MallItem> items) {
        Map<String, PluginWalletAsset> index = assetIndex();
        return items.stream().map(item -> assembler.toItemDTO(item, index.get(item.assetCode()))).toList();
    }

    private MallItemDTO toItemDTO(MallItem item) {
        return assembler.toItemDTO(item, wallet.findAsset(item.assetCode()).orElse(null));
    }

    private MallRedemptionDTO toRedemptionDTO(MallRedemption redemption) {
        return assembler.toRedemptionDTO(redemption, displayName(redemption.userId()));
    }

    private static MallRedemptionStatus parseStatus(String value) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return MallRedemptionStatus.valueOf(text.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("兑换状态不支持：" + value);
        }
    }

    private static long requirePricePoints(Long pricePoints) {
        if (pricePoints == null) {
            throw new IllegalArgumentException("请填写所需积分");
        }
        return pricePoints;
    }

    private static String requireUser(String userId) {
        return requireText(userId, "请先登录");
    }

    private static String requireText(String value, String message) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private static String blankToNull(String value) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? null : text;
    }

    private static String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static int safePage(int page) {
        return Math.max(page, 1);
    }

    private static int safeSize(int size) {
        return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    }

    /** 展示名：优先昵称，其次用户名，查不到就回落成用户 ID；只用于展示，不参与任何判定。 */
    private String displayName(String userId) {
        return userProfile(userId)
                .map(profile -> {
                    String nickname = profile.nickname() == null ? "" : profile.nickname().trim();
                    if (!nickname.isEmpty()) {
                        return nickname;
                    }
                    String username = profile.username() == null ? "" : profile.username().trim();
                    return username.isEmpty() ? userId : username;
                })
                .orElse(userId);
    }

    private Optional<PluginUserProfile> userProfile(String userId) {
        if (framework == null || framework.users() == null || userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        try {
            return framework.users().findById(Long.parseLong(userId.trim()));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
