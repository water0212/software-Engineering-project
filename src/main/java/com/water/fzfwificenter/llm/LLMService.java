package com.water.fzfwificenter.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class LLMService {

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private static final String API_KEY = System.getenv("GEMINI_API_KEY");

    // 🚨 API 端點不變
    private static final String API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + API_KEY;

    public LLMService() {
        // 加上一個防呆機制：如果忘記設定環境變數，會在控制台印出警告
        if (API_KEY == null || API_KEY.isBlank()) {
            System.err.println("🚨 嚴重錯誤：找不到環境變數 GEMINI_API_KEY！請確認是否已正確設定。");
        }

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(60))
                .build();
        this.mapper = new ObjectMapper();
    }
    // 🚨 新增：專門處理自由聊天的 API (帶有系統護欄與 Cytoscape 連動標籤)
    public CompletableFuture<String> answerChatQueryAsync(String userQuery, String globalProjectJson) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 系統護欄與人設
                String systemPrompt =
                        "【核心職責】\n" +
                                "你只能基於目前提供的 Java 專案內容、分析結果、類別資訊、方法資訊與依賴關係來回答問題。\n\n" +
                                "【允許的任務範圍】\n" +
                                "1. 分析專案中的 class、method、field、dependency、call flow、模組關係。\n" +
                                "2. 解釋專案內既有程式碼的邏輯、用途與流程。\n" +
                                "3. 根據專案內容產出延伸資料，例如：\n" +
                                "   - 專案摘要\n" +
                                "   - 功能說明\n" +
                                "   - README 草稿\n" +
                                "   - 測試案例建議\n" +
                                "   - 重構建議\n" +
                                "   - 專案相關翻譯\n" +
                                "4. 若使用者要求生成內容，必須明確以目前專案內容為基礎，不可脫離專案上下文。\n\n" +
                                "【拒絕條件】\n" +
                                "1. 若使用者要求與此專案無關的內容，例如閒聊、一般翻譯、無關寫作、與專案無關的新程式碼，必須拒絕。\n" +
                                "2. 若問題需要的資訊不存在於目前專案內容中，不得捏造類別、檔案、方法或流程。\n" +
                                "3. 若資料不足，必須明確指出缺少哪些資訊。\n\n" +
                                "【回答原則】\n" +
                                "1. 優先引用已知的類別、檔案、方法名稱。\n" +
                                "2. 不得虛構不存在的檔案或類別。\n" +
                                "3. 若使用者需求屬於「專案延伸任務」，可以執行，但要清楚說明是根據現有專案資訊整理或推導。"+
                                "【Cytoscape 畫面連動規則】\n" +
                                "為了在前端視覺化，你必須在回答的最後，嚴格使用以下兩種標籤輸出 JSON 陣列：\n" +
                                "1. 牽涉類別：<TARGET_CLASSES>[\"ClassA\", \"ClassB\"]</TARGET_CLASSES>\n" +
                                "2. 資料流向：如果你判斷出類別之間有呼叫、依賴或資料傳遞關係，請標示出箭頭（若無則輸出 []）：\n" +
                                "<DATA_FLOW>[{\"source\":\"ClassA\", \"target\":\"ClassB\", \"label\":\"呼叫 / 查詢\"}]</DATA_FLOW>\n\n" +
                                "【專案結構 JSON】\n" + globalProjectJson;

                ObjectNode requestBodyNode = mapper.createObjectNode();

                ObjectNode systemInstruction = requestBodyNode.putObject("systemInstruction");
                systemInstruction.putArray("parts").addObject().put("text", systemPrompt);

                ArrayNode contents = requestBodyNode.putArray("contents");
                contents.addObject().putArray("parts").addObject().put("text", userQuery);

                ObjectNode generationConfig = requestBodyNode.putObject("generationConfig");
                generationConfig.put("temperature", 0.3); // 聊天可稍微提高一點點隨機性，但保持精準

                String requestBody = mapper.writeValueAsString(requestBodyNode);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode rootNode = mapper.readTree(response.body());

                if (rootNode.has("error")) {
                    return "❌ API 發生錯誤：" + rootNode.path("error").path("message").asText();
                }

                return rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

            } catch (Exception e) {
                e.printStackTrace();
                return "❌ LLM 聊天分析失敗：" + e.getMessage();
            }
        });
    }

    public CompletableFuture<String> suggestProjectQuestionsAsync(String globalProjectJson) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String systemPrompt =
                        "你是一個 Java 專案分析助手。請只根據提供的專案結構 JSON，推薦使用者接下來最可能想問的 3 個問題。\n" +
                                "問題必須具體、與這個專案內容相關、適合用來理解架構、核心流程、資料流、重要類別或潛在改善方向。\n" +
                                "每個問題最多 10 個字，請用短句，不要加標點符號。\n" +
                                "請只輸出 JSON Array，不要輸出 Markdown、編號、解釋或其他文字。\n" +
                                "格式範例：[\"核心功能\", \"資料流向\", \"主要類別\"]\n\n" +
                                "【專案結構 JSON】\n" + globalProjectJson;

                ObjectNode requestBodyNode = mapper.createObjectNode();

                ObjectNode systemInstruction = requestBodyNode.putObject("systemInstruction");
                systemInstruction.putArray("parts").addObject().put("text", systemPrompt);

                ArrayNode contents = requestBodyNode.putArray("contents");
                contents.addObject().putArray("parts").addObject().put("text", "請產生 3 個推薦問題。");

                ObjectNode generationConfig = requestBodyNode.putObject("generationConfig");
                generationConfig.put("temperature", 0.4);
                generationConfig.put("responseMimeType", "application/json");

                String requestBody = mapper.writeValueAsString(requestBodyNode);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonNode rootNode = mapper.readTree(response.body());

                if (rootNode.has("error")) {
                    return "[]";
                }

                JsonNode candidates = rootNode.path("candidates");
                if (candidates.isArray() && candidates.size() > 0) {
                    return candidates.get(0).path("content").path("parts").get(0).path("text").asText();
                }

                return "[]";
            } catch (Exception e) {
                e.printStackTrace();
                return "[]";
            }
        });
    }

    // 🚨 把 MainScreen.java 需要的方法加回來了！並升級為 Gemini API 版本
    public CompletableFuture<String> analyzeCodeAsync(String javaCode, String astJson) {
        // 向後相容：若沒有提供 dep 資訊，呼叫三參數版本
        return analyzeCodeAsync(javaCode, astJson, null);
    }

    public CompletableFuture<String> analyzeCodeAsync(String javaCode, String astJson, String projectDepsJson) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 1. 定義 System Prompt (嚴格要求輸出 JSON)
                String systemPrompt = "你是一個專門分析 Java 程式碼並回傳固定格式 JSON 的專家。\n" +
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
                        "}";

                // 2. 組合使用者輸入
                StringBuilder userPromptBuilder = new StringBuilder();
                userPromptBuilder.append("<AST>\n").append(astJson).append("\n</AST>\n\n");
                if (projectDepsJson != null && !projectDepsJson.isBlank()) {
                    userPromptBuilder.append("<PROJECT_DEPS>\n").append(projectDepsJson).append("\n</PROJECT_DEPS>\n\n");
                }
                userPromptBuilder.append("<CODE>\n").append(javaCode).append("\n</CODE>\n\n請直接輸出純 JSON 格式：");
                String userPrompt = userPromptBuilder.toString();

                // 3. 建立 Gemini JSON Payload
                ObjectNode requestBodyNode = mapper.createObjectNode();

                // 系統提示
                ObjectNode systemInstruction = requestBodyNode.putObject("systemInstruction");
                systemInstruction.putArray("parts").addObject().put("text", systemPrompt);

                // 使用者輸入
                ArrayNode contents = requestBodyNode.putArray("contents");
                contents.addObject().putArray("parts").addObject().put("text", userPrompt);

                // 🚨 設定 Generation Config (強制輸出 JSON 格式)
                ObjectNode generationConfig = requestBodyNode.putObject("generationConfig");
                generationConfig.put("temperature", 0.1); // 降低隨機性，保證格式穩定
                generationConfig.put("responseMimeType", "application/json");

                String requestBody = mapper.writeValueAsString(requestBodyNode);

                // 4. 發送請求
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                // 5. 解析回傳資料
                JsonNode rootNode = mapper.readTree(response.body());

                if (rootNode.has("error")) {
                    return "{\"error\": \"API 發生錯誤：" + rootNode.path("error").path("message").asText() + "\"}";
                }

                JsonNode candidates = rootNode.path("candidates");
                if (candidates.isArray() && candidates.size() > 0) {
                    return candidates.get(0).path("content").path("parts").get(0).path("text").asText();
                }

                return "{\"error\": \"無法解析 Gemini 回傳的格式\"}";

            } catch (Exception e) {
                e.printStackTrace();
                return "{\"error\": \"LLM 分析發生例外狀況：" + e.getMessage() + "\"}";
            }
        });
    }

    // 保留相容性：如果你之前有使用 analyzeLargeFileAsync
    public CompletableFuture<String> analyzeLargeFileAsync(String code, String astJson) {
        return analyzeCodeAsync(code, astJson);
    }



}
