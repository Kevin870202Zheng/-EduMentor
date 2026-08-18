package com.edumentor.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * TTS 语音合成服务 — 代理 edge-tts 微服务。
 * <p>
 * 链路：调用方 → /api/tts/synthesize → tts-service（edge-tts）→ 下载音频缓存到本地 → 返回可访问 URL
 * </p>
 */
@Service
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public TtsService(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    @Value("${tts.service-url:http://localhost:5080}")
    private String serviceUrl;

    @Value("${tts.cache-dir:./tts-cache}")
    private String cacheDir;

    @Value("${tts.default-voice:zh-CN-YunxiNeural}")
    private String defaultVoice;

    /** 合成结果 */
    public record TtsResult(String audioUrl, int durationMs, String format) {}

    /**
     * 合成文本语音：调 tts-service 合成 → 下载 mp3 缓存到本地 → 返回后端可访问 URL。
     *
     * @throws IllegalStateException tts-service 不可用或合成失败
     */
    public TtsResult synthesize(String text, String voice, Double rate) {
        String finalVoice = (voice == null || voice.isBlank() || "default".equals(voice))
                ? defaultVoice : voice;
        double finalRate = (rate == null || rate <= 0) ? 0.95 : Math.min(2.0, Math.max(0.5, rate));

        try {
            // 1. 请求 tts-service 合成
            Map<String, Object> reqBody = new LinkedHashMap<>();
            reqBody.put("text", text);
            reqBody.put("voice", finalVoice);
            reqBody.put("rate", finalRate);
            String respBody = restClient.post()
                    .uri(serviceUrl + "/synthesize")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(reqBody)
                    .retrieve()
                    .body(String.class);
            if (respBody == null || respBody.isBlank()) {
                throw new IllegalStateException("tts-service 返回空响应");
            }
            JsonNode resp = objectMapper.readTree(respBody);
            String remoteAudioUrl = resp.path("audioUrl").asText();
            int durationMs = resp.path("durationMs").asInt(2000);

            // 2. 下载音频字节
            byte[] audio = restClient.get()
                    .uri(serviceUrl + remoteAudioUrl)
                    .retrieve()
                    .body(byte[].class);
            if (audio == null || audio.length == 0) {
                throw new IllegalStateException("tts-service 音频为空");
            }

            // 3. 本地缓存（md5 命名，幂等）
            String key = md5(finalVoice + "|" + finalRate + "|" + text);
            Path dir = Paths.get(cacheDir);
            Files.createDirectories(dir);
            Path file = dir.resolve(key + ".mp3");
            if (!Files.exists(file)) {
                Files.write(file, audio);
            }

            return new TtsResult("/api/tts/audio/" + key + ".mp3", Math.max(durationMs, 500), "mp3");
        } catch (IOException e) {
            log.warn("TTS synthesize I/O error: {}", e.getMessage());
            throw new IllegalStateException("语音服务暂不可用（网络异常）");
        } catch (Exception e) {
            log.warn("TTS synthesize failed: {}", e.getMessage());
            throw new IllegalStateException("语音服务暂不可用");
        }
    }

    /** 获取中文音色列表（透传 tts-service） */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> listVoices() {
        try {
            String respBody = restClient.get()
                    .uri(serviceUrl + "/voices")
                    .retrieve()
                    .body(String.class);
            JsonNode resp = objectMapper.readTree(respBody == null ? "{}" : respBody);
            JsonNode voices = resp.path("voices");
            if (voices.isArray()) {
                return objectMapper.convertValue(voices, List.class);
            }
        } catch (Exception e) {
            log.warn("TTS voices fetch failed, use local fallback: {}", e.getMessage());
        }
        // 本地兜底（与 tts-service 保持一致）
        return List.of(
                Map.of("voiceId", "zh-CN-YunxiNeural", "name", "云希（男 · 讲师）", "gender", "男", "style", "沉稳清晰"),
                Map.of("voiceId", "zh-CN-YunjianNeural", "name", "云健（男 · 新闻）", "gender", "男", "style", "专业播报"),
                Map.of("voiceId", "zh-CN-YunyangNeural", "name", "云扬（男 · 活力）", "gender", "男", "style", "阳光热情"),
                Map.of("voiceId", "zh-CN-YunxiaNeural", "name", "云夏（男 · 少年）", "gender", "男", "style", "青春明快"),
                Map.of("voiceId", "zh-CN-XiaoxiaoNeural", "name", "晓晓（女 · 温暖）", "gender", "女", "style", "亲切柔和"),
                Map.of("voiceId", "zh-CN-XiaoyiNeural", "name", "晓伊（女 · 活泼）", "gender", "女", "style", "灵动俏皮"),
                Map.of("voiceId", "zh-CN-XiaomoNeural", "name", "晓墨（女 · 知性）", "gender", "女", "style", "温婉知性"),
                Map.of("voiceId", "zh-CN-XiaohanNeural", "name", "晓涵（女 · 温柔）", "gender", "女", "style", "轻柔舒缓"));
    }

    /** 解析音频文件路径（防路径穿越：仅允许 32 位 hex 的 mp3） */
    public Path resolveAudioFile(String fileName) {
        if (fileName == null || !fileName.matches("[a-f0-9]{32}\\.mp3")) {
            throw new IllegalArgumentException("非法音频文件名");
        }
        return Paths.get(cacheDir).resolve(fileName).normalize();
    }

    /** 服务可用性探测 */
    public boolean isAvailable() {
        try {
            String resp = restClient.get().uri(serviceUrl + "/health").retrieve().body(String.class);
            return resp != null && resp.contains("UP");
        } catch (Exception e) {
            return false;
        }
    }

    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 不可用", e);
        }
    }
}

