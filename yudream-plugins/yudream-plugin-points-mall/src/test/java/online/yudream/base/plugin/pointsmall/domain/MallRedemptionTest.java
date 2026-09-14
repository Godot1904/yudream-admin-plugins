package online.yudream.base.plugin.pointsmall.domain;

import online.yudream.base.plugin.pointsmall.domain.aggregate.MallItem;
import online.yudream.base.plugin.pointsmall.domain.aggregate.MallRedemption;
import online.yudream.base.plugin.pointsmall.domain.enumerate.MallRedemptionStatus;
import online.yudream.base.plugin.pointsmall.domain.valobj.MallDeliveryProof;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MallRedemptionTest {

    private static MallItem item() {
        return MallItem.create("称号兑换券", "兑换一个自定义称号", "", "POINT", 120, 10, 1, true, 0);
    }

    private static List<MallDeliveryProof> proofs() {
        return List.of(new MallDeliveryProof("/api/files/9/content", "proof.png", "image/png", 2048L));
    }

    @Test
    void createSnapshotsNameUnitPriceAssetAndTotal() {
        MallRedemption redemption = MallRedemption.create(item(), "1001", 3, " 备注 ");
        assertEquals("称号兑换券", redemption.itemName());
        assertEquals("POINT", redemption.assetCode());
        assertEquals(120, redemption.unitPoints());
        assertEquals(360, redemption.totalPoints());
        assertEquals(MallRedemptionStatus.PENDING, redemption.status());
        assertTrue(redemption.pending());
        assertTrue(redemption.belongsTo("1001"));
        assertFalse(redemption.belongsTo("1002"));
        assertTrue(redemption.proofs().isEmpty());
        assertEquals("mall-redeem-" + redemption.id(), redemption.debitBusinessNo());
        assertEquals("mall-refund-" + redemption.id(), redemption.refundBusinessNo());
    }

    @Test
    void deliverRequiresAtLeastOneProofAndMovesToAwaitingConfirmation() {
        MallRedemption pending = MallRedemption.create(item(), "1001", 1, "");
        assertEquals("发放需要上传至少一张凭证",
                assertThrows(IllegalArgumentException.class, () -> pending.deliver("9001", "已交付", List.of()))
                        .getMessage());

        MallRedemption delivered = pending.deliver("9001", "已当面交付", proofs());
        assertEquals(MallRedemptionStatus.DELIVERED, delivered.status());
        assertEquals("待确认收货", delivered.status().label());
        assertEquals("9001", delivered.deliveredByUserId());
        assertEquals("已当面交付", delivered.deliveryNote());
        assertEquals(1, delivered.proofs().size());
        assertTrue(delivered.proofs().get(0).image());
        assertTrue(delivered.deliveredAt() > 0);
        assertTrue(delivered.delivered());
        assertEquals(0L, delivered.confirmedAt());
        // 发放之后不能再发放，也不能取消退款。
        assertEquals("只有待发放的兑换可以发放",
                assertThrows(IllegalArgumentException.class, () -> delivered.deliver("9001", "", proofs())).getMessage());
        assertEquals("只有待发放的兑换可以取消并退款",
                assertThrows(IllegalArgumentException.class, () -> delivered.cancel("9001", "反悔")).getMessage());
    }

    @Test
    void onlyTheOwnerCanConfirmReceipt() {
        MallRedemption delivered = MallRedemption.create(item(), "1001", 1, "").deliver("9001", "", proofs());
        assertEquals("兑换记录不存在",
                assertThrows(IllegalArgumentException.class, () -> delivered.confirmReceipt("1002")).getMessage());
        assertEquals("只有已发放、待确认的兑换可以确认收货",
                assertThrows(IllegalArgumentException.class,
                        () -> MallRedemption.create(item(), "1001", 1, "").confirmReceipt("1001")).getMessage());

        MallRedemption completed = delivered.confirmReceipt("1001");
        assertEquals(MallRedemptionStatus.COMPLETED, completed.status());
        assertEquals("已完成", completed.status().label());
        assertTrue(completed.confirmedAt() > 0);
        assertFalse(completed.delivered());
        assertEquals(1, completed.proofs().size());
        assertEquals("只有已发放、待确认的兑换可以确认收货",
                assertThrows(IllegalArgumentException.class, () -> completed.confirmReceipt("1001")).getMessage());
    }

    @Test
    void cancelRequiresAReasonAndCannotRunTwice() {
        MallRedemption pending = MallRedemption.create(item(), "1001", 2, "");
        assertEquals("取消原因不能为空",
                assertThrows(IllegalArgumentException.class, () -> pending.cancel("9001", " ")).getMessage());

        MallRedemption cancelled = pending.cancel("9001", "库存有误");
        assertEquals(MallRedemptionStatus.CANCELLED, cancelled.status());
        assertEquals("库存有误", cancelled.cancelNote());
        assertEquals("只有待发放的兑换可以取消并退款",
                assertThrows(IllegalArgumentException.class, () -> cancelled.cancel("9001", "再来一次")).getMessage());
    }

    @Test
    void remarkIsTrimmedAndBounded() {
        MallRedemption redemption = MallRedemption.create(item(), "1001", 1, "   ");
        assertEquals("", redemption.remark());
        String longRemark = "x".repeat(500);
        assertEquals(200, MallRedemption.create(item(), "1001", 1, longRemark).remark().length());
    }

    @Test
    void proofsKeepAtMostSixAndSkipBrokenEntries() {
        MallDeliveryProof proof = new MallDeliveryProof("/api/files/1/content", "a.png", "image/png", 10L);
        List<MallDeliveryProof> many = List.of(proof, proof, proof, proof, proof, proof, proof);
        MallRedemption delivered = MallRedemption.create(item(), "1001", 1, "").deliver("9001", "", many);
        assertEquals(6, delivered.proofs().size());

        assertEquals(2048L, proofs().get(0).size());
        assertEquals("", new MallDeliveryProof("/api/files/2/content", null, null, -5).filename());
        assertFalse(new MallDeliveryProof("/api/files/2/content", "a.txt", "text/plain", 1).image());
        assertEquals("凭证地址不能为空",
                assertThrows(IllegalArgumentException.class,
                        () -> new MallDeliveryProof("  ", "a.png", "image/png", 1)).getMessage());
    }
}
