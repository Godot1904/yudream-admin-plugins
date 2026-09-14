package online.yudream.base.plugin.pointsmall.application;

import online.yudream.base.plugin.pointsmall.application.cmd.MallDeliverCmd;
import online.yudream.base.plugin.pointsmall.application.cmd.MallDeliveryProofCmd;
import online.yudream.base.plugin.pointsmall.application.cmd.MallItemSaveCmd;
import online.yudream.base.plugin.pointsmall.application.cmd.MallRedeemCmd;
import online.yudream.base.plugin.pointsmall.application.cmd.MallSettingsSaveCmd;
import online.yudream.base.plugin.pointsmall.application.dto.MallItemDTO;
import online.yudream.base.plugin.pointsmall.application.dto.MallOverviewDTO;
import online.yudream.base.plugin.pointsmall.application.dto.MallRedemptionDTO;
import online.yudream.base.plugin.pointsmall.application.dto.MallSettingsDTO;
import online.yudream.base.plugin.pointsmall.application.service.MallAppService;
import online.yudream.base.plugin.pointsmall.application.service.MallWalletPort;
import online.yudream.base.plugin.pointsmall.domain.aggregate.MallItem;
import online.yudream.base.plugin.pointsmall.domain.aggregate.MallRedemption;
import online.yudream.base.plugin.pointsmall.domain.enumerate.MallRedemptionStatus;
import online.yudream.base.plugin.pointsmall.domain.valobj.MallSettings;
import online.yudream.base.plugin.pointsmall.infrastructure.FakeMallRepository;
import online.yudream.base.plugin.pointsmall.infrastructure.FakeWalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 兑换、发放与退款的主流程测试。
 *
 * <p>覆盖最容易出错的三件事：资产按商品结算时只动对应资产、积分只在兑换时扣一次、取消退款只退一次且
 * 发放之后不能再退；另外覆盖发放必须带凭证、确认收货只能本人。
 */
class MallAppServiceTest {

    private static final String USER = "1001";
    private static final String OTHER_USER = "1002";
    private static final String ADMIN = "9001";

    private FakeMallRepository repository;
    private FakeWalletService wallet;
    private MallAppService service;

    @BeforeEach
    void setUp() {
        repository = new FakeMallRepository();
        wallet = new FakeWalletService();
        service = new MallAppService(repository, new MallWalletPort(wallet), null);
        repository.saveSettings(new MallSettings(true, "兑换后请联系管理员领取"));
        wallet.setBalance(USER, "POINT", "1000");
        wallet.setBalance(OTHER_USER, "POINT", "1000");
        wallet.setBalance(USER, "CNY", "50");
    }

    private MallItemDTO createItem(String assetCode, long pricePoints, int stock, int perUserLimit, boolean enabled) {
        return service.createItem(new MallItemSaveCmd("称号兑换券", "兑换一个自定义称号", "/api/files/1/content",
                assetCode, pricePoints, stock, perUserLimit, enabled, 0));
    }

    private MallDeliverCmd deliverCmd(String note) {
        return new MallDeliverCmd(note,
                List.of(new MallDeliveryProofCmd("/api/files/77/content", "proof.png", "image/png", 1024L)));
    }

    // ------------------------------------------------------------------ 兑换

    @Test
    void redeemDeductsPointsStockAndWritesAPendingRecord() {
        MallItemDTO item = createItem("POINT", 100, 5, 0, true);
        assertEquals("POINT", item.assetCode());
        assertEquals("积分", item.assetName());

        MallRedemptionDTO redemption = service.redeem(USER, new MallRedeemCmd(item.id(), 2, "给朋友也换一个"));

        assertEquals("PENDING", redemption.status());
        assertEquals("待发放", redemption.statusLabel());
        assertEquals(200, redemption.totalPoints());
        assertEquals("POINT", redemption.assetCode());
        assertEquals(new BigDecimal("800"), wallet.balanceOf(USER, "POINT"));
        assertEquals(List.of("mall-redeem-" + redemption.id()), wallet.debitBusinessNos());
        assertEquals(3, repository.findItem(item.id()).orElseThrow().stock());
        assertEquals(1, service.countMyRedemptions(USER));
    }

    @Test
    void eachItemSettlesInItsOwnAsset() {
        MallItemDTO pointsItem = createItem("POINT", 100, 5, 0, true);
        MallItemDTO moneyItem = service.createItem(new MallItemSaveCmd("现金券", "", "", "CNY", 10L, 5, 0, true, 1));

        service.redeem(USER, new MallRedeemCmd(pointsItem.id(), 1, ""));
        service.redeem(USER, new MallRedeemCmd(moneyItem.id(), 2, ""));

        assertEquals(new BigDecimal("900"), wallet.balanceOf(USER, "POINT"));
        assertEquals(new BigDecimal("30"), wallet.balanceOf(USER, "CNY"));
        // 概览按在架商品用到的资产分别列余额。
        MallOverviewDTO overview = service.overview(USER);
        assertEquals(2, overview.assets().size());
        assertEquals("POINT", overview.assets().get(0).code());
        assertEquals("900", overview.assets().get(0).balance());
        assertEquals("CNY", overview.assets().get(1).code());
        assertEquals("30", overview.assets().get(1).balance());
    }

    @Test
    void redeemRejectsWhenTheBalanceIsTooLow() {
        MallItemDTO item = createItem("POINT", 800, 5, 0, true);
        assertEquals("积分不足，当前 1000，需要 1600", assertThrows(IllegalArgumentException.class,
                () -> service.redeem(USER, new MallRedeemCmd(item.id(), 2, ""))).getMessage());
        assertEquals(0, service.countMyRedemptions(USER));
        assertEquals(5, repository.findItem(item.id()).orElseThrow().stock());
        assertTrue(wallet.debitBusinessNos().isEmpty());
    }

    @Test
    void redeemKeepsTheRecordCancelledWhenTheWalletFails() {
        MallItemDTO item = createItem("POINT", 100, 5, 0, true);
        wallet.failDebits();

        assertThrows(IllegalStateException.class, () -> service.redeem(USER, new MallRedeemCmd(item.id(), 1, "")));

        List<MallRedemptionDTO> records = service.listMyRedemptions(USER, 1, 10);
        assertEquals(1, records.size());
        assertEquals("CANCELLED", records.get(0).status());
        assertTrue(records.get(0).cancelNote().startsWith("扣减积分失败"));
        assertEquals(new BigDecimal("1000"), wallet.balanceOf(USER, "POINT"));
        assertEquals(5, repository.findItem(item.id()).orElseThrow().stock());
    }

    @Test
    void redeemHonoursThePerUserLimitAcrossCalls() {
        MallItemDTO item = createItem("POINT", 10, 10, 2, true);
        service.redeem(USER, new MallRedeemCmd(item.id(), 1, ""));

        assertEquals("每人限兑 2 件，你已兑换 1 件", assertThrows(IllegalArgumentException.class,
                () -> service.redeem(USER, new MallRedeemCmd(item.id(), 2, ""))).getMessage());
        // 取消掉之后，限兑额度要还回来。
        MallRedemptionDTO first = service.listMyRedemptions(USER, 1, 10).get(0);
        service.cancelMyRedemption(USER, first.id(), "不想要了");
        service.redeem(USER, new MallRedeemCmd(item.id(), 2, ""));
        assertEquals(2, service.countMyRedemptions(USER));
    }

    @Test
    void redeemRejectsOfflineAndSoldOutItems() {
        MallItemDTO offline = createItem("POINT", 10, 5, 0, false);
        assertEquals("商品已下架", assertThrows(IllegalArgumentException.class,
                () -> service.redeem(USER, new MallRedeemCmd(offline.id(), 1, ""))).getMessage());

        MallItemDTO single = createItem("POINT", 10, 1, 0, true);
        service.redeem(USER, new MallRedeemCmd(single.id(), 1, ""));
        assertEquals("商品已兑完", assertThrows(IllegalArgumentException.class,
                () -> service.redeem(USER, new MallRedeemCmd(single.id(), 1, ""))).getMessage());
    }

    @Test
    void everyRedeemGetsItsOwnBusinessNoSoRetriesCannotCollide() {
        MallItemDTO item = createItem("POINT", 10, 10, 0, true);
        MallRedemptionDTO first = service.redeem(USER, new MallRedeemCmd(item.id(), 1, ""));
        MallRedemptionDTO second = service.redeem(USER, new MallRedeemCmd(item.id(), 1, ""));

        assertNotEquals(first.id(), second.id());
        assertEquals(List.of("mall-redeem-" + first.id(), "mall-redeem-" + second.id()), wallet.debitBusinessNos());
        assertEquals(new BigDecimal("980"), wallet.balanceOf(USER, "POINT"));
    }

    // ------------------------------------------------------------------ 发放与确认收货

    @Test
    void deliverRequiresProofsAndThenWaitsForTheUser() {
        MallItemDTO item = createItem("POINT", 100, 5, 0, true);
        MallRedemptionDTO redemption = service.redeem(USER, new MallRedeemCmd(item.id(), 1, ""));

        assertEquals("发放需要上传至少一张凭证", assertThrows(IllegalArgumentException.class,
                () -> service.deliverRedemption(redemption.id(), ADMIN, new MallDeliverCmd("已交付", List.of())))
                .getMessage());

        MallRedemptionDTO delivered = service.deliverRedemption(redemption.id(), ADMIN, deliverCmd("已当面交付"));

        assertEquals("DELIVERED", delivered.status());
        assertEquals("待确认收货", delivered.statusLabel());
        assertEquals(ADMIN, delivered.deliveredByUserId());
        assertEquals(1, delivered.proofs().size());
        assertEquals("/api/files/77/content", delivered.proofs().get(0).url());
        assertTrue(delivered.proofs().get(0).image());
        assertEquals(new BigDecimal("900"), wallet.balanceOf(USER, "POINT"));
        assertTrue(wallet.creditBusinessNos().isEmpty());
        // 概览里待发放归零、待确认收货 +1。
        assertEquals(0, service.overview(USER).pendingCount());
        assertEquals(1, service.overview(USER).deliveredCount());

        MallRedemptionDTO confirmed = service.confirmReceipt(USER, redemption.id());
        assertEquals("COMPLETED", confirmed.status());
        assertEquals("已完成", confirmed.statusLabel());
        assertTrue(confirmed.confirmedAt() > 0);
        assertEquals(1, service.countMyRedemptions(USER));
    }

    @Test
    void onlyTheOwnerCanConfirmAndDeliveredRecordsCannotBeCancelled() {
        MallItemDTO item = createItem("POINT", 100, 5, 0, true);
        MallRedemptionDTO redemption = service.redeem(USER, new MallRedeemCmd(item.id(), 1, ""));
        service.deliverRedemption(redemption.id(), ADMIN, deliverCmd(""));

        assertEquals("兑换记录不存在", assertThrows(IllegalArgumentException.class,
                () -> service.confirmReceipt(OTHER_USER, redemption.id())).getMessage());
        assertEquals("只有待发放的兑换可以取消并退款", assertThrows(IllegalArgumentException.class,
                () -> service.cancelRedemption(redemption.id(), ADMIN, "想反悔")).getMessage());
        assertEquals(new BigDecimal("900"), wallet.balanceOf(USER, "POINT"));
        assertTrue(wallet.creditBusinessNos().isEmpty());
    }

    // ------------------------------------------------------------------ 取消与退款

    @Test
    void cancelRefundsOnceAndRestoresStock() {
        MallItemDTO item = createItem("POINT", 100, 5, 0, true);
        MallRedemptionDTO redemption = service.redeem(USER, new MallRedeemCmd(item.id(), 2, ""));
        assertEquals(new BigDecimal("800"), wallet.balanceOf(USER, "POINT"));

        MallRedemptionDTO cancelled = service.cancelRedemption(redemption.id(), ADMIN, "库存有误");

        assertEquals("CANCELLED", cancelled.status());
        assertEquals("库存有误", cancelled.cancelNote());
        assertEquals(new BigDecimal("1000"), wallet.balanceOf(USER, "POINT"));
        assertEquals(List.of("mall-refund-" + redemption.id()), wallet.creditBusinessNos());
        assertEquals(5, repository.findItem(item.id()).orElseThrow().stock());
        assertEquals("只有待发放的兑换可以取消并退款", assertThrows(IllegalArgumentException.class,
                () -> service.cancelRedemption(redemption.id(), ADMIN, "再来一次")).getMessage());
        assertEquals(new BigDecimal("1000"), wallet.balanceOf(USER, "POINT"));
    }

    @Test
    void userCannotCancelAnotherUsersRedemption() {
        MallItemDTO item = createItem("POINT", 100, 5, 0, true);
        MallRedemptionDTO redemption = service.redeem(OTHER_USER, new MallRedeemCmd(item.id(), 1, ""));

        assertEquals("兑换记录不存在", assertThrows(IllegalArgumentException.class,
                () -> service.cancelMyRedemption(USER, redemption.id(), "顺手取消")).getMessage());
        assertEquals(new BigDecimal("900"), wallet.balanceOf(OTHER_USER, "POINT"));
        assertTrue(wallet.creditBusinessNos().isEmpty());
    }

    // ------------------------------------------------------------------ 商品与设置

    @Test
    void itemWithRedemptionsCannotBeDeleted() {
        MallItemDTO item = createItem("POINT", 100, 5, 0, true);
        service.redeem(USER, new MallRedeemCmd(item.id(), 1, ""));

        assertEquals("该商品已有 1 条兑换记录，不能删除；请改为下架",
                assertThrows(IllegalArgumentException.class, () -> service.deleteItem(item.id())).getMessage());
        assertEquals(1, service.adminItemCount(null, null));

        MallItemDTO clean = createItem("POINT", 100, 5, 0, true);
        service.deleteItem(clean.id());
        assertEquals(1, service.adminItemCount(null, null));
    }

    @Test
    void itemEnabledToggleIsVisibleToTheUserSurface() {
        MallItemDTO item = createItem("POINT", 100, 5, 0, true);
        assertEquals(1, service.countOpenItems(null));

        service.setItemEnabled(item.id(), false);
        assertEquals(0, service.countOpenItems(null));
        assertEquals(1, service.adminItemCount(null, false));
        assertEquals("商品已下架", assertThrows(IllegalArgumentException.class,
                () -> service.redeem(USER, new MallRedeemCmd(item.id(), 1, ""))).getMessage());
    }

    @Test
    void itemAssetMustExistInTheWallet() {
        assertEquals("结算资产「MISSING」在钱包里不存在或已停用，请重新选择",
                assertThrows(IllegalArgumentException.class, () -> createItem("MISSING", 100, 1, 0, true)).getMessage());

        MallItemDTO item = createItem("POINT", 100, 1, 0, true);
        wallet.disableAsset("POINT");
        assertEquals("结算资产「POINT」在钱包里不存在或已停用，请重新选择",
                assertThrows(IllegalArgumentException.class,
                        () -> service.updateItem(item.id(), new MallItemSaveCmd(null, null, null, "POINT", 100L, 1, 0, true, 0)))
                        .getMessage());
        assertEquals("结算资产「POINT」在钱包里不存在或已停用，请重新选择",
                assertThrows(IllegalArgumentException.class,
                        () -> service.redeem(USER, new MallRedeemCmd(item.id(), 1, ""))).getMessage());
    }

    @Test
    void settingsOnlyCarryTheSwitchAndNotice() {
        MallSettingsDTO saved = service.saveSettings(new MallSettingsSaveCmd(false, "仅周末开放"));
        assertFalse(saved.enabled());
        assertEquals("仅周末开放", saved.notice());

        MallItemDTO item = createItem("POINT", 10, 5, 0, true);
        assertEquals("积分商城当前未开放", assertThrows(IllegalArgumentException.class,
                () -> service.redeem(USER, new MallRedeemCmd(item.id(), 1, ""))).getMessage());
    }

    @Test
    void overviewOnlyCountsTheCurrentUser() {
        MallItemDTO item = createItem("POINT", 100, 5, 0, true);
        service.redeem(USER, new MallRedeemCmd(item.id(), 1, ""));
        service.redeem(OTHER_USER, new MallRedeemCmd(item.id(), 2, ""));

        MallOverviewDTO mine = service.overview(USER);
        assertEquals(1, mine.assets().size());
        assertEquals("900", mine.assets().get(0).balance());
        assertEquals(1, mine.pendingCount());
        assertEquals(0, mine.deliveredCount());
        assertEquals(1, mine.totalCount());
        assertEquals(1, mine.itemCount());
        assertEquals("兑换后请联系管理员领取", mine.notice());

        MallOverviewDTO theirs = service.overview(OTHER_USER);
        assertEquals(1, theirs.totalCount());
        assertEquals("800", theirs.assets().get(0).balance());
    }

    @Test
    void adminRedemptionFiltersAreScopedToTheRequestedSlice() {
        MallItemDTO item = createItem("POINT", 100, 5, 0, true);
        MallRedemptionDTO mine = service.redeem(USER, new MallRedeemCmd(item.id(), 1, ""));
        service.redeem(OTHER_USER, new MallRedeemCmd(item.id(), 1, ""));
        service.deliverRedemption(mine.id(), ADMIN, deliverCmd(""));

        assertEquals(2, service.adminRedemptionCount(null, item.id(), null));
        assertEquals(1, service.adminRedemptionCount(null, item.id(), "PENDING"));
        assertEquals(1, service.adminRedemptionCount(null, item.id(), "DELIVERED"));
        assertEquals(1, service.adminRedemptionCount(USER, null, null));
        assertEquals(1, service.adminRedemptions(OTHER_USER, null, null, 1, 10).size());
        assertEquals("兑换状态不支持：NOPE", assertThrows(IllegalArgumentException.class,
                () -> service.adminRedemptions(null, null, "NOPE", 1, 10)).getMessage());
    }

    @Test
    void adminListClampsPagingAndCountsTheWholeFilteredSet() {
        for (int index = 0; index < 12; index++) {
            service.createItem(new MallItemSaveCmd("商品 " + index, "", "", "POINT", 10L + index, 5, 0,
                    index % 2 == 0, index));
        }

        assertEquals(12, service.adminItemCount(null, null));
        assertEquals(6, service.adminItemCount(null, true));
        assertEquals(10, service.adminItems(null, null, 1, 10).size());
        assertEquals(2, service.adminItems(null, null, 2, 10).size());
        // size 超过上限时被夹到 100，页码从 1 起算，负数不会被当成第一页之外。
        assertEquals(12, service.adminItems(null, null, -3, 999).size());
    }

    @Test
    void recordKeepsItsAssetSnapshotAfterTheItemChangesAsset() {
        MallItemDTO item = createItem("POINT", 100, 5, 0, true);
        MallRedemptionDTO redemption = service.redeem(USER, new MallRedeemCmd(item.id(), 1, ""));
        service.updateItem(item.id(), new MallItemSaveCmd(null, null, null, "CNY", null, null, null, null, null));

        MallRedemptionDTO cancelled = service.cancelRedemption(redemption.id(), ADMIN, "换资产后取消");
        assertEquals("POINT", cancelled.assetCode());
        assertEquals(new BigDecimal("1000"), wallet.balanceOf(USER, "POINT"));
        assertEquals(new BigDecimal("50"), wallet.balanceOf(USER, "CNY"));
    }

    @Test
    void repositorySnapshotHelpersWorkOnStoredRecords() {
        MallItem item = MallItem.create("称号兑换券", "", "", "POINT", 100, 5, 0, true, 0);
        repository.saveItem(item);
        MallRedemption redemption = repository.saveRedemption(MallRedemption.create(item, USER, 2, ""));

        assertEquals(MallRedemptionStatus.PENDING, repository.findRedemption(redemption.id()).orElseThrow().status());
        assertEquals(2, repository.sumUserItemQuantity(USER, item.id()));
        assertEquals(1, repository.countItemRedemptions(item.id()));
        assertEquals(List.of("POINT"), repository.listOpenItemAssetCodes());
    }

    // ------------------------------------------------------------------ 历史数据

    /** 老版本留下的商品没有 assetCode：列表与概览照常工作，只是不能被兑换，管理员补选资产后即可恢复。 */
    @Test
    void legacyItemWithoutAssetKeepsListsWorkingAndAsksForRepair() {
        MallItem legacy = new MallItem("legacy-1", "老商品", "", "", "", 100, 5, 0, true, 0, 1L, 1L);
        repository.saveItem(legacy);
        createItem("POINT", 100, 5, 0, true);

        assertEquals(2, service.adminItemCount(null, null));
        assertEquals(1, service.adminItems(null, null, 1, 10).stream()
                .filter(row -> row.id().equals("legacy-1"))
                .filter(row -> row.assetCode().isEmpty() && row.assetName().isEmpty())
                .count());
        assertEquals(2, service.listOpenItems("", 1, 10).size());
        // 概览只按真正设置了资产的商品查余额，不会拿空代码去问钱包。
        MallOverviewDTO overview = service.overview(USER);
        assertEquals(1, overview.assets().size());
        assertEquals("POINT", overview.assets().get(0).code());

        assertEquals("该商品还没有设置结算资产，请联系管理员", assertThrows(IllegalArgumentException.class,
                () -> service.redeem(USER, new MallRedeemCmd("legacy-1", 1, ""))).getMessage());
        assertEquals(0, wallet.debitBusinessNos().size());

        MallItemDTO repaired = service.updateItem("legacy-1",
                new MallItemSaveCmd(null, null, null, "POINT", null, null, null, null, null));
        assertEquals("POINT", repaired.assetCode());
        assertEquals("积分", repaired.assetName());
        assertEquals("PENDING", service.redeem(USER, new MallRedeemCmd("legacy-1", 1, "")).status());
    }

    /** 保存商品时仍然强制选择结算资产：空白或不在钱包里的资产都会被拒绝。 */
    @Test
    void savingItemStillRequiresAUsableAsset() {
        MallItemDTO item = createItem("POINT", 100, 5, 0, true);
        assertEquals("请选择结算资产", assertThrows(IllegalArgumentException.class,
                () -> service.createItem(new MallItemSaveCmd("称号兑换券", "", "", "", 10L, 1, 0, true, 0)))
                .getMessage());
        assertEquals("结算资产「NOPE」在钱包里不存在或已停用，请重新选择",
                assertThrows(IllegalArgumentException.class,
                        () -> service.updateItem(item.id(),
                                new MallItemSaveCmd(null, null, null, "NOPE", null, null, null, null, null)))
                        .getMessage());
    }
}
