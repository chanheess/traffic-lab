package com.trafficlab.cache;

import com.trafficlab.post.PostService;
import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/demo/cache")
@RequiredArgsConstructor
public class CacheController {

    private final PostService postService;
    private final CacheMetrics cacheMetrics;

    @DeleteMapping("/popular")
    public void evict() {
        postService.evictPopularPostsCache();
        cacheMetrics.reset();
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return Map.of(
                "hits", cacheMetrics.getHits(),
                "misses", cacheMetrics.getMisses(),
                "hitRatio", cacheMetrics.hitRatio()
        );
    }
}
