package com.example.bookstore.cart;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartDto get(@RequestParam Long userId) {
        return cartService.get(userId);
    }

    @PostMapping("/items")
    public CartDto add(@Valid @RequestBody AddCartItemRequest request) {
        return cartService.add(request);
    }

    @PutMapping("/items/{id}")
    public CartDto update(@PathVariable Long id, @Valid @RequestBody UpdateCartItemRequest request) {
        return cartService.update(id, request);
    }

    @DeleteMapping("/items/{id}")
    public CartDto remove(@PathVariable Long id) {
        return cartService.remove(id);
    }
}
