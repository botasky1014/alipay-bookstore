package com.example.bookstore.fund;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FundController {

    private final FundService fundService;

    public FundController(FundService fundService) {
        this.fundService = fundService;
    }

    @GetMapping("/api/admin/funds")
    public FundSummaryDto summary() {
        return fundService.summary();
    }
}
