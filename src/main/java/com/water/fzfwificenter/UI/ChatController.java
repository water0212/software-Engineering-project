package com.water.fzfwificenter.UI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.water.fzfwificenter.llm.LLMService;
import javafx.application.Platform;
import javafx.scene.web.WebEngine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

class ChatController {
    private final WebEngine chatEngine;
    private final WebEngine graphEngine;
    private final LLMService llmService;
    private final ObjectMapper mapper;

    ChatController(WebEngine chatEngine, WebEngine graphEngine, LLMService llmService, ObjectMapper mapper) {
        this.chatEngine = chatEngine;
        this.graphEngine = graphEngine;
        this.llmService = llmService;
        this.mapper = mapper;
    }

    void addChatMessage(String role, String text) {
        Platform.runLater(() -> {
            String escapedText = text.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                    .replace("\r", "");
            chatEngine.executeScript("appendMessage('" + role + "', '" + escapedText + "')");
        });
    }

    CompletableFuture<Void> askAiAndDisplayResponse(String query, String globalContext) {
        if (globalContext.isEmpty() || globalContext.equals("[]")) {
            addChatMessage("ai", "請先匯入 Java 檔案或資料夾，我才能回答您的問題喔！");
            return CompletableFuture.completedFuture(null);
        }

        return requestAiDisplayResponse(query, globalContext).thenAccept(aiResponse -> Platform.runLater(() -> {
            addChatMessage("ai", aiResponse.displayText());
            // 🚨 改為呼叫帶有箭頭資料的方法
            highlightAndDrawFlow(aiResponse.targetClassesJson(), aiResponse.dataFlowJson());
        }));
    }

    // 2. 新增 highlightAndDrawFlow 方法 (取代原本的 highlightClasses)
    void highlightAndDrawFlow(String targetClassesJson, String dataFlowJson) {
        try {
            graphEngine.executeScript("highlightAndDrawFlow(" + targetClassesJson + ", " + dataFlowJson + ")");
        } catch (Exception e) {
            System.err.println("Cytoscape 連動失敗: " + e.getMessage());
        }
    }

    CompletableFuture<AiDisplayResponse> requestAiDisplayResponse(String query, String globalContext) {
        return llmService.answerChatQueryAsync(query, globalContext)
                .thenApply(this::parseAiDisplayResponse);
    }

    CompletableFuture<List<String>> suggestQuestions(String globalContext) {
        if (globalContext.isEmpty() || globalContext.equals("[]")) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }
        return llmService.suggestProjectQuestionsAsync(globalContext)
                .thenApply(this::parseSuggestedQuestions);
    }

    void setChatChips(List<String> questions) {
        try {
            chatEngine.executeScript("setChips(" + mapper.writeValueAsString(questions) + ")");
        } catch (Exception e) {
            System.err.println("更新推薦問題失敗: " + e.getMessage());
        }
    }

    void showImportLoading() {
        try {
            chatEngine.executeScript("showLoading('正在分析專案...')");
        } catch (Exception e) {
            System.err.println("顯示 loading 失敗: " + e.getMessage());
        }
    }

    void hideImportLoading() {
        try {
            chatEngine.executeScript("hideLoading()");
        } catch (Exception e) {
            System.err.println("隱藏 loading 失敗: " + e.getMessage());
        }
    }

    void highlightClasses(String targetClassesJson) {
        try {
            // 🚨 將原本呼叫的 highlightClasses 改為新的 highlightAndDrawFlow，並預設傳入空陣列 [] 給箭頭
            graphEngine.executeScript("highlightAndDrawFlow(" + targetClassesJson + ", [])");
        } catch (Exception e) {
            System.err.println("Cytoscape 連動失敗: " + e.getMessage());
        }
    }

    // 3. 修改 parseAiDisplayResponse，使用 Regex 安全地抽離兩組標籤
    private AiDisplayResponse parseAiDisplayResponse(String response) {
        String targetClassesJson = "[]";
        String dataFlowJson = "[]";

        // 抽取 TARGET_CLASSES
        java.util.regex.Matcher tm = java.util.regex.Pattern.compile("(?s)<TARGET_CLASSES>(.*?)</TARGET_CLASSES>").matcher(response);
        if (tm.find()) targetClassesJson = tm.group(1).trim();

        // 抽取 DATA_FLOW
        java.util.regex.Matcher fm = java.util.regex.Pattern.compile("(?s)<DATA_FLOW>(.*?)</DATA_FLOW>").matcher(response);
        if (fm.find()) dataFlowJson = fm.group(1).trim();

        // 將這兩組標籤從顯示文字中徹底抹除
        String displayText = response.replaceAll("(?s)<TARGET_CLASSES>.*?</TARGET_CLASSES>", "")
                .replaceAll("(?s)<DATA_FLOW>.*?</DATA_FLOW>", "")
                .trim();

        return new AiDisplayResponse(displayText, targetClassesJson, dataFlowJson);
    }

    private List<String> parseSuggestedQuestions(String response) {
        try {
            String json = cleanJsonPayload(response);
            JsonNode root = mapper.readTree(json);
            if (!root.isArray()) {
                return Collections.emptyList();
            }

            List<String> questions = new ArrayList<>();
            for (JsonNode node : root) {
                String question = limitQuestionLength(node.asText("").trim(), 10);
                if (!question.isEmpty()) {
                    questions.add(question);
                }
                if (questions.size() == 3) {
                    break;
                }
            }
            return questions;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String limitQuestionLength(String question, int maxLength) {
        if (question.codePointCount(0, question.length()) <= maxLength) {
            return question;
        }
        int endIndex = question.offsetByCodePoints(0, maxLength);
        return question.substring(0, endIndex);
    }

    private String cleanJsonPayload(String jsonStr) {
        return jsonStr.replaceAll("^```json\\s*", "").replaceAll("```$", "").trim();
    }
}
