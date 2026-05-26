package com.mock.maesoongan.authservice.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mock.maesoongan.authservice.common.BusinessException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class VerificationCodeStoreTest {

    @Test
    void verifyAnyReturnsGoneWhenAnyMatchingPurposeCodeIsExpired() throws Exception {
        VerificationCodeStore store = new VerificationCodeStore();
        putExpiredCode(store, "email", "expired@example.com", "signup", "123456");

        assertThatThrownBy(() -> store.verifyAny(
                "email",
                "expired@example.com",
                "123456",
                "signup",
                "find-id",
                "reset-password"
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.GONE);
    }

    @SuppressWarnings("unchecked")
    private void putExpiredCode(
            VerificationCodeStore store,
            String channel,
            String target,
            String purpose,
            String code
    ) throws Exception {
        Field codesField = VerificationCodeStore.class.getDeclaredField("codes");
        codesField.setAccessible(true);
        Map<String, Object> codes = (Map<String, Object>) codesField.get(store);

        Class<?> codeEntryClass = Class.forName(VerificationCodeStore.class.getName() + "$CodeEntry");
        Constructor<?> constructor = codeEntryClass.getDeclaredConstructor(String.class, LocalDateTime.class);
        constructor.setAccessible(true);

        codes.put(
                channel + ":" + purpose + ":" + target,
                constructor.newInstance(code, LocalDateTime.now().minusSeconds(1))
        );
    }
}
