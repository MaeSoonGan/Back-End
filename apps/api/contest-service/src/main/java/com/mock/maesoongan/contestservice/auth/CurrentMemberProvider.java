package com.mock.maesoongan.contestservice.auth;

import com.mock.maesoongan.contestservice.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentMemberProvider {

    public Long memberId() {
        return currentMember().memberId();
    }

    public String loginId() {
        return currentMember().loginId();
    }

    private CurrentMember currentMember() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentMember currentMember)) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication failed");
        }

        return currentMember;
    }
}
