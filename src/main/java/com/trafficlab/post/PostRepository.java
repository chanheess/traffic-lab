package com.trafficlab.post;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<Post, Long> {

    List<Post> findByTitleStartingWith(String keyword);

    List<Post> findTop10ByOrderByViewCountDesc();
}
