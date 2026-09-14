package online.yudream.base.plugin.pointsmall.domain.repo;

import online.yudream.base.plugin.pointsmall.domain.aggregate.MallItem;
import online.yudream.base.plugin.pointsmall.domain.aggregate.MallRedemption;
import online.yudream.base.plugin.pointsmall.domain.enumerate.MallRedemptionStatus;
import online.yudream.base.plugin.pointsmall.domain.valobj.MallSettings;

import java.util.List;
import java.util.Optional;

/**
 * 积分商城的持久化契约。
 *
 * <p>分页参数与「用户」维度都是明确的：用户端只会用 {@code userId = 当前登录人} 调用，管理员端才会传
 * 空的 userId 做跨用户查询。仓储不认识权限，调用方必须先把范围定死。
 */
public interface MallRepository {

    MallItem saveItem(MallItem item);

    Optional<MallItem> findItem(String itemId);

    /** 商品列表；{@code keyword} 匹配名称与说明，{@code enabled} 为空表示不限上下架状态。 */
    List<MallItem> listItems(String keyword, Boolean enabled, int page, int size);

    long countItems(String keyword, Boolean enabled);

    /** 在架商品用到的结算资产代码，去重后按商品排序顺序返回；用户端概览据此列出各项余额。 */
    List<String> listOpenItemAssetCodes();

    void deleteItem(String itemId);

    MallRedemption saveRedemption(MallRedemption redemption);

    Optional<MallRedemption> findRedemption(String redemptionId);

    /** 兑换记录；三个筛选条件为空表示不限。按创建时间从新到旧。 */
    List<MallRedemption> listRedemptions(String userId, String itemId, MallRedemptionStatus status, int page, int size);

    long countRedemptions(String userId, String itemId, MallRedemptionStatus status);

    /** 该商品是否被兑换过（含已取消），用于判断能否删除商品。 */
    long countItemRedemptions(String itemId);

    /** 某用户在某商品上已兑换且未取消的件数合计，用于「每人限兑」。 */
    long sumUserItemQuantity(String userId, String itemId);

    MallSettings findSettings();

    MallSettings saveSettings(MallSettings settings);
}
