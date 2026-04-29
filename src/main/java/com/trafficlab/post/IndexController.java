package com.trafficlab.post;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/demo/index")
@RequiredArgsConstructor
public class IndexController {

    private final JdbcTemplate jdbcTemplate;

    @PostMapping("/toggle")
    public void toggleIndex(@RequestParam boolean enabled) {
        if (enabled) {
            try {
                jdbcTemplate.execute("CREATE INDEX idx_post_title ON posts(title)");
            } catch (DataAccessException ignored) {
                // 이미 존재하는 경우 무시
            }
        } else {
            try {
                jdbcTemplate.execute("DROP INDEX idx_post_title ON posts");
            } catch (DataAccessException ignored) {
                // 존재하지 않는 경우 무시
            }
        }
    }
}