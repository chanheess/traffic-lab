package com.trafficlab.order;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final NotificationService notificationService;
    private final RewardService rewardService;
    private final ApplicationEventPublisher eventPublisher;

    public Order create(OrderRequest request) {
        Order order = Order.builder()
                .productName(request.getProductName())
                .amount(request.getAmount())
                .build();
        return orderRepository.save(order);
    }

    public OrderResponse createSync(OrderRequest request) {
        Order order = create(request);
        notificationService.send(order);   // 300~500ms 블로킹
        rewardService.calculate(order);    // 100ms 블로킹
        return OrderResponse.from(order);
    }

    public OrderResponse createAsync(OrderRequest request) {
        Order order = create(request);
        eventPublisher.publishEvent(new OrderCreatedEvent(order));  // 즉시 반환
        return OrderResponse.from(order);
    }
}
