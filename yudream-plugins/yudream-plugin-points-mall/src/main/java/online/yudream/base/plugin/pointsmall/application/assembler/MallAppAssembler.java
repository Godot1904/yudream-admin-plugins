package online.yudream.base.plugin.pointsmall.application.assembler;

import online.yudream.base.plugin.pointsmall.application.dto.MallAssetBalanceDTO;
import online.yudream.base.plugin.pointsmall.application.dto.MallAssetOptionDTO;
import online.yudream.base.plugin.pointsmall.application.dto.MallDeliveryProofDTO;
import online.yudream.base.plugin.pointsmall.application.dto.MallItemDTO;
import online.yudream.base.plugin.pointsmall.application.dto.MallRedemptionDTO;
import online.yudream.base.plugin.pointsmall.application.dto.MallSettingsDTO;
import online.yudream.base.plugin.pointsmall.domain.aggregate.MallItem;
import online.yudream.base.plugin.pointsmall.domain.aggregate.MallRedemption;
import online.yudream.base.plugin.pointsmall.domain.valobj.MallDeliveryProof;
import online.yudream.base.plugin.pointsmall.domain.valobj.MallSettings;
import online.yudream.base.plugin.wallet.api.PluginWalletAsset;

import java.math.BigDecimal;

/** 领域对象到应用 DTO 的转换。 */
public class MallAppAssembler {

    /** {@code asset} 是商品的结算资产在钱包侧的记录，取不到时名称留空（前端会提示资产不可用）。 */
    public MallItemDTO toItemDTO(MallItem item, PluginWalletAsset asset) {
        return new MallItemDTO(item.id(), item.name(), item.description(), item.imageUrl(), item.assetCode(),
                asset == null ? "" : asset.name(), asset == null ? "" : asset.symbol(), item.pricePoints(),
                item.stock(), item.perUserLimit(), item.enabled(), item.sort(), item.available(), item.createdAt(),
                item.updatedAt());
    }

    public MallRedemptionDTO toRedemptionDTO(MallRedemption redemption, String userName) {
        return new MallRedemptionDTO(redemption.id(), redemption.itemId(), redemption.itemName(), redemption.userId(),
                userName, redemption.assetCode(), redemption.quantity(), redemption.unitPoints(),
                redemption.totalPoints(), redemption.status().name(), redemption.status().label(), redemption.remark(),
                redemption.proofs().stream().map(this::toProofDTO).toList(), redemption.deliveredByUserId(),
                redemption.deliveryNote(), redemption.deliveredAt(), redemption.confirmedAt(), redemption.cancelNote(),
                redemption.createdAt(), redemption.updatedAt());
    }

    public MallDeliveryProofDTO toProofDTO(MallDeliveryProof proof) {
        return new MallDeliveryProofDTO(proof.url(), proof.filename(), proof.contentType(), proof.size(),
                proof.image());
    }

    public MallSettingsDTO toSettingsDTO(MallSettings settings) {
        return new MallSettingsDTO(settings.enabled(), settings.notice());
    }

    public MallAssetOptionDTO toAssetOptionDTO(PluginWalletAsset asset) {
        return new MallAssetOptionDTO(asset.code(), asset.name(), asset.symbol(), asset.scale(), asset.enabled(),
                asset.money());
    }

    public MallAssetBalanceDTO toAssetBalanceDTO(String code, PluginWalletAsset asset, BigDecimal balance) {
        return new MallAssetBalanceDTO(code, asset == null ? "" : asset.name(), asset == null ? "" : asset.symbol(),
                balance == null ? "0" : balance.stripTrailingZeros().toPlainString());
    }
}
