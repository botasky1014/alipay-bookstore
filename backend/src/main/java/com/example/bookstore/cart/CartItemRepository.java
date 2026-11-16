package com.example.bookstore.cart;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByUserIdOrderByCreatedAtAscIdAsc(Long userId);

    Optional<CartItem> findByUserIdAndBookId(Long userId, Long bookId);

    void deleteByUserIdAndBookIdIn(Long userId, Collection<Long> bookIds);
}
