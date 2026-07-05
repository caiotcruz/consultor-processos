package com.consultorprocessos.court.repository;

import com.consultorprocessos.court.entity.Court;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourtRepository extends JpaRepository<Court, UUID> {

    Optional<Court> findByCode(String code);

    List<Court> findAllByActiveTrueOrderByNameAsc();

    boolean existsByCode(String code);
}