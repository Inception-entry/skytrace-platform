package com.skytrace.backend.evidence.repository;

import com.skytrace.backend.evidence.domain.EvidenceArchiveStatus;
import com.skytrace.backend.evidence.domain.EvidenceAsset;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EvidenceAssetRepository
        extends JpaRepository<EvidenceAsset, Long>,
                JpaSpecificationExecutor<EvidenceAsset> {

    Optional<EvidenceAsset> findByEvidenceCode(String evidenceCode);

    Optional<EvidenceAsset> findByObjectKey(String objectKey);

    List<EvidenceAsset> findByTaskCodeAndDeletedFalseOrderByCreatedAtDesc(
            String taskCode
    );

    List<EvidenceAsset> findByAlarmEventCodeAndDeletedFalseOrderByCreatedAtDesc(
            String alarmEventCode
    );

    List<EvidenceAsset> findByTaskCodeAndDeletedFalseOrderByCreatedAtAsc(
            String taskCode
    );

    List<EvidenceAsset> findByAlarmEventCodeAndDeletedFalseOrderByCreatedAtAsc(
            String alarmEventCode
    );

    @Query("""
            SELECT asset
            FROM EvidenceAsset asset
            WHERE (asset.contentHash IS NULL OR TRIM(asset.contentHash) = '')
              AND asset.archiveStatus <> :purgedStatus
              AND (
                    asset.hashBackfillAttemptedAt IS NULL
                    OR asset.hashBackfillAttemptedAt <= :retryBefore
              )
            ORDER BY
              CASE WHEN asset.hashBackfillAttemptedAt IS NULL THEN 0 ELSE 1 END,
              asset.hashBackfillAttemptedAt ASC,
              asset.createdAt ASC,
              asset.id ASC
            """)
    List<EvidenceAsset> findHashBackfillCandidates(
            @Param("purgedStatus") EvidenceArchiveStatus purgedStatus,
            @Param("retryBefore") LocalDateTime retryBefore,
            Pageable pageable
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE EvidenceAsset asset
            SET asset.hashBackfillAttemptedAt = :claimedAt,
                asset.hashBackfillError = NULL
            WHERE asset.id = :assetId
              AND (asset.contentHash IS NULL OR TRIM(asset.contentHash) = '')
              AND asset.archiveStatus <> :purgedStatus
              AND (
                    asset.hashBackfillAttemptedAt IS NULL
                    OR asset.hashBackfillAttemptedAt <= :retryBefore
              )
            """)
    int claimHashBackfill(
            @Param("assetId") Long assetId,
            @Param("purgedStatus") EvidenceArchiveStatus purgedStatus,
            @Param("retryBefore") LocalDateTime retryBefore,
            @Param("claimedAt") LocalDateTime claimedAt
    );

    @Query("""
            SELECT asset
            FROM EvidenceAsset asset
            WHERE asset.deleted = true
              AND asset.archiveStatus = :archivedStatus
              AND asset.archivedAt <= :cutoff
              AND asset.deletedAt <= :cutoff
              AND asset.archiveBatchCode IS NOT NULL
              AND asset.contentHash IS NOT NULL
              AND TRIM(asset.contentHash) <> ''
            ORDER BY asset.archivedAt ASC, asset.id ASC
            """)
    List<EvidenceAsset> findPurgeCandidates(
            @Param("archivedStatus") EvidenceArchiveStatus archivedStatus,
            @Param("cutoff") LocalDateTime cutoff,
            Pageable pageable
    );

    @Query("""
            SELECT COUNT(asset)
            FROM EvidenceAsset asset
            WHERE asset.deleted = true
              AND asset.archiveStatus = :archivedStatus
              AND asset.archivedAt <= :cutoff
              AND asset.deletedAt <= :cutoff
              AND asset.archiveBatchCode IS NOT NULL
              AND asset.contentHash IS NOT NULL
              AND TRIM(asset.contentHash) <> ''
            """)
    long countPurgeCandidates(
            @Param("archivedStatus") EvidenceArchiveStatus archivedStatus,
            @Param("cutoff") LocalDateTime cutoff
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE EvidenceAsset asset
            SET asset.archiveStatus = :purgingStatus,
                asset.purgeStartedAt = :claimedAt,
                asset.purgeError = NULL
            WHERE asset.id = :assetId
              AND asset.archiveStatus = :archivedStatus
              AND asset.deleted = true
              AND asset.archivedAt <= :cutoff
              AND asset.deletedAt <= :cutoff
              AND asset.archiveBatchCode IS NOT NULL
              AND asset.contentHash IS NOT NULL
              AND TRIM(asset.contentHash) <> ''
            """)
    int claimPurge(
            @Param("assetId") Long assetId,
            @Param("archivedStatus") EvidenceArchiveStatus archivedStatus,
            @Param("purgingStatus") EvidenceArchiveStatus purgingStatus,
            @Param("cutoff") LocalDateTime cutoff,
            @Param("claimedAt") LocalDateTime claimedAt
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("""
            UPDATE EvidenceAsset asset
            SET asset.archiveStatus = :archivedStatus,
                asset.purgeStartedAt = NULL,
                asset.purgeError = :reason
            WHERE asset.archiveStatus = :purgingStatus
              AND asset.purgeStartedAt <= :staleBefore
            """)
    int releaseStalePurgeClaims(
            @Param("purgingStatus") EvidenceArchiveStatus purgingStatus,
            @Param("archivedStatus") EvidenceArchiveStatus archivedStatus,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("reason") String reason
    );

    List<EvidenceAsset>
            findByTaskCodeAndAlarmEventCodeAndDeletedFalseOrderByCreatedAtDesc(
                    String taskCode,
                    String alarmEventCode
            );
}
