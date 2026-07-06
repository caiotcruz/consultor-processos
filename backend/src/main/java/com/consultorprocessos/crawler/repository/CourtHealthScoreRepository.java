package com.consultorprocessos.crawler.repository;

import com.consultorprocessos.crawler.entity.CourtHealthScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourtHealthScoreRepository extends JpaRepository<CourtHealthScore, UUID> {}