package com.edumentor.diagnosis.service;

import com.edumentor.diagnosis.dto.*;
import com.edumentor.diagnosis.repository.AnswerRecordRepository;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.diagnosis.repository.StudentProfileRepository;
import com.edumentor.diagnosis.repository.StudySessionRepository;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.record.entity.AnswerRecord;
import com.edumentor.entity.enums.UserRole;
import com.edumentor.user.entity.User;
import com.edumentor.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link DiagnosisService} 的单元测试。
 * <p>
 * 测试覆盖：诊断分析、认知画像、雷达图、热力图四大核心功能，
 * 以及空数据、异常学生等边界路径。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DiagnosisService — 学情诊断服务单元测试")
class DiagnosisServiceTest {

    @Mock
    private AnswerRecordRepository answerRecordRepository;

    @Mock
    private KnowledgePointRepository knowledgePointRepository;

    @Mock
    private StudySessionRepository studySessionRepository;

    @Mock
    private StudentProfileRepository studentProfileRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private DiagnosisService diagnosisService;

    private UUID studentId;
    private UUID kp1Id;
    private UUID kp2Id;
    private UUID kp3Id;
    private UUID courseId;
    private User mockStudent;
    private KnowledgePoint mockKp1;
    private KnowledgePoint mockKp2;
    private KnowledgePoint mockKp3;

    @BeforeEach
    void setUp() {
        studentId = UUID.randomUUID();
        kp1Id = UUID.randomUUID();
        kp2Id = UUID.randomUUID();
        kp3Id = UUID.randomUUID();
        courseId = UUID.randomUUID();

        mockStudent = new User();
        mockStudent.setId(studentId);
        mockStudent.setUsername("student01");
        mockStudent.setDisplayName("张三");
        mockStudent.setRole(UserRole.STUDENT);
        mockStudent.setIsActive(true);

        mockKp1 = new KnowledgePoint();
        mockKp1.setId(kp1Id);
        mockKp1.setName("一元一次方程");
        mockKp1.setCourseId(courseId);
        mockKp1.setDifficulty(2);
        mockKp1.setImportance(3);
        mockKp1.setTags("[\"基础\"]");

        mockKp2 = new KnowledgePoint();
        mockKp2.setId(kp2Id);
        mockKp2.setName("二元一次方程组");
        mockKp2.setCourseId(courseId);
        mockKp2.setDifficulty(3);
        mockKp2.setImportance(4);
        mockKp2.setTags("[\"核心\"]");

        mockKp3 = new KnowledgePoint();
        mockKp3.setId(kp3Id);
        mockKp3.setName("函数");
        mockKp3.setCourseId(courseId);
        mockKp3.setDifficulty(4);
        mockKp3.setImportance(5);
        mockKp3.setTags("[\"进阶\"]");
    }

    // ══════════════════════════════════════════════════════════════
    //  诊断分析
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("diagnose() — 诊断分析")
    class DiagnoseTests {

        @Test
        @DisplayName("有作答记录时 — 应返回完整诊断分析结果")
        void diagnoseWithRecords() {
            when(userRepository.findById(studentId)).thenReturn(Optional.of(mockStudent));

            List<AnswerRecord> records = createMockRecords();
            when(answerRecordRepository.findByStudentIdAndCourseIdAndAttemptedAtBetween(
                    eq(studentId), eq(courseId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(records);

            List<Object[]> kpAggregations = createMockKpAggregations();
            when(answerRecordRepository.aggregateByKnowledgePointAndCourse(
                    eq(studentId), eq(courseId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(kpAggregations);

            when(knowledgePointRepository.countByCourseId(courseId)).thenReturn(5L);

            DiagnosisResponse result = diagnosisService.diagnose(studentId, courseId, 30);

            assertThat(result).isNotNull();
            assertThat(result.getStudentId()).isEqualTo(studentId);
            assertThat(result.getStudentName()).isEqualTo("张三");
            assertThat(result.getTotalQuestions()).isEqualTo(4);
            assertThat(result.getCorrectCount()).isEqualTo(3);
            assertThat(result.getWeakKpCount()).isGreaterThanOrEqualTo(0);
            assertThat(result.getDiagnosisSummary()).isNotBlank();
            assertThat(result.getRecommendations()).isNotEmpty();
            assertThat(result.getRecentTrend()).hasSizeLessThanOrEqualTo(7);
        }

        @Test
        @DisplayName("无作答记录时 — 应返回空诊断结果")
        void diagnoseWithNoRecords() {
            when(userRepository.findById(studentId)).thenReturn(Optional.of(mockStudent));
            when(answerRecordRepository.findByStudentIdAndCourseIdAndAttemptedAtBetween(
                    eq(studentId), eq(courseId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of());

            DiagnosisResponse result = diagnosisService.diagnose(studentId, courseId, 30);

            assertThat(result).isNotNull();
            assertThat(result.getTotalQuestions()).isZero();
            assertThat(result.getCorrectCount()).isZero();
            assertThat(result.getTopWeakKps()).isEmpty();
            assertThat(result.getRecommendations()).contains("开始作答题目以获得学情诊断分析");
        }

        @Test
        @DisplayName("学生不存在 — 应抛出 IllegalArgumentException")
        void diagnoseWithNonExistentStudent() {
            when(userRepository.findById(studentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> diagnosisService.diagnose(studentId, courseId, 30))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("学生不存在");
        }

        @Test
        @DisplayName("所有作答正确 — 应识别为优势知识点")
        void diagnoseAllCorrect() {
            when(userRepository.findById(studentId)).thenReturn(Optional.of(mockStudent));

            List<AnswerRecord> records = List.of(
                    createAnswerRecord(kp1Id, true, 30),
                    createAnswerRecord(kp1Id, true, 25),
                    createAnswerRecord(kp2Id, true, 40)
            );
            when(answerRecordRepository.findByStudentIdAndCourseIdAndAttemptedAtBetween(
                    eq(studentId), eq(courseId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(records);

            List<Object[]> kpAggs = List.of(
                    new Object[]{kp1Id, 2L, 2L, 55L},
                    new Object[]{kp2Id, 1L, 1L, 40L}
            );
            when(answerRecordRepository.aggregateByKnowledgePointAndCourse(
                    eq(studentId), eq(courseId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(kpAggs);
            when(knowledgePointRepository.countByCourseId(courseId)).thenReturn(10L);

            DiagnosisResponse result = diagnosisService.diagnose(studentId, courseId, 30);

            assertThat(result.getAccuracyRate()).isEqualByComparingTo(BigDecimal.ONE);
            assertThat(result.getWeakKpCount()).isZero();
            assertThat(result.getStrongKpCount()).isPositive();
        }

        @Test
        @DisplayName("部分作答错误 — 应识别薄弱知识点")
        void diagnoseWithWeakKps() {
            when(userRepository.findById(studentId)).thenReturn(Optional.of(mockStudent));

            List<AnswerRecord> records = List.of(
                    createAnswerRecord(kp1Id, false, 30),
                    createAnswerRecord(kp1Id, false, 25),
                    createAnswerRecord(kp2Id, false, 40),
                    createAnswerRecord(kp3Id, true, 50)
            );
            when(answerRecordRepository.findByStudentIdAndCourseIdAndAttemptedAtBetween(
                    eq(studentId), eq(courseId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(records);

            List<Object[]> kpAggs = List.of(
                    new Object[]{kp1Id, 2L, 0L, 55L},
                    new Object[]{kp2Id, 1L, 0L, 40L},
                    new Object[]{kp3Id, 1L, 1L, 50L}
            );
            when(answerRecordRepository.aggregateByKnowledgePointAndCourse(
                    eq(studentId), eq(courseId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(kpAggs);
            when(knowledgePointRepository.countByCourseId(courseId)).thenReturn(10L);

            DiagnosisResponse result = diagnosisService.diagnose(studentId, courseId, 30);

            assertThat(result.getWeakKpCount()).isEqualTo(2);
            assertThat(result.getTopWeakKps()).hasSize(2);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  认知画像
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("buildCognitiveProfile() — 认知画像")
    class CognitiveProfileTests {

        @Test
        @DisplayName("有知识点数据 — 应返回完整认知画像")
        void buildProfileWithData() {
            when(userRepository.findById(studentId)).thenReturn(Optional.of(mockStudent));

            List<Object[]> kpAggs = List.of(
                    new Object[]{kp1Id, 10L, 9L, 300L},
                    new Object[]{kp2Id, 5L, 2L, 200L},
                    new Object[]{kp3Id, 8L, 7L, 400L}
            );
            when(answerRecordRepository.aggregateByKnowledgePointAll(studentId))
                    .thenReturn(kpAggs);
            when(knowledgePointRepository.countByCourseId(courseId)).thenReturn(3L);

            CognitiveProfile result = diagnosisService.buildCognitiveProfile(studentId, courseId);

            assertThat(result).isNotNull();
            assertThat(result.getTotalKpCount()).isEqualTo(3);
            assertThat(result.getOverallMasteryLevel()).isNotNull();
            assertThat(result.getRadarChartData()).hasSize(6);
            assertThat(result.getSummary()).contains("张三");
        }

        @Test
        @DisplayName("无作答记录 — 应返回零值画像")
        void buildProfileWithNoData() {
            when(userRepository.findById(studentId)).thenReturn(Optional.of(mockStudent));
            when(answerRecordRepository.aggregateByKnowledgePointAll(studentId))
                    .thenReturn(List.of());

            CognitiveProfile result = diagnosisService.buildCognitiveProfile(studentId, courseId);

            assertThat(result.getTotalKpCount()).isZero();
            assertThat(result.getMasteredKpCount()).isZero();
            assertThat(result.getWeakKpCount()).isZero();
            assertThat(result.getOverallMasteryLevel()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getRadarChartData()).hasSize(6);
        }

        @Test
        @DisplayName("学生不存在 — 应抛出 IllegalArgumentException")
        void buildProfileWithNonExistentStudent() {
            when(userRepository.findById(studentId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> diagnosisService.buildCognitiveProfile(studentId, courseId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("学生不存在");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  雷达图
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateRadarChart() — 雷达图")
    class RadarChartTests {

        @Test
        @DisplayName("有知识点数据 — 应返回雷达图维度")
        void generateRadarWithData() {
            when(userRepository.findById(studentId)).thenReturn(Optional.of(mockStudent));

            List<Object[]> kpAggs = List.of(
                    new Object[]{kp1Id, 10L, 8L, 300L},
                    new Object[]{kp2Id, 5L, 3L, 200L}
            );
            when(answerRecordRepository.aggregateByKnowledgePointAll(studentId))
                    .thenReturn(kpAggs);
            when(knowledgePointRepository.countByCourseId(courseId)).thenReturn(2L);

            RadarChartData result = diagnosisService.generateRadarChart(studentId, courseId);

            assertThat(result).isNotNull();
            assertThat(result.getStudentName()).isEqualTo("张三");
            assertThat(result.getDimensions()).hasSize(6);
            assertThat(result.getOverallScore()).isNotNull();
        }

        @Test
        @DisplayName("无知识点数据 — 应返回零值雷达图")
        void generateRadarWithNoData() {
            when(userRepository.findById(studentId)).thenReturn(Optional.of(mockStudent));
            when(answerRecordRepository.aggregateByKnowledgePointAll(studentId))
                    .thenReturn(List.of());

            RadarChartData result = diagnosisService.generateRadarChart(studentId, courseId);

            assertThat(result.getDimensions()).hasSize(6);
            assertThat(result.getOverallScore()).isEqualByComparingTo(BigDecimal.ZERO);
            result.getDimensions().forEach(dim ->
                    assertThat(dim.getValue()).isEqualByComparingTo(BigDecimal.ZERO));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  热力图
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("generateHeatMap() — 学习热力图")
    class HeatMapTests {

        @Test
        @DisplayName("有作答和会话数据 — 应返回完整热力图")
        void generateHeatMapWithData() {
            LocalDate today = LocalDate.now();
            Date todaySql = Date.valueOf(today);

            List<Object[]> dailyRecords = Collections.singletonList(
                    new Object[]{todaySql, 10L, 7L, 600L}
            );
            when(answerRecordRepository.dailyAggregate(
                    eq(studentId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(dailyRecords);

            List<Object[]> dailySessions = Collections.singletonList(
                    new Object[]{todaySql, 45L, 1L, 0L, 10L}
            );
            when(studySessionRepository.dailySessionAggregate(
                    eq(studentId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(dailySessions);

            HeatMapData result = diagnosisService.generateHeatMap(studentId, courseId, 7);

            assertThat(result).isNotNull();
            assertThat(result.getStudentId()).isEqualTo(studentId);
            assertThat(result.getTotalDays()).isEqualTo(8); // day 0~7 = 8 days
            assertThat(result.getActiveDays()).isPositive();
            assertThat(result.getHeatData()).hasSize(8);
        }

        @Test
        @DisplayName("无学习数据 — 应返回全零热力图")
        void generateHeatMapWithNoData() {
            when(answerRecordRepository.dailyAggregate(
                    eq(studentId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of());
            when(studySessionRepository.dailySessionAggregate(
                    eq(studentId), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of());

            HeatMapData result = diagnosisService.generateHeatMap(studentId, courseId, 7);

            assertThat(result.getActiveDays()).isZero();
            result.getHeatData().forEach(day -> {
                assertThat(day.getQuestionCount()).isZero();
                assertThat(day.getAccuracyRate()).isEqualByComparingTo(BigDecimal.ZERO);
            });
        }

        @Test
        @DisplayName("daysBack 为 0 或负数 — 应使用默认值")
        void generateHeatMapWithInvalidDaysBack() {
            HeatMapData result = diagnosisService.generateHeatMap(studentId, courseId, 0);

            assertThat(result.getTotalDays()).isEqualTo(30); // HEATMAP_DEFAULT_DAYS + 1
        }

        @Test
        @DisplayName("daysBack 超过最大值 — 应限制为最大值")
        void generateHeatMapWithExcessiveDaysBack() {
            HeatMapData result = diagnosisService.generateHeatMap(studentId, courseId, 365);

            // MAX_DAYS_BACK is 90, so total days = 91
            assertThat(result.getTotalDays()).isLessThanOrEqualTo(92);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  辅助方法 — 创建测试数据
    // ══════════════════════════════════════════════════════════════

    private List<AnswerRecord> createMockRecords() {
        return List.of(
                createAnswerRecord(kp1Id, true, 30),
                createAnswerRecord(kp1Id, true, 25),
                createAnswerRecord(kp2Id, false, 40),
                createAnswerRecord(kp3Id, true, 50)
        );
    }

    private AnswerRecord createAnswerRecord(UUID kpId, boolean correct, int timeSpent) {
        AnswerRecord record = new AnswerRecord();
        record.setId(UUID.randomUUID());
        record.setStudentId(studentId);
        record.setKnowledgePointId(kpId);
        record.setIsCorrect(correct);
        record.setTimeSpentSeconds(timeSpent);
        record.setAttemptedAt(LocalDateTime.now().minusHours(1));
        return record;
    }

    private List<Object[]> createMockKpAggregations() {
        return List.of(
                new Object[]{kp1Id, 2L, 2L, 55L},
                new Object[]{kp2Id, 1L, 0L, 40L},
                new Object[]{kp3Id, 1L, 1L, 50L}
        );
    }
}
