package com.edumentor.alert.service;

import com.edumentor.alert.dto.AlertDto;
import com.edumentor.alert.dto.AlertHandleRequest;
import com.edumentor.alert.dto.AlertStatisticsDto;
import com.edumentor.alert.entity.AlertRecord;
import com.edumentor.alert.repository.AlertRecordRepository;
import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.common.exception.ValidationException;
import com.edumentor.entity.enums.AlertSeverity;
import com.edumentor.entity.enums.AlertType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link AlertService} 的单元测试。
 * <p>
 * 测试覆盖：预警生成、预警查询（多维度）、预警处理（RESOLVE/DISMISS/ESCALATE）、
 * 预警统计、批量处理、过期清理、重复预警检测。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlertService — 预警系统服务单元测试")
class AlertServiceTest {

    @Mock
    private AlertRecordRepository alertRecordRepository;

    @InjectMocks
    private AlertService alertService;

    private UUID alertId;
    private UUID studentId;
    private UUID teacherId;
    private AlertRecord mockAlert;
    private AlertRecord mockResolvedAlert;
    private OffsetDateTime now;

    @BeforeEach
    void setUp() {
        alertId = UUID.randomUUID();
        studentId = UUID.randomUUID();
        teacherId = UUID.randomUUID();
        now = OffsetDateTime.now();

        mockAlert = new AlertRecord(
                studentId,
                AlertType.PERFORMANCE_DECLINE,
                AlertSeverity.MEDIUM,
                "成绩下滑预警",
                "最近正确率显著下降"
        );
        mockAlert.setId(alertId);
        mockAlert.setTeacherId(teacherId);
        mockAlert.setCreatedAt(now);
        mockAlert.setExpiresAt(now.plusDays(14));
        mockAlert.setRead(false);
        mockAlert.setResolved(false);

        mockResolvedAlert = new AlertRecord(
                studentId,
                AlertType.PERFORMANCE_DECLINE,
                AlertSeverity.MEDIUM,
                "已解决的预警",
                "已处理"
        );
        mockResolvedAlert.setId(UUID.randomUUID());
        mockResolvedAlert.setResolved(true);
        mockResolvedAlert.setResolvedAt(now);
    }

    // ══════════════════════════════════════════════════════════════
    //  预警生成
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("createAlert() / evaluateRules() — 预警生成")
    class AlertCreationTests {

        @Test
        @DisplayName("创建预警 — 应返回带过期时间的预警记录")
        void createAlert() {
            when(alertRecordRepository.save(any(AlertRecord.class)))
                    .thenAnswer(inv -> {
                        AlertRecord r = inv.getArgument(0);
                        r.setId(alertId);
                        return r;
                    });

            AlertRecord result = alertService.createAlert(
                    studentId, AlertType.KNOWLEDGE_GAP, AlertSeverity.HIGH,
                    "测试预警", "描述", "{\"key\":\"val\"}", teacherId);

            assertThat(result).isNotNull();
            assertThat(result.getAlertType()).isEqualTo(AlertType.KNOWLEDGE_GAP);
            assertThat(result.getSeverity()).isEqualTo(AlertSeverity.HIGH);
            assertThat(result.getExpiresAt()).isNotNull();
        }

        @Test
        @DisplayName("创建 CRITICAL 级别预警 — 过期时间应为 3 天")
        void createCriticalAlertExpiry() {
            when(alertRecordRepository.save(any(AlertRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AlertRecord result = alertService.createAlert(
                    studentId, AlertType.TIME_PRESSURE, AlertSeverity.CRITICAL,
                    "紧急预警", null, null, null);

            long expiryDays = result.getExpiresAt().toEpochSecond() - now.toEpochSecond();
            assertThat(expiryDays).isLessThan(4 * 86400); // Less than 4 days
        }

        @Test
        @DisplayName("evaluateRules — 成绩下滑触发 PERFORMANCE_DECLINE 预警")
        void evaluatePerformanceDecline() {
            when(alertRecordRepository.findByStudentIdAndIsResolvedFalseOrderByCreatedAtDesc(studentId))
                    .thenReturn(List.of());

            alertService.evaluateRules(studentId, teacherId,
                    0.35, 0.6, 2, 0.3, 60, 0.7, 0.75);

            verify(alertRecordRepository, atLeastOnce()).save(any(AlertRecord.class));
        }

        @Test
        @DisplayName("evaluateRules — 掌握度低触发 KNOWLEDGE_GAP 预警")
        void evaluateKnowledgeGap() {
            when(alertRecordRepository.findByStudentIdAndIsResolvedFalseOrderByCreatedAtDesc(studentId))
                    .thenReturn(List.of());

            alertService.evaluateRules(studentId, teacherId,
                    0.6, 0.3, 2, 0.3, 60, 0.7, 0.75);

            verify(alertRecordRepository, atLeastOnce()).save(any(AlertRecord.class));
        }

        @Test
        @DisplayName("evaluateRules — 连续 7 天未学习触发 STUDY_ENGAGEMENT 预警")
        void evaluateStudyEngagement() {
            when(alertRecordRepository.findByStudentIdAndIsResolvedFalseOrderByCreatedAtDesc(studentId))
                    .thenReturn(List.of());

            alertService.evaluateRules(studentId, teacherId,
                    0.6, 0.6, 10, 0.3, 60, 0.7, 0.75);

            verify(alertRecordRepository, atLeastOnce()).save(any(AlertRecord.class));
        }

        @Test
        @DisplayName("evaluateRules — 未触发任何预警")
        void evaluateNoAlerts() {
            alertService.evaluateRules(studentId, teacherId,
                    0.8, 0.8, 1, 0.2, 90, 0.9, 0.8);

            verify(alertRecordRepository, never()).save(any(AlertRecord.class));
        }

        @Test
        @DisplayName("evaluateRules — 触发综合预警（2+条规则同时触发）")
        void evaluateCombinedAlert() {
            when(alertRecordRepository.findByStudentIdAndIsResolvedFalseOrderByCreatedAtDesc(studentId))
                    .thenReturn(List.of());

            alertService.evaluateRules(studentId, teacherId,
                    0.3, 0.25, 15, 0.7, 20, 0.4, 0.6);

            // Should trigger PERFORMANCE_DECLINE + KNOWLEDGE_GAP + STUDY_ENGAGEMENT + ERROR_RATE + TIME_PRESSURE + COMPARISON + COMBINED
            verify(alertRecordRepository, atLeast(3)).save(any(AlertRecord.class));
        }

        @Test
        @DisplayName("evaluateRules — 重复预警检测应阻止重复创建")
        void evaluateDuplicateDetection() {
            when(alertRecordRepository.findByStudentIdAndIsResolvedFalseOrderByCreatedAtDesc(studentId))
                    .thenReturn(List.of(mockAlert)); // Has existing unresolved PERFORMANCE_DECLINE within 3 days

            alertService.evaluateRules(studentId, teacherId,
                    0.35, 0.6, 2, 0.3, 60, 0.7, 0.75);

            // PERFORMANCE_DECLINE should be skipped due to duplicate detection
            verify(alertRecordRepository, never()).save(any(AlertRecord.class));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  预警查询
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("预警查询")
    class AlertQueryTests {

        @Test
        @DisplayName("按级别查询预警列表")
        void getAlertsBySeverity() {
            Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<AlertRecord> alertPage = new PageImpl<>(List.of(mockAlert));

            when(alertRecordRepository.findBySeverityOrderByCreatedAtDesc(
                    AlertSeverity.MEDIUM, pageable))
                    .thenReturn(alertPage);

            Page<AlertDto> result = alertService.getAlerts(1, 10, AlertSeverity.MEDIUM, null, null);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("按类型查询预警")
        void getAlertsByType() {
            Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

            when(alertRecordRepository.findByAlertTypeOrderByCreatedAtDesc(
                    AlertType.PERFORMANCE_DECLINE, pageable))
                    .thenReturn(new PageImpl<>(List.of(mockAlert)));

            Page<AlertDto> result = alertService.getAlerts(1, 10, null, AlertType.PERFORMANCE_DECLINE, null);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("通过 ID 获取预警详情")
        void getAlertById() {
            when(alertRecordRepository.findById(alertId)).thenReturn(Optional.of(mockAlert));

            AlertDto result = alertService.getAlertById(alertId);

            assertThat(result.getTitle()).isEqualTo("成绩下滑预警");
        }

        @Test
        @DisplayName("获取不存在的预警详情 — 应抛出 ResourceNotFoundException")
        void getNonExistentAlert() {
            when(alertRecordRepository.findById(alertId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> alertService.getAlertById(alertId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("查询学生的预警列表")
        void getAlertsByStudent() {
            Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));

            when(alertRecordRepository.findByStudentIdOrderByCreatedAtDesc(studentId, pageable))
                    .thenReturn(new PageImpl<>(List.of(mockAlert)));

            Page<AlertDto> result = alertService.getAlertsByStudent(studentId, 1, 10);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("查询学生未处理预警")
        void getUnresolvedAlertsByStudent() {
            when(alertRecordRepository.findByStudentIdAndIsResolvedFalseOrderByCreatedAtDesc(studentId))
                    .thenReturn(List.of(mockAlert));

            List<AlertDto> result = alertService.getUnresolvedAlertsByStudent(studentId);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("查询教师负责的未处理预警")
        void getUnresolvedAlertsByTeacher() {
            when(alertRecordRepository.findByTeacherIdAndIsResolvedFalseOrderByCreatedAtDesc(teacherId))
                    .thenReturn(List.of(mockAlert));

            List<AlertDto> result = alertService.getUnresolvedAlertsByTeacher(teacherId);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("查询所有未处理预警（按优先级排序）")
        void getUnresolvedAlerts() {
            when(alertRecordRepository.findUnresolvedAlertsOrderedBySeverity())
                    .thenReturn(List.of(mockAlert));

            List<AlertDto> result = alertService.getUnresolvedAlerts();

            assertThat(result).hasSize(1);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  预警处理
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("预警处理 (handleAlert)")
    class AlertHandleTests {

        @Test
        @DisplayName("标记为已读")
        void markAsRead() {
            when(alertRecordRepository.findById(alertId)).thenReturn(Optional.of(mockAlert));
            when(alertRecordRepository.save(any(AlertRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AlertDto result = alertService.markAsRead(alertId);

            assertThat(result.isRead()).isTrue();
        }

        @Test
        @DisplayName("RESOLVE — 解决预警")
        void resolveAlert() {
            when(alertRecordRepository.findById(alertId)).thenReturn(Optional.of(mockAlert));
            when(alertRecordRepository.save(any(AlertRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AlertHandleRequest request = new AlertHandleRequest();
            request.setAlertId(alertId);
            request.setAction("RESOLVE");
            request.setResolvedBy(teacherId);
            request.setNote("已解决");

            AlertDto result = alertService.handleAlert(request);

            assertThat(result.isResolved()).isTrue();
        }

        @Test
        @DisplayName("DISMISS — 忽略预警")
        void dismissAlert() {
            when(alertRecordRepository.findById(alertId)).thenReturn(Optional.of(mockAlert));
            when(alertRecordRepository.save(any(AlertRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AlertHandleRequest request = new AlertHandleRequest();
            request.setAlertId(alertId);
            request.setAction("DISMISS");
            request.setResolvedBy(teacherId);

            AlertDto result = alertService.handleAlert(request);

            assertThat(result.isResolved()).isTrue();
            assertThat(result.getHandleNote()).startsWith("已忽略");
        }

        @Test
        @DisplayName("ESCALATE — 升级预警级别")
        void escalateAlert() {
            when(alertRecordRepository.findById(alertId)).thenReturn(Optional.of(mockAlert));
            when(alertRecordRepository.save(any(AlertRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AlertHandleRequest request = new AlertHandleRequest();
            request.setAlertId(alertId);
            request.setAction("ESCALATE");
            request.setNote("需要优先处理");

            AlertDto result = alertService.handleAlert(request);

            // MEDIUM → HIGH
            assertThat(result.getSeverity()).isEqualTo(AlertSeverity.HIGH);
        }

        @Test
        @DisplayName("CRITICAL 级别升级 — 保持 CRITICAL")
        void escalateCriticalAlert() {
            mockAlert.setSeverity(AlertSeverity.CRITICAL);
            when(alertRecordRepository.findById(alertId)).thenReturn(Optional.of(mockAlert));
            when(alertRecordRepository.save(any(AlertRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AlertHandleRequest request = new AlertHandleRequest();
            request.setAlertId(alertId);
            request.setAction("ESCALATE");

            AlertDto result = alertService.handleAlert(request);

            assertThat(result.getSeverity()).isEqualTo(AlertSeverity.CRITICAL);
        }

        @Test
        @DisplayName("处理已解决的预警 — 应抛出 ValidationException")
        void handleResolvedAlert() {
            when(alertRecordRepository.findById(alertId)).thenReturn(Optional.of(mockResolvedAlert));

            AlertHandleRequest request = new AlertHandleRequest();
            request.setAlertId(alertId);
            request.setAction("RESOLVE");
            request.setResolvedBy(teacherId);

            assertThatThrownBy(() -> alertService.handleAlert(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("已被处理");
        }

        @Test
        @DisplayName("无效处理方式 — 应抛出 ValidationException")
        void invalidAction() {
            when(alertRecordRepository.findById(alertId)).thenReturn(Optional.of(mockAlert));

            AlertHandleRequest request = new AlertHandleRequest();
            request.setAlertId(alertId);
            request.setAction("INVALID");
            request.setResolvedBy(teacherId);

            assertThatThrownBy(() -> alertService.handleAlert(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("无效的处理方式");
        }

        @Test
        @DisplayName("alertId 为 null — 应抛出 ValidationException")
        void nullAlertId() {
            AlertHandleRequest request = new AlertHandleRequest();
            request.setAction("RESOLVE");
            request.setResolvedBy(teacherId);

            assertThatThrownBy(() -> alertService.handleAlert(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("预警 ID 不能为空");
        }

        @Test
        @DisplayName("批量处理预警")
        void batchHandleAlerts() {
            UUID alertId2 = UUID.randomUUID();
            AlertRecord alert2 = new AlertRecord(
                    studentId, AlertType.KNOWLEDGE_GAP, AlertSeverity.HIGH, "test", "desc");
            alert2.setId(alertId2);
            alert2.setCreatedAt(now);

            when(alertRecordRepository.findById(alertId)).thenReturn(Optional.of(mockAlert));
            when(alertRecordRepository.findById(alertId2)).thenReturn(Optional.of(alert2));
            when(alertRecordRepository.save(any(AlertRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AlertHandleRequest request = new AlertHandleRequest();
            request.setAlertIds(List.of(alertId, alertId2));
            request.setAction("RESOLVE");
            request.setResolvedBy(teacherId);

            int count = alertService.batchHandleAlerts(request);

            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("批量处理 — 空 ID 列表抛出异常")
        void batchHandleEmptyIds() {
            AlertHandleRequest request = new AlertHandleRequest();
            request.setAlertIds(List.of());
            request.setAction("RESOLVE");

            assertThatThrownBy(() -> alertService.batchHandleAlerts(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("不能为空");
        }

        @Test
        @DisplayName("更新预警备注")
        void updateAlertNote() {
            when(alertRecordRepository.findById(alertId)).thenReturn(Optional.of(mockAlert));
            when(alertRecordRepository.save(any(AlertRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            AlertDto result = alertService.updateAlertNote(alertId, "新备注");

            assertThat(result.getHandleNote()).isEqualTo("新备注");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  预警统计
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("预警统计")
    class AlertStatisticsTests {

        @Test
        @DisplayName("获取全局预警统计")
        void getAlertStatistics() {
            when(alertRecordRepository.countByIsResolvedFalse()).thenReturn(5L);
            when(alertRecordRepository.countByDateRange(any(), any())).thenReturn(2L);
            when(alertRecordRepository.countUnresolvedBySeverity())
                    .thenReturn(List.of(
                            new Object[]{AlertSeverity.HIGH, 2L},
                            new Object[]{AlertSeverity.MEDIUM, 3L}
                    ));
            when(alertRecordRepository.countUnresolvedByType())
                    .thenReturn(List.of(
                            new Object[]{AlertType.PERFORMANCE_DECLINE, 3L},
                            new Object[]{AlertType.KNOWLEDGE_GAP, 2L}
                    ));
            when(alertRecordRepository.findBySeveritiesAndUnresolved(anyList()))
                    .thenReturn(List.of(mockAlert));
            when(alertRecordRepository.count()).thenReturn(20L);

            AlertStatisticsDto result = alertService.getAlertStatistics();

            assertThat(result.getTotalUnresolved()).isEqualTo(5);
            assertThat(result.getBySeverity()).containsKey(AlertSeverity.HIGH);
            assertThat(result.getBySeverity()).containsKey(AlertSeverity.LOW); // default 0
            assertThat(result.getByType()).containsKey(AlertType.PERFORMANCE_DECLINE);
            assertThat(result.getUrgentAlerts()).isNotEmpty();
            assertThat(result.getDailyTrend()).hasSize(7);
        }

        @Test
        @DisplayName("获取学生预警统计")
        void getStudentAlertStatistics() {
            when(alertRecordRepository.countByStudentIdAndIsResolvedFalse(studentId))
                    .thenReturn(2L);
            when(alertRecordRepository.findByStudentIdAndIsResolvedFalseOrderByCreatedAtDesc(studentId))
                    .thenReturn(List.of(mockAlert));

            AlertStatisticsDto result = alertService.getStudentAlertStatistics(studentId);

            assertThat(result.getTotalUnresolved()).isEqualTo(2);
            assertThat(result.getBySeverity()).isNotEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  过期清理
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("cleanExpiredAlerts() — 清理过期预警")
    class CleanExpiredTests {

        @Test
        @DisplayName("有过期预警 — 应自动关闭")
        void cleanExpiredAlerts() {
            AlertRecord expiredAlert = new AlertRecord(
                    studentId, AlertType.STUDY_ENGAGEMENT, AlertSeverity.LOW, "过期", "");
            expiredAlert.setId(UUID.randomUUID());
            expiredAlert.setExpiresAt(now.minusDays(1)); // Already expired

            when(alertRecordRepository.findActiveAlerts(any(OffsetDateTime.class)))
                    .thenReturn(List.of(expiredAlert));
            when(alertRecordRepository.save(any(AlertRecord.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            int count = alertService.cleanExpiredAlerts();

            assertThat(count).isEqualTo(1);
            assertThat(expiredAlert.isResolved()).isTrue();
            assertThat(expiredAlert.getHandleNote()).contains("过期");
        }

        @Test
        @DisplayName("无过期预警 — 不执行任何操作")
        void cleanNoExpiredAlerts() {
            when(alertRecordRepository.findActiveAlerts(any(OffsetDateTime.class)))
                    .thenReturn(List.of());

            int count = alertService.cleanExpiredAlerts();

            assertThat(count).isZero();
            verify(alertRecordRepository, never()).save(any());
        }
    }
}
