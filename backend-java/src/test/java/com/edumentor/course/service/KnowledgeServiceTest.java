package com.edumentor.course.service;

import com.edumentor.common.exception.DuplicateResourceException;
import com.edumentor.common.exception.ResourceNotFoundException;
import com.edumentor.common.exception.ValidationException;
import com.edumentor.course.dto.*;
import com.edumentor.course.entity.Course;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.entity.KnowledgeRelation;
import com.edumentor.course.entity.enums.RelationType;
import com.edumentor.course.repository.CourseRepository;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.course.repository.KnowledgeRelationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link KnowledgeService} 的单元测试。
 * <p>
 * 测试覆盖：课程 CRUD、知识点 CRUD（含树形结构）、知识点关系管理、知识图谱构建。
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KnowledgeService — 知识管理服务单元测试")
class KnowledgeServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private KnowledgePointRepository knowledgePointRepository;

    @Mock
    private KnowledgeRelationRepository knowledgeRelationRepository;

    @InjectMocks
    private KnowledgeService knowledgeService;

    @Captor
    private ArgumentCaptor<Course> courseCaptor;

    @Captor
    private ArgumentCaptor<KnowledgePoint> kpCaptor;

    private UUID userId;
    private UUID courseId;
    private UUID kpId;
    private UUID parentKpId;
    private UUID relationId;
    private Course mockCourse;
    private KnowledgePoint mockKp;
    private KnowledgePoint mockParentKp;
    private KnowledgeRelation mockRelation;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        courseId = UUID.randomUUID();
        kpId = UUID.randomUUID();
        parentKpId = UUID.randomUUID();
        relationId = UUID.randomUUID();

        mockCourse = new Course();
        mockCourse.setId(courseId);
        mockCourse.setName("高中数学");
        mockCourse.setDescription("高中数学课程");
        mockCourse.setSubject("数学");
        mockCourse.setGradeLevel("高中");
        mockCourse.setPublished(false);
        mockCourse.setCreatedBy(userId);
        mockCourse.setCreatedAt(OffsetDateTime.now());

        mockKp = new KnowledgePoint();
        mockKp.setId(kpId);
        mockKp.setCourseId(courseId);
        mockKp.setName("函数");
        mockKp.setDescription("函数基础知识");
        mockKp.setDifficulty(3);
        mockKp.setImportance(4);
        mockKp.setSubject("数学");
        mockKp.setTags("[\"基础\",\"核心\"]");
        mockKp.setOrderIndex(1);

        mockParentKp = new KnowledgePoint();
        mockParentKp.setId(parentKpId);
        mockParentKp.setCourseId(courseId);
        mockParentKp.setName("代数");
        mockParentKp.setDifficulty(2);
        mockParentKp.setImportance(3);
        mockParentKp.setOrderIndex(0);

        mockRelation = new KnowledgeRelation();
        mockRelation.setId(relationId);
        mockRelation.setSourceKpId(parentKpId);
        mockRelation.setTargetKpId(kpId);
        mockRelation.setRelationType(RelationType.PREREQUISITE);
        mockRelation.setWeight(new BigDecimal("1.00"));
    }

    // ═══════════════════════════════════════════════════════════════
    //  课程管理测试
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("课程管理 (Course Management)")
    class CourseManagementTests {

        @Test
        @DisplayName("创建课程 — 应返回创建后的 CourseDto")
        void createCourse() {
            CourseCreateRequest request = new CourseCreateRequest(
                    "高中数学", "高中数学课程", "数学", "高中", null);

            when(courseRepository.existsByName("高中数学")).thenReturn(false);
            when(courseRepository.save(any(Course.class))).thenAnswer(inv -> {
                Course c = inv.getArgument(0);
                c.setId(courseId);
                c.setCreatedAt(OffsetDateTime.now());
                return c;
            });

            CourseDto result = knowledgeService.createCourse(request, userId);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("高中数学");
            assertThat(result.getSubject()).isEqualTo("数学");
            assertThat(result.getIsPublished()).isFalse();

            verify(courseRepository).save(courseCaptor.capture());
            assertThat(courseCaptor.getValue().getCreatedBy()).isEqualTo(userId);
        }

        @Test
        @DisplayName("创建已存在的课程名 — 应抛出 DuplicateResourceException")
        void createDuplicateCourse() {
            CourseCreateRequest request = new CourseCreateRequest(
                    "高中数学", "desc", "数学", "高中", null);

            when(courseRepository.existsByName("高中数学")).thenReturn(true);

            assertThatThrownBy(() -> knowledgeService.createCourse(request, userId))
                    .isInstanceOf(DuplicateResourceException.class)
                    .hasMessageContaining("课程名称");
        }

        @Test
        @DisplayName("更新课程 — 部分字段更新")
        void updateCourse() {
            CourseUpdateRequest request = new CourseUpdateRequest(
                    "高等数学", "updated desc", null, null, null, null);

            when(courseRepository.findById(courseId)).thenReturn(Optional.of(mockCourse));
            when(courseRepository.existsByName("高等数学")).thenReturn(false);
            when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

            CourseDto result = knowledgeService.updateCourse(courseId, request);

            assertThat(result.getName()).isEqualTo("高等数学");
            assertThat(result.getDescription()).isEqualTo("updated desc");
        }

        @Test
        @DisplayName("更新不存在的课程 — 应抛出 ResourceNotFoundException")
        void updateNonExistentCourse() {
            CourseUpdateRequest request = new CourseUpdateRequest(
                    "新名称", null, null, null, null, null);

            when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> knowledgeService.updateCourse(courseId, request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("课程");
        }

        @Test
        @DisplayName("更新课程名与已有课程冲突 — 应抛出 DuplicateResourceException")
        void updateCourseNameConflict() {
            mockCourse.setName("原名称");
            CourseUpdateRequest request = new CourseUpdateRequest(
                    "已存在的名称", null, null, null, null, null);

            when(courseRepository.findById(courseId)).thenReturn(Optional.of(mockCourse));
            when(courseRepository.existsByName("已存在的名称")).thenReturn(true);

            assertThatThrownBy(() -> knowledgeService.updateCourse(courseId, request))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("获取课程详情 — 返回课程 DTO")
        void getCourse() {
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(mockCourse));

            CourseDto result = knowledgeService.getCourse(courseId);

            assertThat(result.getName()).isEqualTo("高中数学");
        }

        @Test
        @DisplayName("获取不存在的课程 — 应抛出 ResourceNotFoundException")
        void getNonExistentCourse() {
            when(courseRepository.findById(courseId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> knowledgeService.getCourse(courseId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("分页查询课程列表 — 按名称搜索")
        void listCoursesWithKeyword() {
            Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<Course> coursePage = new PageImpl<>(List.of(mockCourse));

            when(courseRepository.findByNameContainingIgnoreCase(eq("数学"), any(Pageable.class)))
                    .thenReturn(coursePage);

            Page<CourseDto> result = knowledgeService.listCourses(1, 10, null, "数学", false);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getName()).isEqualTo("高中数学");
        }

        @Test
        @DisplayName("分页查询课程列表 — 按学科筛选已发布课程")
        void listCoursesBySubjectPublished() {
            Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
            Page<Course> coursePage = new PageImpl<>(List.of(mockCourse));

            when(courseRepository.findBySubjectAndIsPublishedTrue(eq("数学"), any(Pageable.class)))
                    .thenReturn(coursePage);

            Page<CourseDto> result = knowledgeService.listCourses(1, 10, "数学", null, true);

            assertThat(result.getContent()).hasSize(1);
        }

        @Test
        @DisplayName("删除课程 — 成功删除")
        void deleteCourse() {
            when(courseRepository.existsById(courseId)).thenReturn(true);

            knowledgeService.deleteCourse(courseId);

            verify(courseRepository).deleteById(courseId);
        }

        @Test
        @DisplayName("删除不存在的课程 — 应抛出 ResourceNotFoundException")
        void deleteNonExistentCourse() {
            when(courseRepository.existsById(courseId)).thenReturn(false);

            assertThatThrownBy(() -> knowledgeService.deleteCourse(courseId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("发布课程 — 状态应为已发布")
        void publishCourse() {
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(mockCourse));
            when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

            CourseDto result = knowledgeService.publishCourse(courseId, true);

            assertThat(result.getIsPublished()).isTrue();
        }

        @Test
        @DisplayName("下架课程 — 状态应为未发布")
        void unpublishCourse() {
            mockCourse.setPublished(true);
            when(courseRepository.findById(courseId)).thenReturn(Optional.of(mockCourse));
            when(courseRepository.save(any(Course.class))).thenAnswer(inv -> inv.getArgument(0));

            CourseDto result = knowledgeService.publishCourse(courseId, false);

            assertThat(result.getIsPublished()).isFalse();
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  知识点管理测试
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("知识点管理 (Knowledge Point Management)")
    class KnowledgePointManagementTests {

        @Test
        @DisplayName("创建知识点 — 应返回创建后的 KnowledgePointDto")
        void createKnowledgePoint() {
            KnowledgePointCreateRequest request = new KnowledgePointCreateRequest(
                    courseId, null, "二次函数", "desc", null, 3, 4, "数学",
                    "[\"重点\"]", 2);

            when(courseRepository.existsById(courseId)).thenReturn(true);
            when(knowledgePointRepository.existsByCourseIdAndName(courseId, "二次函数")).thenReturn(false);
            when(knowledgePointRepository.save(any(KnowledgePoint.class))).thenAnswer(inv -> {
                KnowledgePoint kp = inv.getArgument(0);
                kp.setId(UUID.randomUUID());
                return kp;
            });

            KnowledgePointDto result = knowledgeService.createKnowledgePoint(request);

            assertThat(result.getName()).isEqualTo("二次函数");
            assertThat(result.getDifficulty()).isEqualTo(3);

            verify(knowledgePointRepository).save(kpCaptor.capture());
            assertThat(kpCaptor.getValue().getCourseId()).isEqualTo(courseId);
        }

        @Test
        @DisplayName("创建知识点 — 带父知识点")
        void createKnowledgePointWithParent() {
            KnowledgePointCreateRequest request = new KnowledgePointCreateRequest(
                    courseId, parentKpId, "一元二次方程", null, null, 3, 3, null, null, null);

            when(courseRepository.existsById(courseId)).thenReturn(true);
            when(knowledgePointRepository.existsById(parentKpId)).thenReturn(true);
            when(knowledgePointRepository.findById(parentKpId)).thenReturn(Optional.of(mockParentKp));
            when(knowledgePointRepository.existsByCourseIdAndName(courseId, "一元二次方程")).thenReturn(false);
            when(knowledgePointRepository.save(any(KnowledgePoint.class))).thenAnswer(inv -> {
                KnowledgePoint kp = inv.getArgument(0);
                kp.setId(kpId);
                return kp;
            });

            KnowledgePointDto result = knowledgeService.createKnowledgePoint(request);

            assertThat(result).isNotNull();
            verify(knowledgePointRepository).save(kpCaptor.capture());
            assertThat(kpCaptor.getValue().getParentKpId()).isEqualTo(parentKpId);
        }

        @Test
        @DisplayName("创建知识点 — 父知识点与课程不一致 — 应抛出 ValidationException")
        void createKpWithWrongCourseParent() {
            UUID otherCourseId = UUID.randomUUID();
            KnowledgePoint otherCourseParent = new KnowledgePoint();
            otherCourseParent.setId(parentKpId);
            otherCourseParent.setCourseId(otherCourseId);

            KnowledgePointCreateRequest request = new KnowledgePointCreateRequest(
                    courseId, parentKpId, "新知识点", null, null, 3, 3, null, null, null);

            when(courseRepository.existsById(courseId)).thenReturn(true);
            when(knowledgePointRepository.existsById(parentKpId)).thenReturn(true);
            when(knowledgePointRepository.findById(parentKpId)).thenReturn(Optional.of(otherCourseParent));

            assertThatThrownBy(() -> knowledgeService.createKnowledgePoint(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("父知识点必须属于同一课程");
        }

        @Test
        @DisplayName("创建知识点 — 课程不存在 — 应抛出 ResourceNotFoundException")
        void createKpWithNonExistentCourse() {
            KnowledgePointCreateRequest request = new KnowledgePointCreateRequest(
                    courseId, null, "新知识点", null, null, 3, 3, null, null, null);

            when(courseRepository.existsById(courseId)).thenReturn(false);

            assertThatThrownBy(() -> knowledgeService.createKnowledgePoint(request))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("课程");
        }

        @Test
        @DisplayName("创建知识点 — 同名课程下知识点重复 — 应抛出 DuplicateResourceException")
        void createDuplicateKpName() {
            KnowledgePointCreateRequest request = new KnowledgePointCreateRequest(
                    courseId, null, "函数", null, null, 3, 3, null, null, null);

            when(courseRepository.existsById(courseId)).thenReturn(true);
            when(knowledgePointRepository.existsByCourseIdAndName(courseId, "函数")).thenReturn(true);

            assertThatThrownBy(() -> knowledgeService.createKnowledgePoint(request))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("更新知识点 — 部分字段更新")
        void updateKnowledgePoint() {
            KnowledgePointUpdateRequest request = new KnowledgePointUpdateRequest(
                    "高级函数", "updated desc", null, null, null, null, null, null, null);

            when(knowledgePointRepository.findById(kpId)).thenReturn(Optional.of(mockKp));
            when(knowledgePointRepository.save(any(KnowledgePoint.class))).thenAnswer(inv -> inv.getArgument(0));

            KnowledgePointDto result = knowledgeService.updateKnowledgePoint(kpId, request);

            assertThat(result.getName()).isEqualTo("高级函数");
        }

        @Test
        @DisplayName("更新知识点 — 将自己设为父节点 — 应抛出 ValidationException")
        void updateKpSetSelfAsParent() {
            KnowledgePointUpdateRequest request = new KnowledgePointUpdateRequest(
                    null, null, null, null, null, null, null, null, kpId);

            when(knowledgePointRepository.findById(kpId)).thenReturn(Optional.of(mockKp));

            assertThatThrownBy(() -> knowledgeService.updateKnowledgePoint(kpId, request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("不能将知识点自身设为父节点");
        }

        @Test
        @DisplayName("获取知识点详情")
        void getKnowledgePoint() {
            when(knowledgePointRepository.findById(kpId)).thenReturn(Optional.of(mockKp));

            KnowledgePointDto result = knowledgeService.getKnowledgePoint(kpId);

            assertThat(result.getName()).isEqualTo("函数");
        }

        @Test
        @DisplayName("按课程查询知识点列表")
        void listKpsByCourse() {
            when(knowledgePointRepository.findByCourseIdOrderByOrderIndexAsc(courseId))
                    .thenReturn(List.of(mockKp));

            List<KnowledgePointDto> result = knowledgeService.listKnowledgePointsByCourse(courseId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getName()).isEqualTo("函数");
        }

        @Test
        @DisplayName("获取知识点树形结构 — 含父子关系")
        void getKnowledgePointTree() {
            KnowledgePoint parent = new KnowledgePoint();
            parent.setId(parentKpId);
            parent.setCourseId(courseId);
            parent.setName("代数");
            parent.setOrderIndex(0);
            parent.setDifficulty(2);
            parent.setImportance(3);

            KnowledgePoint child = new KnowledgePoint();
            child.setId(kpId);
            child.setCourseId(courseId);
            child.setName("函数");
            child.setParentKpId(parentKpId);
            child.setOrderIndex(1);
            child.setDifficulty(3);
            child.setImportance(4);

            when(knowledgePointRepository.findByCourseIdOrderByOrderIndexAsc(courseId))
                    .thenReturn(List.of(parent, child));

            List<KnowledgeService.KnowledgePointTreeNode> tree = knowledgeService.getKnowledgePointTree(courseId);

            assertThat(tree).hasSize(2);
            // Parent should be level 0
            assertThat(tree.get(0).level()).isEqualTo(0);
            // Child should be level 1
            assertThat(tree.get(1).level()).isEqualTo(1);
            assertThat(tree.get(1).hasChild()).isFalse();
        }

        @Test
        @DisplayName("获取空课程的知识点树形结构 — 应返回空列表")
        void getKnowledgePointTreeEmpty() {
            when(knowledgePointRepository.findByCourseIdOrderByOrderIndexAsc(courseId))
                    .thenReturn(List.of());

            List<KnowledgeService.KnowledgePointTreeNode> tree = knowledgeService.getKnowledgePointTree(courseId);

            assertThat(tree).isEmpty();
        }

        @Test
        @DisplayName("删除知识点 — 无子节点时成功删除")
        void deleteKpWithoutChildren() {
            when(knowledgePointRepository.existsById(kpId)).thenReturn(true);
            when(knowledgePointRepository.findByParentKpIdOrderByOrderIndexAsc(kpId))
                    .thenReturn(List.of());

            knowledgeService.deleteKnowledgePoint(kpId);

            verify(knowledgePointRepository).deleteById(kpId);
            verify(knowledgeRelationRepository).deleteBySourceKpIdOrTargetKpId(kpId, kpId);
        }

        @Test
        @DisplayName("删除知识点 — 有子节点时抛出 ValidationException")
        void deleteKpWithChildren() {
            KnowledgePoint child = new KnowledgePoint();
            child.setId(UUID.randomUUID());
            child.setParentKpId(kpId);

            when(knowledgePointRepository.existsById(kpId)).thenReturn(true);
            when(knowledgePointRepository.findByParentKpIdOrderByOrderIndexAsc(kpId))
                    .thenReturn(List.of(child));

            assertThatThrownBy(() -> knowledgeService.deleteKnowledgePoint(kpId))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("子节点");

            verify(knowledgePointRepository, never()).deleteById(any());
        }

        @Test
        @DisplayName("批量查询知识点")
        void getKpsByIds() {
            when(knowledgePointRepository.findAllById(List.of(kpId)))
                    .thenReturn(List.of(mockKp));

            List<KnowledgePointDto> result = knowledgeService.getKnowledgePointsByIds(List.of(kpId));

            assertThat(result).hasSize(1);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  知识点关系管理测试
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("知识点关系管理 (Knowledge Relation Management)")
    class RelationManagementTests {

        @Test
        @DisplayName("创建知识点关系 — 成功创建")
        void createRelation() {
            KnowledgeRelationCreateRequest request = new KnowledgeRelationCreateRequest(
                    parentKpId, kpId, RelationType.PREREQUISITE, new BigDecimal("1.00"), "前置关系");

            when(knowledgePointRepository.existsById(parentKpId)).thenReturn(true);
            when(knowledgePointRepository.existsById(kpId)).thenReturn(true);
            when(knowledgeRelationRepository
                    .existsBySourceKpIdAndTargetKpIdAndRelationType(parentKpId, kpId, RelationType.PREREQUISITE))
                    .thenReturn(false);
            when(knowledgeRelationRepository.save(any(KnowledgeRelation.class)))
                    .thenReturn(mockRelation);

            KnowledgeRelationDto result = knowledgeService.createRelation(request);

            assertThat(result.getSourceKpId()).isEqualTo(parentKpId);
            assertThat(result.getTargetKpId()).isEqualTo(kpId);
            assertThat(result.getRelationType()).isEqualTo(RelationType.PREREQUISITE);
        }

        @Test
        @DisplayName("创建自引用关系 — 应抛出 ValidationException")
        void createSelfReferencingRelation() {
            KnowledgeRelationCreateRequest request = new KnowledgeRelationCreateRequest(
                    kpId, kpId, RelationType.PREREQUISITE, null, null);

            assertThatThrownBy(() -> knowledgeService.createRelation(request))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("不能相同");
        }

        @Test
        @DisplayName("创建重复关系 — 应抛出 DuplicateResourceException")
        void createDuplicateRelation() {
            KnowledgeRelationCreateRequest request = new KnowledgeRelationCreateRequest(
                    parentKpId, kpId, RelationType.PREREQUISITE, null, null);

            when(knowledgePointRepository.existsById(parentKpId)).thenReturn(true);
            when(knowledgePointRepository.existsById(kpId)).thenReturn(true);
            when(knowledgeRelationRepository
                    .existsBySourceKpIdAndTargetKpIdAndRelationType(parentKpId, kpId, RelationType.PREREQUISITE))
                    .thenReturn(true);

            assertThatThrownBy(() -> knowledgeService.createRelation(request))
                    .isInstanceOf(DuplicateResourceException.class);
        }

        @Test
        @DisplayName("删除知识点关系")
        void deleteRelation() {
            when(knowledgeRelationRepository.existsById(relationId)).thenReturn(true);

            knowledgeService.deleteRelation(relationId);

            verify(knowledgeRelationRepository).deleteById(relationId);
        }

        @Test
        @DisplayName("删除不存在的知识点关系 — 应抛出 ResourceNotFoundException")
        void deleteNonExistentRelation() {
            when(knowledgeRelationRepository.existsById(relationId)).thenReturn(false);

            assertThatThrownBy(() -> knowledgeService.deleteRelation(relationId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("查询知识点的所有关系（作为源和目标）")
        void getRelationsForKnowledgePoint() {
            when(knowledgeRelationRepository.findBySourceKpId(kpId))
                    .thenReturn(List.of(mockRelation));
            when(knowledgeRelationRepository.findByTargetKpId(kpId))
                    .thenReturn(List.of());

            List<KnowledgeRelationDto> result = knowledgeService.getRelationsForKnowledgePoint(kpId);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("查询前置依赖关系")
        void getPrerequisites() {
            when(knowledgeRelationRepository.findByTargetKpIdAndRelationType(
                    kpId, RelationType.PREREQUISITE))
                    .thenReturn(List.of(mockRelation));

            List<KnowledgeRelationDto> result = knowledgeService.getPrerequisites(kpId);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getSourceKpId()).isEqualTo(parentKpId);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    //  知识图谱测试
    // ═══════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("知识图谱 (Knowledge Graph)")
    class KnowledgeGraphTests {

        @Test
        @DisplayName("构建知识图谱 — 返回节点和边")
        void getKnowledgeGraph() {
            when(courseRepository.existsById(courseId)).thenReturn(true);
            when(knowledgePointRepository.findByCourseIdOrderByOrderIndexAsc(courseId))
                    .thenReturn(List.of(mockKp));
            when(knowledgeRelationRepository.findByCourseId(courseId))
                    .thenReturn(List.of(mockRelation));

            KnowledgeGraphDto result = knowledgeService.getKnowledgeGraph(courseId);

            assertThat(result.nodes()).hasSize(1);
            assertThat(result.edges()).hasSize(1);
            assertThat(result.nodes().get(0).name()).isEqualTo("函数");
            assertThat(result.edges().get(0).sourceId()).isEqualTo(parentKpId);
            assertThat(result.edges().get(0).targetId()).isEqualTo(kpId);
        }

        @Test
        @DisplayName("构建不存在的课程的知识图谱 — 应抛出 ResourceNotFoundException")
        void getKnowledgeGraphForNonExistentCourse() {
            when(courseRepository.existsById(courseId)).thenReturn(false);

            assertThatThrownBy(() -> knowledgeService.getKnowledgeGraph(courseId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("课程");
        }

        @Test
        @DisplayName("构建空课程的知识图谱 — 返回空节点和边")
        void getKnowledgeGraphForEmptyCourse() {
            when(courseRepository.existsById(courseId)).thenReturn(true);
            when(knowledgePointRepository.findByCourseIdOrderByOrderIndexAsc(courseId))
                    .thenReturn(List.of());
            when(knowledgeRelationRepository.findByCourseId(courseId))
                    .thenReturn(List.of());

            KnowledgeGraphDto result = knowledgeService.getKnowledgeGraph(courseId);

            assertThat(result.nodes()).isEmpty();
            assertThat(result.edges()).isEmpty();
        }
    }
}
