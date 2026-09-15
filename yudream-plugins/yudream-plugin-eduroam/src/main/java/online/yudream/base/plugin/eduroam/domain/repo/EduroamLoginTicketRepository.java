package online.yudream.base.plugin.eduroam.domain.repo;

import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamLoginTicket;

import java.util.Optional;

/** 外部登录交接票据仓储。 */
public interface EduroamLoginTicketRepository {

    EduroamLoginTicket save(EduroamLoginTicket ticket);

    /**
     * 取出并立刻删除票据（一次性核销）。返回空表示票据不存在或已被用过。
     */
    Optional<EduroamLoginTicket> consume(String id);

    /** 清理过期票据，返回清理条数。 */
    int purgeExpired(long now);

    long count();
}
