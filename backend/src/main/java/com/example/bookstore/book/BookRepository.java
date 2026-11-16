package com.example.bookstore.book;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findAllByOrderByIdAsc();

    /** Locks the given books in ascending id order (fixed order avoids deadlocks). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Book b where b.id in :ids order by b.id")
    List<Book> lockAllByIds(@Param("ids") Collection<Long> ids);

    /**
     * Atomically adds {@code delta} (may be negative) to the stock.
     *
     * @return 1 if applied, 0 if the book does not exist or the result would be negative
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Book b set b.stock = b.stock + :delta, b.updatedAt = :now "
            + "where b.id = :id and b.stock + :delta >= 0")
    int adjustStock(@Param("id") Long id, @Param("delta") int delta, @Param("now") Instant now);
}
