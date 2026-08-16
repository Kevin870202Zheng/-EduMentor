package com.edumentor.timemachine.repository;

import com.edumentor.timemachine.entity.TimeMachineLetter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 时光机信件 Repository。
 */
@Repository
public interface TimeMachineLetterRepository extends JpaRepository<TimeMachineLetter, UUID> {

    List<TimeMachineLetter> findByStudentIdOrderByCreatedAtDesc(UUID studentId);
}
