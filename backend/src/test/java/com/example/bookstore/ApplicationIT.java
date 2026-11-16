package com.example.bookstore;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.example.bookstore.support.IntegrationTestBase;

class ApplicationIT extends IntegrationTestBase {

    @Test
    void healthEndpointReturnsUp() {
        var response = api.get("/api/health");
        assertThat(response.getBody()).containsEntry("status", "UP");
    }

    @Test
    void migrationsSeedUsersBooksAndSettings() {
        assertThat(jdbc.queryForList("SELECT username || ':' || role FROM users ORDER BY id", String.class))
                .containsExactly("buyer:USER", "admin:ADMIN");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM books WHERE stock = 50 AND price < 500", Integer.class))
                .isEqualTo(30);
        assertThat(jdbc.queryForList("SELECT setting_key FROM system_settings ORDER BY setting_key", String.class))
                .containsExactly("alipay.notify_url", "alipay.return_url");
    }
}
