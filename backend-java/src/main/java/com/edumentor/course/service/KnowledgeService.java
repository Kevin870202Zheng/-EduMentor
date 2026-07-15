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
import com.edumentor.engine.llm.LLMService;
import com.edumentor.engine.llm.LLMResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private final LLMService llmService;
    private final ObjectMapper objectMapper;

    public KnowledgeService(CourseRepository courseRepository,
                            KnowledgePointRepository knowledgePointRepository,
                            KnowledgeRelationRepository knowledgeRelationRepository,
                            LLMService llmService,
                            ObjectMapper objectMapper) {
        this.courseRepository = courseRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.knowledgeRelationRepository = knowledgeRelationRepository;
        this.llmService = llmService;
        this.objectMapper = objectMapper;
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
        kp.setType(request.getType() != null ? request.getType() : "LEAF");

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

        if (request.getType() != null) {
            kp.setType(request.getType());
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

    // ═══════════════════════════════════════════════════════════════
    //  AI 树结构生成
    // ═══════════════════════════════════════════════════════════════

    private static final Pattern JSON_ARRAY_PATTERN = Pattern.compile(
            "\\[.*\\]", Pattern.DOTALL);
    private static final Pattern JSON_OBJECT_PATTERN = Pattern.compile(
            "\\{.*\\}", Pattern.DOTALL);

    /**
     * AI 生成/更新课程知识点树结构。
     * <p>
     * 核心流程：
     * <ol>
     *   <li>加载课程所有知识点和已有树结构</li>
     *   <li>计算差异（新增/删除/变更的知识点）</li>
     *   <li>构建 Prompt 并调用 LLM</li>
     *   <li>解析 LLM 返回的 JSON 树结构</li>
     *   <li>合并已有树节点（保留稳定节点的 UUID）</li>
     *   <li>保存到数据库</li>
     * </ol>
     *
     * @param courseId 课程 ID
     * @param request  生成请求（含粒度参数）
     * @return 树生成结果（含统计和孤立知识点）
     */
    @Transactional
    public TreeGenerateResult generateTreeStructure(UUID courseId, TreeGenerateRequest request) {
        log.info("AI 生成树结构: courseId={}, granularity={}", courseId, request.getGranularity());

        // 1. 加载课程和知识点
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("课程", courseId));
        List<KnowledgePoint> allKps = knowledgePointRepository
                .findByCourseIdOrderByOrderIndexAsc(courseId);
        if (allKps.size() < 3) {
            throw new ValidationException("知识点太少（至少 3 个），无法生成树结构");
        }

        // 2. 分离已有树节点和中点（LEAF）
        List<KnowledgePoint> existingTreeNodes = allKps.stream()
                .filter(kp -> !"LEAF".equals(kp.getType()))
                .collect(Collectors.toList());
        List<KnowledgePoint> leafKps = allKps.stream()
                .filter(kp -> "LEAF".equals(kp.getType()))
                .collect(Collectors.toList());

        // 3. 构建 Prompt
        String prompt = buildTreePrompt(course, existingTreeNodes, leafKps, request);
        log.debug("树生成 Prompt 长度: {} 字符", prompt.length());

        // 4. 调用 LLM
        LLMResponse response = llmService.ask(LLM_TREE_SYSTEM_PROMPT, prompt);
        String content = response.getContent();
        log.debug("LLM 返回: {} 字符", content.length());

        // 5. 解析 JSON
        List<TreeGenerateResult.TreeNode> generatedTree = parseTreeJson(content);
        if (generatedTree == null || generatedTree.isEmpty()) {
            throw new ValidationException("AI 未能生成有效的树结构，请重试");
        }

        // 6. 合并已有节点 ID（保留稳定性）
        Map<String, UUID> existingNodeNameToId = new HashMap<>();
        for (KnowledgePoint tn : existingTreeNodes) {
            existingNodeNameToId.put(tn.getName().trim(), tn.getId());
        }

        // 6.1 知识点名称→ID 映射（用于 LLM 只返回名称时的匹配）
        Map<String, UUID> leafKpNameToId = new HashMap<>();
        for (KnowledgePoint lkp : leafKps) {
            leafKpNameToId.put(lkp.getName().trim(), lkp.getId());
        }

        // 7. 统计信息
        int[] stats = new int[]{0, 0, 0, 0, 0, 0, 0};
        // stats: [total, new, kept, removed, volumes, chapters, sections]
        List<UUID> orphanedIds = new ArrayList<>();

        // 8. 递归处理和保存
        List<KnowledgePoint> allToSave = new ArrayList<>();
        int[] orderAcc = {0};
        for (TreeGenerateResult.TreeNode node : generatedTree) {
            processTreeNode(node, null, courseId, course.getCourseCode(),
                    existingNodeNameToId, leafKpNameToId, allToSave, stats, orderAcc);
        }

        // 8.1 删除旧的树节点（不再存在于新树中的）
        Set<UUID> keptIds = allToSave.stream()
                .map(KnowledgePoint::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (KnowledgePoint oldTn : existingTreeNodes) {
            UUID oid = oldTn.getId();
            if (oid != null && !keptIds.contains(oid)) {
                // 将其子节点解除父引用
                List<KnowledgePoint> children = knowledgePointRepository
                        .findByParentKpId(oid);
                for (KnowledgePoint child : children) {
                    child.setParentKpId(null);
                    knowledgePointRepository.save(child);
                }
                knowledgePointRepository.delete(oldTn);
                stats[3]++; // removed
            }
        }

        // 8.2 删除旧树节点后，重新查询所有 LEAF（因为旧树节点删除时，其子 LEAF 的 parentKpId 被清空了）
        List<KnowledgePoint> allLeavesNow = knowledgePointRepository
                .findByCourseIdOrderByOrderIndexAsc(courseId).stream()
                .filter(kp -> "LEAF".equals(kp.getType()))
                .collect(Collectors.toList());

        // 收集所有 parentKpId 仍为 null 的 LEAF
        // 包括：未被 LLM 处理的（名称未匹配）、被 LLM 放在顶层的（parent 为 null）、被旧树节点删除释放的
        List<KnowledgePoint> allOrphanLeaves = new ArrayList<>();
        for (KnowledgePoint leaf : allLeavesNow) {
            if (leaf.getParentKpId() == null) {
                allOrphanLeaves.add(leaf);
                orphanedIds.add(leaf.getId());
            }
        }

        // 为孤立知识点找到第一个 VOLUME（作为"未归类"节的父节点）
        KnowledgePoint volumeParent = null;
        for (KnowledgePoint saved : allToSave) {
            if ("VOLUME".equals(saved.getType())) {
                volumeParent = saved;
                break;
            }
        }

        if (!allOrphanLeaves.isEmpty()) {
            // 创建「未归类知识点」SECTION，先保存获取 ID
            KnowledgePoint uncategorizedSection = new KnowledgePoint();
            uncategorizedSection.setName("未归类知识点");
            uncategorizedSection.setType("SECTION");
            uncategorizedSection.setCourseId(courseId);
            uncategorizedSection.setSubject(course.getCourseCode());
            uncategorizedSection.setOrderIndex(999);
            uncategorizedSection.setDescription("AI 未能自动归类的知识点，请手动调整到合适章节");
            uncategorizedSection.setDifficulty(3);
            uncategorizedSection.setImportance(3);
            if (volumeParent != null) {
                uncategorizedSection.setParentKpId(volumeParent.getId());
            }
            // 先保存以获取 ID
            uncategorizedSection = knowledgePointRepository.save(uncategorizedSection);
            allToSave.add(uncategorizedSection);
            stats[0]++; // total +1

            // 将所有孤立 LEAF 挂到「未归类知识点」节下
            int leafOrder = 1;
            for (KnowledgePoint orphan : allOrphanLeaves) {
                orphan.setParentKpId(uncategorizedSection.getId());
                orphan.setOrderIndex(leafOrder++);
                if (!allToSave.contains(orphan)) {
                    allToSave.add(orphan);
                }
                stats[2]++; // kept
            }
        }

        // 9. 保存所有树节点
        knowledgePointRepository.saveAll(allToSave);

        // 10. 构建返回结果
        List<TreeGenerateResult.TreeNode> resultTree = buildResultTree(allToSave, courseId);

        log.info("树结构生成完成: courseId={}, total={}, new={}, kept={}, removed={}",
                courseId, stats[0], stats[1], stats[2], stats[3]);

        return new TreeGenerateResult(
                resultTree,
                new TreeGenerateResult.TreeStats(
                        stats[0], stats[1], stats[2], stats[3],
                        stats[4], stats[5], stats[6],
                        (int) allLeavesNow.stream().filter(l -> l.getParentKpId() == null).count()
                ),
                orphanedIds
        );
    }

    private static final String LLM_TREE_SYSTEM_PROMPT = """
            你是一个课程知识结构专家。请根据提供的课程知识点列表，生成或更新该课程的树状知识结构。
            
            要求：
            1. 按照「编(VOLUME) → 卷(PART) → 章(CHAPTER) → 节(SECTION) → 知识点(LEAF)」五层结构组织
            2. 优先保留已有的树结构：对于已有的编/卷/章/节节点，尽量保留其名称和层级关系
            3. 将新增知识点放入最合适的已有章节中，必要时创建新的章节
            4. 已删除的知识点对应的章节节点保留（如果还有其他有效知识点）
            5. 内容变更的知识点保持在原位置不变
            6. 返回 JSON 数组，格式如下，不要包含其他内容。注意：LEAF 节点必须有 kpId 字段（引用已有知识点的 ID 或 null 表示新建）。

            【关键规则】：
            - 所有 LEAF 类型的节点（知识点）必须放在 SECTION 节点下作为其子节点
            - 不允许 LEAF 节点出现在 VOLUME、PART 或 CHAPTER 的直接子节点中
            - 如果某个知识点没有合适的 SECTION 位置，创建一个新的 SECTION 来收纳它
            - 每个 LEAF 都必须是树中最深层的节点（不能再有子节点）
            - 输出必须是完整的树结构，不要省略任何知识点

            JSON 格式示例：
            [
              {
                "name": "第一编 法的基本原理",
                "type": "VOLUME",
                "order": 1,
                "children": [
                  {
                    "name": "第一章 法的概念",
                    "type": "CHAPTER",
                    "order": 1,
                    "children": [
                      {
                        "name": "第一节 法的定义",
                        "type": "SECTION",
                        "order": 1,
                        "children": [
                          {"kpId": null, "name": "法的词源", "type": "LEAF", "order": 1}
                        ]
                      }
                    ]
                  }
                ]
              }
            ]
            """;

    private String buildTreePrompt(Course course,
                                   List<KnowledgePoint> existingTreeNodes,
                                   List<KnowledgePoint> leafKps,
                                   TreeGenerateRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 课程信息 ===\n");
        sb.append("名称: ").append(course.getName()).append("\n");
        sb.append("学科: ").append(course.getSubject()).append("\n");
        sb.append("描述: ").append(Optional.ofNullable(course.getDescription()).orElse("")).append("\n");
        sb.append("生成粒度: ").append(request.getGranularity()).append("\n\n");

        // 已有树结构
        if (!existingTreeNodes.isEmpty()) {
            sb.append("=== 已有树结构（请优先保留这些节点名称和层级关系）===\n");
            List<KnowledgePoint> roots = existingTreeNodes.stream()
                    .filter(kp -> kp.getParentKpId() == null)
                    .collect(Collectors.toList());
            for (KnowledgePoint root : roots) {
                sb.append("- ").append(root.getName())
                        .append(" [").append(root.getType()).append("]")
                        .append(" id:").append(root.getId()).append("\n");
                appendChildren(existingTreeNodes, root.getId(), sb, 1);
            }
            sb.append("\n");
        }

        // 所有知识点 — 只传名称，后端再做名称映射
        sb.append("=== 所有知识点列表（请将这些知识点归类到合适的章/节下）===\n");
        // 如果知识点太多，分批或只传名称
        int batchSize = Math.min(leafKps.size(), 500);
        for (int i = 0; i < batchSize; i++) {
            KnowledgePoint kp = leafKps.get(i);
            sb.append("- ").append(kp.getName());
            // 可选：加上描述（如果存在且简短）
            if (kp.getDescription() != null && !kp.getDescription().isBlank()
                    && kp.getDescription().length() < 80) {
                sb.append("（").append(kp.getDescription()).append("）");
            }
            sb.append("\n");
        }
        if (leafKps.size() > batchSize) {
            sb.append("... 等 ").append(leafKps.size()).append(" 个知识点\n");
        }

        // 新增/变更知识点标注
        sb.append("\n=== 变化说明 ===\n");
        sb.append("（1）已有树节点请尽量保留其名称和层级\n");
        sb.append("（2）新增知识点请根据内容归入合适的章节\n");
        sb.append("（3）如果某些知识点没有合适的章节，可以新建章节来容纳它们\n");
        sb.append("（4）返回格式必须是 JSON 数组，每个节点包含 name, type, order, children/kpId 字段\n");
        sb.append("（5）【重要】所有知识点（LEAF）必须放在 SECTION 节点下作为子节点，不允许直接放在 VOLUME/CHAPTER 下\n");

        return sb.toString();
    }

    private void appendChildren(List<KnowledgePoint> allNodes, UUID parentId,
                                StringBuilder sb, int indent) {
        String prefix = "  ".repeat(indent) + "- ";
        for (KnowledgePoint child : allNodes) {
            if (parentId.equals(child.getParentKpId())) {
                sb.append(prefix).append(child.getName())
                        .append(" [").append(child.getType()).append("]")
                        .append(" id:").append(child.getId()).append(" 包含子知识点:");
                // 列出此节点下的 LEAF 知识点
                List<KnowledgePoint> leafChildren = knowledgePointRepository
                        .findByParentKpId(child.getId());
                for (KnowledgePoint lc : leafChildren) {
                    if ("LEAF".equals(lc.getType())) {
                        sb.append(lc.getName()).append(", ");
                    }
                }
                sb.append("\n");
                appendChildren(allNodes, child.getId(), sb, indent + 1);
            }
        }
    }

    private List<TreeGenerateResult.TreeNode> parseTreeJson(String content) {
        try {
            // 提取 JSON 数组
            Matcher matcher = JSON_ARRAY_PATTERN.matcher(content);
            if (matcher.find()) {
                String json = matcher.group();
                return objectMapper.readValue(json,
                        new TypeReference<List<TreeGenerateResult.TreeNode>>() {});
            }
        } catch (Exception e) {
            log.warn("解析树结构 JSON 失败: {}", e.getMessage());
        }
        return null;
    }

    private void processTreeNode(TreeGenerateResult.TreeNode node,
                                  KnowledgePoint parent,
                                  UUID courseId, String courseCode,
                                  Map<String, UUID> existingNodeNameToId,
                                  Map<String, UUID> leafKpNameToId,
                                  List<KnowledgePoint> allToSave,
                                  int[] stats, int[] orderAcc) {
        String type = Optional.ofNullable(node.type()).orElse("LEAF");
        String name = Optional.ofNullable(node.name()).orElse("未命名");
        stats[0]++;

        // 确定节点 ID
        UUID nodeId = null;
        if ("LEAF".equals(type)) {
            if (node.kpId() != null) {
                // LLM 直接返回了 ID
                nodeId = node.kpId();
            } else if (leafKpNameToId.containsKey(name.trim())) {
                // LLM 只返回了名称，通过名称匹配
                nodeId = leafKpNameToId.get(name.trim());
                leafKpNameToId.remove(name.trim());
                stats[2]++; // kept
            } else {
                stats[1]++; // new
            }
        } else {
            // 中间节点：尝试匹配已有节点
            String key = name.trim();
            if (existingNodeNameToId.containsKey(key)) {
                nodeId = existingNodeNameToId.get(key);
                existingNodeNameToId.remove(key);
                stats[2]++; // kept
            } else {
                stats[1]++; // new
            }
        }

        orderAcc[0]++;

        // 创建或更新 KnowledgePoint
        KnowledgePoint kp;
        if (nodeId != null) {
            kp = knowledgePointRepository.findById(nodeId)
                    .orElse(new KnowledgePoint());
            kp.setId(nodeId);
        } else {
            kp = new KnowledgePoint();
        }

        kp.setName(name);
        kp.setType(type);
        kp.setCourseId(courseId);
        kp.setOrderIndex(orderAcc[0]);
        kp.setParentKpId(parent != null ? parent.getId() : null);

        if (kp.getDescription() == null) {
            kp.setDescription("");
        }
        if (kp.getDifficulty() == null) {
            kp.setDifficulty(3);
        }
        if (kp.getImportance() == null) {
            kp.setImportance(3);
        }
        if (kp.getSubject() == null || !courseCode.equals(kp.getSubject())) {
            kp.setSubject(courseCode);
        }

        // 统计
        switch (type) {
            case "VOLUME" -> stats[4]++;
            case "CHAPTER" -> stats[5]++;
            case "SECTION" -> stats[6]++;
        }

        allToSave.add(kp);

        // 递归处理子节点
        if (node.children() != null) {
            // 将当前节点的子 LEAF 的 parentKpId 设为当前节点
            for (TreeGenerateResult.TreeNode child : node.children()) {
                processTreeNode(child, kp, courseId, courseCode,
                        existingNodeNameToId, leafKpNameToId, allToSave, stats, orderAcc);
            }
        }
    }

    private List<TreeGenerateResult.TreeNode> buildResultTree(
            List<KnowledgePoint> allTreeNodes, UUID courseId) {
        List<KnowledgePoint> roots = allTreeNodes.stream()
                .filter(kp -> kp.getParentKpId() == null)
                .sorted(Comparator.comparingInt(KnowledgePoint::getOrderIndex))
                .collect(Collectors.toList());

        List<TreeGenerateResult.TreeNode> result = new ArrayList<>();
        for (KnowledgePoint root : roots) {
            result.add(buildTreeNode(root, allTreeNodes));
        }
        return result;
    }

    private TreeGenerateResult.TreeNode buildTreeNode(
            KnowledgePoint kp, List<KnowledgePoint> allNodes) {
        List<KnowledgePoint> children = allNodes.stream()
                .filter(c -> kp.getId().equals(c.getParentKpId()))
                .sorted(Comparator.comparingInt(KnowledgePoint::getOrderIndex))
                .collect(Collectors.toList());

        List<TreeGenerateResult.TreeNode> childNodes = new ArrayList<>();
        for (KnowledgePoint child : children) {
            childNodes.add(buildTreeNode(child, allNodes));
        }

        return new TreeGenerateResult.TreeNode(
                kp.getId(),
                kp.getName(),
                kp.getType(),
                kp.getOrderIndex() != null ? kp.getOrderIndex() : 0,
                childNodes,
                "UNCHANGED"
        );
    }

    /**
     * 移动知识点到新的父节点。
     *
     * @param id          知识点 ID
     * @param parentKpId  新的父节点 ID（null 表示移到根层级）
     * @param orderIndex  在新的父节点下的排序序号
     * @return 更新后的知识点 DTO
     */
    @Transactional
    public KnowledgePointDto moveKnowledgePoint(UUID id, UUID parentKpId, Integer orderIndex) {
        log.info("移动知识点: id={}, newParentId={}, orderIndex={}", id, parentKpId, orderIndex);

        KnowledgePoint kp = knowledgePointRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("知识点", id));

        // 校验不能将自己设为父节点
        if (parentKpId != null && parentKpId.equals(id)) {
            throw new ValidationException("不能将知识点自身设为父节点");
        }

        // 校验不能将节点移入自己的子节点（防止循环引用）
        if (parentKpId != null) {
            KnowledgePoint parent = knowledgePointRepository.findById(parentKpId)
                    .orElseThrow(() -> new ResourceNotFoundException("父知识点", parentKpId));
            if (!parent.getCourseId().equals(kp.getCourseId())) {
                throw new ValidationException("父知识点必须属于同一课程");
            }
            // 检查循环引用
            if (isDescendant(id, parentKpId)) {
                throw new ValidationException("不能将知识点移入其自身子节点中");
            }
        }

        kp.setParentKpId(parentKpId);
        if (orderIndex != null) {
            kp.setOrderIndex(orderIndex);
        }

        KnowledgePoint saved = knowledgePointRepository.save(kp);
        return KnowledgePointDto.fromEntity(saved);
    }

    /**
     * 检查 targetId 是否是 sourceId 的后代节点。
     */
    private boolean isDescendant(UUID sourceId, UUID targetId) {
        Set<UUID> visited = new HashSet<>();
        Queue<UUID> queue = new LinkedList<>();
        queue.add(targetId);
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            if (current.equals(sourceId)) return true;
            if (visited.contains(current)) continue;
            visited.add(current);
            List<KnowledgePoint> children = knowledgePointRepository.findByParentKpId(current);
            for (KnowledgePoint child : children) {
                if (!visited.contains(child.getId())) {
                    queue.add(child.getId());
                }
            }
        }
        return false;
    }
}
