package com.edumentor.classroom.repository;

import com.edumentor.classroom.entity.CollabClassroomProject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CollabClassroomProjectRepository extends JpaRepository<CollabClassroomProject, UUID> {

    List<CollabClassroomProject> findByCreatorIdOrderByCreatedAtDesc(UUID creatorId);

    List<CollabClassroomProject> findByStatusOrderByCreatedAtDesc(String status);
}
