package com.example.bookstore.alipay;

/** An Alipay call failed or returned an error; the message is safe to show to users. */
public class AlipayCallException extends RuntimeException {

    public AlipayCallException(String message) {
        super(message);
    }

    public AlipayCallException(String message, Throwable cause) {
        super(message, cause);
    }
}
