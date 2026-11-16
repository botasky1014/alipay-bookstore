package com.example.bookstore.order;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.example.bookstore.book.BookRepository;

/** Returns an order's books to stock (on close and on refund). */
@Service
public class InventoryService {

    private final OrderItemRepository orderItems;
    private final BookRepository books;
    private final Clock clock;

    public InventoryService(OrderItemRepository orderItems, BookRepository books, Clock clock) {
        this.orderItems = orderItems;
        this.books = books;
        this.clock = clock;
    }

    /** Must run inside the transaction that changed the order status, so both commit together. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restoreStock(Long orderId) {
        for (OrderItem item : orderItems.findByOrderIdOrderByIdAsc(orderId)) {
            books.adjustStock(item.getBookId(), item.getQuantity(), clock.instant());
        }
    }
}
