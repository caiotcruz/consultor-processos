package com.consultorprocessos.crawler.repository;

import com.consultorprocessos.crawler.entity.ParserVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ParserVersionRepository extends JpaRepository<ParserVersion, UUID> {

    Optional<ParserVersion> findByCourtCodeAndActiveTrue(String courtCode);
}