package com.example.bookstore.book;

import java.math.BigDecimal;

public record BookDto(Long id, String title, String author, String description, String coverImage,
        BigDecimal price, int stock) {

    public static BookDto from(Book book) {
        return new BookDto(book.getId(), book.getTitle(), book.getAuthor(), book.getDescription(),
                book.getCoverImage(), book.getPrice(), book.getStock());
    }
}
