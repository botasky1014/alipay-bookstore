package com.example.bookstore.book;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.bookstore.common.ApiException;

@Service
@Transactional(readOnly = true)
public class BookService {

    private final BookRepository books;

    public BookService(BookRepository books) {
        this.books = books;
    }

    public List<BookDto> list() {
        return books.findAllByOrderByIdAsc().stream().map(BookDto::from).toList();
    }

    public BookDto get(Long id) {
        return BookDto.from(require(id));
    }

    public Book require(Long id) {
        return books.findById(id).orElseThrow(() -> ApiException.notFound("Book not found"));
    }
}
