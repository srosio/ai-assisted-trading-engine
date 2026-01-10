package com.trading.engine.repository;

import com.trading.engine.domain.JournalEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    Optional<JournalEntry> findBySignalId(String signalId);

    List<JournalEntry> findBySymbolOrderByCreatedAtDesc(String symbol);

    List<JournalEntry> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(j) FROM JournalEntry j WHERE j.createdAt >= :startOfDay")
    long countTradesToday(LocalDateTime startOfDay);

    @Query("SELECT j FROM JournalEntry j WHERE j.tradeTaken = true AND j.createdAt >= :startOfDay")
    List<JournalEntry> findTakenTradesToday(LocalDateTime startOfDay);

    @Query("SELECT j FROM JournalEntry j WHERE j.setupQuality = :quality ORDER BY j.createdAt DESC")
    List<JournalEntry> findBySetupQuality(String quality);
}
