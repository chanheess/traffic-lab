package com.trafficlab.order;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class OrderCreatedEvent {
    private final Order order;
}
