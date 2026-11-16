INSERT INTO users (username, role) VALUES
    ('buyer', 'USER'),
    ('admin', 'ADMIN');

-- Empty until the admin sets it on the Settings page.
INSERT INTO system_settings (setting_key, setting_value, updated_at) VALUES
    ('alipay.notify_url', '', now());
