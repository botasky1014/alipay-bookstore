package com.example.bookstore.order;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * @param fromCart true when checking out from the cart; the ordered books are then removed from it
 */
public record CreateOrderRequest(@NotNull Long userId, @NotEmpty List<@Valid Line> items, boolean fromCart) {

    public record Line(@NotNull Long bookId, @NotNull @Min(1) Integer quantity) {
    }
}
