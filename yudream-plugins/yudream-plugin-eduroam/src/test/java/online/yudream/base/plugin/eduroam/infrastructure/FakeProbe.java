package online.yudream.base.plugin.eduroam.infrastructure;

import online.yudream.base.plugin.eduroam.application.service.EduroamProbePort;
import online.yudream.base.plugin.eduroam.domain.aggregate.EduroamSettings;
import online.yudream.base.plugin.eduroam.domain.enumerate.EduroamFailureReason;
import online.yudream.base.plugin.eduroam.domain.valobj.EduroamProbeOutcome;

import java.util.ArrayList;
import java.util.List;

/** 测试用探测实现：按预置密码判定结果，并记录调用过的账号，便于断言「密码不落库、账号被补全」。 */
public class FakeProbe implements EduroamProbePort {

    private final List<String> identities = new ArrayList<>();
    private String expectedPassword = "correct-password";
    private EduroamFailureReason failure = EduroamFailureReason.CREDENTIAL_INVALID;

    public FakeProbe expectPassword(String password) {
        this.expectedPassword = password;
        return this;
    }

    public FakeProbe failWith(EduroamFailureReason reason) {
        this.failure = reason;
        return this;
    }

    public List<String> identities() {
        return List.copyOf(identities);
    }

    @Override
    public EduroamProbeOutcome probe(String identity, String password, EduroamSettings settings) {
        identities.add(identity);
        if (expectedPassword.equals(password)) {
            return EduroamProbeOutcome.passed("EAP authentication completed successfully", 120L);
        }
        return EduroamProbeOutcome.failed(failure, "EAP Failure", 130L);
    }
}
