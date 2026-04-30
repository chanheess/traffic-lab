package com.trafficlab.post;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PostService {

    private static final int SEED_COUNT = 100_000;
    private static final int BATCH_SIZE = 1_000;

    private final PostRepository postRepository;

    @PostConstruct
    public void seedData() {
        if (postRepository.count() > 0) {
            return;
        }

        Random random = new Random();
        List<Post> posts = new ArrayList<>(BATCH_SIZE);
        for (int i = 0; i < SEED_COUNT; i++) {
            posts.add(Post.builder()
                    .title(UUID.randomUUID() + " 게시글 내용")
                    .content("내용 " + i)
                    .viewCount(random.nextLong(1_000_000))
                    .build());

            if (posts.size() >= BATCH_SIZE) {
                saveAndClear(posts);
            }
        }

        if (!posts.isEmpty()) {
            saveAndClear(posts);
        }
    }

    public List<Post> getPopularPosts() {
        return postRepository.findTop10ByOrderByViewCountDesc();
    }

    @Cacheable(value = "popularPosts", key = "'top10'")
    public List<Post> getPopularPostsCached() {
        return postRepository.findTop10ByOrderByViewCountDesc();
    }

    @CacheEvict(value = "popularPosts", allEntries = true)
    public void evictPopularPostsCache() {
    }

    private void saveAndClear(List<Post> posts) {
        postRepository.saveAll(posts);
        posts.clear();
    }
}
