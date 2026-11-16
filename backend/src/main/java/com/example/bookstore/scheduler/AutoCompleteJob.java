package com.example.bookstore.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.bookstore.order.OrderStateService;

/** PAID orders become COMPLETED 30 minutes after payment (design 6.7). */
@Component
public class AutoCompleteJob {

    private static final Logger log = LoggerFactory.getLogger(AutoCompleteJob.class);

    private final OrderStateService state;

    public AutoCompleteJob(OrderStateService state) {
        this.state = state;
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 60_000)
    public void run() {
        int completed = state.completeExpiredPaidOrders();
        if (completed > 0) {
            log.info("Auto-completed {} paid order(s)", completed);
        }
    }
}
