package com.edumentor.classroom.repository;

import com.edumentor.classroom.entity.CollabProjectTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CollabProjectTaskRepository extends JpaRepository<CollabProjectTask, UUID> {

    List<CollabProjectTask> findByProjectIdOrderByRoleTypeAsc(UUID projectId);

    List<CollabProjectTask> findByAssignedUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<CollabProjectTask> findByProjectIdAndRoleType(UUID projectId, com.edumentor.classroom.entity.enums.CollabRoleType roleType);
}
