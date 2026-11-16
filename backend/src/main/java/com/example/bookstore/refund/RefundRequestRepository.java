package com.example.bookstore.refund;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    Optional<RefundRequest> findByOrderId(Long orderId);

    List<RefundRequest> findByOrderIdIn(Collection<Long> orderIds);

    List<RefundRequest> findAllByOrderByCreatedAtDescIdDesc();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefundRequest r set r.status = :to, r.reviewedAt = :now, r.lastError = null "
            + "where r.id = :id and r.status = com.example.bookstore.refund.RefundStatus.PENDING_REVIEW")
    int review(@Param("id") Long id, @Param("to") RefundStatus to, @Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefundRequest r set r.lastError = :error "
            + "where r.id = :id and r.status = com.example.bookstore.refund.RefundStatus.PENDING_REVIEW")
    int recordError(@Param("id") Long id, @Param("error") String error);
}
