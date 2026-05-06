package com.trafficlab.order;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/demo/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping("/sync")
    public OrderResponse createSync(@RequestBody OrderRequest request) {
        return orderService.createSync(request);
    }

    @PostMapping("/async")
    public OrderResponse createAsync(@RequestBody OrderRequest request) {
        return orderService.createAsync(request);
    }
}
