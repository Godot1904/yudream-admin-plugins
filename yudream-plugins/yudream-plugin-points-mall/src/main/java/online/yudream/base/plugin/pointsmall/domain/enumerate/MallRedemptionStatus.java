package online.yudream.base.plugin.pointsmall.domain.enumerate;

/**
 * 兑换记录的状态。
 *
 * <p>正常流程是「待发放 → 待确认收货 → 已完成」：管理员发放时必须带凭证，用户看到凭证后确认收到，
 * 记录才闭环。取消只能在发放之前（{@link #PENDING}）发生，取消时按兑换单号退款并归还库存。
 */
public enum MallRedemptionStatus {

    /** 已兑换、待发放。积分已扣，管理员尚未交付。 */
    PENDING("待发放"),

    /** 管理员已发放（带凭证），等用户确认收到。积分已扣，库存不归还。 */
    DELIVERED("待确认收货"),

    /** 用户已确认收到，流程闭环。 */
    COMPLETED("已完成"),

    /** 已取消。积分已按原单号退回，库存已归还。 */
    CANCELLED("已取消");

    private final String label;

    MallRedemptionStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
