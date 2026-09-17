package online.yudream.base.plugin.eduroam.interfaces.assembler;

import online.yudream.base.plugin.eduroam.application.cmd.EduroamLoginCmd;
import online.yudream.base.plugin.eduroam.application.cmd.EduroamRegisterCmd;
import online.yudream.base.plugin.eduroam.application.cmd.EduroamReviewCmd;
import online.yudream.base.plugin.eduroam.application.cmd.EduroamSettingsSaveCmd;
import online.yudream.base.plugin.eduroam.application.dto.EduroamAccountDTO;
import online.yudream.base.plugin.eduroam.application.dto.EduroamAttemptDTO;
import online.yudream.base.plugin.eduroam.application.dto.EduroamLoginResultDTO;
import online.yudream.base.plugin.eduroam.application.dto.EduroamPublicConfigDTO;
import online.yudream.base.plugin.eduroam.application.dto.EduroamSettingsDTO;
import online.yudream.base.plugin.eduroam.interfaces.request.EduroamLoginRequest;
import online.yudream.base.plugin.eduroam.interfaces.request.EduroamRegisterRequest;
import online.yudream.base.plugin.eduroam.interfaces.request.EduroamReviewRequest;
import online.yudream.base.plugin.eduroam.interfaces.request.EduroamSettingsSaveRequest;
import online.yudream.base.plugin.eduroam.interfaces.res.EduroamAccountRes;
import online.yudream.base.plugin.eduroam.interfaces.res.EduroamAttemptRes;
import online.yudream.base.plugin.eduroam.interfaces.res.EduroamLoginResultRes;
import online.yudream.base.plugin.eduroam.interfaces.res.EduroamPublicConfigRes;
import online.yudream.base.plugin.eduroam.interfaces.res.EduroamSettingsRes;

/** 请求到命令、DTO 到响应的转换。控制器与 facade 只调用这里，不自己拼装字段。 */
public class EduroamWebAssembler {

    public EduroamRegisterCmd toCmd(EduroamRegisterRequest request) {
        EduroamRegisterRequest safe = request == null ? new EduroamRegisterRequest(null, null, null, null) : request;
        return new EduroamRegisterCmd(safe.ticket(), safe.state(), safe.password(), safe.confirmPassword());
    }

    public EduroamLoginCmd toCmd(EduroamLoginRequest request) {
        EduroamLoginRequest safe = request == null ? new EduroamLoginRequest(null, null, null) : request;
        return new EduroamLoginCmd(safe.account(), safe.password(), safe.state());
    }

    public EduroamSettingsSaveCmd toCmd(EduroamSettingsSaveRequest request) {
        if (request == null) {
            return new EduroamSettingsSaveCmd(null, null, null, null, null, null, null, null);
        }
        return new EduroamSettingsSaveCmd(request.enabled(), request.eduDomain(), request.storeDomain(),
                request.verifyEndpoint(), request.connectTimeoutSeconds(), request.requestTimeoutSeconds(),
                request.maxAttemptsPerHour(), request.tutorialMarkdown());
    }

    public EduroamReviewCmd toCmd(EduroamReviewRequest request) {
        return new EduroamReviewCmd(request == null ? null : request.reason());
    }

    public EduroamAccountRes toRes(EduroamAccountDTO dto) {
        return new EduroamAccountRes(dto.id(), dto.email(), dto.identity(), dto.account(), dto.domain(),
                dto.status(), dto.statusLabel(), dto.lastLoginIp(), dto.lastLoginAt(), dto.loginCount(),
                dto.firstLoginAt(), dto.blockedByUserId(), dto.blockReason(), dto.blockedAt(),
                dto.localUserId(), dto.localUsername(), dto.localNickname(), dto.createdAt(),
                dto.updatedAt());
    }

    public EduroamLoginResultRes toRes(EduroamLoginResultDTO dto) {
        return new EduroamLoginResultRes(dto.success(), dto.email(), dto.identity(), dto.account(), dto.ticket(),
                dto.expiresAt(), dto.reasonCode(), dto.message(), dto.registrationRequired(), dto.accountCreated());
    }

    public EduroamSettingsRes toRes(EduroamSettingsDTO dto) {
        return new EduroamSettingsRes(dto.enabled(), dto.eduDomain(), dto.storeDomain(), dto.verifyEndpoint(),
                dto.connectTimeoutSeconds(), dto.requestTimeoutSeconds(), dto.maxAttemptsPerHour(),
                dto.tutorialMarkdown());
    }

    public EduroamPublicConfigRes toRes(EduroamPublicConfigDTO dto) {
        return new EduroamPublicConfigRes(dto.enabled(), dto.eduDomain(), dto.accountHint(),
                dto.tutorialMarkdown());
    }

    public EduroamAttemptRes toRes(EduroamAttemptDTO dto) {
        return new EduroamAttemptRes(dto.id(), dto.email(), dto.identity(), dto.account(), dto.domain(),
                dto.success(), dto.reasonCode(), dto.reasonLabel(), dto.message(), dto.detail(), dto.clientIp(),
                dto.latencyMs(), dto.createdAt());
    }
}
