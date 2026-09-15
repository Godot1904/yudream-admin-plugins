package online.yudream.base.plugin.eduroam.application.assembler;

import online.yudream.base.plugin.eduroam.application.dto.EduroamAccountDTO;
import online.yudream.base.plugin.eduroam.application.dto.EduroamAttemptDTO;
import online.yudream.base.plugin.eduroam.application.dto.EduroamLoginResultDTO;
import online.yudream.base.plugin.eduroam.application.dto.EduroamPublicConfigDTO;
import online.yudream.base.plugin.eduroam.application.dto.EduroamSettingsDTO;
import online.yudream.base.plugin.eduroam.application.service.EduroamLocalUserPort;
import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamAccount;
import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamAttempt;
import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamLoginTicket;
import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamSettings;
import online.yudream.base.plugin.eduroam.domain.enumerate.EduroamFailureReason;

/** 领域对象到应用 DTO 的转换。 */
public class EduroamAppAssembler {

    /** {@code localUser} 为空表示站内还没有注册该「本站邮箱」对应的账号。 */
    public EduroamAccountDTO toAccountDTO(EduroamAccount account, EduroamLocalUserPort.LocalUser localUser) {
        return new EduroamAccountDTO(
                account.id(),
                account.email(),
                account.identity(),
                account.account(),
                account.domain(),
                account.status().name(),
                account.status().label(),
                account.lastLoginIp(),
                account.lastLoginAt(),
                account.loginCount(),
                account.firstLoginAt(),
                account.blockedByUserId(),
                account.blockReason(),
                account.blockedAt(),
                localUser == null ? "" : localUser.userId(),
                localUser == null ? "" : localUser.username(),
                localUser == null ? "" : localUser.nickname(),
                account.createdAt(),
                account.updatedAt()
        );
    }

    public EduroamSettingsDTO toSettingsDTO(EduroamSettings settings) {
        return new EduroamSettingsDTO(
                settings.enabled(),
                settings.eduDomain(),
                settings.storeDomain(),
                settings.verifyEndpoint(),
                settings.connectTimeoutSeconds(),
                settings.requestTimeoutSeconds(),
                settings.maxAttemptsPerHour(),
                settings.tutorialMarkdown()
        );
    }

    public EduroamPublicConfigDTO toPublicConfigDTO(EduroamSettings settings) {
        return new EduroamPublicConfigDTO(
                settings.enabled(),
                settings.eduDomain(),
                accountHint(settings),
                settings.tutorialMarkdown()
        );
    }

    public EduroamLoginResultDTO toLoginResultDTO(EduroamLoginTicket ticket) {
        return new EduroamLoginResultDTO(true, ticket.email(), ticket.identity(), ticket.account(),
                ticket.id(), ticket.expiresAt(), "SUCCESS", "");
    }

    public EduroamLoginResultDTO toFailureDTO(String email, String identity, String account,
                                              String reasonCode, String message) {
        return new EduroamLoginResultDTO(false, text(email), text(identity), text(account), "", 0L,
                reasonCode, message);
    }

    public EduroamAttemptDTO toAttemptDTO(EduroamAttempt attempt) {
        return new EduroamAttemptDTO(
                attempt.id(),
                attempt.email(),
                attempt.identity(),
                attempt.account(),
                attempt.domain(),
                attempt.success(),
                attempt.reasonCode(),
                reasonLabel(attempt.reasonCode()),
                attempt.message(),
                attempt.detail(),
                attempt.clientIp(),
                attempt.latencyMs(),
                attempt.createdAt()
        );
    }

    /** 审计列表上的原因标签：成功 / 已知原因中文名 / 原样返回。 */
    private String reasonLabel(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return "";
        }
        return switch (reasonCode) {
            case "SUCCESS" -> "登录成功";
            case "INVALID_ACCOUNT" -> "账号格式不正确";
            case "RATE_LIMITED" -> "触发限流";
            case "BLOCKED" -> "账号已被禁止登录";
            default -> {
                try {
                    yield EduroamFailureReason.valueOf(reasonCode).message();
                } catch (IllegalArgumentException ignored) {
                    yield reasonCode;
                }
            }
        };
    }

    private String accountHint(EduroamSettings settings) {
        if (settings.restrictToEduDomain()) {
            return "只填学号/工号即可，系统会自动补 @" + settings.eduDomain();
        }
        return "请填写完整账号，形如 学号@学校域名";
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }
}
