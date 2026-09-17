package online.yudream.base.plugin.tarusso.domain.repo;

import online.yudream.base.plugin.tarusso.domain.aggregate.RelayTicket;

import java.util.Optional;

/** CAS 兜底模式的待回调记录仓储。 */
public interface RelayTicketRepository {

    void save(RelayTicket ticket);

    /**
     * 该平台类型下最近一次仍未过期的记录（兜底模式没有载体码，只能按最近一次匹配）。
     *
     * @param platformType 平台类型（cas / oidc），大小写不敏感
     * @param now          当前时间戳
     */
    Optional<RelayTicket> latest(String platformType, long now);

    /** 消费一条记录（一次性，取出后即删除）。 */
    void consume(String hostState);

    /** 清理过期记录，返回清理条数。 */
    int purgeExpired(long now);
}
