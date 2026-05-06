package com.trafficlab.order;

import org.springframework.stereotype.Service;

@Service
public class RewardService {

    public void calculate(Order order) {
        try {
            // 적립금 계산 시뮬레이션 (100ms)
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
