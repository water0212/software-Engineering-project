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
                // 🚨 終極鎮壓版 Prompt：使用 XML 標籤隔離資料，並禁止翻譯 JSON 鍵值
                String systemPrompt = "你是一個專門分析 Java 程式碼並回傳固定格式 JSON 的 API 伺服器。\n" +
                        "請閱讀下方 <AST> 與 <CODE> 標籤內的資料，並分析其結構與邏輯。\n\n" +
                        "【嚴格規定】\n" +
                        "1. 只能回傳 JSON，絕對不能包含任何 Markdown (如 ```json) 或其他說明文字。\n" +
                        "2. JSON 的 Key 名稱【絕對禁止更改或翻譯】，必須維持英文。\n" +
                        "3. JSON 的 Value (內容) 必須使用【繁體中文】詳細解說。\n\n" +
                        "【強制輸出的 JSON 格式】\n" +
                        "{\n" +
                        "  \"className\": \"此檔案的類別名稱\",\n" +
                        "  \"classDescription\": \"(以繁體中文說明該類別的主要職責)\",\n" +
                        "  \"methods\": [\n" +
                        "    { \"methodName\": \"方法名稱\", \"description\": \"(以繁體中文說明該方法的邏輯與功能)\" }\n" +
                        "  ]\n" +
                        "}\n\n";

                // 將資料用 XML 標籤包起來，防止 AI 把「輸入的 JSON」跟「輸出的 JSON」搞混
                String fullPrompt = systemPrompt +
                        "<AST>\n" + astJson + "\n</AST>\n\n" +
                        "<CODE>\n" + javaCode + "\n</CODE>\n\n" +
                        "請直接輸出純 JSON 格式：";

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