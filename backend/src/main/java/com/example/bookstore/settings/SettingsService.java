package com.example.bookstore.settings;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bookstore.common.ApiException;

/**
 * Dynamic settings stored in the database (design 3.2): the Alipay notify URL and return URL. Both
 * point at the tunnel, whose address changes often. Values are read on every call with no cache, so
 * a change takes effect immediately without restarting anything.
 */
@Service
public class SettingsService {

    static final String NOTIFY_URL = "alipay.notify_url";
    static final String RETURN_URL = "alipay.return_url";

    private final SystemSettingRepository settings;
    private final Clock clock;

    public SettingsService(SystemSettingRepository settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AlipayUrlsDto getAlipayUrls() {
        return toDto(require(NOTIFY_URL), require(RETURN_URL));
    }

    /** The current notify URL, or an empty string when the admin has not set it yet. */
    @Transactional(readOnly = true)
    public String getNotifyUrl() {
        return require(NOTIFY_URL).getValue();
    }

    /** The current return URL, or an empty string when the admin has not set it yet. */
    @Transactional(readOnly = true)
    public String getReturnUrl() {
        return require(RETURN_URL).getValue();
    }

    @Transactional
    public AlipayUrlsDto updateAlipayUrls(String notifyUrl, String returnUrl) {
        String notify = validateHttpUrl("Notify URL", notifyUrl);
        String ret = validateHttpUrl("Return URL", returnUrl);
        Instant now = clock.instant();
        SystemSetting notifySetting = require(NOTIFY_URL);
        SystemSetting returnSetting = require(RETURN_URL);
        update(notifySetting, notify, now);
        update(returnSetting, ret, now);
        return toDto(notifySetting, returnSetting);
    }

    /** Only touches updated_at when the value really changed, so the page shows when each URL last changed. */
    private static void update(SystemSetting setting, String value, Instant now) {
        if (!setting.getValue().equals(value)) {
            setting.setValue(value);
            setting.setUpdatedAt(now);
        }
    }

    private static AlipayUrlsDto toDto(SystemSetting notify, SystemSetting ret) {
        return new AlipayUrlsDto(notify.getValue(), notify.getUpdatedAt(), ret.getValue(), ret.getUpdatedAt());
    }

    private SystemSetting require(String key) {
        return settings.findById(key)
                .orElseThrow(() -> new IllegalStateException("Missing system setting " + key));
    }

    private static String validateHttpUrl(String label, String raw) {
        String value = raw.trim();
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            if (scheme != null && (scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))
                    && uri.getHost() != null) {
                return value;
            }
        } catch (URISyntaxException e) {
            // Fall through to the error below.
        }
        throw ApiException.badRequest("INVALID_URL", label + " must be a valid http:// or https:// URL");
    }
}
