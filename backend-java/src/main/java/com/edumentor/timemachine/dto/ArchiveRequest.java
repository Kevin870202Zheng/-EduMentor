package com.edumentor.timemachine.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * 手动归档快照请求。
 */
@Getter
@Setter
public class ArchiveRequest {

    @NotNull
    private UUID studentId;

    /** 归档时所在学段；缺省取学生画像当前学段 */
    private String stage;

    private UUID courseId;
}
