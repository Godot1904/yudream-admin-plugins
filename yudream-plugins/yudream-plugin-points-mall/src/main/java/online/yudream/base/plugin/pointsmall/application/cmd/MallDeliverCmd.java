package online.yudream.base.plugin.pointsmall.application.cmd;

import java.util.List;

/** 管理员发放兑换：发放备注 + 至少一张凭证。 */
public record MallDeliverCmd(String note, List<MallDeliveryProofCmd> proofs) {

    public List<MallDeliveryProofCmd> proofsOrEmpty() {
        return proofs == null ? List.of() : proofs;
    }
}
