package com.example.bookstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.bookstore.support.IntegrationTestBase;

class SettingsIT extends IntegrationTestBase {

    private static final String PATH = "/api/admin/settings/alipay-urls";

    @Test
    void urlsAreEmptyByDefault() {
        assertThat(api.get(PATH).getBody()).containsEntry("notifyUrl", "").containsEntry("returnUrl", "");
    }

    @Test
    void updatedUrlsAreReturnedImmediately() {
        var update = api.put(PATH, Map.of("notifyUrl", " https://abc.tunnel.example/api/alipay/notify ",
                "returnUrl", "https://abc.tunnel.example/pay/return"));
        assertThat(update.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(api.get(PATH).getBody())
                .containsEntry("notifyUrl", "https://abc.tunnel.example/api/alipay/notify")
                .containsEntry("returnUrl", "https://abc.tunnel.example/pay/return");
    }

    @Test
    void updatedAtChangesOnlyForTheUrlThatChanged() {
        configureAlipayUrls("https://a.example/api/alipay/notify", "https://a.example/pay/return");
        Object returnUpdatedAt = api.get(PATH).getBody().get("returnUrlUpdatedAt");

        clock.advance(java.time.Duration.ofMinutes(5));
        configureAlipayUrls("https://b.example/api/alipay/notify", "https://a.example/pay/return");

        Map<String, Object> body = api.get(PATH).getBody();
        assertThat(body.get("returnUrlUpdatedAt")).isEqualTo(returnUpdatedAt);
        assertThat(body.get("notifyUrlUpdatedAt")).isNotEqualTo(returnUpdatedAt);
    }

    @Test
    void rejectsInvalidUrls() {
        for (String bad : new String[] {"not a url", "ftp://host/path", "https://", ""}) {
            var badNotify = api.put(PATH, Map.of("notifyUrl", bad, "returnUrl", "https://ok.example/pay/return"));
            assertThat(badNotify.getStatusCode()).as("notify " + bad).isEqualTo(HttpStatus.BAD_REQUEST);
            var badReturn = api.put(PATH, Map.of("notifyUrl", "https://ok.example/notify", "returnUrl", bad));
            assertThat(badReturn.getStatusCode()).as("return " + bad).isEqualTo(HttpStatus.BAD_REQUEST);
        }
        assertThat(api.get(PATH).getBody()).containsEntry("notifyUrl", "").containsEntry("returnUrl", "");
    }
}
