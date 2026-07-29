package com.edumentor.classroom.service;

import com.edumentor.classroom.entity.enums.ProgressStatus;
import com.edumentor.classroom.repository.ClassroomProgressRepository;
import com.edumentor.classroom.repository.ClassroomRepository;
import com.edumentor.classroom.repository.SceneQuizRecordRepository;
import com.edumentor.classroom.repository.SceneRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 课堂驾驶舱服务 — 课堂学情概览与分析。
 * <p>
 * 提供：班级课堂完成度聚合、场景级成绩分布、学生课堂行为分析
 * </p>
 */
@Service
public class ClassroomDashboardService {

    private static final Logger log = LoggerFactory.getLogger(ClassroomDashboardService.class);

    private final ClassroomRepository classroomRepository;
    private final ClassroomProgressRepository progressRepository;
    private final SceneRepository sceneRepository;
    private final SceneQuizRecordRepository quizRecordRepository;

    public ClassroomDashboardService(ClassroomRepository classroomRepository,
                                     ClassroomProgressRepository progressRepository,
                                     SceneRepository sceneRepository,
                                     SceneQuizRecordRepository quizRecordRepository) {
        this.classroomRepository = classroomRepository;
        this.progressRepository = progressRepository;
        this.sceneRepository = sceneRepository;
        this.quizRecordRepository = quizRecordRepository;
    }

    /**
     * 获取班级课堂完成度概览。
     *
     * @param classroomId 课堂ID
     * @return 概览数据
     */
    @Transactional(readOnly = true)
    public ClassroomOverview getClassroomOverview(UUID classroomId) {
        List<com.edumentor.classroom.entity.ClassroomProgress> allProgress =
                progressRepository.findByClassroomId(classroomId);

        int total = allProgress.size();
        long completed = allProgress.stream()
                .filter(p -> p.getStatus() == ProgressStatus.completed).count();
        long inProgress = allProgress.stream()
                .filter(p -> p.getStatus() == ProgressStatus.in_progress).count();
        long notStarted = allProgress.stream()
                .filter(p -> p.getStatus() == ProgressStatus.not_started).count();

        double avgQuizCorrectRate = allProgress.stream()
                .filter(p -> p.getQuizTotalCount() > 0)
                .mapToDouble(p -> (double) p.getQuizCorrectCount() / p.getQuizTotalCount())
                .average().orElse(0.0);

        double avgCompletionRate = allProgress.stream()
                .filter(p -> p.getTotalScenes() > 0)
                .mapToDouble(p -> (double) p.getScenesCompleted() / p.getTotalScenes())
                .average().orElse(0.0);

        // 平均观看时长（秒）
        double avgWatchDuration = allProgress.stream()
                .filter(p -> p.getTotalWatchSeconds() > 0)
                .mapToInt(com.edumentor.classroom.entity.ClassroomProgress::getTotalWatchSeconds)
                .average().orElse(0.0);

        ClassroomOverview overview = new ClassroomOverview();
        overview.setTotalStudents(total);
        overview.setCompletedCount((int) completed);
        overview.setInProgressCount((int) inProgress);
        overview.setNotStartedCount((int) notStarted);
        overview.setCompletionRate(Math.round(avgCompletionRate * 10000) / 100.0);
        overview.setAvgQuizCorrectRate(Math.round(avgQuizCorrectRate * 10000) / 100.0);
        overview.setAvgWatchDurationSeconds((int) avgWatchDuration);

        return overview;
    }

    /**
     * 获取学生在课堂中的场景级详情。
     *
     * @param studentId   学生ID
     * @param classroomId 课堂ID
     * @return 场景级详情
     */
    @Transactional(readOnly = true)
    public StudentClassroomDetail getStudentDetail(UUID studentId, UUID classroomId) {
        var progress = progressRepository.findByStudentIdAndClassroomId(studentId, classroomId).orElse(null);
        if (progress == null) return null;

        var scenes = sceneRepository.findByClassroomIdOrderByOrderIndexAsc(classroomId);

        StudentClassroomDetail detail = new StudentClassroomDetail();
        detail.setStudentId(studentId.toString());
        detail.setClassroomId(classroomId.toString());
        detail.setStatus(progress.getStatus().name());
        detail.setScenesCompleted(progress.getScenesCompleted());
        detail.setTotalScenes(progress.getTotalScenes());
        detail.setQuizCorrectCount(progress.getQuizCorrectCount());
        detail.setQuizTotalCount(progress.getQuizTotalCount());
        detail.setTotalWatchSeconds(progress.getTotalWatchSeconds());

        // 场景级别数据
        List<SceneDetail> sceneDetails = new ArrayList<>();
        for (var scene : scenes) {
            var quizRecords = quizRecordRepository
                    .findByStudentIdAndSceneIdOrderByCreatedAtAsc(studentId, scene.getId());

            SceneDetail sd = new SceneDetail();
            sd.setSceneId(scene.getId().toString());
            sd.setSceneTitle(scene.getTitle());
            sd.setSceneType(scene.getSceneType().name());
            sd.setOrderIndex(scene.getOrderIndex());
            sd.setQuizAttempts(quizRecords.size());
            sd.setQuizCorrectCount((int) quizRecords.stream()
                    .filter(r -> Boolean.TRUE.equals(r.getIsCorrect())).count());
            sceneDetails.add(sd);
        }
        detail.setSceneDetails(sceneDetails);

        return detail;
    }

    /**
     * 获取课堂知识点掌握度分布（用于教师驾驶舱）。
     *
     * @param classroomId 课堂ID
     * @return 知识点掌握度数据
     */
    @Transactional(readOnly = true)
    public List<KnowledgePointMastery> getKnowledgePointMastery(UUID classroomId) {
        var allProgress = progressRepository.findByClassroomId(classroomId);
        var scenes = sceneRepository.findByClassroomIdOrderByOrderIndexAsc(classroomId);
        List<UUID> sceneIds = scenes.stream()
                .map(com.edumentor.classroom.entity.Scene::getId)
                .collect(Collectors.toList());

        // 按知识点ID聚合Quiz正确率
        Map<UUID, List<Boolean>> kpResults = new HashMap<>();
        for (var progress : allProgress) {
            var records = quizRecordRepository
                    .findByStudentIdAndSceneIdIn(progress.getStudentId(), sceneIds);
            for (var record : records) {
                if (record.getKnowledgePointId() != null) {
                    kpResults.computeIfAbsent(record.getKnowledgePointId(), k -> new ArrayList<>())
                            .add(record.getIsCorrect());
                }
            }
        }

        List<KnowledgePointMastery> result = new ArrayList<>();
        for (var entry : kpResults.entrySet()) {
            List<Boolean> results = entry.getValue();
            long correctCount = results.stream().filter(r -> r).count();
            double rate = (double) correctCount / results.size();

            KnowledgePointMastery km = new KnowledgePointMastery();
            km.setKnowledgePointId(entry.getKey().toString());
            km.setCorrectRate(Math.round(rate * 10000) / 100.0);
            km.setTotalQuestions(results.size());
            result.add(km);
        }

        return result;
    }

    // ═══════════════════════════════════════════════════════════════
    //  内部DTO类
    // ═══════════════════════════════════════════════════════════════

    public static class ClassroomOverview {
        private int totalStudents;
        private int completedCount;
        private int inProgressCount;
        private int notStartedCount;
        private double completionRate;
        private double avgQuizCorrectRate;
        private int avgWatchDurationSeconds;

        public int getTotalStudents() { return totalStudents; }
        public void setTotalStudents(int totalStudents) { this.totalStudents = totalStudents; }
        public int getCompletedCount() { return completedCount; }
        public void setCompletedCount(int completedCount) { this.completedCount = completedCount; }
        public int getInProgressCount() { return inProgressCount; }
        public void setInProgressCount(int inProgressCount) { this.inProgressCount = inProgressCount; }
        public int getNotStartedCount() { return notStartedCount; }
        public void setNotStartedCount(int notStartedCount) { this.notStartedCount = notStartedCount; }
        public double getCompletionRate() { return completionRate; }
        public void setCompletionRate(double completionRate) { this.completionRate = completionRate; }
        public double getAvgQuizCorrectRate() { return avgQuizCorrectRate; }
        public void setAvgQuizCorrectRate(double avgQuizCorrectRate) { this.avgQuizCorrectRate = avgQuizCorrectRate; }
        public int getAvgWatchDurationSeconds() { return avgWatchDurationSeconds; }
        public void setAvgWatchDurationSeconds(int avgWatchDurationSeconds) { this.avgWatchDurationSeconds = avgWatchDurationSeconds; }
    }

    public static class StudentClassroomDetail {
        private String studentId;
        private String classroomId;
        private String status;
        private int scenesCompleted;
        private int totalScenes;
        private int quizCorrectCount;
        private int quizTotalCount;
        private int totalWatchSeconds;
        private List<SceneDetail> sceneDetails;

        public String getStudentId() { return studentId; }
        public void setStudentId(String studentId) { this.studentId = studentId; }
        public String getClassroomId() { return classroomId; }
        public void setClassroomId(String classroomId) { this.classroomId = classroomId; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public int getScenesCompleted() { return scenesCompleted; }
        public void setScenesCompleted(int scenesCompleted) { this.scenesCompleted = scenesCompleted; }
        public int getTotalScenes() { return totalScenes; }
        public void setTotalScenes(int totalScenes) { this.totalScenes = totalScenes; }
        public int getQuizCorrectCount() { return quizCorrectCount; }
        public void setQuizCorrectCount(int quizCorrectCount) { this.quizCorrectCount = quizCorrectCount; }
        public int getQuizTotalCount() { return quizTotalCount; }
        public void setQuizTotalCount(int quizTotalCount) { this.quizTotalCount = quizTotalCount; }
        public int getTotalWatchSeconds() { return totalWatchSeconds; }
        public void setTotalWatchSeconds(int totalWatchSeconds) { this.totalWatchSeconds = totalWatchSeconds; }
        public List<SceneDetail> getSceneDetails() { return sceneDetails; }
        public void setSceneDetails(List<SceneDetail> sceneDetails) { this.sceneDetails = sceneDetails; }
    }

    public static class SceneDetail {
        private String sceneId;
        private String sceneTitle;
        private String sceneType;
        private int orderIndex;
        private int quizAttempts;
        private int quizCorrectCount;

        public String getSceneId() { return sceneId; }
        public void setSceneId(String sceneId) { this.sceneId = sceneId; }
        public String getSceneTitle() { return sceneTitle; }
        public void setSceneTitle(String sceneTitle) { this.sceneTitle = sceneTitle; }
        public String getSceneType() { return sceneType; }
        public void setSceneType(String sceneType) { this.sceneType = sceneType; }
        public int getOrderIndex() { return orderIndex; }
        public void setOrderIndex(int orderIndex) { this.orderIndex = orderIndex; }
        public int getQuizAttempts() { return quizAttempts; }
        public void setQuizAttempts(int quizAttempts) { this.quizAttempts = quizAttempts; }
        public int getQuizCorrectCount() { return quizCorrectCount; }
        public void setQuizCorrectCount(int quizCorrectCount) { this.quizCorrectCount = quizCorrectCount; }
    }

    public static class KnowledgePointMastery {
        private String knowledgePointId;
        private double correctRate;
        private int totalQuestions;

        public String getKnowledgePointId() { return knowledgePointId; }
        public void setKnowledgePointId(String knowledgePointId) { this.knowledgePointId = knowledgePointId; }
        public double getCorrectRate() { return correctRate; }
        public void setCorrectRate(double correctRate) { this.correctRate = correctRate; }
        public int getTotalQuestions() { return totalQuestions; }
        public void setTotalQuestions(int totalQuestions) { this.totalQuestions = totalQuestions; }
    }
}
