package com.edumentor.timemachine.repository;

import com.edumentor.timemachine.entity.GrowthArchiveSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 成长档案快照 Repository。
 */
@Repository
public interface GrowthArchiveSnapshotRepository extends JpaRepository<GrowthArchiveSnapshot, UUID> {

    List<GrowthArchiveSnapshot> findByStudentIdOrderByCreatedAtAsc(UUID studentId);
}
