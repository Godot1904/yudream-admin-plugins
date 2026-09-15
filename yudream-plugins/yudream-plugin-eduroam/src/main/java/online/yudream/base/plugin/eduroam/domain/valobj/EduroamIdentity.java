package online.yudream.base.plugin.eduroam.domain.valobj;

import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamSettings;

import java.util.Locale;

/**
 * 一次核验的账号解析结果。
 *
 * <p>用户只填一个账号，本对象负责把它拆成两个不同的东西：
 * <ul>
 *   <li>{@code identity}：真正提交给 Eduroam 认证服务的账号（补上认证域后）；</li>
 *   <li>{@code email}：入库与注册用的邮箱（补上「入库邮箱域」后，可与认证域不同）。</li>
 * </ul>
 * 域名限定、格式校验都在这里一次做完，下游不再重复判断。
 */
public record EduroamIdentity(String account, String identity, String email, String domain) {

    private static final int MAX_ACCOUNT_LENGTH = 190;
    private static final int MAX_EMAIL_LENGTH = 190;

    public static EduroamIdentity resolve(String rawAccount, EduroamSettings settings) {
        EduroamSettings safeSettings = settings == null ? EduroamSettings.defaults() : settings;
        String account = rawAccount == null ? "" : rawAccount.trim();
        if (account.isEmpty()) {
            throw new IllegalArgumentException("请填写 Eduroam 账号");
        }
        if (account.length() > MAX_ACCOUNT_LENGTH) {
            throw new IllegalArgumentException("Eduroam 账号过长");
        }
        if (account.indexOf(' ') >= 0 || account.indexOf('\t') >= 0) {
            throw new IllegalArgumentException("Eduroam 账号不能包含空格");
        }

        int at = account.lastIndexOf('@');
        if (at < 0) {
            if (!safeSettings.restrictToEduDomain()) {
                throw new IllegalArgumentException(
                        "请填写完整的 Eduroam 账号（形如 学号@学校域名），或联系管理员配置认证域");
            }
            String local = account.toLowerCase(Locale.ROOT);
            String domain = safeSettings.eduDomain();
            String email = buildEmail(local, safeSettings.effectiveStoreDomain(), domain);
            return new EduroamIdentity(account, local + "@" + domain, email, storeDomainOf(email));
        }

        String local = account.substring(0, at).trim();
        String domain = account.substring(at + 1).trim().toLowerCase(Locale.ROOT);
        if (local.isEmpty() || domain.isEmpty() || domain.indexOf('.') < 0) {
            throw new IllegalArgumentException("Eduroam 账号格式不正确，请填写形如 学号@学校域名 的账号");
        }
        if (safeSettings.restrictToEduDomain() && !domain.equals(safeSettings.eduDomain())) {
            throw new IllegalArgumentException("Eduroam 账号域名必须是 @" + safeSettings.eduDomain());
        }
        String email = buildEmail(local.toLowerCase(Locale.ROOT), safeSettings.effectiveStoreDomain(), domain);
        return new EduroamIdentity(account, local + "@" + domain, email, storeDomainOf(email));
    }

    /** 归一化邮箱：去空格、转小写；统一入口，避免各处大小写不一致导致查不到记录。 */
    public static String normalizeEmail(String rawEmail) {
        return rawEmail == null ? "" : rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    public static boolean looksLikeEmail(String value) {
        String email = normalizeEmail(value);
        if (email.isEmpty() || email.length() > MAX_EMAIL_LENGTH) {
            return false;
        }
        int at = email.lastIndexOf('@');
        if (at <= 0 || at == email.length() - 1) {
            return false;
        }
        String domain = email.substring(at + 1);
        return domain.indexOf('.') > 0 && !domain.startsWith(".") && !domain.endsWith(".")
                && email.indexOf(' ') < 0;
    }

    private static String buildEmail(String local, String storeDomain, String authDomain) {
        String domain = storeDomain == null || storeDomain.isBlank() ? authDomain : storeDomain;
        String email = local + "@" + domain;
        if (!looksLikeEmail(email)) {
            throw new IllegalArgumentException("邮箱域名不正确，请联系管理员检查「认证域 / 入库邮箱域」配置");
        }
        return normalizeEmail(email);
    }

    private static String storeDomainOf(String email) {
        int at = email.lastIndexOf('@');
        return at < 0 ? "" : email.substring(at + 1);
    }
}
