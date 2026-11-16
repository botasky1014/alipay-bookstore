package com.example.bookstore.settings;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/settings")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/alipay-urls")
    public AlipayUrlsDto getAlipayUrls() {
        return settingsService.getAlipayUrls();
    }

    @PutMapping("/alipay-urls")
    public AlipayUrlsDto updateAlipayUrls(@Valid @RequestBody UpdateAlipayUrlsRequest request) {
        return settingsService.updateAlipayUrls(request.notifyUrl(), request.returnUrl());
    }
}
