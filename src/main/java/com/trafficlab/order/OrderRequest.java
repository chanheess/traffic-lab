package com.trafficlab.order;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderRequest {
    private String productName;
    private int amount;
}
