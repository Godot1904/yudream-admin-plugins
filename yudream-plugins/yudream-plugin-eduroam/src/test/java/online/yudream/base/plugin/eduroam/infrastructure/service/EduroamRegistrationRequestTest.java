package online.yudream.base.plugin.eduroam.infrastructure.service;

import online.yudream.base.plugin.eduroam.infrastructure.support.JsonSupport;
import online.yudream.base.plugin.eduroam.interfaces.request.EduroamRegisterRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EduroamRegistrationRequestTest {
    @Test
    void noIdentityOrRoleCanBeChosenInRegistrationBody() {
        var request = JsonSupport.read("""
                {"ticket":"proof","state":"state","password":"site-secret","confirmPassword":"site-secret",
                 "email":"admin@example.edu.cn","userId":"1","role":"admin"}
                """, EduroamRegisterRequest.class);
        assertEquals("proof", request.ticket());
        assertFalse(request.toString().contains("site-secret"));
        assertFalse(request.toString().contains("admin"));
        assertEquals(4, request.getClass().getRecordComponents().length);
    }

    @Test
    void malformedSecretInputNeverAppearsInErrorOrCause() {
        var failure = assertThrows(IllegalArgumentException.class, () -> JsonSupport.read(
                "{\"password\":\"site-secret\", broken}", EduroamRegisterRequest.class));
        assertEquals("请求 JSON 格式不正确", failure.getMessage());
        assertNull(failure.getCause());
    }
}
