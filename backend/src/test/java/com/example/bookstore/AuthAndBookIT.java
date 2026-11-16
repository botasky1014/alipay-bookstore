package com.example.bookstore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.example.bookstore.support.IntegrationTestBase;

class AuthAndBookIT extends IntegrationTestBase {

    @Test
    void loginReturnsUserWithRole() {
        var buyer = api.post("/api/auth/login", Map.of("username", "buyer"));
        assertThat(buyer.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(buyer.getBody()).containsEntry("username", "buyer").containsEntry("role", "USER");

        var admin = api.post("/api/auth/login", Map.of("username", " admin "));
        assertThat(admin.getBody()).containsEntry("role", "ADMIN");
    }

    @Test
    void loginWithUnknownUsernameFails() {
        var response = api.post("/api/auth/login", Map.of("username", "nobody"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).containsEntry("message", "Username does not exist");
    }

    @Test
    void loginRequiresUsername() {
        var response = api.post("/api/auth/login", Map.of("username", ""));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listsThirtyBooksAndShowsDetail() {
        var list = api.getList("/api/books");
        assertThat(list.getBody()).hasSize(30);
        assertThat(list.getBody().getFirst()).containsKeys("id", "title", "author", "coverImage", "price", "stock");

        var detail = api.get("/api/books/2");
        assertThat(detail.getBody()).containsEntry("title", "1984").containsEntry("stock", 50);

        assertThat(api.get("/api/books/999").getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
