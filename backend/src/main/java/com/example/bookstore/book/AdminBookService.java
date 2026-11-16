package com.example.bookstore.book;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bookstore.common.ApiException;

/** Admin edits of stock, price and title (design 6.10). Changes affect only new orders. */
@Service
public class AdminBookService {

    private final BookRepository books;
    private final Clock clock;

    public AdminBookService(BookRepository books, Clock clock) {
        this.books = books;
        this.clock = clock;
    }

    @Transactional
    public BookDto update(Long id, UpdateBookRequest request) {
        if (request.stockDelta() == null && request.price() == null && request.title() == null) {
            throw ApiException.badRequest("NOTHING_TO_UPDATE", "Provide a stock change, price or title");
        }
        if (!books.existsById(id)) {
            throw ApiException.notFound("Book not found");
        }
        Instant now = clock.instant();
        // Stock is changed by a delta in one atomic statement so it cannot overwrite concurrent orders.
        if (request.stockDelta() != null && request.stockDelta() != 0
                && books.adjustStock(id, request.stockDelta(), now) == 0) {
            throw ApiException.conflict("STOCK_BELOW_ZERO", "Stock cannot go below 0");
        }
        Book book = books.findById(id).orElseThrow();
        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw ApiException.badRequest("VALIDATION_ERROR", "title: must not be blank");
            }
            book.setTitle(request.title().trim());
        }
        if (request.price() != null) {
            book.setPrice(request.price());
        }
        book.setUpdatedAt(now);
        return BookDto.from(book);
    }
}
