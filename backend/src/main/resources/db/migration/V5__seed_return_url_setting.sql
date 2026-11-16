-- The return URL becomes a dynamic setting like the notify URL (both go through the tunnel).
-- Empty until the admin sets it on the Settings page.
INSERT INTO system_settings (setting_key, setting_value, updated_at) VALUES
    ('alipay.return_url', '', now());
