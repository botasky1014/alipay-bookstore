package com.example.bookstore.payment;

import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.bookstore.order.OrderDto;

@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final AlipayCallbackService callbackService;

    public PaymentController(PaymentService paymentService, AlipayCallbackService callbackService) {
        this.paymentService = paymentService;
        this.callbackService = callbackService;
    }

    @PostMapping("/api/orders/{id}/pay")
    public PayResponse pay(@PathVariable Long id) {
        return paymentService.pay(id);
    }

    @PostMapping("/api/orders/{id}/close")
    public OrderDto close(@PathVariable Long id) {
        return paymentService.close(id);
    }

    /** Alipay asynchronous notification: form-encoded in, plain text "success" / "failure" out. */
    @PostMapping(value = "/api/alipay/notify", produces = MediaType.TEXT_PLAIN_VALUE)
    public String notify(@RequestParam Map<String, String> params) {
        return callbackService.handleNotification(params) ? "success" : "failure";
    }

    /** Called by the frontend return page with all query parameters Alipay appended to return_url. */
    @PostMapping("/api/alipay/return-check")
    public OrderDto returnCheck(@RequestBody Map<String, String> params) {
        return callbackService.handleReturn(params);
    }
}
