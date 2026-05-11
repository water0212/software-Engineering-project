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
            highlightClasses(aiResponse.targetClassesJson());
        }));
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
            graphEngine.executeScript("highlightClasses(" + targetClassesJson + ")");
        } catch (Exception e) {
            System.err.println("Cytoscape 連動失敗: " + e.getMessage());
        }
    }

    private AiDisplayResponse parseAiDisplayResponse(String response) {
        String displayText = response;
        String targetClassesJson = "[]";

        int startIndex = response.indexOf("<TARGET_CLASSES>");
        int endIndex = response.indexOf("</TARGET_CLASSES>");

        if (startIndex != -1 && endIndex != -1) {
            targetClassesJson = response.substring(startIndex + 16, endIndex);
            displayText = response.substring(0, startIndex).trim();
        }

        return new AiDisplayResponse(displayText, targetClassesJson);
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
