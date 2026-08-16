package com.edumentor.student.service;

import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.student.dto.StudentProfileUpdateRequest;
import com.edumentor.student.entity.StudentProfile;
import com.edumentor.diagnosis.repository.StudentProfileRepository;
import com.edumentor.timemachine.service.TimeMachineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class StudentProfileService {

    private static final Logger log = LoggerFactory.getLogger(StudentProfileService.class);

    private final StudentProfileRepository studentProfileRepository;
    private final TimeMachineService timeMachineService;

    public StudentProfileService(StudentProfileRepository studentProfileRepository,
                                 TimeMachineService timeMachineService) {
        this.studentProfileRepository = studentProfileRepository;
        this.timeMachineService = timeMachineService;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProfile(UUID userId) {
        StudentProfile profile = studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("学生画像", userId));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", profile.getUserId());
        result.put("stage", profile.getStage());
        result.put("grade", profile.getGrade());
        result.put("className", profile.getClassName());
        result.put("major", profile.getMajor());
        result.put("department", profile.getDepartment());
        result.put("college", profile.getCollege());
        result.put("learningStyle", profile.getLearningStyle());
        result.put("dailyStudyMinutes", profile.getDailyStudyMinutes());
        return result;
    }

    @Transactional
    public Map<String, Object> updateProfile(UUID userId, StudentProfileUpdateRequest request) {
        StudentProfile profile = studentProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("学生画像", userId));

        // 学段变更 → 自动归档上一学段成长档案（成长时光机）
        if (request.getStage() != null && !request.getStage().equals(profile.getStage())) {
            String oldStage = profile.getStage();
            profile.setStage(request.getStage());
            if (oldStage != null && !oldStage.isBlank()) {
                timeMachineService.archiveOnPromotion(userId, oldStage, request.getStage());
            }
        } else if (request.getStage() != null) {
            profile.setStage(request.getStage());
        }
        if (request.getGrade() != null) profile.setGrade(request.getGrade());
        if (request.getClassName() != null) profile.setClassName(request.getClassName());
        if (request.getMajor() != null) profile.setMajor(request.getMajor());
        if (request.getDepartment() != null) profile.setDepartment(request.getDepartment());
        if (request.getCollege() != null) profile.setCollege(request.getCollege());
        if (request.getLearningStyle() != null) profile.setLearningStyle(request.getLearningStyle());
        if (request.getDailyStudyMinutes() != null) profile.setDailyStudyMinutes(request.getDailyStudyMinutes());

        studentProfileRepository.save(profile);
        log.info("学生画像更新: userId={}", userId);
        return getProfile(userId);
    }
}
