package com.edumentor.peer.dto;

import com.edumentor.question.dto.QuestionDto;

import java.util.List;
import java.util.UUID;

/**
 * 单题答题统计 — 出题者视角查看每题的正确率。
 */
public record PeerQuizQuestionResultDto(
        QuestionDto question,
        int totalAnswers,
        int correctCount,
        double correctRate,
        List<StudentAnswerDetail> answers
) {
    public record StudentAnswerDetail(
            UUID studentId,
            String studentName,
            String studentAnswer,
            boolean isCorrect
    ) {}
}
