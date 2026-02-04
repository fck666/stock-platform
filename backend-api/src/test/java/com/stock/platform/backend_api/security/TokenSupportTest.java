package com.stock.platform.backend_api.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TokenSupportTest {
    @Test
    void refreshTokenIsRandomEnough() {
        String a = TokenSupport.newRefreshToken();
        String b = TokenSupport.newRefreshToken();
        assertNotNull(a);
        assertNotNull(b);
        assertNotEquals(a, b);
        assertTrue(a.length() >= 40);
    }

    @Test
    void hashIsStable() {
        String h1 = TokenSupport.sha256Base64Url("abc");
        String h2 = TokenSupport.sha256Base64Url("abc");
        String h3 = TokenSupport.sha256Base64Url("abcd");
        assertEquals(h1, h2);
        assertNotEquals(h1, h3);
    }
}
