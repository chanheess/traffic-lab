package com.trafficlab.post;

import com.trafficlab.cache.CacheMetrics;
import java.util.List;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/demo/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostRepository postRepository;
    private final PostService postService;
    private final CacheManager cacheManager;
    private final CacheMetrics cacheMetrics;

    @GetMapping("/search")
    public List<Post> search(@RequestParam String keyword) {
        return postRepository.findByTitleStartingWith(keyword);
    }

    @GetMapping("/popular")
    public List<Post> getPopularPosts() {
        return postService.getPopularPosts();
    }

    @GetMapping("/popular-cached")
    public List<Post> getPopularPostsCached() {
        Cache cache = cacheManager.getCache("popularPosts");
        if (cache != null && cache.get("top10") != null) {
            cacheMetrics.hit();
        } else {
            cacheMetrics.miss();
        }
        return postService.getPopularPostsCached();
    }
}
