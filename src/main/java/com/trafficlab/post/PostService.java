package com.trafficlab.post;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

        List<Post> posts = new ArrayList<>(BATCH_SIZE);
        for (int i = 0; i < SEED_COUNT; i++) {
            // UUID prefix로 각 제목이 고유 → 키워드 검색 시 소수 행만 히트
            posts.add(Post.builder()
                    .title(UUID.randomUUID() + " 게시글 내용")
                    .content("내용 " + i)
                    .build());

            if (posts.size() >= BATCH_SIZE) {
                saveAndClear(posts);
            }
        }

        if (!posts.isEmpty()) {
            saveAndClear(posts);
        }
    }

    private void saveAndClear(List<Post> posts) {
        postRepository.saveAll(posts);
        posts.clear();
    }
}
