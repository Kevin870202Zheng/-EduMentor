package com.edumentor.timemachine.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * 创建时光机信件请求。
 */
@Getter
@Setter
public class TimeMachineLetterRequest {

    @NotNull
    private UUID studentId;

    /** 写信人所在学段（过去的自己） */
    private String stage;

    private UUID courseId;

    /** PAST_TO_NOW（默认）| NOW_TO_FUTURE */
    private String direction = "PAST_TO_NOW";

    /** 提问内容；留空则 AI 基于历史学习数据生成 */
    private String question;

    /** 仅生成提问（true 时不落库，供预览），默认 false */
    private Boolean generateOnly = false;
}
