package com.patientcase.audit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for AuditService.extractIp — no Spring context needed.
 */
class AuditIpTest {

    @Test
    void extractIp_nullRequest_returnsNull() {
        assertThat(AuditService.extractIp(null)).isNull();
    }

    @Test
    void extractIp_remoteAddrOnly_returnsRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.10");

        assertThat(AuditService.extractIp(request)).isEqualTo("192.168.1.10");
    }

    @Test
    void extractIp_xForwardedForSingle_returnsThatIp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.5");

        assertThat(AuditService.extractIp(request)).isEqualTo("203.0.113.5");
    }

    @Test
    void extractIp_xForwardedForChain_returnsFirstIp() {
        // Chain: client → proxy1 → proxy2 → app
        // Only the first (leftmost) entry is the original client
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "203.0.113.5, 10.0.0.2, 10.0.0.3");

        assertThat(AuditService.extractIp(request)).isEqualTo("203.0.113.5");
    }

    @Test
    void extractIp_xForwardedForBlank_fallsBackToRemoteAddr() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("192.168.1.10");
        request.addHeader("X-Forwarded-For", "   ");

        assertThat(AuditService.extractIp(request)).isEqualTo("192.168.1.10");
    }

    @Test
    void extractIp_veryLongIp_isTruncatedTo50Chars() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("A".repeat(100));

        String result = AuditService.extractIp(request);
        assertThat(result).hasSize(50);
    }

    @Test
    void extractIp_xForwardedForWithSpaces_isStripped() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "  203.0.113.5  , 10.0.0.1");

        assertThat(AuditService.extractIp(request)).isEqualTo("203.0.113.5");
    }
}
