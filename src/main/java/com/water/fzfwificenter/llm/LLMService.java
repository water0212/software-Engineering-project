package com.water.fzfwificenter.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class LLMService {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;

    public LLMService() {
        // 建立一個有 30 秒超時限制的 HttpClient (如果模型跑比較久，可以改為 60 秒)
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .build();
        this.mapper = new ObjectMapper();
    }

    // 🚨 修改 1：加入 astJson 參數
    public CompletableFuture<String> analyzeCodeAsync(String javaCode, String astJson) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 🚨 修改 2：升級 Prompt，明確告知有結構圖和原始碼
                String systemPrompt = "你是一個資深的 Java 軟體架構師。我將提供給你一份 Java 檔案的「靜態分析結構圖 (JSON)」以及它的「完整原始碼」。\n" +
                        "請結合兩者的資訊，嚴格以 JSON 格式回傳類別與方法的繁體中文功能說明。絕對不要輸出任何解釋性文字或 Markdown 標記 (如 ```json)。\n" +
                        "回傳格式範例：\n" +
                        "{ \"className\": \"名稱\", \"classDescription\": \"類別綜合說明...\", \"methods\": [ { \"methodName\": \"方法名\", \"description\": \"方法說明...\" } ] }\n\n";

                // 將結構圖和程式碼組合在一起
                String fullPrompt = systemPrompt +
                        "【靜態分析結構圖】\n" + astJson + "\n\n" +
                        "【完整原始碼】\n" + javaCode;

                // 處理字串跳脫，準備 JSON Payload
                String safePrompt = fullPrompt.replace("\\", "\\\\")
                        .replace("\"", "\\\"")
                        .replace("\n", "\\n")
                        .replace("\r", "");

                String requestBody = "{\n" +
                        "  \"model\": \"qwen2.5-coder\",\n" +
                        "  \"prompt\": \"" + safePrompt + "\",\n" +
                        "  \"stream\": false,\n" +
                        "  \"format\": \"json\"\n" +
                        "}";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:11434/api/generate"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                JsonNode rootNode = mapper.readTree(response.body());
                return rootNode.get("response").asText();

            } catch (Exception e) {
                e.printStackTrace();
                return "{\"error\": \"LLM 分析失敗：" + e.getMessage() + "\"}";
            }
        });
    }
}