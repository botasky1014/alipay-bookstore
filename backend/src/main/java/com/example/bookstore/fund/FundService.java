package com.example.bookstore.fund;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.example.bookstore.alipay.AlipayGateway;
import com.example.bookstore.order.Order;
import com.example.bookstore.order.OrderRepository;

/** Fund summary from local records plus a reference comparison with the Alipay balance (design 6.11). */
@Service
public class FundService {

    private final FundTransactionRepository funds;
    private final OrderRepository orders;
    private final AlipayGateway alipay;

    public FundService(FundTransactionRepository funds, OrderRepository orders, AlipayGateway alipay) {
        this.funds = funds;
        this.orders = orders;
        this.alipay = alipay;
    }

    public FundSummaryDto summary() {
        BigDecimal totalIn = funds.sumByType(FundType.PAYMENT_IN);
        BigDecimal totalOut = funds.sumByType(FundType.REFUND_OUT);
        BigDecimal net = totalIn.subtract(totalOut);

        List<FundTransaction> list = funds.findAllByOrderByOccurredAtDescIdDesc();
        Map<Long, String> orderNos = orders.findAllById(list.stream().map(FundTransaction::getOrderId).toList())
                .stream().collect(Collectors.toMap(Order::getId, Order::getOrderNo, (a, b) -> a));
        List<FundSummaryDto.Transaction> transactions = list.stream()
                .map(f -> new FundSummaryDto.Transaction(f.getId(), f.getType(), f.getOrderId(),
                        orderNos.get(f.getOrderId()), f.getAmount(), f.getAlipayTradeNo(), f.getOccurredAt()))
                .toList();

        BigDecimal balance = alipay.balanceQuery().orElse(null);
        BigDecimal difference = balance == null ? null : balance.subtract(net);
        return new FundSummaryDto(totalIn, totalOut, net, balance, difference, transactions);
    }
}
