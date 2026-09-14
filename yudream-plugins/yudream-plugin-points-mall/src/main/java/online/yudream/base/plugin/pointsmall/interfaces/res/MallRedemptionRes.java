package online.yudream.base.plugin.pointsmall.interfaces.res;

import java.util.List;

/**
 * 兑换记录响应。
 *
 * <p>{@code userName} 仅用于展示，查不到时回落成用户 ID；{@code proofs} 是发放凭证（图片地址来自对象存储）。
 */
public record MallRedemptionRes(
        String id,
        String itemId,
        String itemName,
        String userId,
        String userName,
        String assetCode,
        int quantity,
        long unitPoints,
        long totalPoints,
        String status,
        String statusLabel,
        String remark,
        List<MallDeliveryProofRes> proofs,
        String deliveredByUserId,
        String deliveryNote,
        long deliveredAt,
        long confirmedAt,
        String cancelNote,
        long createdAt,
        long updatedAt
) {
}
