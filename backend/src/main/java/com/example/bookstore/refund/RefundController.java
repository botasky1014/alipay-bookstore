package com.example.bookstore.refund;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.bookstore.order.OrderDto;

@RestController
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/api/orders/{orderId}/refund-requests")
    public OrderDto request(@PathVariable Long orderId, @Valid @RequestBody RefundRequestBody body) {
        return refundService.request(orderId, body.reason());
    }

    @GetMapping("/api/admin/refund-requests")
    public List<RefundRequestDto> list() {
        return refundService.listAll();
    }

    @PostMapping("/api/admin/refund-requests/{id}/approve")
    public RefundRequestDto approve(@PathVariable Long id) {
        return refundService.approve(id);
    }

    @PostMapping("/api/admin/refund-requests/{id}/reject")
    public RefundRequestDto reject(@PathVariable Long id) {
        return refundService.reject(id);
    }
}
