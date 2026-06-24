package com.edumentor.knowledge.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量导入文档请求 DTO。
 *
 * @author EduMentor Team
 */
@Data
public class ImportRequest {

    @NotEmpty(message = "文档列表不能为空")
    @Valid
    private List<DocumentItem> documents;

    @Data
    public static class DocumentItem {
        private String title;
        private String content;
        private String source;
        private String courseId;
    }
}
