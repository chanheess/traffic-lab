package com.trafficlab.order;

import java.util.Random;

import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private final Random random = new Random();

    public void send(Order order) {
        try {
            // 외부 알림 API 호출 시뮬레이션 (300~500ms)
            Thread.sleep(300 + random.nextInt(200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
