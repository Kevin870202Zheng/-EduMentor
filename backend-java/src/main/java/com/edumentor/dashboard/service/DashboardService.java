package com.edumentor.dashboard.service;

import com.edumentor.dashboard.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    @PersistenceContext
    private EntityManager entityManager;

    public DashboardService() {
    }

    /**
     * 获取班级学情总览
     */
    @Transactional(readOnly = true)
    public ClassOverviewDto getClassOverview(String courseIdStr) {
        ClassOverviewDto dto = new ClassOverviewDto();
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime yesterdayStart = today.minusDays(1).atStartOfDay();
        LocalDateTime weekAgo = today.minusDays(7).atStartOfDay();

        // 总学生数
        Number totalStudents = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM users WHERE role = 'STUDENT' AND is_active = true"
        ).getSingleResult();
        dto.totalStudents(totalStudents.intValue());

        // 今日活跃学生数
        Number activeToday = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(DISTINCT ar.student_id) FROM answer_records ar WHERE ar.attempted_at >= :today"
        ).setParameter("today", todayStart).getSingleResult();
        dto.activeStudentsToday(activeToday.intValue());
        dto.activeRate(totalStudents.intValue() > 0 ? (double) activeToday.intValue() / totalStudents.intValue() * 100 : 0);

        // 全班平均正确率
        Number avgCorrect = (Number) entityManager.createNativeQuery(
            "SELECT COALESCE(SUM(CASE WHEN ar.is_correct = true THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 0) " +
            "FROM answer_records ar"
        ).getSingleResult();
        dto.averageCorrectRate(avgCorrect.doubleValue());

        // 今日正确率
        Number todayCorrect = (Number) entityManager.createNativeQuery(
            "SELECT COALESCE(SUM(CASE WHEN is_correct = true THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 0) " +
            "FROM answer_records WHERE attempted_at >= :today"
        ).setParameter("today", todayStart).getSingleResult();

        // 昨日正确率
        Number yesterdayCorrect = (Number) entityManager.createNativeQuery(
            "SELECT COALESCE(SUM(CASE WHEN is_correct = true THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 0) " +
            "FROM answer_records WHERE attempted_at >= :yesterday AND attempted_at < :today"
        ).setParameter("yesterday", yesterdayStart).setParameter("today", todayStart).getSingleResult();

        dto.correctRateChange(todayCorrect.doubleValue() - yesterdayCorrect.doubleValue());

        // 本周答题总数
        Number weekAnswers = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM answer_records WHERE attempted_at >= :weekAgo"
        ).setParameter("weekAgo", weekAgo).getSingleResult();
        dto.totalAnswersThisWeek(weekAnswers.longValue());

        // 今日答题数
        Number todayAnswers = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM answer_records WHERE attempted_at >= :today"
        ).setParameter("today", todayStart).getSingleResult();
        dto.answersToday(todayAnswers.longValue());

        // 总正确数
        Number totalCorrect = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM answer_records WHERE is_correct = true"
        ).getSingleResult();
        dto.totalCorrectAnswers(totalCorrect.longValue());

        // 预警统计
        Number pendingAlerts = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM alert_records WHERE is_resolved = false"
        ).getSingleResult();
        dto.pendingAlertCount(pendingAlerts.intValue());

        Number criticalAlerts = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM alert_records WHERE is_resolved = false AND severity IN ('HIGH', 'CRITICAL')"
        ).getSingleResult();
        dto.criticalAlertCount(criticalAlerts.intValue());

        // 薄弱知识点数量
        Number weakCount = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM (" +
            "SELECT ar.knowledge_point_id FROM answer_records ar " +
            "GROUP BY ar.knowledge_point_id " +
            "HAVING SUM(CASE WHEN ar.is_correct = true THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0) < 60" +
            ") sub"
        ).getSingleResult();
        dto.weakKnowledgeCount(weakCount.intValue());

        // 各知识点掌握度
        @SuppressWarnings("unchecked")
        List<Object[]> masteryRows = entityManager.createNativeQuery(
            "SELECT ar.knowledge_point_id, kp.name, " +
            "SUM(CASE WHEN ar.is_correct = true THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), " +
            "COUNT(*), kp.difficulty " +
            "FROM answer_records ar " +
            "JOIN knowledge_points kp ON kp.id = ar.knowledge_point_id " +
            "GROUP BY ar.knowledge_point_id, kp.name, kp.difficulty " +
            "ORDER BY 3 ASC"
        ).getResultList();

        List<ClassOverviewDto.KnowledgeMasterySummary> masteryList = new ArrayList<>();
        for (Object[] row : masteryRows) {
            ClassOverviewDto.KnowledgeMasterySummary summary = new ClassOverviewDto.KnowledgeMasterySummary();
            if (row[0] != null) summary.setKnowledgePointId(row[0].toString());
            if (row[1] != null) summary.setKnowledgePointName(row[1].toString());
            if (row[2] != null) summary.setMasteryRate(((Number) row[2]).doubleValue());
            if (row[3] != null) summary.setAnswerCount(((Number) row[3]).longValue());
            if (row[4] != null) summary.setDifficulty(((Number) row[4]).intValue());
            masteryList.add(summary);
        }
        dto.setKnowledgeMastery(masteryList);

        return dto;
    }

    /**
     * 获取学生列表（分页，带学情摘要）
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getStudentList(String courseIdStr, int page, int size, String sortBy, String sortDir) {
        int offset = (page - 1) * size;
        String orderClause = buildStudentSortClause(sortBy, sortDir);
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();

        // 总记录数
        Number total = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM users WHERE role = 'STUDENT' AND is_active = true"
        ).getSingleResult();

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(
            "SELECT u.id, u.display_name, u.username, u.avatar_url, u.last_login_at, " +
            "COALESCE(stats.total_ans, 0), COALESCE(stats.correct_ans, 0), " +
            "COALESCE(stats.today_ans, 0), COALESCE(stats.today_correct, 0), " +
            "COALESCE(stats.correct_rate, 0), " +
            "COALESCE(alert_cnt.unresolved, 0), " +
            "COALESCE(sess.duration_today, 0) " +
            "FROM users u " +
            "LEFT JOIN ( " +
            "  SELECT ar.student_id, COUNT(*) AS total_ans, " +
            "  SUM(CASE WHEN ar.is_correct THEN 1 ELSE 0 END) AS correct_ans, " +
            "  SUM(CASE WHEN ar.attempted_at >= :today AND ar.is_correct THEN 1 ELSE 0 END) AS today_correct, " +
            "  SUM(CASE WHEN ar.attempted_at >= :today THEN 1 ELSE 0 END) AS today_ans, " +
            "  SUM(CASE WHEN ar.is_correct THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0) AS correct_rate " +
            "  FROM answer_records ar GROUP BY ar.student_id " +
            ") stats ON stats.student_id = u.id " +
            "LEFT JOIN ( " +
            "  SELECT student_id, COUNT(*) AS unresolved FROM alert_records " +
            "  WHERE is_resolved = false GROUP BY student_id " +
            ") alert_cnt ON alert_cnt.student_id = u.id " +
            "LEFT JOIN ( " +
            "  SELECT student_id, COALESCE(SUM(duration_minutes), 0) AS duration_today " +
            "  FROM study_sessions WHERE start_time >= :today GROUP BY student_id " +
            ") sess ON sess.student_id = u.id " +
            "WHERE u.role = 'STUDENT' AND u.is_active = true " +
            "ORDER BY " + orderClause + " " +
            "LIMIT :size OFFSET :offset"
        )
        .setParameter("today", todayStart)
        .setParameter("size", size)
        .setParameter("offset", offset)
        .getResultList();

        List<StudentSummaryDto> items = new ArrayList<>();
        for (Object[] row : rows) {
            StudentSummaryDto s = new StudentSummaryDto();
            if (row[0] != null) s.studentId(row[0].toString());
            if (row[1] != null) s.displayName(row[1].toString());
            if (row[2] != null) s.username(row[2].toString());
            if (row[3] != null) s.avatarUrl(row[3].toString());
            if (row[4] != null) s.lastActiveAt(row[4].toString());
            if (row[5] != null) s.totalAnswers(((Number) row[5]).longValue());
            if (row[6] != null) s.correctAnswers(((Number) row[6]).longValue());
            if (row[7] != null) s.answersToday(((Number) row[7]).longValue());
            if (row[8] != null) s.correctToday(((Number) row[8]).longValue());
            if (row[9] != null) s.correctRate(((Number) row[9]).doubleValue());
            if (row[10] != null) s.pendingAlertCount(((Number) row[10]).intValue());
            if (row[11] != null) s.studyMinutesToday(((Number) row[11]).intValue());

            // 计算状态
            if (s.getPendingAlertCount() >= 2) {
                s.status("at-risk");
            } else if (s.getAnswersToday() > 0) {
                s.status("active");
            } else {
                s.status("inactive");
            }

            // 薄弱知识点
            @SuppressWarnings("unchecked")
            List<String> weakAreas = entityManager.createNativeQuery(
                "SELECT kp.name FROM answer_records ar " +
                "JOIN knowledge_points kp ON kp.id = ar.knowledge_point_id " +
                "WHERE ar.student_id = :sid " +
                "GROUP BY ar.knowledge_point_id, kp.name " +
                "HAVING SUM(CASE WHEN ar.is_correct THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0) < 60 " +
                "ORDER BY 3 ASC LIMIT 5"
            ).setParameter("sid", UUID.fromString(s.getStudentId())).getResultList();
            s.weakAreas(weakAreas);

            items.add(s);
        }

        int totalPages = (int) Math.ceil((double) total.intValue() / size);
        boolean hasMore = page < totalPages;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", total.intValue());
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", totalPages);
        result.put("hasMore", hasMore);
        return result;
    }

    private String buildStudentSortClause(String sortBy, String sortDir) {
        String dir = "desc".equalsIgnoreCase(sortDir) ? "DESC" : "ASC";
        return switch (sortBy != null ? sortBy : "correct_rate") {
            case "answers" -> "stats.total_ans " + dir;
            case "name" -> "u.display_name " + dir;
            case "lastActive" -> "u.last_login_at " + dir + " NULLS LAST";
            default -> "stats.correct_rate " + dir + " NULLS LAST";
        };
    }

    /**
     * 获取薄弱知识点列表
     */
    @Transactional(readOnly = true)
    public List<WeakKnowledgeDto> getWeakKnowledgePoints(String courseIdStr, double threshold, int minStudents) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT kp.id, kp.name, c.name AS course_name, kp.difficulty, kp.importance, ");
        sql.append("SUM(CASE WHEN ar.is_correct THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0) AS avg_rate, ");
        sql.append("COUNT(DISTINCT ar.student_id) AS affected_students, ");
        sql.append("COUNT(*) AS total_answers ");
        sql.append("FROM answer_records ar ");
        sql.append("JOIN knowledge_points kp ON kp.id = ar.knowledge_point_id ");
        sql.append("JOIN courses c ON c.id = kp.course_id ");
        sql.append("GROUP BY kp.id, kp.name, c.name, kp.difficulty, kp.importance ");
        sql.append("HAVING SUM(CASE WHEN ar.is_correct THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0) < :threshold ");
        sql.append("AND COUNT(DISTINCT ar.student_id) >= :minStudents ");
        sql.append("ORDER BY avg_rate ASC");

        @SuppressWarnings("unchecked")
        List<Object[]> rows = entityManager.createNativeQuery(sql.toString())
            .setParameter("threshold", threshold)
            .setParameter("minStudents", minStudents)
            .getResultList();

        List<WeakKnowledgeDto> result = new ArrayList<>();
        for (Object[] row : rows) {
            WeakKnowledgeDto dto = new WeakKnowledgeDto();
            if (row[0] != null) dto.knowledgePointId(row[0].toString());
            if (row[1] != null) dto.knowledgePointName(row[1].toString());
            if (row[2] != null) dto.courseName(row[2].toString());
            if (row[3] != null) dto.difficulty(((Number) row[3]).intValue());
            if (row[4] != null) dto.importance(((Number) row[4]).intValue());
            if (row[5] != null) dto.averageCorrectRate(((Number) row[5]).doubleValue());
            if (row[6] != null) dto.affectedStudentCount(((Number) row[6]).intValue());
            if (row[7] != null) dto.totalAnswerCount(((Number) row[7]).longValue());

            // 建议
            dto.suggestion(generateSuggestion(dto));

            result.add(dto);
        }
        return result;
    }

    private String generateSuggestion(WeakKnowledgeDto dto) {
        if (dto.getAverageCorrectRate() < 40) {
            return String.format(
                "「%s」正确率仅 %.1f%%，涉及 %d 名学生。建议安排专题讲解，并布置针对性练习。",
                dto.getKnowledgePointName(), dto.getAverageCorrectRate(), dto.getAffectedStudentCount());
        } else if (dto.getAverageCorrectRate() < 50) {
            return String.format(
                "「%s」正确率 %.1f%%，建议组织小组讨论或录制讲解视频供学生反复观看。",
                dto.getKnowledgePointName(), dto.getAverageCorrectRate());
        } else {
            return String.format(
                "「%s」正确率 %.1f%%，存在提升空间。建议在每日练习中增加该知识点题目数量。",
                dto.getKnowledgePointName(), dto.getAverageCorrectRate());
        }
    }

    /**
     * 获取每日简报
     */
    @Transactional(readOnly = true)
    public DailyBriefDto getDailyBrief(String courseIdStr) {
        DailyBriefDto dto = new DailyBriefDto();
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime yesterdayStart = today.minusDays(1).atStartOfDay();

        dto.date(today);
        dto.title(today.format(DateTimeFormatter.ofPattern("M月d日")) + " 教学简报");

        // 总学生数
        Number totalStudents = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM users WHERE role = 'STUDENT' AND is_active = true"
        ).getSingleResult();
        dto.totalStudents(totalStudents.intValue());

        // 今日活跃
        Number active = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(DISTINCT student_id) FROM answer_records WHERE attempted_at >= :today"
        ).setParameter("today", todayStart).getSingleResult();
        dto.activeStudents(active.intValue());

        // 今日答题
        Number newAnswers = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM answer_records WHERE attempted_at >= :today"
        ).setParameter("today", todayStart).getSingleResult();
        dto.newAnswers(newAnswers.longValue());

        // 今日正确率
        Number todayCorrect = (Number) entityManager.createNativeQuery(
            "SELECT COALESCE(SUM(CASE WHEN is_correct THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 0) " +
            "FROM answer_records WHERE attempted_at >= :today"
        ).setParameter("today", todayStart).getSingleResult();
        dto.todayCorrectRate(todayCorrect.doubleValue());

        // 昨日正确率
        Number yesterdayCorrect = (Number) entityManager.createNativeQuery(
            "SELECT COALESCE(SUM(CASE WHEN is_correct THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), 0) " +
            "FROM answer_records WHERE attempted_at >= :yesterday AND attempted_at < :today"
        ).setParameter("yesterday", yesterdayStart).setParameter("today", todayStart).getSingleResult();
        dto.correctRateChange(todayCorrect.doubleValue() - yesterdayCorrect.doubleValue());

        // 学习会话
        Number sessions = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM study_sessions WHERE start_time >= :today"
        ).setParameter("today", todayStart).getSingleResult();
        dto.newSessions(sessions.intValue());

        Number totalMinutes = (Number) entityManager.createNativeQuery(
            "SELECT COALESCE(SUM(duration_minutes), 0) FROM study_sessions WHERE start_time >= :today"
        ).setParameter("today", todayStart).getSingleResult();
        dto.totalStudyMinutes(totalMinutes.intValue());
        dto.averageStudyMinutes(active.intValue() > 0 ? (double) totalMinutes.intValue() / active.intValue() : 0);

        // 预警
        Number newAlerts = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM alert_records WHERE created_at >= :today"
        ).setParameter("today", todayStart).getSingleResult();
        dto.newAlerts(newAlerts.intValue());

        Number resolvedAlerts = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM alert_records WHERE resolved_at >= :today"
        ).setParameter("today", todayStart).getSingleResult();
        dto.resolvedAlerts(resolvedAlerts.intValue());

        // 错题
        Number newErrors = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM error_records WHERE created_at >= :today"
        ).setParameter("today", todayStart).getSingleResult();
        dto.newErrors(newErrors.intValue());

        // 复习
        Number reviewed = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM review_records WHERE reviewed_at >= :today"
        ).setParameter("today", todayStart).getSingleResult();
        dto.reviewedCount(reviewed.intValue());

        // 进步最快学生
        @SuppressWarnings("unchecked")
        List<Object[]> topStudent = entityManager.createNativeQuery(
            "SELECT u.display_name, " +
            "(SUM(CASE WHEN ar.is_correct THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0)) - " +
            "(CASE WHEN pre.pre_rate IS NULL THEN 0 ELSE pre.pre_rate END) AS improvement " +
            "FROM answer_records ar " +
            "JOIN users u ON u.id = ar.student_id " +
            "LEFT JOIN (" +
            "  SELECT student_id, SUM(CASE WHEN is_correct THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0) AS pre_rate " +
            "  FROM answer_records " +
            "  WHERE attempted_at >= :yesterday AND attempted_at < :today GROUP BY student_id" +
            ") pre ON pre.student_id = ar.student_id " +
            "WHERE ar.attempted_at >= :today " +
            "GROUP BY ar.student_id, u.display_name, pre.pre_rate " +
            "ORDER BY improvement DESC LIMIT 1"
        ).setParameter("today", todayStart).setParameter("yesterday", yesterdayStart).getResultList();

        if (!topStudent.isEmpty()) {
            Object[] row = topStudent.get(0);
            if (row[0] != null) dto.mostImprovedStudent(row[0].toString());
            if (row[1] != null) dto.mostImprovedRate(((Number) row[1]).doubleValue());
        }

        // 需要关注的学生
        Number attention = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(DISTINCT student_id) FROM alert_records " +
            "WHERE is_resolved = false AND severity IN ('HIGH', 'CRITICAL') " +
            "GROUP BY student_id HAVING COUNT(*) >= 2"
        ).getSingleResult();
        dto.studentsNeedingAttention(attention != null ? attention.intValue() : 0);

        // 摘要
        StringBuilder summary = new StringBuilder();
        summary.append("今日 ").append(active.intValue()).append(" 名学生参与学习，");
        summary.append("新增答题 ").append(newAnswers.longValue()).append(" 题，");
        summary.append("正确率 ").append(String.format("%.1f%%", todayCorrect.doubleValue())).append("。");
        if (newAlerts.intValue() > 0) {
            summary.append("新增 ").append(newAlerts.intValue()).append(" 条预警待处理。");
        }
        if (dto.getStudentsNeedingAttention() > 0) {
            summary.append("有 ").append(dto.getStudentsNeedingAttention()).append(" 名同学需要特别关注。");
        }
        dto.summary(summary.toString());

        return dto;
    }

    /**
     * 获取策略建议
     */
    @Transactional(readOnly = true)
    public List<StrategySuggestionDto> getStrategySuggestions(String courseIdStr, int limit) {
        List<StrategySuggestionDto> suggestions = new ArrayList<>();
        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        LocalDateTime weekAgo = today.minusDays(7).atStartOfDay();

        // 1. 薄弱知识点建议
        @SuppressWarnings("unchecked")
        List<Object[]> weakKps = entityManager.createNativeQuery(
            "SELECT kp.id, kp.name, c.name, " +
            "SUM(CASE WHEN ar.is_correct THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0), " +
            "COUNT(DISTINCT ar.student_id) " +
            "FROM answer_records ar JOIN knowledge_points kp ON kp.id = ar.knowledge_point_id " +
            "JOIN courses c ON c.id = kp.course_id " +
            "GROUP BY kp.id, kp.name, c.name " +
            "HAVING SUM(CASE WHEN ar.is_correct THEN 1 ELSE 0 END) * 100.0 / NULLIF(COUNT(*), 0) < 50 " +
            "ORDER BY 4 ASC LIMIT 3"
        ).getResultList();

        for (Object[] row : weakKps) {
            StrategySuggestionDto s = new StrategySuggestionDto();
            s.suggestionId(UUID.randomUUID().toString())
             .priority("HIGH")
             .category("mastery")
             .title(row[1] + " 掌握度偏低，建议集中复习")
             .description(String.format("全班在「%s」的正确率仅 %.1f%%，涉及 %d 名学生。该知识点整体掌握情况不理想。",
                 row[1], ((Number) row[3]).doubleValue(), ((Number) row[4]).intValue()))
             .rootCause("学生对概念理解不够深入，缺乏系统练习")
             .action(String.format("建议安排专题讲解「%s」，并布置针对性练习作业", row[1]))
             .expectedOutcome("预计可将该知识点正确率提升至 70% 以上")
             .relatedKnowledgePointId(row[0].toString())
             .relatedKnowledgePointName(row[1].toString())
             .affectedStudentCount(((Number) row[4]).intValue())
             .impactScore(Math.min((int) (100 - ((Number) row[3]).doubleValue()), 100));
            suggestions.add(s);
        }

        // 2. 活跃度建议
        Number activeWeek = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(DISTINCT student_id) FROM answer_records WHERE attempted_at >= :weekAgo"
        ).setParameter("weekAgo", weekAgo).getSingleResult();

        Number totalStudents = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM users WHERE role = 'STUDENT' AND is_active = true"
        ).getSingleResult();

        double activeRate = totalStudents.intValue() > 0
            ? (double) activeWeek.intValue() / totalStudents.intValue() * 100 : 0;

        if (activeRate < 60) {
            StrategySuggestionDto s = new StrategySuggestionDto();
            s.suggestionId(UUID.randomUUID().toString())
             .priority("MEDIUM")
             .category("engagement")
             .title("本周学生活跃率偏低，建议加强互动")
             .description(String.format("本周活跃率 %.1f%%，共 %d 名学生参与学习，建议关注未活跃学生的学习状态。", activeRate, activeWeek.intValue()))
             .rootCause("部分学生缺乏学习动力，未养成每日学习习惯")
             .action("建议组织学习小组或引入积分激励机制，提升学习参与度")
             .expectedOutcome("预计可将活跃率提升至 75% 以上")
             .affectedStudentCount(totalStudents.intValue() - activeWeek.intValue())
             .impactScore(65);
            suggestions.add(s);
        }

        // 3. 预警建议
        Number highAlerts = (Number) entityManager.createNativeQuery(
            "SELECT COUNT(*) FROM alert_records WHERE is_resolved = false AND severity IN ('HIGH', 'CRITICAL')"
        ).getSingleResult();

        if (highAlerts.intValue() > 0) {
            StrategySuggestionDto s = new StrategySuggestionDto();
            s.suggestionId(UUID.randomUUID().toString())
             .priority(highAlerts.intValue() >= 5 ? "HIGH" : "MEDIUM")
             .category("alert")
             .title("存在 " + highAlerts.intValue() + " 条高危预警待处理")
             .description(String.format("当前有 %d 条高危/严重预警未处理，涉及学生学习状态异常，建议及时介入。", highAlerts.intValue()))
             .rootCause("学生正确率持续偏低、长时间未学习或题量骤减")
             .action("建议查看预警详情，针对性地约谈学生或调整学习计划")
             .expectedOutcome("及时干预可降低学生掉队风险")
             .affectedStudentCount(highAlerts.intValue())
             .impactScore(Math.min(highAlerts.intValue() * 10, 100));
            suggestions.add(s);
        }

        // 按 impactScore 排序，取前 limit 条
        suggestions.sort((a, b) -> Integer.compare(b.getImpactScore(), a.getImpactScore()));
        return suggestions.size() > limit ? suggestions.subList(0, limit) : suggestions;
    }
}
