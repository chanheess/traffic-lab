package com.trafficlab.pool;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/demo")
@RequiredArgsConstructor
public class PoolController {

    private final HikariDataSource dataSource;
    private final JdbcTemplate jdbcTemplate;

    @GetMapping("/heavy-query")
    public Map<String, Object> heavyQuery() {
        long startedAt = System.currentTimeMillis();
        jdbcTemplate.queryForObject("SELECT SLEEP(0.1)", Integer.class);
        return Map.of(
                "message", "slow query completed",
                "elapsedMs", System.currentTimeMillis() - startedAt
        );
    }

    @PostMapping("/pool/resize")
    public PoolStats resize(@RequestParam int size) {
        if (size < 1) {
            throw new IllegalArgumentException("Pool size must be greater than 0");
        }
        dataSource.setMinimumIdle(Math.min(dataSource.getMinimumIdle(), size));
        dataSource.setMaximumPoolSize(size);
        dataSource.setMinimumIdle(size);
        return stats();
    }

    @GetMapping("/pool/stats")
    public PoolStats stats() {
        HikariPoolMXBean bean = dataSource.getHikariPoolMXBean();
        return new PoolStats(
                bean.getActiveConnections(),
                bean.getIdleConnections(),
                bean.getThreadsAwaitingConnection(),
                bean.getTotalConnections(),
                dataSource.getMaximumPoolSize()
        );
    }
}
