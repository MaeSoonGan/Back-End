package com.mock.maesoongan.member.entity;

import com.mock.maesoongan.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "member")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "login_id", nullable = false, length = 50, unique = true)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100, unique = true)
    private String email;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Column(length = 20)
    private String phone;

    @Column(name = "login_fail_count")
    private Integer loginFailCount;

    @Column(name = "email_verified")
    private Boolean emailVerified;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "total_asset", precision = 18, scale = 2)
    private BigDecimal totalAsset;

    private Member(String loginId, String password, String email, String nickname, String phone) {
        this.loginId = loginId;
        this.password = password;
        this.email = email;
        this.nickname = nickname;
        this.phone = phone;
        this.loginFailCount = 0;
        this.emailVerified = false;
        this.status = MemberStatus.ACTIVE;
        this.totalAsset = BigDecimal.ZERO;
    }

    public static Member create(String loginId, String password, String email, String nickname, String phone) {
        return new Member(loginId, password, email, nickname, phone);
    }

    public boolean isLocked() {
        return lockedAt != null || status == MemberStatus.SUSPENDED;
    }

    public void increaseLoginFailCount() {
        int failCount = loginFailCount == null ? 0 : loginFailCount;
        this.loginFailCount = failCount + 1;
        if (this.loginFailCount >= 5) {
            this.lockedAt = LocalDateTime.now();
            this.status = MemberStatus.SUSPENDED;
        }
    }

    public void resetLoginFailCount() {
        this.loginFailCount = 0;
    }

    public void verifyEmail() {
        this.emailVerified = true;
    }

    public void changePassword(String password) {
        this.password = password;
        resetLoginFailCount();
    }

    public void suspend() {
        this.status = MemberStatus.SUSPENDED;
        this.lockedAt = LocalDateTime.now();
    }

    public void paySeedMoney(BigDecimal seedAmount) {
        BigDecimal currentAsset = totalAsset == null ? BigDecimal.ZERO : totalAsset;
        this.totalAsset = currentAsset.add(seedAmount);
    }
}
