package com.mock.maesoongan.authservice.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefreshTokenStoreTest {

    @Test
    void replaceRotatesRefreshToken() {
        RefreshTokenStore store = new RefreshTokenStore();
        store.save("testuser", "old-refresh-token");

        store.replace("testuser", "old-refresh-token", "new-refresh-token");

        assertThat(store.isActive("testuser", "old-refresh-token")).isFalse();
        assertThat(store.isActive("testuser", "new-refresh-token")).isTrue();
    }

    @Test
    void revokeAllRemovesAllRefreshTokensForUser() {
        RefreshTokenStore store = new RefreshTokenStore();
        store.save("testuser", "device-a-refresh-token");
        store.save("testuser", "device-b-refresh-token");

        store.revokeAll("testuser");

        assertThat(store.countByUserId("testuser")).isZero();
    }
}
