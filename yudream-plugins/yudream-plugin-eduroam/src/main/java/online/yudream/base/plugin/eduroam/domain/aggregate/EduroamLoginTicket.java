package online.yudream.base.plugin.eduroam.domain.aggregate;

/**
 * 一次外部登录的交接票据。
 *
 * <p>凭据页认证成功后签发，浏览器立刻带着它回调宿主的第三方登录回调端点；宿主再用
 * {@code exchange(ticket, state)} 把它换成外部身份。票据一次性、短时效：
 * <ul>
 *   <li>只能核销一次，核销即删除，避免同一个票据被重放成第二次登录；</li>
 *   <li>{@code state} 必须与宿主签发的一致，防止把票据塞进别的登录会话；</li>
 *   <li>存放于插件文档集合（而非进程内存），多实例部署时认证请求与回调可以落在不同节点。</li>
 * </ul>
 */
public record EduroamLoginTicket(
        String id,
        String email,
        String identity,
        String account,
        String state,
        String platformType,
        long expiresAt,
        long createdAt
) {

    public EduroamLoginTicket {
        id = requireText(id, "登录票据不能为空");
        state = requireText(state, "登录票据缺少 state");
        email = text(email);
        identity = requireText(identity, "登录票据缺少外部账号标识");
        account = text(account);
        platformType = text(platformType);
    }

    public boolean expired(long now) {
        return expiresAt > 0 && now >= expiresAt;
    }

    public boolean matchesState(String candidate) {
        return state.equals(candidate == null ? "" : candidate.trim());
    }

    public boolean matchesType(String candidate) {
        return candidate == null || candidate.isBlank() || platformType.isBlank()
                || platformType.equalsIgnoreCase(candidate.trim());
    }

    private static String requireText(String value, String message) {
        String text = text(value);
        if (text.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return text;
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }
}
