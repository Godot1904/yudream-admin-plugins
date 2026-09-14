package online.yudream.base.plugin.pointsmall.domain.aggregate;

import online.yudream.base.plugin.pointsmall.domain.enumerate.MallRedemptionStatus;
import online.yudream.base.plugin.pointsmall.domain.valobj.MallDeliveryProof;

import java.util.List;
import java.util.UUID;

/**
 * 一次积分兑换。
 *
 * <p>商品名称、单价与结算资产都做快照：商品之后改名、改价、换结算资产或下架，都不影响历史记录与退款
 * 去向。积分扣减与退款都以本记录的单号做幂等键（{@link #debitBusinessNo()} / {@link #refundBusinessNo()}），
 * 所以重复提交、重试、取消后重放都不会重复扣分或重复退款。
 *
 * <p>状态流转：待发放 --发放（带凭证）--> 待确认收货 --用户确认--> 已完成；待发放 --取消--> 已取消。
 */
public record MallRedemption(
        String id,
        String itemId,
        String itemName,
        String userId,
        String assetCode,
        int quantity,
        long unitPoints,
        long totalPoints,
        MallRedemptionStatus status,
        String remark,
        List<MallDeliveryProof> proofs,
        String deliveredByUserId,
        String deliveryNote,
        long deliveredAt,
        long confirmedAt,
        String cancelNote,
        long createdAt,
        long updatedAt
) {

    private static final String DEBIT_PREFIX = "mall-redeem-";
    private static final String REFUND_PREFIX = "mall-refund-";
    private static final int MAX_REMARK_LENGTH = 200;
    private static final int MAX_PROOFS = 6;

    public MallRedemption {
        id = requireText(id, "兑换记录 ID 不能为空");
        itemId = requireText(itemId, "商品 ID 不能为空");
        itemName = requireText(itemName, "商品名称不能为空");
        userId = requireText(userId, "兑换用户不能为空");
        assetCode = requireText(assetCode, "结算资产不能为空");
        if (quantity < 1) {
            throw new IllegalArgumentException("兑换数量至少为 1 件");
        }
        if (unitPoints < 0 || totalPoints < 0) {
            throw new IllegalArgumentException("积分不能为负");
        }
        status = status == null ? MallRedemptionStatus.PENDING : status;
        remark = truncate(remark);
        proofs = normalizeProofs(proofs);
        deliveredByUserId = text(deliveredByUserId);
        deliveryNote = truncate(deliveryNote);
        cancelNote = truncate(cancelNote);
    }

    public static MallRedemption create(MallItem item, String userId, int quantity, String remark) {
        long now = System.currentTimeMillis();
        return new MallRedemption(UUID.randomUUID().toString(), item.id(), item.name(), userId, item.assetCode(),
                quantity, item.pricePoints(), item.totalPoints(quantity), MallRedemptionStatus.PENDING, remark,
                List.of(), "", "", 0L, 0L, "", now, now);
    }

    /** 管理员发放：必须带至少一张凭证，之后等用户确认收到。 */
    public MallRedemption deliver(String operatorUserId, String note, List<MallDeliveryProof> proofs) {
        if (status != MallRedemptionStatus.PENDING) {
            throw new IllegalArgumentException("只有待发放的兑换可以发放");
        }
        List<MallDeliveryProof> safeProofs = normalizeProofs(proofs);
        if (safeProofs.isEmpty()) {
            throw new IllegalArgumentException("发放需要上传至少一张凭证");
        }
        long now = System.currentTimeMillis();
        return new MallRedemption(id, itemId, itemName, userId, assetCode, quantity, unitPoints, totalPoints,
                MallRedemptionStatus.DELIVERED, remark, safeProofs,
                requireText(operatorUserId, "操作人不能为空"), truncate(note), now, confirmedAt, cancelNote,
                createdAt, now);
    }

    /** 用户确认收到。只有兑换人自己能确认。 */
    public MallRedemption confirmReceipt(String userId) {
        if (status != MallRedemptionStatus.DELIVERED) {
            throw new IllegalArgumentException("只有已发放、待确认的兑换可以确认收货");
        }
        if (!belongsTo(userId)) {
            throw new IllegalArgumentException("兑换记录不存在");
        }
        long now = System.currentTimeMillis();
        return new MallRedemption(id, itemId, itemName, this.userId, assetCode, quantity, unitPoints, totalPoints,
                MallRedemptionStatus.COMPLETED, remark, proofs, deliveredByUserId, deliveryNote, deliveredAt, now,
                cancelNote, createdAt, now);
    }

    /** 取消并退款。只允许在发放之前；调用方负责按 {@link #refundBusinessNo()} 退款与归还库存。 */
    public MallRedemption cancel(String operatorUserId, String reason) {
        if (status != MallRedemptionStatus.PENDING) {
            throw new IllegalArgumentException("只有待发放的兑换可以取消并退款");
        }
        return new MallRedemption(id, itemId, itemName, userId, assetCode, quantity, unitPoints, totalPoints,
                MallRedemptionStatus.CANCELLED, remark, proofs, deliveredByUserId, deliveryNote, deliveredAt,
                confirmedAt, requireText(reason, "取消原因不能为空"), createdAt, System.currentTimeMillis());
    }

    public boolean pending() {
        return status == MallRedemptionStatus.PENDING;
    }

    public boolean delivered() {
        return status == MallRedemptionStatus.DELIVERED;
    }

    public boolean belongsTo(String userId) {
        return this.userId.equals(text(userId));
    }

    /** 兑换扣分的业务单号；钱包按它对账，重复提交不会二次扣分。 */
    public String debitBusinessNo() {
        return DEBIT_PREFIX + id;
    }

    /** 取消退款的业务单号；钱包按它对账，重复取消不会二次退款。 */
    public String refundBusinessNo() {
        return REFUND_PREFIX + id;
    }

    private static List<MallDeliveryProof> normalizeProofs(List<MallDeliveryProof> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(proof -> proof != null)
                .limit(MAX_PROOFS)
                .toList();
    }

    private static String truncate(String value) {
        String text = text(value);
        return text.length() <= MAX_REMARK_LENGTH ? text : text.substring(0, MAX_REMARK_LENGTH);
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String requireText(String value, String message) {
        String text = text(value);
        if (text.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }
}
