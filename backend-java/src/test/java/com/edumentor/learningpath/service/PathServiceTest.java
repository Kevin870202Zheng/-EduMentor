package com.edumentor.learningpath.service;

import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.common.exception.ValidationException;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.learningpath.dto.*;
import com.edumentor.learningpath.entity.LearningPath;
import com.edumentor.learningpath.entity.LearningPathNode;
import com.edumentor.learningpath.entity.PathNodeStatus;
import com.edumentor.learningpath.entity.PathStatus;
import com.edumentor.learningpath.repository.LearningPathNodeRepository;
import com.edumentor.learningpath.repository.LearningPathRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link PathService} 的单元测试。
 * <p>
 * 测试覆盖：路径规划、路径查询、路径状态管理、节点进度管理、智能适配、图谱查询。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PathService — 学习路径规划服务单元测试")
class PathServiceTest {

    @Mock
    private LearningPathRepository learningPathRepository;

    @Mock
    private LearningPathNodeRepository learningPathNodeRepository;

    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private PathService pathService;

    private UUID studentId;
    private UUID courseId;
    private UUID pathId;
    private UUID nodeId;
    private UUID kp1Id;
    private UUID kp2Id;
    private UUID kp3Id;
    private UUID targetKpId;

    private LearningPath mockPath;
    private LearningPathNode mockNode1;
    private LearningPathNode mockNode2;
    private KnowledgePoint mockKp1;
    private KnowledgePoint mockKp2;
    private KnowledgePoint mockKp3;

    @SuppressWarnings("unchecked")
    private <T> TypedQuery<T> mockTypedQuery() {
        return mock(TypedQuery.class);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        studentId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        pathId = UUID.randomUUID();
        nodeId = UUID.randomUUID();
        kp1Id = UUID.randomUUID();
        kp2Id = UUID.randomUUID();
        kp3Id = UUID.randomUUID();
        targetKpId = kp3Id;

        mockKp1 = new KnowledgePoint();
        mockKp1.setId(kp1Id);
        mockKp1.setCourseId(courseId);
        mockKp1.setName("一元一次方程");
        mockKp1.setDifficulty(2);
        mockKp1.setImportance(3);
        mockKp1.setOrderIndex(0);

        mockKp2 = new KnowledgePoint();
        mockKp2.setId(kp2Id);
        mockKp2.setCourseId(courseId);
        mockKp2.setName("二元一次方程组");
        mockKp2.setDifficulty(3);
        mockKp2.setImportance(4);
        mockKp2.setOrderIndex(1);

        mockKp3 = new KnowledgePoint();
        mockKp3.setId(kp3Id);
        mockKp3.setCourseId(courseId);
        mockKp3.setName("函数");
        mockKp3.setDifficulty(4);
        mockKp3.setImportance(5);
        mockKp3.setOrderIndex(2);

        mockPath = new LearningPath();
        mockPath.setId(pathId);
        mockPath.setStudentId(studentId);
        mockPath.setCreatedBy(studentId);
        mockPath.setName("高中数学基础路径");
        mockPath.setStatus(PathStatus.DRAFT);
        mockPath.setProgress(0);
        mockPath.setCreatedAt(LocalDateTime.now());
        mockPath.setNodes(new ArrayList<>());

        mockNode1 = new LearningPathNode();
        mockNode1.setId(nodeId);
        mockNode1.setLearningPath(mockPath);
        mockNode1.setKnowledgePointId(kp1Id);
        mockNode1.setOrderIndex(0);
        mockNode1.setStatus(PathNodeStatus.PENDING);
        mockNode1.setEstimatedMinutes(30);

        mockNode2 = new LearningPathNode();
        mockNode2.setId(UUID.randomUUID());
        mockNode2.setLearningPath(mockPath);
        mockNode2.setKnowledgePointId(kp2Id);
        mockNode2.setOrderIndex(1);
        mockNode2.setStatus(PathNodeStatus.PENDING);
        mockNode2.setEstimatedMinutes(45);

        mockPath.setNodes(new ArrayList<>(List.of(mockNode1, mockNode2)));
    }

    // ══════════════════════════════════════════════════════════════
    //  路径规划
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("planPath() — 路径规划")
    @SuppressWarnings("unchecked")
    class PlanPathTests {

        @Test
        @DisplayName("规划路径 — 应成功创建路径和节点")
        void planPathSuccess() {
            TypedQuery<KnowledgePoint> kpQuery = mock(TypedQuery.class);
            TypedQuery<Object[]> relQuery = mock(TypedQuery.class);

            when(entityManager.createQuery(
                    contains("FROM KnowledgePoint kp"), eq(KnowledgePoint.class)))
                    .thenReturn(kpQuery);
            when(kpQuery.setParameter("courseId", courseId)).thenReturn(kpQuery);
            when(kpQuery.getResultList()).thenReturn(List.of(mockKp1, mockKp2, mockKp3));

            when(entityManager.createQuery(
                    contains("FROM KnowledgeRelation kr"), eq(Object[].class)))
                    .thenReturn(relQuery);
            when(relQuery.setParameter("kpIds", anyList())).thenReturn(relQuery);
            when(relQuery.getResultList()).thenReturn(List.of());

            when(learningPathRepository.save(any(LearningPath.class)))
                    .thenAnswer(inv -> {
                        LearningPath p = inv.getArgument(0);
                        p.setId(pathId);
                        p.setCreatedAt(LocalDateTime.now());
                        return p;
                    });
            when(learningPathNodeRepository.saveAll(anyList()))
                    .thenAnswer(inv -> inv.getArgument(0));

            PathPlanRequest request = new PathPlanRequest();
            request.setStudentId(studentId);
            request.setCourseId(courseId);
            request.setName("基础路径");
            request.setSkipMastered(false);

            LearningPathDto result = pathService.planPath(request);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("基础路径");
            assertThat(result.getNodes()).hasSize(3);
        }

        @Test
        @DisplayName("规划路径 — 课程无知识点 — 应抛出 ResourceNotFoundException")
        void planPathNoKps() {
            TypedQuery<KnowledgePoint> kpQuery = mock(TypedQuery.class);

            when(entityManager.createQuery(
                    contains("FROM KnowledgePoint kp"), eq(KnowledgePoint.class)))
                    .thenReturn(kpQuery);
            when(kpQuery.setParameter("courseId", courseId)).thenReturn(kpQuery);
            when(kpQuery.getResultList()).thenReturn(List.of());

            PathPlanRequest request = new PathPlanRequest();
            request.setStudentId(studentId);
            request.setCourseId(courseId);

            assertThatThrownBy(() -> pathService.planPath(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("课程");
        }

        @Test
        @DisplayName("规划路径 — 跳过已掌握知识点")
        void planPathSkipMastered() {
            TypedQuery<KnowledgePoint> kpQuery = mock(TypedQuery.class);
            TypedQuery<Object[]> relQuery = mock(TypedQuery.class);

            when(entityManager.createQuery(
                    contains("FROM KnowledgePoint kp"), eq(KnowledgePoint.class)))
                    .thenReturn(kpQuery);
            when(kpQuery.setParameter("courseId", courseId)).thenReturn(kpQuery);
            when(kpQuery.getResultList()).thenReturn(List.of(mockKp1, mockKp2, mockKp3));

            when(entityManager.createQuery(
                    contains("FROM KnowledgeRelation kr"), eq(Object[].class)))
                    .thenReturn(relQuery);
            when(relQuery.setParameter("kpIds", anyList())).thenReturn(relQuery);
            when(relQuery.getResultList()).thenReturn(List.of());

            // kp1 is mastered
            TypedQuery<Object> mockQuery = mock(TypedQuery.class);
            when(entityManager.createQuery(contains("SELECT DISTINCT lpn.knowledgePointId FROM LearningPathNode lpn"), eq(Object.class)))
                    .thenReturn(mockQuery);
            when(mockQuery.setParameter("studentId", studentId)).thenReturn(mockQuery);
            when(mockQuery.getResultList()).thenReturn(List.of(kp1Id));

            when(learningPathRepository.save(any(LearningPath.class)))
                    .thenAnswer(inv -> {
                        LearningPath p = inv.getArgument(0);
                        p.setId(pathId);
                        return p;
                    });
            when(learningPathNodeRepository.saveAll(anyList()))
                    .thenAnswer(inv -> inv.getArgument(0));

            PathPlanRequest request = new PathPlanRequest();
            request.setStudentId(studentId);
            request.setCourseId(courseId);
            request.setName("跳过已掌握");
            request.setSkipMastered(true);

            LearningPathDto result = pathService.planPath(request);

            // kp1 should be skipped, so only 2 nodes
            assertThat(result.getNodes()).hasSize(2);
        }

        @Test
        @DisplayName("规划路径 — 聚焦到目标知识点")
        void planPathWithTarget() {
            TypedQuery<KnowledgePoint> kpQuery = mock(TypedQuery.class);
            TypedQuery<Object[]> relQuery = mock(TypedQuery.class);

            when(entityManager.createQuery(
                    contains("FROM KnowledgePoint kp"), eq(KnowledgePoint.class)))
                    .thenReturn(kpQuery);
            when(kpQuery.setParameter("courseId", courseId)).thenReturn(kpQuery);
            when(kpQuery.getResultList()).thenReturn(List.of(mockKp1, mockKp2, mockKp3));

            when(entityManager.createQuery(
                    contains("FROM KnowledgeRelation kr"), eq(Object[].class)))
                    .thenReturn(relQuery);
            when(relQuery.setParameter("kpIds", anyList())).thenReturn(relQuery);
            when(relQuery.getResultList()).thenReturn(List.of());

            when(learningPathRepository.save(any(LearningPath.class)))
                    .thenAnswer(inv -> {
                        LearningPath p = inv.getArgument(0);
                        p.setId(pathId);
                        return p;
                    });
            when(learningPathNodeRepository.saveAll(anyList()))
                    .thenAnswer(inv -> inv.getArgument(0));

            PathPlanRequest request = new PathPlanRequest();
            request.setStudentId(studentId);
            request.setCourseId(courseId);
            request.setFocusKpId(kp2Id);
            request.setName("聚焦路径");

            LearningPathDto result = pathService.planPath(request);

            // Should truncate to kp2: kp1, kp2
            assertThat(result.getNodes()).hasSize(2);
            assertThat(result.getNodes().get(0).getKnowledgePointId()).isEqualTo(kp1Id);
            assertThat(result.getNodes().get(1).getKnowledgePointId()).isEqualTo(kp2Id);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  路径查询
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("路径查询")
    class PathQueryTests {

        @Test
        @DisplayName("通过 ID 获取路径 — 应返回路径 DTO")
        void getPathById() {
            when(learningPathRepository.findById(pathId)).thenReturn(Optional.of(mockPath));

            LearningPathDto result = pathService.getPath(pathId);

            assertThat(result.getId()).isEqualTo(pathId);
        }

        @Test
        @DisplayName("获取不存在的路径 — 应抛出 ResourceNotFoundException")
        void getNonExistentPath() {
            when(learningPathRepository.findById(pathId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> pathService.getPath(pathId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("学习路径");
        }

        @Test
        @DisplayName("获取学生所有路径")
        @SuppressWarnings("unchecked")
        void getStudentPaths() {
            TypedQuery<LearningPath> query = mockTypedQuery();
            when(entityManager.createQuery(
                    contains("SELECT lp FROM LearningPath lp"), eq(LearningPath.class)))
                    .thenReturn(query);
            when(query.setParameter("studentId", studentId)).thenReturn(query);
            when(query.getResultList()).thenReturn(List.of(mockPath));

            List<LearningPathDto> result = pathService.getStudentPaths(studentId);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("获取学生当前活跃路径")
        void getActivePath() {
            mockPath.setStatus(PathStatus.ACTIVE);
            when(learningPathRepository.findTopByStudentIdAndStatusOrderByCreatedAtDesc(
                    studentId, PathStatus.ACTIVE))
                    .thenReturn(Optional.of(mockPath));

            Optional<LearningPathDto> result = pathService.getActivePath(studentId);

            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(PathStatus.ACTIVE);
        }

        @Test
        @DisplayName("获取下一个待学节点")
        void getNextNode() {
            when(learningPathNodeRepository.findTopByLearningPathIdAndStatusOrderByOrderIndexAsc(
                    pathId, PathNodeStatus.PENDING))
                    .thenReturn(Optional.of(mockNode1));

            @SuppressWarnings("unchecked")
            TypedQuery<Object[]> nameQuery = mock(TypedQuery.class);
            when(entityManager.createQuery(
                    contains("SELECT kp.name, kp.difficulty"), eq(Object[].class)))
                    .thenReturn(nameQuery);
            when(nameQuery.setParameter("kpId", kp1Id)).thenReturn(nameQuery);
            when(nameQuery.getResultList()).then(inv -> List.of((Object[]) new Object[]{"一元一次方程", 2}));

            Optional<LearningPathNodeDto> result = pathService.getNextNode(pathId);

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("无待学节点 — 应返回空")
        void getNextNodeEmpty() {
            when(learningPathNodeRepository.findTopByLearningPathIdAndStatusOrderByOrderIndexAsc(
                    pathId, PathNodeStatus.PENDING))
                    .thenReturn(Optional.empty());

            Optional<LearningPathNodeDto> result = pathService.getNextNode(pathId);

            assertThat(result).isEmpty();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  路径状态管理
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("路径状态管理")
    class PathStatusTests {

        @Test
        @DisplayName("激活路径 — DRAFT → ACTIVE")
        void activatePath() {
            when(learningPathRepository.findById(pathId)).thenReturn(Optional.of(mockPath));
            when(learningPathRepository.save(any(LearningPath.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            LearningPathDto result = pathService.activatePath(pathId);

            assertThat(result.getStatus()).isEqualTo(PathStatus.ACTIVE);
        }

        @Test
        @DisplayName("激活已完成路径 — 应抛出 ValidationException")
        void activateCompletedPath() {
            mockPath.setStatus(PathStatus.COMPLETED);
            when(learningPathRepository.findById(pathId)).thenReturn(Optional.of(mockPath));

            assertThatThrownBy(() -> pathService.activatePath(pathId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("DRAFT 或 PAUSED");
        }

        @Test
        @DisplayName("暂停路径 — ACTIVE → PAUSED")
        void pausePath() {
            mockPath.setStatus(PathStatus.ACTIVE);
            when(learningPathRepository.findById(pathId)).thenReturn(Optional.of(mockPath));
            when(learningPathRepository.save(any(LearningPath.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            LearningPathDto result = pathService.pausePath(pathId);

            assertThat(result.getStatus()).isEqualTo(PathStatus.PAUSED);
        }

        @Test
        @DisplayName("暂停非活跃路径 — 应抛出 ValidationException")
        void pauseNonActivePath() {
            when(learningPathRepository.findById(pathId)).thenReturn(Optional.of(mockPath));

            assertThatThrownBy(() -> pathService.pausePath(pathId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("ACTIVE");
        }

        @Test
        @DisplayName("完成路径 — 进度设为 100%")
        void completePath() {
            when(learningPathRepository.findById(pathId)).thenReturn(Optional.of(mockPath));
            when(learningPathRepository.save(any(LearningPath.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            LearningPathDto result = pathService.completePath(pathId);

            assertThat(result.getStatus()).isEqualTo(PathStatus.COMPLETED);
            assertThat(result.getProgress()).isEqualTo(100);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  节点进度管理
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("节点进度管理")
    class NodeProgressTests {

        @Test
        @DisplayName("更新节点为 IN_PROGRESS")
        void markNodeInProgress() {
            when(learningPathNodeRepository.findById(nodeId))
                    .thenReturn(Optional.of(mockNode1));
            when(learningPathRepository.save(any(LearningPath.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            PathProgressUpdateRequest request = new PathProgressUpdateRequest();
            request.setNodeId(nodeId);
            request.setStatus("IN_PROGRESS");

            LearningPathDto result = pathService.updateNodeProgress(request);

            assertThat(result).isNotNull();
            verify(learningPathNodeRepository).save(mockNode1);
        }

        @Test
        @DisplayName("更新节点为 COMPLETED — 路径进度更新")
        void markNodeCompleted() {
            mockNode1.setStatus(PathNodeStatus.IN_PROGRESS);
            when(learningPathNodeRepository.findById(nodeId))
                    .thenReturn(Optional.of(mockNode1));
            when(learningPathRepository.save(any(LearningPath.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            PathProgressUpdateRequest request = new PathProgressUpdateRequest();
            request.setNodeId(nodeId);
            request.setStatus("COMPLETED");
            request.setActualMinutes(25);

            LearningPathDto result = pathService.updateNodeProgress(request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("更新节点为 SKIPPED")
        void markNodeSkipped() {
            when(learningPathNodeRepository.findById(nodeId))
                    .thenReturn(Optional.of(mockNode1));
            when(learningPathRepository.save(any(LearningPath.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            PathProgressUpdateRequest request = new PathProgressUpdateRequest();
            request.setNodeId(nodeId);
            request.setStatus("SKIPPED");

            pathService.updateNodeProgress(request);

            verify(learningPathNodeRepository).save(mockNode1);
            assertThat(mockNode1.getStatus()).isEqualTo(PathNodeStatus.SKIPPED);
        }

        @Test
        @DisplayName("不支持的节点状态 — 应抛出 ValidationException")
        void unsupportedNodeStatus() {
            when(learningPathNodeRepository.findById(nodeId))
                    .thenReturn(Optional.of(mockNode1));

            PathProgressUpdateRequest request = new PathProgressUpdateRequest();
            request.setNodeId(nodeId);
            request.setStatus("PENDING"); // PENDING not allowed as update target

            assertThatThrownBy(() -> pathService.updateNodeProgress(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("不支持");
        }

        @Test
        @DisplayName("不存在节点 — 应抛出 ResourceNotFoundException")
        void updateNonExistentNode() {
            when(learningPathNodeRepository.findById(nodeId))
                    .thenReturn(Optional.empty());

            PathProgressUpdateRequest request = new PathProgressUpdateRequest();
            request.setNodeId(nodeId);
            request.setStatus("COMPLETED");

            assertThatThrownBy(() -> pathService.updateNodeProgress(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  智能适配
    // ══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("adaptPath() — 智能适配")
    @SuppressWarnings("unchecked")
    class AdaptPathTests {

        @Test
        @DisplayName("REORDER 策略 — 按难度重新排序")
        void adaptReorder() {
            when(learningPathRepository.findById(pathId)).thenReturn(Optional.of(mockPath));
            when(learningPathNodeRepository.findByLearningPathIdOrderByOrderIndexAsc(pathId))
                    .thenReturn(List.of(mockNode1, mockNode2));

            TypedQuery<Integer> diffQuery = mock(TypedQuery.class);
            when(entityManager.createQuery(
                    contains("SELECT kp.difficulty"), eq(Integer.class)))
                    .thenReturn(diffQuery);
            when(diffQuery.setParameter("kpId", any())).thenReturn(diffQuery);
            when(diffQuery.getResultStream()).thenReturn(java.util.stream.Stream.of(2));

            PathAdaptRequest request = new PathAdaptRequest();
            request.setPathId(pathId);
            request.setAdaptStrategy("REORDER");

            LearningPathDto result = pathService.adaptPath(request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("SHORTEN 策略 — 跳过已掌握")
        void adaptShorten() {
            when(learningPathRepository.findById(pathId)).thenReturn(Optional.of(mockPath));
            when(learningPathNodeRepository.findByLearningPathIdOrderByOrderIndexAsc(pathId))
                    .thenReturn(List.of(mockNode1, mockNode2));
            TypedQuery<Object> masteredQuery = mock(TypedQuery.class);
            when(entityManager.createQuery(
                    contains("SELECT DISTINCT lpn.knowledgePointId FROM LearningPathNode lpn"),
                    eq(Object.class)))
                    .thenReturn(masteredQuery);
            when(masteredQuery.setParameter("studentId", studentId)).thenReturn(masteredQuery);
            when(masteredQuery.getResultList()).thenReturn(List.of(kp1Id));

            PathAdaptRequest request = new PathAdaptRequest();
            request.setPathId(pathId);
            request.setAdaptStrategy("SHORTEN");

            pathService.adaptPath(request);

            verify(learningPathNodeRepository).saveAll(anyList());
        }

        @Test
        @DisplayName("EXPAND 策略 — 调用成功")
        void adaptExpand() {
            when(learningPathRepository.findById(pathId)).thenReturn(Optional.of(mockPath));
            when(learningPathNodeRepository.findByLearningPathIdOrderByOrderIndexAsc(pathId))
                    .thenReturn(List.of(mockNode1, mockNode2));

            PathAdaptRequest request = new PathAdaptRequest();
            request.setPathId(pathId);
            request.setAdaptStrategy("EXPAND");

            LearningPathDto result = pathService.adaptPath(request);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("不支持的策略 — 应抛出 ValidationException")
        void unsupportedStrategy() {
            when(learningPathRepository.findById(pathId)).thenReturn(Optional.of(mockPath));
            when(learningPathNodeRepository.findByLearningPathIdOrderByOrderIndexAsc(pathId))
                    .thenReturn(List.of());

            PathAdaptRequest request = new PathAdaptRequest();
            request.setPathId(pathId);
            request.setAdaptStrategy("INVALID");

            assertThatThrownBy(() -> pathService.adaptPath(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("不支持的适配策略");
        }

        @Test
        @DisplayName("不存在路径 — 应抛出 ResourceNotFoundException")
        void adaptNonExistentPath() {
            when(learningPathRepository.findById(pathId)).thenReturn(Optional.empty());

            PathAdaptRequest request = new PathAdaptRequest();
            request.setPathId(pathId);
            request.setAdaptStrategy("REORDER");

            assertThatThrownBy(() -> pathService.adaptPath(request))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
