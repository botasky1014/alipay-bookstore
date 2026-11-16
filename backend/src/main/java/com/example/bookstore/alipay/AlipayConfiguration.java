package com.example.bookstore.alipay;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.AlipayConfig;
import com.alipay.api.DefaultAlipayClient;

@Configuration
@EnableConfigurationProperties(AlipayProperties.class)
public class AlipayConfiguration {

    static final String CHARSET = "UTF-8";
    static final String SIGN_TYPE = "RSA2";

    /** Public key mode, RSA2, UTF-8, JSON. */
    @Bean
    public AlipayClient alipayClient(AlipayProperties properties) throws AlipayApiException {
        AlipayConfig config = new AlipayConfig();
        config.setServerUrl(properties.gateway());
        config.setAppId(properties.appId());
        config.setPrivateKey(properties.appPrivateKey());
        config.setAlipayPublicKey(properties.alipayPublicKey());
        config.setFormat("json");
        config.setCharset(CHARSET);
        config.setSignType(SIGN_TYPE);
        return new DefaultAlipayClient(config);
    }
}
