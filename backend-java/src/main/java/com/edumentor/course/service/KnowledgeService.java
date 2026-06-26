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
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识管理服务 — 提供课程、知识点、知识点关系的业务逻辑操作。
 *
 * <h3>核心功能</h3>
 * <ul>
 *   <li><b>课程管理</b>：课程 CRUD、发布/下架、按学科/名称搜索</li>
 *   <li><b>知识点管理</b>：知识点 CRUD、树形结构查询、批量操作</li>
 *   <li><b>知识点关系</b>：关系 CRUD、前置依赖查询</li>
 *   <li><b>知识图谱</b>：构建课程的知识图谱结构数据</li>
 * </ul>
 *
 * @author EduMentor Team
 * @version 1.0
 */
@Service
public class KnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeService.class);

    private final CourseRepository courseRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final KnowledgeRelationRepository knowledgeRelationRepository;

    public KnowledgeService(CourseRepository courseRepository,
                            KnowledgePointRepository knowledgePointRepository,
                            KnowledgeRelationRepository knowledgeRelationRepository) {
        this.courseRepository = courseRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.knowledgeRelationRepository = knowledgeRelationRepository;
    }

    // ═══════════════════════════════════════════════════════════════
    //  课程管理 (Course Management)
    // ═══════════════════════════════════════════════════════════════

    /**
     * 创建新课程。
     *
     * @param request 创建课程请求
     * @param userId  创建人 ID（当前登录用户）
     * @return 课程 DTO
     * @throws DuplicateResourceException 如果课程名已存在
     */
    @Transactional
    public CourseDto createCourse(CourseCreateRequest request, UUID userId) {
        log.info("创建课程: name={}, courseCode={}, userId={}", request.getName(), request.getCourseCode(), userId);

        if (courseRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("课程名称", request.getName());
        }
        if (courseRepository.existsByCourseCode(request.getCourseCode())) {
            throw new DuplicateResourceException("课程编号", request.getCourseCode());
        }

        Course course = new Course();
        course.setCourseCode(request.getCourseCode());
        course.setName(request.getName());
        course.setDescription(request.getDescription());
        course.setSubject(request.getSubject());
        course.setGradeLevel(request.getGradeLevel());
        course.setCoverUrl(request.getCoverUrl());
        course.setIsPublished(false);
        course.setCreatedBy(userId);

        Course saved = courseRepository.save(course);
        log.info("课程创建成功: id={}, courseCode={}, name={}", saved.getId(), saved.getCourseCode(), saved.getName());
        return CourseDto.fromEntity(saved);
    }

    /**
     * 更新课程信息（部分更新）。
     *
     * @param id      课程 ID
     * @param request 更新请求（只更新非 null 字段）
     * @return 更新后的课程 DTO
     * @throws ResourceNotFoundException  如果课程不存在
     * @throws DuplicateResourceException  如果修改后的名称与其他课程冲突
     */
    @Transactional
    public CourseDto updateCourse(UUID id, CourseUpdateRequest request) {
        log.info("更新课程: id={}", id);

        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("课程", id));

        if (request.getCourseCode() != null) {
            if (!course.getCourseCode().equals(request.getCourseCode())
                    && courseRepository.existsByCourseCode(request.getCourseCode())) {
                throw new DuplicateResourceException("课程编号", request.getCourseCode());
            }
            course.setCourseCode(request.getCourseCode());
        }
        if (request.getName() != null) {
            if (!course.getName().equals(request.getName())
                    && courseRepository.existsByName(request.getName())) {
                throw new DuplicateResourceException("课程名称", request.getName());
            }
            course.setName(request.getName());
        }
        if (request.getDescription() != null) {
            course.setDescription(request.getDescription());
        }
        if (request.getSubject() != null) {
            course.setSubject(request.getSubject());
        }
        if (request.getGradeLevel() != null) {
            course.setGradeLevel(request.getGradeLevel());
        }
        if (request.getCoverUrl() != null) {
            course.setCoverUrl(request.getCoverUrl());
        }
        if (request.getIsPublished() != null) {
            course.setIsPublished(request.getIsPublished());
            log.info("课程发布状态变更: id={}, isPublished={}", id, request.getIsPublished());
        }

        Course saved = courseRepository.save(course);
        return CourseDto.fromEntity(saved);
    }

    /**
     * 获取课程详情。
     *
     * @param id 课程 ID
     * @return 课程 DTO
     * @throws ResourceNotFoundException 如果课程不存在
     */
    @Transactional(readOnly = true)
    public CourseDto getCourse(UUID id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("课程", id));
        return CourseDto.fromEntity(course);
    }

    /**
     * 按课程编号获取课程详情。
     *
     * @param courseCode 课程编号
     * @return 课程 DTO
     * @throws ResourceNotFoundException 如果课程不存在
     */
    @Transactional(readOnly = true)
    public CourseDto getCourseByCode(String courseCode) {
        Course course = courseRepository.findByCourseCode(courseCode)
                .orElseThrow(() -> new ResourceNotFoundException("课程", "编号 " + courseCode));
        return CourseDto.fromEntity(course);
    }

    /**
     * 判断课程编号是否已存在。
     *
     * @param courseCode 课程编号
     * @return true 表示已存在
     */
    @Transactional(readOnly = true)
    public boolean existsByCourseCode(String courseCode) {
        return courseRepository.existsByCourseCode(courseCode);
    }

    /**
     * 获取教师创建的课程列表。
     *
     * @param teacherId 教师用户 ID
     * @return 课程 DTO 列表
     */
    @Transactional(readOnly = true)
    public List<CourseDto> listCoursesByTeacher(UUID teacherId) {
        return courseRepository.findByCreatedByOrderByCreatedAtDesc(teacherId)
                .stream()
                .map(CourseDto::fromEntity)
                .toList();
    }

    /**
     * 分页查询课程列表。
     *
     * @param page          页码（从 1 开始）
     * @param size          每页数量
     * @param subject       学科筛选（可选）
     * @param keyword       名称关键字搜索（可选）
     * @param publishedOnly 是否只查已发布课程
     * @return 分页课程 DTO
     */
    @Transactional(readOnly = true)
    public Page<CourseDto> listCourses(int page, int size, String subject,
                                       String keyword, boolean publishedOnly) {
        Pageable pageable = PageRequest.of(page - 1, size,
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<Course> spec = Specification.where(null);

        if (keyword != null && !keyword.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.like(cb.lower(root.get("name")), "%" + keyword.toLowerCase() + "%"));
        }

        if (subject != null && !subject.isBlank()) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("subject"), subject));
        }

        if (publishedOnly) {
            spec = spec.and((root, query, cb) ->
                    cb.isTrue(root.get("isPublished")));
        }

        Page<Course> coursePage = courseRepository.findAll(spec, pageable);
        return coursePage.map(CourseDto::fromEntity);
    }

    /**
     * 删除课程。
     *
     * @param id 课程 ID
     * @throws ResourceNotFoundException 如果课程不存在
     */
    @Transactional
    public void deleteCourse(UUID id) {
        log.info("删除课程: id={}", id);
        if (!courseRepository.existsById(id)) {
            throw new ResourceNotFoundException("课程", id);
        }
        courseRepository.deleteById(id);
        log.info("课程已删除: id={}", id);
    }

    /**
     * 发布/下架课程。
     *
     * @param id        课程 ID
     * @param published 发布状态
     * @return 更新后的课程 DTO
     * @throws ResourceNotFoundException 如果课程不存在
     */
    @Transactional
    public CourseDto publishCourse(UUID id, boolean published) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("课程", id));
        course.setIsPublished(published);
        Course saved = courseRepository.save(course);
        log.info("课程{}: id={}", published ? "发布" : "下架", id);
        return CourseDto.fromEntity(saved);
    }

    /**
     * 统计指定用户创建的课程数量。
     *
     * @param userId 用户 ID
     * @return 课程数量
     */
    @Transactional(readOnly = true)
    public long countCoursesByUser(UUID userId) {
        return courseRepository.countByCreatedBy(userId);
    }

    // ═══════════════════════════════════════════════════════════════
    //  知识点管理 (Knowledge Point Management)
    // ═══════════════════════════════════════════════════════════════

    /**
     * 创建知识点。
     *
     * @param request 创建知识点请求
     * @return 知识点 DTO
     * @throws ResourceNotFoundException  如果课程不存在或父知识点不存在
     * @throws DuplicateResourceException 如果同一课程下存在同名知识点
     */
    @Transactional
    public KnowledgePointDto createKnowledgePoint(KnowledgePointCreateRequest request) {
        log.info("创建知识点: name={}, courseId={}", request.getName(), request.getCourseId());

        // 校验课程存在
        if (!courseRepository.existsById(request.getCourseId())) {
            throw new ResourceNotFoundException("课程", request.getCourseId());
        }

        // 校验父知识点存在且属于同一课程
        if (request.getParentKpId() != null) {
            KnowledgePoint parent = knowledgePointRepository.findById(request.getParentKpId())
                    .orElseThrow(() -> new ResourceNotFoundException("父知识点", request.getParentKpId()));
            if (!parent.getCourseId().equals(request.getCourseId())) {
                throw new ValidationException("父知识点必须属于同一课程");
            }
        }

        KnowledgePoint kp = new KnowledgePoint();
        kp.setCourseId(request.getCourseId());
        kp.setParentKpId(request.getParentKpId());
        kp.setName(request.getName());
        kp.setDescription(request.getDescription());
        kp.setContent(request.getContent());
        kp.setDifficulty(request.getDifficulty() != null ? request.getDifficulty() : 3);
        kp.setImportance(request.getImportance() != null ? request.getImportance() : 3);
        kp.setSubject(request.getSubject());
        kp.setTags(request.getTags() != null ? request.getTags() : "[]");
        kp.setOrderIndex(request.getOrderIndex() != null ? request.getOrderIndex() : 0);

        KnowledgePoint saved = knowledgePointRepository.save(kp);
        log.info("知识点创建成功: id={}, name={}", saved.getId(), saved.getName());
        return KnowledgePointDto.fromEntity(saved);
    }

    /**
     * 更新知识点（部分更新）。
     *
     * @param id      知识点 ID
     * @param request 更新请求
     * @return 更新后的知识点 DTO
     * @throws ResourceNotFoundException 如果知识点不存在
     * @throws ValidationException       如果父节点校验失败
     */
    @Transactional
    public KnowledgePointDto updateKnowledgePoint(UUID id, KnowledgePointUpdateRequest request) {
        log.info("更新知识点: id={}", id);

        KnowledgePoint kp = knowledgePointRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("知识点", id));

        if (request.getName() != null) {
            kp.setName(request.getName());
        }
        if (request.getDescription() != null) {
            kp.setDescription(request.getDescription());
        }
        if (request.getContent() != null) {
            kp.setContent(request.getContent());
        }
        if (request.getDifficulty() != null) {
            kp.setDifficulty(request.getDifficulty());
        }
        if (request.getImportance() != null) {
            kp.setImportance(request.getImportance());
        }
        if (request.getSubject() != null) {
            kp.setSubject(request.getSubject());
        }
        if (request.getTags() != null) {
            kp.setTags(request.getTags());
        }
        if (request.getOrderIndex() != null) {
            kp.setOrderIndex(request.getOrderIndex());
        }
        if (request.getParentKpId() != null) {
            // 校验不能将自己设为父节点
            if (request.getParentKpId().equals(id)) {
                throw new ValidationException("不能将知识点自身设为父节点");
            }
            if (!knowledgePointRepository.existsById(request.getParentKpId())) {
                throw new ResourceNotFoundException("父知识点", request.getParentKpId());
            }
            kp.setParentKpId(request.getParentKpId());
        }

        KnowledgePoint saved = knowledgePointRepository.save(kp);
        return KnowledgePointDto.fromEntity(saved);
    }

    /**
     * 获取知识点详情。
     *
     * @param id 知识点 ID
     * @return 知识点 DTO
     * @throws ResourceNotFoundException 如果知识点不存在
     */
    @Transactional(readOnly = true)
    public KnowledgePointDto getKnowledgePoint(UUID id) {
        KnowledgePoint kp = knowledgePointRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("知识点", id));
        return KnowledgePointDto.fromEntity(kp);
    }

    /**
     * 按课程 ID 查询知识点列表（按排序序号升序）。
     *
     * @param courseId 课程 ID
     * @return 知识点 DTO 列表
     */
    @Transactional(readOnly = true)
    public List<KnowledgePointDto> listKnowledgePointsByCourse(UUID courseId) {
        return knowledgePointRepository.findByCourseIdOrderByOrderIndexAsc(courseId)
                .stream()
                .map(KnowledgePointDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 查询指定课程的知识点树形结构。
     * <p>
     * 返回按父子关系组织的扁平列表，每项包含 level 层级信息。
     * </p>
     *
     * @param courseId 课程 ID
     * @return 带层级的知识点树形列表
     */
    @Transactional(readOnly = true)
    public List<KnowledgePointTreeNode> getKnowledgePointTree(UUID courseId) {
        List<KnowledgePoint> allKps = knowledgePointRepository
                .findByCourseIdOrderByOrderIndexAsc(courseId);
        Map<UUID, List<KnowledgePoint>> childrenMap = new HashMap<>();

        // 构建父子映射
        for (KnowledgePoint kp : allKps) {
            if (kp.getParentKpId() != null) {
                childrenMap.computeIfAbsent(kp.getParentKpId(), k -> new ArrayList<>()).add(kp);
            }
        }

        // 计算每个节点的层级（BFS 广度优先遍历）
        Map<UUID, Integer> levelMap = new HashMap<>();
        List<KnowledgePoint> roots = allKps.stream()
                .filter(kp -> kp.getParentKpId() == null)
                .collect(Collectors.toList());

        Deque<KnowledgePoint> queue = new ArrayDeque<>(roots);
        for (KnowledgePoint root : roots) {
            levelMap.put(root.getId(), 0);
        }
        while (!queue.isEmpty()) {
            KnowledgePoint current = queue.poll();
            int currentLevel = levelMap.get(current.getId());
            List<KnowledgePoint> children = childrenMap
                    .getOrDefault(current.getId(), Collections.emptyList());
            for (KnowledgePoint child : children) {
                levelMap.put(child.getId(), currentLevel + 1);
                queue.add(child);
            }
        }

        // 构建结果列表
        return allKps.stream()
                .map(kp -> {
                    int level = levelMap.getOrDefault(kp.getId(), 0);
                    boolean hasChild = childrenMap.containsKey(kp.getId())
                            && !childrenMap.get(kp.getId()).isEmpty();
                    return new KnowledgePointTreeNode(
                            KnowledgePointDto.fromEntity(kp), level, hasChild);
                })
                .collect(Collectors.toList());
    }

    /**
     * 删除知识点。
     *
     * @param id 知识点 ID
     * @throws ResourceNotFoundException 如果知识点不存在
     * @throws ValidationException       如果知识点有子节点，禁止删除
     */
    @Transactional
    public void deleteKnowledgePoint(UUID id) {
        log.info("删除知识点: id={}", id);

        if (!knowledgePointRepository.existsById(id)) {
            throw new ResourceNotFoundException("知识点", id);
        }

        // 检查是否有子节点
        List<KnowledgePoint> children = knowledgePointRepository.findByParentKpId(id);
        if (!children.isEmpty()) {
            throw new ValidationException("知识点包含子节点，请先删除子节点后再删除本知识点");
        }

        // 删除关联的所有关系（作为源或目标）
        List<KnowledgeRelation> relations = knowledgeRelationRepository
                .findBySourceKpIdOrTargetKpId(id, id);
        knowledgeRelationRepository.deleteAll(relations);

        knowledgePointRepository.deleteById(id);
        log.info("知识点已删除: id={}", id);
    }

    /**
     * 批量查询知识点详情。
     *
     * @param ids 知识点 ID 列表
     * @return 知识点 DTO 列表
     */
    @Transactional(readOnly = true)
    public List<KnowledgePointDto> getKnowledgePointsByIds(List<UUID> ids) {
        List<KnowledgePoint> points = knowledgePointRepository.findByIdIn(ids);
        return points.stream()
                .map(KnowledgePointDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 统计指定课程的知识点数量。
     *
     * @param courseId 课程 ID
     * @return 知识点数量
     */
    @Transactional(readOnly = true)
    public long countKnowledgePointsByCourse(UUID courseId) {
        return knowledgePointRepository.countByCourseId(courseId);
    }

    // ═══════════════════════════════════════════════════════════════
    //  知识点关系管理 (Knowledge Relation Management)
    // ═══════════════════════════════════════════════════════════════

    /**
     * 创建知识点关系。
     *
     * @param request 创建关系请求
     * @return 关系 DTO
     * @throws ResourceNotFoundException  如果知识点不存在
     * @throws DuplicateResourceException 如果关系已存在
     * @throws ValidationException        如果源和目标知识点相同
     */
    @Transactional
    public KnowledgeRelationDto createRelation(KnowledgeRelationCreateRequest request) {
        log.info("创建知识点关系: source={}, target={}, type={}",
                request.getSourceKpId(), request.getTargetKpId(), request.getRelationType());

        // 自引用校验
        if (request.getSourceKpId().equals(request.getTargetKpId())) {
            throw new ValidationException("源知识点和目标知识点不能相同");
        }

        // 校验知识点存在
        if (!knowledgePointRepository.existsById(request.getSourceKpId())) {
            throw new ResourceNotFoundException("源知识点", request.getSourceKpId());
        }
        if (!knowledgePointRepository.existsById(request.getTargetKpId())) {
            throw new ResourceNotFoundException("目标知识点", request.getTargetKpId());
        }

        // 校验关系唯一性
        if (knowledgeRelationRepository.existsBySourceKpIdAndTargetKpIdAndRelationType(
                request.getSourceKpId(), request.getTargetKpId(), request.getRelationType())) {
            throw new DuplicateResourceException("知识点关系",
                    String.format("%s → %s [%s]",
                            request.getSourceKpId(), request.getTargetKpId(), request.getRelationType()));
        }

        KnowledgeRelation relation = new KnowledgeRelation();
        relation.setSourceKpId(request.getSourceKpId());
        relation.setTargetKpId(request.getTargetKpId());
        relation.setRelationType(request.getRelationType());
        relation.setWeight(request.getWeight() != null
                ? request.getWeight() : BigDecimal.ONE);
        relation.setDescription(request.getDescription());

        KnowledgeRelation saved = knowledgeRelationRepository.save(relation);
        return KnowledgeRelationDto.fromEntity(saved);
    }

    /**
     * 删除知识点关系。
     *
     * @param id 关系 ID
     * @throws ResourceNotFoundException 如果关系不存在
     */
    @Transactional
    public void deleteRelation(UUID id) {
        log.info("删除知识点关系: id={}", id);
        if (!knowledgeRelationRepository.existsById(id)) {
            throw new ResourceNotFoundException("知识点关系", id);
        }
        knowledgeRelationRepository.deleteById(id);
    }

    /**
     * 查询指定知识点的所有关系（作为源或目标）。
     *
     * @param knowledgePointId 知识点 ID
     * @return 关系 DTO 列表
     */
    @Transactional(readOnly = true)
    public List<KnowledgeRelationDto> getRelationsForKnowledgePoint(UUID knowledgePointId) {
        List<KnowledgeRelation> relations = knowledgeRelationRepository
                .findBySourceKpIdOrTargetKpId(knowledgePointId, knowledgePointId);
        return relations.stream()
                .map(KnowledgeRelationDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 查询指定知识点的前置依赖关系。
     *
     * @param knowledgePointId 知识点 ID
     * @return 前置依赖关系 DTO 列表
     */
    @Transactional(readOnly = true)
    public List<KnowledgeRelationDto> getPrerequisites(UUID knowledgePointId) {
        List<KnowledgeRelation> prerequisites = knowledgeRelationRepository
                .findByTargetKpId(knowledgePointId)
                .stream()
                .filter(r -> r.getRelationType() == RelationType.PREREQUISITE)
                .collect(Collectors.toList());

        // 同时查找源知识点是该知识点的 PREREQUISITE 关系（反向）
        List<KnowledgeRelation> reversePrerequisites = knowledgeRelationRepository
                .findBySourceKpId(knowledgePointId)
                .stream()
                .filter(r -> r.getRelationType() == RelationType.PREREQUISITE)
                .collect(Collectors.toList());

        List<KnowledgeRelation> all = new ArrayList<>();
        all.addAll(prerequisites);
        all.addAll(reversePrerequisites);
        return all.stream()
                .map(KnowledgeRelationDto::fromEntity)
                .collect(Collectors.toList());
    }

    /**
     * 查询两个知识点之间的指定类型关系是否存在。
     *
     * @param sourceKpId   源知识点 ID
     * @param targetKpId   目标知识点 ID
     * @param relationType 关系类型
     * @return 是否存在
     */
    @Transactional(readOnly = true)
    public boolean relationExists(UUID sourceKpId, UUID targetKpId, RelationType relationType) {
        return knowledgeRelationRepository
                .existsBySourceKpIdAndTargetKpIdAndRelationType(sourceKpId, targetKpId, relationType);
    }

    // ═══════════════════════════════════════════════════════════════
    //  知识图谱 (Knowledge Graph)
    // ═══════════════════════════════════════════════════════════════

    /**
     * 构建指定课程的知识图谱。
     * <p>
     * 返回包含所有知识点节点和关系边的图谱数据结构，
     * 用于前端知识图谱可视化。
     * </p>
     *
     * @param courseId 课程 ID
     * @return 知识图谱 DTO
     * @throws ResourceNotFoundException 如果课程不存在
     */
    @Transactional(readOnly = true)
    public KnowledgeGraphDto getKnowledgeGraph(UUID courseId) {
        log.info("构建知识图谱: courseId={}", courseId);

        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("课程", courseId);
        }

        // 查询所有知识点和关系
        List<KnowledgePoint> allKps = knowledgePointRepository
                .findByCourseIdOrderByOrderIndexAsc(courseId);
        List<KnowledgeRelation> allRelations = knowledgeRelationRepository
                .findAll(); // 全量查询，后续可优化为按课程过滤

        // 获取该课程下所有知识点 ID 集合
        Set<UUID> courseKpIds = allKps.stream()
                .map(KnowledgePoint::getId)
                .collect(Collectors.toSet());

        // 构建层级映射
        Map<UUID, Integer> levelMap = buildLevelMap(allKps);

        // 构建节点列表
        List<KnowledgeGraphDto.GraphNode> nodes = allKps.stream()
                .map(kp -> new KnowledgeGraphDto.GraphNode(
                        kp.getId(),
                        kp.getName(),
                        levelMap.getOrDefault(kp.getId(), 0),
                        kp.getDifficulty()
                ))
                .collect(Collectors.toList());

        // 构建边列表（只包含该课程知识点之间的关系）
        List<KnowledgeGraphDto.GraphEdge> edges = allRelations.stream()
                .filter(rel -> courseKpIds.contains(rel.getSourceKpId())
                        && courseKpIds.contains(rel.getTargetKpId()))
                .map(rel -> new KnowledgeGraphDto.GraphEdge(
                        rel.getSourceKpId(),
                        rel.getTargetKpId(),
                        rel.getRelationType().name(),
                        rel.getWeight().doubleValue()
                ))
                .collect(Collectors.toList());

        return new KnowledgeGraphDto(nodes, edges);
    }

    /**
     * 构建指定课程中知识点的完整关系图（包含前置依赖的拓扑排序信息）。
     *
     * @param courseId 课程 ID
     * @return 知识图谱 DTO
     */
    @Transactional(readOnly = true)
    public KnowledgeGraphDto getFullKnowledgeGraph(UUID courseId) {
        log.info("构建完整知识图谱: courseId={}", courseId);

        if (!courseRepository.existsById(courseId)) {
            throw new ResourceNotFoundException("课程", courseId);
        }

        // 查询所有知识点
        List<KnowledgePoint> allKps = knowledgePointRepository
                .findByCourseIdOrderByOrderIndexAsc(courseId);
        Set<UUID> courseKpIds = allKps.stream()
                .map(KnowledgePoint::getId)
                .collect(Collectors.toSet());

        // 查询该课程相关的所有关系（通过源知识点或目标知识点过滤）
        List<KnowledgeRelation> allRelations = knowledgeRelationRepository.findAll()
                .stream()
                .filter(rel -> courseKpIds.contains(rel.getSourceKpId())
                        || courseKpIds.contains(rel.getTargetKpId()))
                .collect(Collectors.toList());

        // 构建层级映射
        Map<UUID, Integer> levelMap = buildLevelMap(allKps);

        // 构建节点列表
        List<KnowledgeGraphDto.GraphNode> nodes = allKps.stream()
                .map(kp -> new KnowledgeGraphDto.GraphNode(
                        kp.getId(),
                        kp.getName(),
                        levelMap.getOrDefault(kp.getId(), 0),
                        kp.getDifficulty()
                ))
                .collect(Collectors.toList());

        // 构建边列表
        List<KnowledgeGraphDto.GraphEdge> edges = allRelations.stream()
                .map(rel -> new KnowledgeGraphDto.GraphEdge(
                        rel.getSourceKpId(),
                        rel.getTargetKpId(),
                        rel.getRelationType().name(),
                        rel.getWeight().doubleValue()
                ))
                .collect(Collectors.toList());

        return new KnowledgeGraphDto(nodes, edges);
    }

    // ═══════════════════════════════════════════════════════════════
    //  辅助方法 (Helper Methods)
    // ═══════════════════════════════════════════════════════════════

    /**
     * 辅助方法：计算知识点的树形层级深度。
     *
     * @param allKps 知识点列表
     * @return 知识点 ID 到层级的映射
     */
    private Map<UUID, Integer> buildLevelMap(List<KnowledgePoint> allKps) {
        Map<UUID, Integer> levelMap = new HashMap<>();
        Map<UUID, UUID> parentMap = new HashMap<>();

        for (KnowledgePoint kp : allKps) {
            if (kp.getParentKpId() != null) {
                parentMap.put(kp.getId(), kp.getParentKpId());
            }
        }

        // 使用缓存优化递归查找性能
        Map<UUID, Integer> cache = new HashMap<>();
        for (KnowledgePoint kp : allKps) {
            levelMap.put(kp.getId(), calculateLevel(kp.getId(), parentMap, cache));
        }
        return levelMap;
    }

    /**
     * 递归计算指定知识点的层级深度。
     *
     * @param kpId      知识点 ID
     * @param parentMap 父节点映射
     * @param cache     层级缓存
     * @return 层级深度（根节点为 0）
     */
    private int calculateLevel(UUID kpId, Map<UUID, UUID> parentMap,
                               Map<UUID, Integer> cache) {
        if (cache.containsKey(kpId)) {
            return cache.get(kpId);
        }
        UUID parentId = parentMap.get(kpId);
        if (parentId == null) {
            cache.put(kpId, 0);
            return 0;
        }
        int level = calculateLevel(parentId, parentMap, cache) + 1;
        cache.put(kpId, level);
        return level;
    }

    /**
     * 校验知识点是否属于指定课程。
     *
     * @param kpId     知识点 ID
     * @param courseId 课程 ID
     * @throws ResourceNotFoundException 如果知识点不存在
     * @throws ValidationException       如果知识点不属于该课程
     */
    public void validateKnowledgePointBelongsToCourse(UUID kpId, UUID courseId) {
        KnowledgePoint kp = knowledgePointRepository.findById(kpId)
                .orElseThrow(() -> new ResourceNotFoundException("知识点", kpId));
        if (!kp.getCourseId().equals(courseId)) {
            throw new ValidationException(
                    String.format("知识点 %s 不属于课程 %s", kpId, courseId));
        }
    }

    /**
     * 知识点树形节点（含层级信息）。
     *
     * @param knowledgePoint 知识点 DTO
     * @param level          层级深度（0 为顶层）
     * @param hasChild       是否有子节点
     */
    public record KnowledgePointTreeNode(
            KnowledgePointDto knowledgePoint,
            int level,
            boolean hasChild
    ) {}
}
