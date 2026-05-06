package com.trafficlab.order;

import java.time.LocalDateTime;

import lombok.Getter;

@Getter
public class OrderResponse {
    private final Long id;
    private final String productName;
    private final int amount;
    private final LocalDateTime createdAt;

    private OrderResponse(Order order) {
        this.id = order.getId();
        this.productName = order.getProductName();
        this.amount = order.getAmount();
        this.createdAt = order.getCreatedAt();
    }

    public static OrderResponse from(Order order) {
        return new OrderResponse(order);
    }
}
