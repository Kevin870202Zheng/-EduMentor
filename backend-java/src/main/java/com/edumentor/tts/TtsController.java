package com.edumentor.tts;

import com.edumentor.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * TTS 语音合成控制器。
 * <p>
 * API:
 *   POST /api/tts/synthesize  {text, voice?, rate?} → {audioUrl, durationMs, format}
 *   GET  /api/tts/audio/{file}.mp3  返回音频文件（permitAll，前端 new Audio() 不带 token）
 *   GET  /api/tts/voices     中文音色列表
 * </p>
 */
@RestController
@RequestMapping("/api/tts")
@Tag(name = "TTS 语音合成", description = "edge-tts 语音合成代理：课堂讲解音频生成、音色选择")
public class TtsController {

    private static final Logger log = LoggerFactory.getLogger(TtsController.class);

    private final TtsService ttsService;

    public TtsController(TtsService ttsService) {
        this.ttsService = ttsService;
    }

    /** 合成请求体（与前端 ttsService 契约一致：voiceId；language 忽略） */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record SynthesizeRequest(String text, String voiceId, Double rate) {}

    @PostMapping("/synthesize")
    @Operation(summary = "文本合成语音", description = "调用 edge-tts 合成中文语音，返回音频 URL 与时长")
    public ApiResponse<Map<String, Object>> synthesize(@RequestBody SynthesizeRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            return ApiResponse.error(400, "text 不能为空");
        }
        if (request.text().length() > 2000) {
            return ApiResponse.error(400, "文本过长（≤2000 字）");
        }
        try {
            TtsService.TtsResult result = ttsService.synthesize(
                    request.text().trim(), request.voiceId(), request.rate());
            return ApiResponse.success(Map.of(
                    "audioUrl", result.audioUrl(),
                    "durationMs", result.durationMs(),
                    "format", result.format()));
        } catch (IllegalArgumentException e) {
            return ApiResponse.error(400, e.getMessage());
        } catch (Exception e) {
            log.warn("TTS synthesize unavailable: {}", e.getMessage());
            return ApiResponse.error(503, "语音服务暂不可用，已切换为浏览器语音");
        }
    }

    @GetMapping("/audio/{fileName}")
    @Operation(summary = "获取合成音频文件", description = "返回缓存的 mp3 音频（无需认证，供 Audio 播放）")
    public ResponseEntity<Resource> audio(@PathVariable String fileName) {
        try {
            Path path = ttsService.resolveAudioFile(fileName);
            if (!Files.exists(path)) {
                return ResponseEntity.notFound().build();
            }
            Resource resource = new FileSystemResource(path);
            // 按实际格式返回正确 content-type（espeak-ng 兜底为 wav，edge-tts 为 mp3）
            String contentType = fileName.toLowerCase().endsWith(".wav") ? "audio/wav" : "audio/mpeg";
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .header("Cache-Control", "public, max-age=86400")
                    .body(resource);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/voices")
    @Operation(summary = "中文音色列表", description = "返回可选的 TTS 音色（默认男声云希）")
    public ApiResponse<Map<String, Object>> voices() {
        List<Map<String, Object>> voices = ttsService.listVoices();
        return ApiResponse.success(Map.of(
                "default", "zh-CN-YunxiNeural",
                "voices", voices));
    }
}

