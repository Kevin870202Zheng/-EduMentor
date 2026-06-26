package com.edumentor.seeder;

import com.edumentor.common.util.PasswordUtil;
import com.edumentor.course.entity.Course;
import com.edumentor.course.entity.KnowledgePoint;
import com.edumentor.course.entity.KnowledgeRelation;
import com.edumentor.course.entity.enums.RelationType;
import com.edumentor.course.repository.CourseRepository;
import com.edumentor.course.repository.KnowledgePointRepository;
import com.edumentor.course.repository.KnowledgeRelationRepository;
import com.edumentor.courseteacher.entity.CourseTeacher;
import com.edumentor.courseteacher.repository.CourseTeacherRepository;
import com.edumentor.enrollment.entity.StudentCourse;
import com.edumentor.enrollment.repository.StudentCourseRepository;
import com.edumentor.entity.enums.QuestionType;
import com.edumentor.entity.enums.UserRole;
import com.edumentor.record.entity.Question;
import com.edumentor.record.repository.QuestionRepository;
import com.edumentor.student.entity.StudentProfile;
import com.edumentor.diagnosis.repository.StudentProfileRepository;
import com.edumentor.user.entity.User;
import com.edumentor.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 数据库初始数据填充工具。
 *
 * <p>实现 {@link CommandLineRunner}，在应用启动时自动填充初始数据。
 * 支持幂等性，检查数据是否已存在，避免重复插入。</p>
 *
 * @author EduMentor Team
 */
@Component
@Order(100)
@Profile({"dev", "default"})
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CourseRepository courseRepository;
    private final KnowledgePointRepository knowledgePointRepository;
    private final KnowledgeRelationRepository knowledgeRelationRepository;
    private final QuestionRepository questionRepository;
    private final StudentCourseRepository studentCourseRepository;
    private final CourseTeacherRepository courseTeacherRepository;

    public DataSeeder(UserRepository userRepository,
                      StudentProfileRepository studentProfileRepository,
                      CourseRepository courseRepository,
                      KnowledgePointRepository knowledgePointRepository,
                      KnowledgeRelationRepository knowledgeRelationRepository,
                      QuestionRepository questionRepository,
                      StudentCourseRepository studentCourseRepository,
                      CourseTeacherRepository courseTeacherRepository) {
        this.userRepository = userRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.courseRepository = courseRepository;
        this.knowledgePointRepository = knowledgePointRepository;
        this.knowledgeRelationRepository = knowledgeRelationRepository;
        this.questionRepository = questionRepository;
        this.studentCourseRepository = studentCourseRepository;
        this.courseTeacherRepository = courseTeacherRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("开始填充初始数据...");
        try {
            seedUsers();
            seedCourses();
            seedKnowledgePoints();
            seedQuestions();
            seedEnrollments();
            seedTeacherAssignments();
            log.info("✓ 所有初始数据填充完成");
        } catch (Exception e) {
            log.error("数据填充失败: {}", e.getMessage(), e);
        }
    }

    // ════════════════════════════════════════════
    //  用户数据
    // ════════════════════════════════════════════

    @Transactional
    public void seedUsers() {
        // 管理员
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User();
            admin.setUsername("admin");
            admin.setPasswordHash(PasswordUtil.encode("admin123"));
            admin.setDisplayName("系统管理员");
            admin.setRole(UserRole.ADMIN);
            admin.setEmail("admin@edumentor.cn");
            userRepository.save(admin);
        }

        // 教师
        if (!userRepository.existsByUsername("teacher01")) {
            User teacher = new User();
            teacher.setUsername("teacher01");
            teacher.setPasswordHash(PasswordUtil.encode("teacher123"));
            teacher.setDisplayName("张教授");
            teacher.setRole(UserRole.TEACHER);
            teacher.setEmail("zhang@edumentor.cn");
            userRepository.save(teacher);
        }

        // 学生
        List<Map<String, String>> studentsData = List.of(
                Map.of("username", "student01", "displayName", "李明", "grade", "大一", "class", "计科2101", "major", "计算机科学与技术", "dept", "计算机系", "college", "信息与计算机学院"),
                Map.of("username", "student02", "displayName", "王芳", "grade", "大一", "class", "计科2101", "major", "计算机科学与技术", "dept", "计算机系", "college", "信息与计算机学院"),
                Map.of("username", "student03", "displayName", "赵强", "grade", "大二", "class", "数科2101", "major", "数据科学", "dept", "统计系", "college", "理学院")
        );

        for (Map<String, String> s : studentsData) {
            if (!userRepository.existsByUsername(s.get("username"))) {
                User student = new User();
                student.setUsername(s.get("username"));
                student.setPasswordHash(PasswordUtil.encode("student123"));
                student.setDisplayName(s.get("displayName"));
                student.setRole(UserRole.STUDENT);
                userRepository.save(student);

                // 创建学生画像（含组织维度）
                StudentProfile profile = new StudentProfile();
                profile.setUserId(student.getId());
                profile.setGrade(s.get("grade"));
                profile.setLearningStyle("visual");
                profile.setClassName(s.get("class"));
                profile.setMajor(s.get("major"));
                profile.setDepartment(s.get("dept"));
                profile.setCollege(s.get("college"));
                studentProfileRepository.save(profile);
            }
        }

        log.info("✓ 用户数据填充完成");
    }

    // ════════════════════════════════════════════
    //  课程数据
    // ════════════════════════════════════════════

    @Transactional
    public void seedCourses() {
        if (!courseRepository.existsByName("高等数学（上）")
                && !courseRepository.existsByCourseCode("MATH101")) {
            User teacher = userRepository.findByUsername("teacher01").orElse(null);
            UUID teacherId = teacher != null ? teacher.getId() : null;

            if (teacherId == null) {
                log.warn("未找到教师用户，跳过课程填充");
                return;
            }

            List<Course> courses = List.of(
                    createCourse("高等数学（上）", "函数、极限、导数与微分、不定积分与定积分", "数学", "MATH101", teacherId),
                    createCourse("线性代数", "矩阵理论、向量空间、线性变换、特征值与特征向量", "数学", "MATH201", teacherId),
                    createCourse("Python程序设计", "Python基础语法、数据结构、面向对象编程、常用库", "计算机", "CS101", teacherId)
            );

            courseRepository.saveAll(courses);
        }

        log.info("✓ 课程数据填充完成");
    }

    private Course createCourse(String name, String description, String subject, String courseCode, UUID teacherId) {
        Course course = new Course();
        course.setCourseCode(courseCode);
        course.setName(name);
        course.setDescription(description);
        course.setSubject(subject);
        course.setCreatedBy(teacherId);
        course.setIsPublished(true);
        return course;
    }

    // ════════════════════════════════════════════
    //  知识点数据
    // ════════════════════════════════════════════

    @Transactional
    public void seedKnowledgePoints() {
        Course course = courseRepository.findByNameContainingIgnoreCase("高等数学", null)
                .getContent().stream().findFirst().orElse(null);
        if (course == null) {
            log.warn("未找到高等数学课程，跳过知识点填充");
            return;
        }

        long existingCount = knowledgePointRepository.countByCourseId(course.getId());
        if (existingCount > 0) {
            log.info("知识点数据已存在，跳过填充");
            return;
        }

        // 知识点数据
        List<KpData> kpsData = List.of(
                new KpData("函数的概念与性质", 1, 0.3, 0.9, 45, "concept"),
                new KpData("反函数与复合函数", 2, 0.4, 0.7, 30, "concept"),
                new KpData("数列极限", 3, 0.5, 0.8, 60, "concept"),
                new KpData("函数极限", 4, 0.5, 0.8, 60, "concept"),
                new KpData("极限运算法则", 5, 0.4, 0.9, 45, "method"),
                new KpData("两个重要极限", 6, 0.6, 0.8, 45, "formula"),
                new KpData("函数的连续性与间断点", 7, 0.5, 0.7, 45, "concept"),
                new KpData("导数的概念", 8, 0.5, 0.9, 45, "concept"),
                new KpData("求导法则与公式", 9, 0.4, 0.9, 60, "formula"),
                new KpData("高阶导数", 10, 0.6, 0.7, 45, "method"),
                new KpData("隐函数与参数方程求导", 11, 0.7, 0.7, 45, "method"),
                new KpData("微分中值定理", 12, 0.8, 0.8, 60, "concept"),
                new KpData("洛必达法则", 13, 0.6, 0.8, 45, "method"),
                new KpData("函数的单调性与极值", 14, 0.6, 0.8, 45, "application"),
                new KpData("不定积分的概念与性质", 15, 0.5, 0.8, 45, "concept"),
                new KpData("换元积分法", 16, 0.7, 0.9, 60, "method"),
                new KpData("分部积分法", 17, 0.7, 0.9, 60, "method"),
                new KpData("定积分的概念与性质", 18, 0.6, 0.8, 45, "concept"),
                new KpData("微积分基本公式", 19, 0.6, 0.9, 45, "formula"),
                new KpData("定积分的应用", 20, 0.7, 0.7, 60, "application")
        );

        Map<String, KnowledgePoint> kpMap = new LinkedHashMap<>();

        for (KpData kpData : kpsData) {
            KnowledgePoint kp = new KnowledgePoint();
            kp.setCourseId(course.getId());
            kp.setName(kpData.name);
            kp.setDescription(kpData.name + "是高等数学中的重要内容");
            kp.setDifficulty(kpData.difficultyToInt());
            kp.setImportance(kpData.importanceToInt());
            kp.setOrderIndex(kpData.orderIndex);
            kp.setTags("[\"" + kpData.category + "\"]");
            knowledgePointRepository.save(kp);
            kpMap.put(kpData.name, kp);
        }

        // 填充先修关系
        List<String[]> relations = List.of(
                new String[]{"函数的概念与性质", "反函数与复合函数"},
                new String[]{"函数的概念与性质", "数列极限"},
                new String[]{"函数的概念与性质", "函数极限"},
                new String[]{"数列极限", "函数极限"},
                new String[]{"函数极限", "极限运算法则"},
                new String[]{"极限运算法则", "两个重要极限"},
                new String[]{"函数极限", "函数的连续性与间断点"},
                new String[]{"函数的连续性与间断点", "导数的概念"},
                new String[]{"导数的概念", "求导法则与公式"},
                new String[]{"求导法则与公式", "高阶导数"},
                new String[]{"求导法则与公式", "隐函数与参数方程求导"},
                new String[]{"求导法则与公式", "微分中值定理"},
                new String[]{"微分中值定理", "洛必达法则"},
                new String[]{"导数的概念", "函数的单调性与极值"},
                new String[]{"导数的概念", "不定积分的概念与性质"},
                new String[]{"不定积分的概念与性质", "换元积分法"},
                new String[]{"不定积分的概念与性质", "分部积分法"},
                new String[]{"不定积分的概念与性质", "定积分的概念与性质"},
                new String[]{"定积分的概念与性质", "微积分基本公式"},
                new String[]{"微积分基本公式", "定积分的应用"}
        );

        for (String[] rel : relations) {
            KnowledgePoint source = kpMap.get(rel[0]);
            KnowledgePoint target = kpMap.get(rel[1]);
            if (source != null && target != null) {
                if (!knowledgeRelationRepository.existsBySourceKpIdAndTargetKpIdAndRelationType(
                        source.getId(), target.getId(), RelationType.PREREQUISITE)) {
                    KnowledgeRelation relation = new KnowledgeRelation();
                    relation.setSourceKpId(source.getId());
                    relation.setTargetKpId(target.getId());
                    relation.setRelationType(RelationType.PREREQUISITE);
                    knowledgeRelationRepository.save(relation);
                }
            }
        }

        log.info("✓ 知识点数据填充完成");
    }

    // ════════════════════════════════════════════
    //  题目数据
    // ════════════════════════════════════════════

    @Transactional
    public void seedQuestions() {
        // 获取第一个课程的所有知识点
        Course course = courseRepository.findByNameContainingIgnoreCase("高等数学", null)
                .getContent().stream().findFirst().orElse(null);
        if (course == null) return;

        Map<String, KnowledgePoint> kpMap = new HashMap<>();
        for (KnowledgePoint kp : knowledgePointRepository.findByCourseId(course.getId())) {
            kpMap.put(kp.getName(), kp);
        }

        // 题目数据
        List<QuestionData> questionsData = List.of(
                new QuestionData("函数的概念与性质",
                        "函数 $f(x) = \\\\frac{1}{x-1}$ 的定义域是？",
                        "{\"A\": \"(-∞, +∞)\", \"B\": \"(-∞, 1)∪(1, +∞)\", \"C\": \"(0, +∞)\", \"D\": \"(-∞, 0)∪(0, +∞)\"}",
                        "B", "分母不能为0，即 x-1≠0，解得 x≠1，故定义域为(-∞,1)∪(1,+∞)。", 0.3),
                new QuestionData("两个重要极限",
                        "极限 $\\\\lim_{x \\\\to 0} \\\\frac{\\\\sin x}{x}$ 的值为？",
                        "{\"A\": \"0\", \"B\": \"1\", \"C\": \"∞\", \"D\": \"-1\"}",
                        "B", "这是第一个重要极限，$\\\\lim_{x \\\\to 0} \\\\frac{\\\\sin x}{x} = 1$。", 0.4),
                new QuestionData("导数的概念",
                        "函数 $f(x) = x^2$ 在 $x=2$ 处的导数值为？",
                        "{\"A\": \"2\", \"B\": \"4\", \"C\": \"0\", \"D\": \"8\"}",
                        "B", "$f'(x) = 2x$，代入 $x=2$ 得 $f'(2) = 4$。", 0.3),
                new QuestionData("不定积分的概念与性质",
                        "求不定积分 $\\\\int 2x dx$",
                        "{\"A\": \"x^2 + C\", \"B\": \"x^2\", \"C\": \"2x^2 + C\", \"D\": \"x + C\"}",
                        "A", "$\\\\int 2x dx = 2 \\\\cdot \\\\frac{1}{2}x^2 + C = x^2 + C$。", 0.3),
                new QuestionData("函数的连续性与间断点",
                        "函数 $f(x)$ 在 $x_0$ 处可导是 $f(x)$ 在 $x_0$ 处连续的什么条件？",
                        "{\"A\": \"充分必要条件\", \"B\": \"充分不必要条件\", \"C\": \"必要不充分条件\", \"D\": \"既不充分也不必要条件\"}",
                        "B", "可导一定连续，但连续不一定可导。因此可导是连续的充分不必要条件。", 0.6),
                new QuestionData("换元积分法",
                        "使用换元积分法计算 $\\\\int \\\\frac{1}{x\\\\ln x} dx$",
                        "{\"A\": \"\\\\ln|\\\\ln x| + C\", \"B\": \"\\\\ln|x| + C\", \"C\": \"\\\\frac{1}{\\\\ln x} + C\", \"D\": \"\\\\ln|\\\\ln x|\\\\cdot\\\\ln|x| + C\"}",
                        "A", "令 $u = \\\\ln x$，则 $du = \\\\frac{1}{x}dx$，原积分化为 $\\\\int \\\\frac{1}{u} du = \\\\ln|u| + C = \\\\ln|\\\\ln x| + C$。", 0.7)
        );

        for (QuestionData q : questionsData) {
            KnowledgePoint kp = kpMap.get(q.kpName);
            if (kp != null && !questionRepository.existsByContentAndKnowledgePointId(q.content, kp.getId())) {
                Question question = new Question();
                question.setKnowledgePointId(kp.getId());
                question.setCourseId(course.getId());
                question.setQuestionType(QuestionType.SINGLE_CHOICE);
                question.setContent(q.content);
                question.setOptions(parseOptions(q.options));
                question.setCorrectAnswer(q.answer);
                question.setExplanation(q.explanation);
                question.setDifficulty(difficultyToInt(q.difficulty));
                questionRepository.save(question);
            }
        }

        log.info("✓ 题目数据填充完成");
    }

    private int difficultyToInt(double difficulty) {
        if (difficulty <= 0.3) return 2;
        if (difficulty <= 0.5) return 3;
        if (difficulty <= 0.7) return 4;
        return 5;
    }

    // ════════════════════════════════════════════
    //  内部数据结构
    // ════════════════════════════════════════════

    private record KpData(String name, int orderIndex, double difficulty, double importance,
                           int estimatedMinutes, String category) {
        int difficultyToInt() {
            if (difficulty <= 0.3) return 2;
            if (difficulty <= 0.5) return 3;
            if (difficulty <= 0.7) return 4;
            return 5;
        }
        int importanceToInt() {
            if (importance <= 0.4) return 2;
            if (importance <= 0.6) return 3;
            if (importance <= 0.8) return 4;
            return 5;
        }
    }

    private record QuestionData(String kpName, String content, String options,
                                 String answer, String explanation, double difficulty) {}

    private com.fasterxml.jackson.databind.JsonNode parseOptions(String optionsStr) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(optionsStr);
        } catch (Exception e) {
            log.warn("parseOptions error: {}", e.getMessage());
            return new com.fasterxml.jackson.databind.ObjectMapper().createArrayNode();
        }
    }

    @Transactional
    public void seedEnrollments() {
        User student01 = userRepository.findByUsername("student01").orElse(null);
        User student02 = userRepository.findByUsername("student02").orElse(null);
        if (student01 == null || student02 == null) return;

        List<Course> courses = courseRepository.findByCreatedByOrderByCreatedAtDesc(
                userRepository.findByUsername("teacher01").get().getId());
        if (courses.isEmpty()) return;

        for (Course course : courses) {
            for (User student : List.of(student01, student02)) {
                if (!studentCourseRepository.existsByStudentIdAndCourseId(student.getId(), course.getId())) {
                    StudentCourse sc = new StudentCourse();
                    sc.setStudentId(student.getId());
                    sc.setCourseId(course.getId());
                    sc.setCourseCode(course.getCourseCode());
                    sc.setStatus("active");
                    sc.setEnrolledAt(java.time.LocalDateTime.now());
                    studentCourseRepository.save(sc);
                }
            }
        }
        log.info("✓ 选课数据填充完成");
    }

    @Transactional
    public void seedTeacherAssignments() {
        User teacher = userRepository.findByUsername("teacher01").orElse(null);
        if (teacher == null) return;

        List<Course> courses = courseRepository.findByCreatedByOrderByCreatedAtDesc(teacher.getId());
        for (Course course : courses) {
            if (!courseTeacherRepository.existsByCourseIdAndTeacherId(course.getId(), teacher.getId())) {
                CourseTeacher ct = new CourseTeacher();
                ct.setCourseId(course.getId());
                ct.setTeacherId(teacher.getId());
                ct.setRole("lecturer");
                courseTeacherRepository.save(ct);
            }
        }
        log.info("✓ 教师分配数据填充完成");
    }
}
