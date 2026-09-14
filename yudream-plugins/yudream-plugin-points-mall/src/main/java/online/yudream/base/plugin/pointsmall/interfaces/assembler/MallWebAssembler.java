package online.yudream.base.plugin.pointsmall.interfaces.assembler;

import online.yudream.base.plugin.pointsmall.application.cmd.MallDeliverCmd;
import online.yudream.base.plugin.pointsmall.application.cmd.MallDeliveryProofCmd;
import online.yudream.base.plugin.pointsmall.application.cmd.MallItemSaveCmd;
import online.yudream.base.plugin.pointsmall.application.cmd.MallRedeemCmd;
import online.yudream.base.plugin.pointsmall.application.cmd.MallSettingsSaveCmd;
import online.yudream.base.plugin.pointsmall.application.dto.MallAssetBalanceDTO;
import online.yudream.base.plugin.pointsmall.application.dto.MallAssetOptionDTO;
import online.yudream.base.plugin.pointsmall.application.dto.MallDeliveryProofDTO;
import online.yudream.base.plugin.pointsmall.application.dto.MallItemDTO;
import online.yudream.base.plugin.pointsmall.application.dto.MallOverviewDTO;
import online.yudream.base.plugin.pointsmall.application.dto.MallRedemptionDTO;
import online.yudream.base.plugin.pointsmall.application.dto.MallSettingsDTO;
import online.yudream.base.plugin.pointsmall.interfaces.request.MallItemSaveRequest;
import online.yudream.base.plugin.pointsmall.interfaces.request.MallRedeemRequest;
import online.yudream.base.plugin.pointsmall.interfaces.request.MallRedemptionDeliverRequest;
import online.yudream.base.plugin.pointsmall.interfaces.request.MallSettingsSaveRequest;
import online.yudream.base.plugin.pointsmall.interfaces.res.MallAssetBalanceRes;
import online.yudream.base.plugin.pointsmall.interfaces.res.MallAssetOptionRes;
import online.yudream.base.plugin.pointsmall.interfaces.res.MallDeliveryProofRes;
import online.yudream.base.plugin.pointsmall.interfaces.res.MallItemRes;
import online.yudream.base.plugin.pointsmall.interfaces.res.MallOverviewRes;
import online.yudream.base.plugin.pointsmall.interfaces.res.MallRedemptionRes;
import online.yudream.base.plugin.pointsmall.interfaces.res.MallSettingsRes;

import java.util.List;

/** 请求到命令、DTO 到响应的转换。控制器与 facade 只调用这里，不自己拼装字段。 */
public class MallWebAssembler {

    public MallItemSaveCmd toCmd(MallItemSaveRequest request) {
        MallItemSaveRequest safe = request == null
                ? new MallItemSaveRequest(null, null, null, null, null, null, null, null, null)
                : request;
        return new MallItemSaveCmd(safe.name(), safe.description(), safe.imageUrl(), safe.assetCode(),
                safe.pricePoints(), safe.stock(), safe.perUserLimit(), safe.enabled(), safe.sort());
    }

    public MallRedeemCmd toCmd(MallRedeemRequest request) {
        MallRedeemRequest safe = request == null ? new MallRedeemRequest(null, null, null) : request;
        return new MallRedeemCmd(safe.itemId(), safe.quantity(), safe.remark());
    }

    public MallSettingsSaveCmd toCmd(MallSettingsSaveRequest request) {
        MallSettingsSaveRequest safe = request == null ? new MallSettingsSaveRequest(null, null) : request;
        return new MallSettingsSaveCmd(safe.enabled(), safe.notice());
    }

    public MallDeliverCmd toCmd(MallRedemptionDeliverRequest request) {
        MallRedemptionDeliverRequest safe = request == null ? new MallRedemptionDeliverRequest(null, List.of()) : request;
        List<MallDeliveryProofCmd> proofs = safe.files() == null
                ? List.of()
                : safe.files().stream()
                        .filter(file -> file != null)
                        .map(file -> new MallDeliveryProofCmd(file.url(), file.filename(), file.contentType(),
                                file.size()))
                        .toList();
        return new MallDeliverCmd(safe.note(), proofs);
    }

    public MallItemRes toRes(MallItemDTO dto) {
        return new MallItemRes(dto.id(), dto.name(), dto.description(), dto.imageUrl(), dto.assetCode(),
                dto.assetName(), dto.assetSymbol(), dto.pricePoints(), dto.stock(), dto.perUserLimit(), dto.enabled(),
                dto.sort(), dto.available(), dto.createdAt(), dto.updatedAt());
    }

    public MallRedemptionRes toRes(MallRedemptionDTO dto) {
        return new MallRedemptionRes(dto.id(), dto.itemId(), dto.itemName(), dto.userId(), dto.userName(),
                dto.assetCode(), dto.quantity(), dto.unitPoints(), dto.totalPoints(), dto.status(), dto.statusLabel(),
                dto.remark(), dto.proofs().stream().map(this::toRes).toList(), dto.deliveredByUserId(),
                dto.deliveryNote(), dto.deliveredAt(), dto.confirmedAt(), dto.cancelNote(), dto.createdAt(),
                dto.updatedAt());
    }

    public MallDeliveryProofRes toRes(MallDeliveryProofDTO dto) {
        return new MallDeliveryProofRes(dto.url(), dto.filename(), dto.contentType(), dto.size(), dto.image());
    }

    public MallSettingsRes toRes(MallSettingsDTO dto) {
        return new MallSettingsRes(dto.enabled(), dto.notice());
    }

    public MallOverviewRes toRes(MallOverviewDTO dto) {
        return new MallOverviewRes(dto.enabled(), dto.assets().stream().map(this::toRes).toList(), dto.itemCount(),
                dto.pendingCount(), dto.deliveredCount(), dto.totalCount(), dto.notice());
    }

    public MallAssetBalanceRes toRes(MallAssetBalanceDTO dto) {
        return new MallAssetBalanceRes(dto.code(), dto.name(), dto.symbol(), dto.balance());
    }

    public MallAssetOptionRes toRes(MallAssetOptionDTO dto) {
        return new MallAssetOptionRes(dto.code(), dto.name(), dto.symbol(), dto.scale(), dto.enabled(), dto.money());
    }
}
