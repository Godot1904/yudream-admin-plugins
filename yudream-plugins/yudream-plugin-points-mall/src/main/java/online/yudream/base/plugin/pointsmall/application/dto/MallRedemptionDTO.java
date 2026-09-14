package online.yudream.base.plugin.pointsmall.application.dto;

import java.util.List;

/**
 * 兑换记录视图。
 *
 * <p>{@code userName} 由应用层尽力补齐（查不到就回落成用户 ID），只用于展示；管理员端需要跨用户识别是
 * 谁兑的，用户端只会拿到自己的记录。
 */
public record MallRedemptionDTO(
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
        List<MallDeliveryProofDTO> proofs,
        String deliveredByUserId,
        String deliveryNote,
        long deliveredAt,
        long confirmedAt,
        String cancelNote,
        long createdAt,
        long updatedAt
) {
}
