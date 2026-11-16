package com.example.bookstore.support;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.example.bookstore.alipay.AlipayGateway;
import com.example.bookstore.common.TimeConfig;

/**
 * Base class for all integration tests: one shared PostgreSQL container, a mocked
 * {@link AlipayGateway}, a controllable clock, and a data reset before every test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.scheduling.enabled=false", "alipay.app-id=" + IntegrationTestBase.APP_ID,
                "alipay.seller-id=" + IntegrationTestBase.SELLER_ID, "alipay.timeout-express=2h"})
@Import(IntegrationTestBase.TestClockConfig.class)
public abstract class IntegrationTestBase {

    public static final Instant START = Instant.parse("2026-11-16T02:00:00Z");
    public static final long BUYER_ID = 1L;
    public static final String APP_ID = "test-app-id";
    public static final String SELLER_ID = "test-seller-id";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    static {
        POSTGRES.start();
    }

    private static List<Map<String, Object>> originalBooks;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @TestConfiguration
    static class TestClockConfig {

        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START, TimeConfig.ZONE);
        }
    }

    @MockitoBean
    protected AlipayGateway alipay;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected JdbcTemplate jdbc;

    @Value("${local.server.port}")
    private int port;

    protected TestApi api;

    @BeforeEach
    void resetState() {
        api = new TestApi(port);
        clock.set(START);
        jdbc.execute("TRUNCATE cart_items, fund_transactions, refund_requests, order_items, orders RESTART IDENTITY");
        jdbc.update("UPDATE system_settings SET setting_value = ''");
        restoreBooks();
    }

    private void restoreBooks() {
        if (originalBooks == null) {
            originalBooks = jdbc.queryForList("SELECT id, title, price, stock FROM books ORDER BY id");
        }
        for (Map<String, Object> book : originalBooks) {
            jdbc.update("UPDATE books SET title = ?, price = ?, stock = ? WHERE id = ?",
                    book.get("title"), book.get("price"), book.get("stock"), book.get("id"));
        }
    }

    protected Clock clock() {
        return clock;
    }

    protected int stockOf(long bookId) {
        return jdbc.queryForObject("SELECT stock FROM books WHERE id = ?", Integer.class, bookId);
    }

    protected void setStock(long bookId, int stock) {
        jdbc.update("UPDATE books SET stock = ? WHERE id = ?", stock, bookId);
    }

    public static final String NOTIFY_URL = "https://first.tunnel.example/api/alipay/notify";
    public static final String RETURN_URL = "https://first.tunnel.example/pay/return";

    /** Sets both Alipay URLs through the admin API. */
    protected void configureAlipayUrls(String notifyUrl, String returnUrl) {
        var response = api.put("/api/admin/settings/alipay-urls", Map.of("notifyUrl", notifyUrl, "returnUrl", returnUrl));
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Settings update failed: " + response.getBody());
        }
    }

    protected void configureAlipayUrls() {
        configureAlipayUrls(NOTIFY_URL, RETURN_URL);
    }

    protected BigDecimal priceOf(long bookId) {
        return jdbc.queryForObject("SELECT price FROM books WHERE id = ?", BigDecimal.class, bookId);
    }

    protected String orderStatus(long orderId) {
        return jdbc.queryForObject("SELECT status FROM orders WHERE id = ?", String.class, orderId);
    }

    protected String orderNo(long orderId) {
        return jdbc.queryForObject("SELECT order_no FROM orders WHERE id = ?", String.class, orderId);
    }

    protected int fundCount(String type) {
        return jdbc.queryForObject("SELECT count(*) FROM fund_transactions WHERE type = ?", Integer.class, type);
    }

    /** Places a single-book order for buyer (user id 1) and returns the order id. */
    protected long createOrder(long bookId, int quantity) {
        var response = api.post("/api/orders",
                Map.of("userId", BUYER_ID, "items", List.of(Map.of("bookId", bookId, "quantity", quantity)),
                        "fromCart", false));
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new IllegalStateException("Order creation failed: " + response.getBody());
        }
        return ((Number) response.getBody().get("id")).longValue();
    }

    /** Marks an order as paid directly in the database (for tests that start from PAID). */
    protected void forcePaid(long orderId, Instant paidAt) {
        jdbc.update("UPDATE orders SET status = 'PAID', paid_at = ?, pay_requested_at = ?, alipay_trade_no = ? "
                + "WHERE id = ?", java.sql.Timestamp.from(paidAt), java.sql.Timestamp.from(paidAt),
                "TRADE" + orderId, orderId);
        jdbc.update("INSERT INTO fund_transactions (type, order_id, amount, alipay_trade_no, occurred_at) "
                + "SELECT 'PAYMENT_IN', id, total_amount, alipay_trade_no, paid_at FROM orders WHERE id = ?", orderId);
    }

    protected static BigDecimal dec(Object value) {
        return new BigDecimal(String.valueOf(value));
    }
}
