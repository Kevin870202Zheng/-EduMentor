package com.edumentor.diagnosis.repository;

import com.edumentor.student.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {

    Optional<StudentProfile> findByUserId(UUID userId);

    /** 按学段查询学生档案（学段协作课堂邀请用） */
    List<StudentProfile> findByStageOrderByUpdatedAtDesc(String stage);
}
