package com.edumentor.seeder;

import com.edumentor.common.response.ApiResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据填充 REST API — 手动触发数据初始化。
 *
 * @author EduMentor Team
 */
@RestController
@RequestMapping("/api/v1/seeder")
@Profile({"dev", "default"})
public class SeederController {

    private final DataSeeder dataSeeder;

    public SeederController(DataSeeder dataSeeder) {
        this.dataSeeder = dataSeeder;
    }

    /**
     * 手动触发所有初始数据填充。
     *
     * @return 操作结果
     */
    @PostMapping("/run")
    public ApiResponse<Map<String, Object>> runSeeder() {
        Map<String, Object> result = new LinkedHashMap<>();

        try {
            dataSeeder.seedUsers();
            result.put("users", "✓");

            dataSeeder.seedCourses();
            result.put("courses", "✓");

            dataSeeder.seedKnowledgePoints();
            result.put("knowledgePoints", "✓");

            dataSeeder.seedQuestions();
            result.put("questions", "✓");

            result.put("status", "completed");
            return ApiResponse.success(result, "数据填充完成");
        } catch (Exception e) {
            result.put("status", "failed");
            result.put("error", e.getMessage());
            return ApiResponse.error(500, "数据填充失败: " + e.getMessage(), result);
        }
    }
}
