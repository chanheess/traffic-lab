package com.trafficlab.order;

import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderEventHandler {

    private final NotificationService notificationService;
    private final RewardService rewardService;

    @Async
    @EventListener
    public void handle(OrderCreatedEvent event) {
        notificationService.send(event.getOrder());
        rewardService.calculate(event.getOrder());
    }
}
