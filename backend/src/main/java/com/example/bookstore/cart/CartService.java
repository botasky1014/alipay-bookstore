package com.example.bookstore.cart;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bookstore.auth.UserService;
import com.example.bookstore.book.Book;
import com.example.bookstore.book.BookRepository;
import com.example.bookstore.book.BookService;
import com.example.bookstore.common.ApiException;

/** Server-side cart (design 6.2). Quantities never exceed current stock. */
@Service
@Transactional
public class CartService {

    private final CartItemRepository cartItems;
    private final BookRepository books;
    private final BookService bookService;
    private final UserService userService;
    private final Clock clock;

    public CartService(CartItemRepository cartItems, BookRepository books, BookService bookService,
            UserService userService, Clock clock) {
        this.cartItems = cartItems;
        this.books = books;
        this.bookService = bookService;
        this.userService = userService;
        this.clock = clock;
    }

    /** Returns the cart after syncing it with stock: caps quantities and drops sold-out books. */
    public CartDto get(Long userId) {
        userService.require(userId);
        List<CartItem> items = cartItems.findByUserIdOrderByCreatedAtAscIdAsc(userId);
        Map<Long, Book> bookById = books.findAllById(items.stream().map(CartItem::getBookId).toList())
                .stream().collect(Collectors.toMap(Book::getId, Function.identity()));

        List<CartDto.Item> result = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (CartItem item : items) {
            Book book = bookById.get(item.getBookId());
            if (book.getStock() == 0) {
                cartItems.delete(item);
                continue;
            }
            if (item.getQuantity() > book.getStock()) {
                item.setQuantity(book.getStock());
            }
            BigDecimal subtotal = book.getPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(subtotal);
            result.add(new CartDto.Item(item.getId(), book.getId(), book.getTitle(), book.getCoverImage(),
                    book.getPrice(), item.getQuantity(), book.getStock(), subtotal));
        }
        return new CartDto(result, total);
    }

    public CartDto add(AddCartItemRequest request) {
        userService.require(request.userId());
        Book book = bookService.require(request.bookId());
        if (book.getStock() == 0) {
            throw ApiException.conflict("OUT_OF_STOCK", "This book is out of stock");
        }
        cartItems.findByUserIdAndBookId(request.userId(), book.getId()).ifPresentOrElse(
                existing -> existing.setQuantity(Math.min(existing.getQuantity() + request.quantity(),
                        book.getStock())),
                () -> cartItems.save(new CartItem(request.userId(), book.getId(),
                        Math.min(request.quantity(), book.getStock()), clock.instant())));
        return get(request.userId());
    }

    public CartDto update(Long itemId, UpdateCartItemRequest request) {
        CartItem item = require(itemId);
        Book book = bookService.require(item.getBookId());
        if (book.getStock() == 0) {
            cartItems.delete(item);
        } else {
            item.setQuantity(Math.min(request.quantity(), book.getStock()));
        }
        return get(item.getUserId());
    }

    public CartDto remove(Long itemId) {
        CartItem item = require(itemId);
        cartItems.delete(item);
        return get(item.getUserId());
    }

    private CartItem require(Long itemId) {
        return cartItems.findById(itemId).orElseThrow(() -> ApiException.notFound("Cart item not found"));
    }
}
