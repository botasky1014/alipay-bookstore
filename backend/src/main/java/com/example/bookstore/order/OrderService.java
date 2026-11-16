package com.example.bookstore.order;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bookstore.auth.UserService;
import com.example.bookstore.book.Book;
import com.example.bookstore.book.BookRepository;
import com.example.bookstore.cart.CartItemRepository;
import com.example.bookstore.common.ApiException;
import com.example.bookstore.refund.RefundRequest;
import com.example.bookstore.refund.RefundRequestRepository;

/** Order creation and queries (design 6.3). */
@Service
public class OrderService {

    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final BookRepository books;
    private final CartItemRepository cartItems;
    private final RefundRequestRepository refundRequests;
    private final UserService userService;
    private final OrderNumberGenerator orderNumbers;
    private final Clock clock;

    public OrderService(OrderRepository orders, OrderItemRepository orderItems, BookRepository books,
            CartItemRepository cartItems, RefundRequestRepository refundRequests, UserService userService,
            OrderNumberGenerator orderNumbers, Clock clock) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.books = books;
        this.cartItems = cartItems;
        this.refundRequests = refundRequests;
        this.userService = userService;
        this.orderNumbers = orderNumbers;
        this.clock = clock;
    }

    /** Creates an order and deducts stock in one transaction. */
    @Transactional
    public OrderDto create(CreateOrderRequest request) {
        userService.require(request.userId());
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (CreateOrderRequest.Line line : request.items()) {
            if (quantities.put(line.bookId(), line.quantity()) != null) {
                throw ApiException.badRequest("DUPLICATE_BOOK", "Each book may appear only once in an order");
            }
        }

        // Lock in ascending id order so concurrent orders cannot deadlock.
        List<Book> locked = books.lockAllByIds(quantities.keySet());
        Map<Long, Book> bookById = locked.stream().collect(Collectors.toMap(Book::getId, Function.identity()));
        if (bookById.size() != quantities.size()) {
            throw ApiException.notFound("Book not found");
        }
        List<String> shortTitles = new ArrayList<>();
        for (Book book : locked) {
            if (book.getStock() < quantities.get(book.getId())) {
                shortTitles.add(book.getTitle() + " (only " + book.getStock() + " left)");
            }
        }
        if (!shortTitles.isEmpty()) {
            throw ApiException.conflict("INSUFFICIENT_STOCK", "Not enough stock: " + String.join(", ", shortTitles));
        }

        Instant now = clock.instant();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Book book = bookById.get(entry.getKey());
            book.setStock(book.getStock() - entry.getValue());
            book.setUpdatedAt(now);
            total = total.add(book.getPrice().multiply(BigDecimal.valueOf(entry.getValue())));
        }

        Order order = orders.save(new Order(orderNumbers.next(), request.userId(), total, now));
        List<OrderItem> items = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantities.entrySet()) {
            Book book = bookById.get(entry.getKey());
            items.add(orderItems.save(
                    new OrderItem(order.getId(), book.getId(), book.getTitle(), book.getPrice(), entry.getValue())));
        }
        if (request.fromCart()) {
            cartItems.deleteByUserIdAndBookIdIn(request.userId(), quantities.keySet());
        }
        return OrderDto.from(order, items, null);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> listForUser(Long userId) {
        userService.require(userId);
        return toDtos(orders.findByUserIdOrderByCreatedAtDescIdDesc(userId));
    }

    @Transactional(readOnly = true)
    public OrderDto get(Long orderId) {
        return toDtos(List.of(require(orderId))).getFirst();
    }

    @Transactional(readOnly = true)
    public Order require(Long orderId) {
        return orders.findById(orderId).orElseThrow(() -> ApiException.notFound("Order not found"));
    }

    private List<OrderDto> toDtos(List<Order> list) {
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> ids = new HashSet<>(list.stream().map(Order::getId).toList());
        Map<Long, List<OrderItem>> itemsByOrder = orderItems.findByOrderIdInOrderByIdAsc(ids).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
        Map<Long, RefundRequest> refundByOrder = refundRequests.findByOrderIdIn(ids).stream()
                .collect(Collectors.toMap(RefundRequest::getOrderId, Function.identity()));
        return list.stream()
                .map(o -> OrderDto.from(o, itemsByOrder.getOrDefault(o.getId(), List.of()), refundByOrder.get(o.getId())))
                .toList();
    }
}
