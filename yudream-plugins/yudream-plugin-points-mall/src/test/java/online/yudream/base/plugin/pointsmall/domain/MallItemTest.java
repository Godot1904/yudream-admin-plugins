package online.yudream.base.plugin.pointsmall.domain;

import online.yudream.base.plugin.pointsmall.domain.aggregate.MallItem;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MallItemTest {

    private static MallItem item(int stock, int perUserLimit, boolean enabled) {
        return MallItem.create("称号兑换券", "兑换一个自定义称号", "", "POINT", 100, stock, perUserLimit, enabled, 0);
    }

    @Test
    void createRejectsBlankNameAndNegativeNumbers() {
        assertThrows(IllegalArgumentException.class,
                () -> MallItem.create("  ", "", "", "POINT", 10, -1, 0, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> MallItem.create("称号", "", "", "POINT", -1, -1, 0, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> MallItem.create("称号", "", "", "POINT", 10, -2, 0, true, 0));
        assertThrows(IllegalArgumentException.class,
                () -> MallItem.create("称号", "", "", "POINT", 10, -1, -1, true, 0));
    }

    @Test
    void assetCodeIsRequiredAndNormalised() {
        assertEquals("POINT", item(1, 0, true).assetCode());
        assertEquals("EVENT_COIN", MallItem.create("称号", "", "", " event_coin ", 10, 1, 0, true, 0).assetCode());
        assertEquals("请选择结算资产",
                assertThrows(IllegalArgumentException.class,
                        () -> MallItem.create("称号", "", "", "  ", 10, 1, 0, true, 0)).getMessage());
    }

    /** 老版本商品没有 assetCode 字段：读出来必须是能用的对象，不能因为一条历史数据炸掉整个列表接口。 */
    @Test
    void legacyItemWithoutAssetCodeStillReads() {
        MallItem legacy = new MallItem("legacy-1", "老商品", "", "", "", 100, 5, 0, true, 0, 1L, 1L);
        assertEquals("", legacy.assetCode());
        assertTrue(legacy.available());
        legacy.requireAvailable(1);
        assertEquals("", legacy.withEnabled(false).assetCode());
        assertEquals("", legacy.deduct(1).assetCode());
        assertEquals("", legacy.restore(1).assetCode());
        assertEquals("CNY", legacy.update("老商品", "", "", "cny", 100, 5, 0, true, 0).assetCode());
    }

    @Test
    void coverUrlKeepsOnlyTheRelativePath() {
        MallItem withOrigin = MallItem.create("称号", "", "https://admin.example.com/api/files/1/content", "POINT",
                10, 1, 0, true, 0);
        assertEquals("/api/files/1/content", withOrigin.imageUrl());
        MallItem withProxy = MallItem.create("称号", "", "/proxy/api/files/2/content", "POINT", 10, 1, 0, true, 0);
        assertEquals("/api/files/2/content", withProxy.imageUrl());
        assertEquals("", MallItem.create("称号", "", "   ", "POINT", 10, 1, 0, true, 0).imageUrl());
    }

    @Test
    void unlimitedStockIsNeverSoldOutAndSurvivesDeduct() {
        MallItem unlimited = item(MallItem.UNLIMITED_STOCK, 0, true);
        assertTrue(unlimited.unlimitedStock());
        assertFalse(unlimited.soldOut());
        assertTrue(unlimited.available());
        assertEquals(MallItem.UNLIMITED_STOCK, unlimited.deduct(5).stock());
        assertEquals(MallItem.UNLIMITED_STOCK, unlimited.restore(5).stock());
    }

    @Test
    void requireAvailableReportsTheConcreteReason() {
        MallItem offline = item(5, 0, false);
        assertEquals("商品已下架", assertThrows(IllegalArgumentException.class,
                () -> offline.requireAvailable(1)).getMessage());

        MallItem soldOut = item(0, 0, true);
        assertTrue(soldOut.soldOut());
        assertEquals("商品已兑完", assertThrows(IllegalArgumentException.class,
                () -> soldOut.requireAvailable(1)).getMessage());

        MallItem few = item(2, 0, true);
        assertEquals("库存不足，仅剩 2 件", assertThrows(IllegalArgumentException.class,
                () -> few.requireAvailable(3)).getMessage());

        assertEquals("兑换数量至少为 1 件", assertThrows(IllegalArgumentException.class,
                () -> few.requireAvailable(0)).getMessage());
        assertEquals("单次兑换最多 " + MallItem.MAX_QUANTITY + " 件", assertThrows(IllegalArgumentException.class,
                () -> few.requireAvailable(MallItem.MAX_QUANTITY + 1)).getMessage());
    }

    @Test
    void deductAndRestoreKeepTheStockHonest() {
        MallItem item = item(5, 1, true);
        assertEquals(3, item.deduct(2).stock());
        assertEquals(7, item.restore(2).stock());
        // 库存不会被还成「超过原始值」以外的负数：扣到 0 就打住，避免负库存被当成不限量。
        assertEquals(0, item.deduct(9).stock());
    }

    @Test
    void totalPointsMultipliesAndUpdateKeepsIdentity() {
        MallItem item = item(5, 0, true);
        assertEquals(300, item.totalPoints(3));
        MallItem updated = item.update("新名字", "新说明", "https://example.com/a.png", "CNY", 200, 9, 2, false, 3);
        assertEquals(item.id(), updated.id());
        assertEquals(item.createdAt(), updated.createdAt());
        assertEquals("CNY", updated.assetCode());
        assertEquals(200, updated.pricePoints());
        assertEquals(2, updated.perUserLimit());
        assertFalse(updated.enabled());
        assertEquals(3, updated.sort());
    }
}
