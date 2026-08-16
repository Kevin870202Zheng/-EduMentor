package com.edumentor.classroom.service;

import com.edumentor.classroom.dto.ClassroomMaterial;
import com.edumentor.classroom.entity.*;
import com.edumentor.classroom.entity.enums.CollabProjectStatus;
import com.edumentor.classroom.entity.enums.CollabRoleType;
import com.edumentor.classroom.entity.enums.CollabTaskStatus;
import com.edumentor.classroom.repository.CollabClassroomProjectRepository;
import com.edumentor.classroom.repository.CollabProjectTaskRepository;
import com.edumentor.classroom.repository.StoryLibraryRepository;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.repository.CourseRepository;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.diagnosis.repository.StudentProfileRepository;
import com.edumentor.student.entity.StudentProfile;
import com.edumentor.user.entity.User;
import com.edumentor.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 学段协作课堂服务（设计文档 §5）— 状态机与素材聚合。
 * <p>状态机：DRAFT → INVITING → COLLECTING → REVIEW → GENERATING → PUBLISHED</p>
 * <p>角色分工：STORY_PICKER(小学选故事) / CHARACTER_DESIGNER(初中角色形象)
 * / SCRIPT_WRITER(高中台词) / LEGAL_MAPPER(大学法律映射)</p>
 */
@Service
public class CollabClassroomService {

    private static final Logger log = LoggerFactory.getLogger(CollabClassroomService.class);

    /** 角色 → 要求学段 */
    private static final Map<CollabRoleType, String> ROLE_STAGE = Map.of(
            CollabRoleType.STORY_PICKER, "PRIMARY",
            CollabRoleType.CHARACTER_DESIGNER, "JUNIOR",
            CollabRoleType.SCRIPT_WRITER, "SENIOR",
            CollabRoleType.LEGAL_MAPPER, "UNIVERSITY");

    private final CollabClassroomProjectRepository projectRepository;
    private final CollabProjectTaskRepository taskRepository;
    private final StoryLibraryRepository storyRepository;
    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final CourseRepository courseRepository;
    private final ClassroomGenerator classroomGenerator;
    private final ObjectMapper objectMapper;

    public CollabClassroomService(CollabClassroomProjectRepository projectRepository,
                                  CollabProjectTaskRepository taskRepository,
                                  StoryLibraryRepository storyRepository,
                                  UserRepository userRepository,
                                  StudentProfileRepository studentProfileRepository,
                                  KnowledgePointRepository knowledgePointRepository,
                                  CourseRepository courseRepository,
                                  ClassroomGenerator classroomGenerator,
                                  ObjectMapper objectMapper) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.storyRepository = storyRepository;
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.courseRepository = courseRepository;
        this.classroomGenerator = classroomGenerator;
        this.objectMapper = objectMapper;
    }

    // ═══════════════════════════════════════════════════
    //  创建 / 查询
    // ═══════════════════════════════════════════════════

    /** 创建项目（教师）+ 预建 4 个角色任务 */
    @Transactional
    public CollabClassroomProject create(UUID creatorId, String title, String description, UUID courseId, Integer difficulty) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("请填写项目标题");
        }
        CollabClassroomProject project = new CollabClassroomProject();
        project.setTitle(title.trim());
        project.setDescription(description);
        project.setCourseId(courseId);
        project.setCreatorId(creatorId);
        project.setDifficulty(difficulty != null ? difficulty : 3);
        project.setStatus(CollabProjectStatus.DRAFT);
        project = projectRepository.save(project);

        for (CollabRoleType role : CollabRoleType.values()) {
            CollabProjectTask task = new CollabProjectTask();
            task.setProjectId(project.getId());
            task.setRoleType(role);
            task.setRequiredStage(ROLE_STAGE.get(role));
            task.setStatus(CollabTaskStatus.PENDING);
            taskRepository.save(task);
        }
        log.info("Collab project created: id={}, title={}, creator={}", project.getId(), project.getTitle(), creatorId);
        return project;
    }

    /** 我发起的 + 我参与的协作项目 */
    public List<Map<String, Object>> listMine(UUID userId) {
        Set<UUID> ids = new LinkedHashSet<>();
        projectRepository.findByCreatorIdOrderByCreatedAtDesc(userId).forEach(p -> ids.add(p.getId()));
        taskRepository.findByAssignedUserIdOrderByUpdatedAtDesc(userId).forEach(t -> ids.add(t.getProjectId()));
        List<Map<String, Object>> result = new ArrayList<>();
        for (UUID id : ids) {
            projectRepository.findById(id).ifPresent(p -> result.add(toDetail(p)));
        }
        return result;
    }

    /** 项目详情（含任务 + 学生姓名 + 故事信息） */
    public Map<String, Object> getDetail(UUID projectId) {
        CollabClassroomProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("协作项目不存在: " + projectId));
        return toDetail(project);
    }

    /** 更新项目基础信息 */
    @Transactional
    public CollabClassroomProject update(UUID projectId, String title, String description, UUID courseId, Integer difficulty) {
        CollabClassroomProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("协作项目不存在: " + projectId));
        if (CollabProjectStatus.GENERATING.equals(project.getStatus())
                || CollabProjectStatus.PUBLISHED.equals(project.getStatus())) {
            throw new IllegalArgumentException("项目已进入生成阶段，无法修改");
        }
        if (title != null && !title.isBlank()) project.setTitle(title.trim());
        if (description != null) project.setDescription(description);
        if (courseId != null) project.setCourseId(courseId);
        if (difficulty != null && difficulty >= 1 && difficulty <= 5) project.setDifficulty(difficulty);
        return projectRepository.save(project);
    }

    // ═══════════════════════════════════════════════════
    //  邀请
    // ═══════════════════════════════════════════════════

    /** 该学段可邀学生列表 */
    public List<Map<String, Object>> candidates(String stage) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (StudentProfile sp : studentProfileRepository.findByStageOrderByUpdatedAtDesc(stage)) {
            userRepository.findById(sp.getUserId()).ifPresent(u -> {
                Map<String, Object> m = new HashMap<>();
                m.put("userId", sp.getUserId());
                m.put("displayName", u.getDisplayName() != null ? u.getDisplayName() : u.getUsername());
                m.put("username", u.getUsername());
                m.put("stage", sp.getStage());
                m.put("grade", sp.getGrade());
                m.put("school", sp.getSchool());
                result.add(m);
            });
        }
        return result;
    }

    /** 邀请学生到指定角色任务（校验学段匹配） */
    @Transactional
    public CollabProjectTask invite(UUID projectId, CollabRoleType roleType, UUID studentId) {
        CollabClassroomProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("协作项目不存在: " + projectId));
        if (!CollabProjectStatus.DRAFT.equals(project.getStatus())
                && !CollabProjectStatus.INVITING.equals(project.getStatus())) {
            throw new IllegalArgumentException("当前状态（" + project.getStatus() + "）不允许邀请");
        }
        StudentProfile sp = studentProfileRepository.findByUserId(studentId)
                .orElseThrow(() -> new IllegalArgumentException("该学生没有学段档案"));
        String required = ROLE_STAGE.get(roleType);
        if (!required.equals(sp.getStage())) {
            throw new IllegalArgumentException("该学生学段为「" + sp.getStage() + "」，与角色要求的「" + required + "」不符");
        }

        CollabProjectTask task = taskRepository.findByProjectIdAndRoleType(projectId, roleType)
                .orElseThrow(() -> new IllegalArgumentException("角色任务不存在: " + roleType));
        task.setAssignedUserId(studentId);
        task.setStatus(CollabTaskStatus.PENDING);
        task.setContent(null);
        task.setSubmittedAt(null);
        task.setReviewedAt(null);
        taskRepository.save(task);

        if (CollabProjectStatus.DRAFT.equals(project.getStatus())) {
            project.setStatus(CollabProjectStatus.INVITING);
        }
        if (allAssigned(projectId)) {
            project.setStatus(CollabProjectStatus.COLLECTING);
        }
        projectRepository.save(project);
        return task;
    }

    // ═══════════════════════════════════════════════════
    //  提交 / 复核
    // ═══════════════════════════════════════════════════

    /** 被邀学生提交任务 */
    @Transactional
    public CollabProjectTask submit(UUID projectId, UUID taskId, UUID studentId, String content) {
        CollabClassroomProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("协作项目不存在: " + projectId));
        if (!CollabProjectStatus.COLLECTING.equals(project.getStatus())
                && !CollabProjectStatus.REVIEW.equals(project.getStatus())) {
            throw new IllegalArgumentException("当前状态不允许提交（状态: " + project.getStatus() + "）");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("提交内容不能为空");
        }
        CollabProjectTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        if (!task.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("任务不属于该项目");
        }
        if (!studentId.equals(task.getAssignedUserId())) {
            throw new IllegalArgumentException("只能提交自己被分配的任务");
        }
        task.setContent(content);
        task.setStatus(CollabTaskStatus.COMPLETED);
        task.setSubmittedAt(LocalDateTime.now());
        taskRepository.save(task);

        // STORY_PICKER 提交时同步选定的故事到项目（生成时使用）
        if (task.getRoleType() == CollabRoleType.STORY_PICKER) {
            try {
                Map<String, Object> m = objectMapper.readValue(content, Map.class);
                Object sid = m.get("storyId");
                if (sid != null) {
                    project.setStoryId(UUID.fromString(sid.toString()));
                    projectRepository.save(project);
                }
            } catch (Exception ignored) {
                log.warn("解析故事选择内容失败: project={}", projectId);
            }
        }

        // 全部提交完成 → 进入 REVIEW 等待教师审阅
        if (CollabProjectStatus.COLLECTING.equals(project.getStatus()) && allCompleted(projectId)) {
            project.setStatus(CollabProjectStatus.REVIEW);
            projectRepository.save(project);
        }
        return task;
    }

    /** 教师复核/修改任务内容 */
    @Transactional
    public CollabProjectTask review(UUID projectId, UUID taskId, UUID creatorId, String content) {
        CollabClassroomProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("协作项目不存在: " + projectId));
        if (!project.getCreatorId().equals(creatorId)) {
            throw new IllegalArgumentException("仅项目发起者（教师）可以复核");
        }
        if (!CollabProjectStatus.COLLECTING.equals(project.getStatus())
                && !CollabProjectStatus.REVIEW.equals(project.getStatus())) {
            throw new IllegalArgumentException("当前状态不允许复核");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("复核内容不能为空");
        }
        CollabProjectTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        if (!task.getProjectId().equals(projectId)) {
            throw new IllegalArgumentException("任务不属于该项目");
        }
        task.setContent(content);
        task.setStatus(CollabTaskStatus.REVIEWED);
        task.setReviewedAt(LocalDateTime.now());
        taskRepository.save(task);

        // 教师复核故事选择时同步故事 ID
        if (task.getRoleType() == CollabRoleType.STORY_PICKER) {
            try {
                Map<String, Object> m = objectMapper.readValue(content, Map.class);
                Object sid = m.get("storyId");
                if (sid != null) {
                    project.setStoryId(UUID.fromString(sid.toString()));
                    projectRepository.save(project);
                }
            } catch (Exception ignored) {
                log.warn("解析故事选择内容失败: project={}", projectId);
            }
        }

        if (CollabProjectStatus.COLLECTING.equals(project.getStatus())) {
            project.setStatus(CollabProjectStatus.REVIEW);
            projectRepository.save(project);
        }
        return task;
    }

    // ═══════════════════════════════════════════════════
    //  生成
    // ═══════════════════════════════════════════════════

    /** 确认生成课堂（教师触发，同步生成） */
    @Transactional
    public Classroom generate(UUID projectId, UUID creatorId) {
        CollabClassroomProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("协作项目不存在: " + projectId));
        if (!project.getCreatorId().equals(creatorId)) {
            throw new IllegalArgumentException("仅项目发起者（教师）可以生成课堂");
        }
        if (!CollabProjectStatus.REVIEW.equals(project.getStatus())
                && !CollabProjectStatus.GENERATING.equals(project.getStatus())) {
            throw new IllegalArgumentException("请先完成全部任务的审阅后再生成");
        }
        if (project.getStoryId() == null) {
            // 兜底：从 STORY_PICKER 任务内容解析故事 ID
            CollabProjectTask storyTask = taskRepository
                    .findByProjectIdAndRoleType(projectId, CollabRoleType.STORY_PICKER).orElse(null);
            if (storyTask != null && storyTask.getContent() != null) {
                try {
                    Map<String, Object> m = objectMapper.readValue(storyTask.getContent(), Map.class);
                    if (m.get("storyId") != null) {
                        project.setStoryId(UUID.fromString(m.get("storyId").toString()));
                        projectRepository.save(project);
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (project.getStoryId() == null) {
            throw new IllegalArgumentException("尚未选定故事，请先确认小学学生的故事选择");
        }
        List<CollabProjectTask> tasks = taskRepository.findByProjectIdOrderByRoleTypeAsc(projectId);
        boolean allReady = tasks.stream().allMatch(t -> CollabTaskStatus.REVIEWED.equals(t.getStatus())
                || CollabTaskStatus.COMPLETED.equals(t.getStatus()));
        if (!allReady) {
            throw new IllegalArgumentException("还有任务未完成审阅");
        }

        // courseId 兜底：课堂必须关联课程，优先取法律类课程，否则取最早课程
        if (project.getCourseId() == null) {
            List<com.edumentor.course.entity.Course> all = courseRepository.findAll();
            UUID fallback = all.stream()
                    .filter(c -> c.getName() != null && c.getName().contains("法"))
                    .map(c -> c.getId()).findFirst()
                    .orElseGet(() -> all.stream().map(c -> c.getId()).findFirst().orElse(null));
            if (fallback == null) {
                throw new IllegalArgumentException("请先为项目关联课程（课程库为空）");
            }
            project.setCourseId(fallback);
            projectRepository.save(project);
        }

        project.setStatus(CollabProjectStatus.GENERATING);
        projectRepository.save(project);
        try {
            Classroom classroom = classroomGenerator.generateFromMaterial(buildMaterial(project, tasks));
            project.setClassroomId(classroom.getId());
            project.setStatus(CollabProjectStatus.PUBLISHED);
            projectRepository.save(project);
            log.info("Collab classroom generated: project={}, classroom={}", projectId, classroom.getId());
            return classroom;
        } catch (Exception e) {
            log.error("Collab classroom generation failed: project={}, error={}", projectId, e.getMessage(), e);
            project.setStatus(CollabProjectStatus.REVIEW);
            projectRepository.save(project);
            throw new RuntimeException("课堂生成失败：" + e.getMessage(), e);
        }
    }

    /** 聚合四角色产出 + 故事原文 → 课堂素材 */
    private ClassroomMaterial buildMaterial(CollabClassroomProject project, List<CollabProjectTask> tasks) {
        StoryLibrary story = project.getStoryId() != null
                ? storyRepository.findById(project.getStoryId()).orElse(null) : null;
        Map<CollabRoleType, CollabProjectTask> byRole = new HashMap<>();
        tasks.forEach(t -> byRole.put(t.getRoleType(), t));

        StringBuilder sb = new StringBuilder();
        if (story != null) {
            sb.append("【故事原文】《").append(story.getTitle()).append("》\n").append(story.getContent()).append("\n\n");
        }

        CollabProjectTask character = byRole.get(CollabRoleType.CHARACTER_DESIGNER);
        if (character != null && character.getContent() != null) {
            sb.append("【角色形象设计】\n").append(character.getContent()).append("\n\n");
        }

        CollabProjectTask script = byRole.get(CollabRoleType.SCRIPT_WRITER);
        if (script != null && script.getContent() != null) {
            sb.append("【台词脚本】\n").append(script.getContent()).append("\n\n");
        }

        List<UUID> kpIds = new ArrayList<>();
        CollabProjectTask legal = byRole.get(CollabRoleType.LEGAL_MAPPER);
        if (legal != null && legal.getContent() != null) {
            try {
                Map<String, Object> m = objectMapper.readValue(legal.getContent(), Map.class);
                Object ids = m.get("knowledgePointIds");
                if (ids instanceof List<?> list) {
                    for (Object o : list) {
                        try {
                            kpIds.add(UUID.fromString(o.toString()));
                        } catch (Exception ignored) {
                        }
                    }
                }
                Object mapping = m.get("mapping");
                sb.append("【法律知识映射】\n").append(mapping != null ? mapping : "").append("\n");
                if (!kpIds.isEmpty()) {
                    for (KnowledgePoint kp : knowledgePointRepository.findAllById(kpIds)) {
                        sb.append("- ").append(kp.getName()).append("：")
                          .append(kp.getContent() != null ? kp.getContent() : "").append("\n");
                    }
                }
                sb.append("\n");
            } catch (Exception e) {
                sb.append("【法律知识映射】\n").append(legal.getContent()).append("\n\n");
            }
        }

        String courseName = "";
        if (project.getCourseId() != null) {
            courseName = courseRepository.findById(project.getCourseId())
                    .map(c -> c.getName()).orElse("");
        }

        return ClassroomMaterial.builder()
                .courseId(project.getCourseId())
                .courseName(courseName)
                .title(project.getTitle())
                .description("学段协作课堂：故事「" + (story != null ? story.getTitle() : "")
                        + "」× 法律知识，由小学/初中/高中/大学学生协作共创")
                .knowledgeContext(sb.toString())
                .knowledgePointIds(kpIds)
                .source("collaborative")
                .metadata("{\"projectId\":\"" + project.getId() + "\"}")
                .difficulty(project.getDifficulty() != null ? project.getDifficulty() : 3)
                .build();
    }

    // ═══════════════════════════════════════════════════
    //  私有辅助
    // ═══════════════════════════════════════════════════

    private boolean allAssigned(UUID projectId) {
        return taskRepository.findByProjectIdOrderByRoleTypeAsc(projectId)
                .stream().allMatch(t -> t.getAssignedUserId() != null);
    }

    private boolean allCompleted(UUID projectId) {
        return taskRepository.findByProjectIdOrderByRoleTypeAsc(projectId)
                .stream().allMatch(t -> CollabTaskStatus.COMPLETED.equals(t.getStatus())
                        || CollabTaskStatus.REVIEWED.equals(t.getStatus()));
    }

    private Map<String, Object> toDetail(CollabClassroomProject project) {
        Map<String, Object> dto = project.toDto();
        List<Map<String, Object>> tasks = new ArrayList<>();
        for (CollabProjectTask t : taskRepository.findByProjectIdOrderByRoleTypeAsc(project.getId())) {
            Map<String, Object> td = t.toDto();
            if (t.getAssignedUserId() != null) {
                userRepository.findById(t.getAssignedUserId()).ifPresent(u ->
                        td.put("assignedName", u.getDisplayName() != null ? u.getDisplayName() : u.getUsername()));
            }
            tasks.add(td);
        }
        dto.put("tasks", tasks);
        if (project.getStoryId() != null) {
            storyRepository.findById(project.getStoryId()).ifPresent(s -> {
                dto.put("storyTitle", s.getTitle());
                dto.put("storyContent", s.getContent());
            });
        }
        return dto;
    }
}
