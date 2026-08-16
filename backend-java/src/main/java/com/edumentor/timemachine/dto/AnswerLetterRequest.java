package com.edumentor.timemachine.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * 回答信件请求。
 */
@Getter
@Setter
public class AnswerLetterRequest {

    @NotBlank
    private String answer;
}
