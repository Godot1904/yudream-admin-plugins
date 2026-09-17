package online.yudream.base.plugin.tarusso.domain.aggregate;

/**
 * CAS 兜底模式（state 不进 service 查询串）用的待回调记录。
 *
 * <p>正常模式把宿主签发的 state 追加在 service 上，CAS 回跳时会原样带回；兜底模式下 service 是插件的
 * 固定中转地址（不含任何查询串），IdP 不会把 state 带回来，因此发起登录时先把宿主 state 存下来，
 * 回调时取「该平台类型下最近一次仍未消费」的记录补回宿主回调。
 *
 * <p>代价：并发或多标签登录只能按"最近一次"匹配，CSRF 保护弱于正常模式；因此这是显式开启的兜底路径，
 * 记录存活时间也刻意压得很短（只覆盖"跳去 IdP 再回来"这一小段）。
 */
public final class RelayTicket {

    /** 兜底记录的存活时间：超过即视为过期，不再参与匹配。 */
    public static final long TTL_MILLIS = 5 * 60_000L;

    private final String hostState;
    private final String platformType;
    private final long createdAt;
    private final long expiresAt;

    public RelayTicket(String hostState, String platformType, long createdAt, long expiresAt) {
        if (hostState == null || hostState.isBlank()) {
            throw new IllegalArgumentException("兜底记录缺少宿主 state");
        }
        this.hostState = hostState.trim();
        this.platformType = platformType == null ? "" : platformType.trim();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt > 0 ? expiresAt : createdAt + TTL_MILLIS;
    }

    public static RelayTicket issue(String hostState, String platformType, long now) {
        return new RelayTicket(hostState, platformType, now, now + TTL_MILLIS);
    }

    public String hostState() {
        return hostState;
    }

    public String platformType() {
        return platformType;
    }

    public long createdAt() {
        return createdAt;
    }

    public long expiresAt() {
        return expiresAt;
    }

    public boolean expired(long now) {
        return expiresAt <= now;
    }

    /** 平台类型是否匹配（大小写不敏感，兼容 cas/CAS 两种写法）。 */
    public boolean matchesPlatform(String type) {
        return platformType.equalsIgnoreCase(type == null ? "" : type.trim());
    }
}
