package online.yudream.base.plugin.eduroam.application.service;

import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamSettings;
import online.yudream.base.plugin.eduroam.domain.valobj.EduroamProbeOutcome;

/**
 * Eduroam 认证探测端口。
 *
 * <p>应用层只关心「这个账号密码能不能通过 Eduroam 认证」，具体是哪个上游服务、怎么解析响应由基础设施实现，
 * 这样应用服务可以在没有网络的情况下用假实现完整测试。
 */
public interface EduroamProbePort {

    /**
     * @param identity 提交给上游的完整账号（已按配置补好认证域）
     * @param password 用户输入的密码，只在本次调用内使用，实现方不得记录或持久化
     */
    EduroamProbeOutcome probe(String identity, String password, EduroamSettings settings);
}
