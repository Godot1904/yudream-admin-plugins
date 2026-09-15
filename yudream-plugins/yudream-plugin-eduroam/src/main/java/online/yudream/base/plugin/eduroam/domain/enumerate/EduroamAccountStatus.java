package online.yudream.base.plugin.eduroam.domain.enumerate;

/**
 * Eduroam 账号在本站的状态。
 *
 * <p>只有「正常」的账号能登录；「已禁止」是管理员对该账号的封禁，即使密码正确也拒绝登录，
 * 保留记录是为了让管理员随时解除封禁并留下操作痕迹。
 */
public enum EduroamAccountStatus {

    ACTIVE("正常"),
    BLOCKED("已禁止");

    private final String label;

    EduroamAccountStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static EduroamAccountStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return ACTIVE;
        }
        try {
            return valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ACTIVE;
        }
    }
}
