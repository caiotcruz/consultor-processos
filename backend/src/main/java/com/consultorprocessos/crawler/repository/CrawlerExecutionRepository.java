package com.consultorprocessos.crawler.repository;

import com.consultorprocessos.crawler.entity.CrawlerExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CrawlerExecutionRepository extends JpaRepository<CrawlerExecution, UUID> {

    @Query("""
        SELECT ce FROM CrawlerExecution ce
        WHERE ce.court.id = :courtId
        ORDER BY ce.executedAt DESC
        LIMIT :limit
        """)
    List<CrawlerExecution> findLatestByCourt(@Param("courtId") UUID courtId,
                                              @Param("limit") int limit);
}