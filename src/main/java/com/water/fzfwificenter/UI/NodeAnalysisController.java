package com.water.fzfwificenter.UI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.water.fzfwificenter.llm.LLMService;
import javafx.application.Platform;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

class NodeAnalysisController {
    private final Path outputDirectory;
    private final ObjectMapper mapper;
    private final LLMService llmService;
    private final ProjectImportService projectImportService;
    private final ChatController chatController;
    private final Map<String, String> fileCache;
    private final Map<String, String> jsonCache;

    NodeAnalysisController(
            Path outputDirectory,
            ObjectMapper mapper,
            LLMService llmService,
            ProjectImportService projectImportService,
            ChatController chatController,
            Map<String, String> fileCache,
            Map<String, String> jsonCache
    ) {
        this.outputDirectory = outputDirectory;
        this.mapper = mapper;
        this.llmService = llmService;
        this.projectImportService = projectImportService;
        this.chatController = chatController;
        this.fileCache = fileCache;
        this.jsonCache = jsonCache;
    }

    void handleNodeSelectionWithAnalyzeStatus(String payload) {
        String[] parts = payload.split("\\|\\|\\|");
        String fileName = parts[0];
        String nodeType = parts.length > 1 ? parts[1] : "file";
        String nodeName = parts.length > 2 ? parts[2] : fileName;

        String code = fileCache.get(fileName);
        String fallbackJson = jsonCache.get(fileName);

        if (code == null) {
            Platform.runLater(() -> chatController.addChatMessage("ai", buildFileAnalysisFailedMessage(fileName)));
            return;
        }

        Platform.runLater(() -> {
            String displayType = nodeType.equals("method") ? "方法" : (nodeType.equals("class") ? "類別" : "檔案");
            chatController.addChatMessage("user", "請幫我分析 " + displayType + "：「**" + nodeName + "**」");
        });

        try {
            String persistedJson = loadPersistedAnalysisJson(fileName, fallbackJson);
            if (isAnalyzeMarkedFailed(persistedJson)) {
                Platform.runLater(() -> chatController.addChatMessage("ai", buildFileAnalysisFailedMessage(fileName)));
                return;
            }

            if (hasMissingClassDescription(persistedJson)) {
                String focusedAstJson = attachFocusTarget(persistedJson, nodeType, nodeName);
                // 讀取 dep.json（若存在），並一併傳給 LLM
                    String projectDepsJson = "";
                    try {
                        Path depPath = outputDirectory.resolve("dep.json");
                        if (Files.exists(depPath)) projectDepsJson = Files.readString(depPath, StandardCharsets.UTF_8);
                    } catch (Exception ignored) {}

                    llmService.analyzeCodeAsync(code, focusedAstJson, projectDepsJson).thenAccept(llmJsonResult -> {
                    Platform.runLater(() -> {
                        try {
                            if (isLlmAnalysisFailed(llmJsonResult)) {
                                persistAnalyzeFailure(fileName, persistedJson);
                                chatController.addChatMessage("ai", buildFileAnalysisFailedMessage(fileName));
                                return;
                            }

                            String mergedJson = mergeAndPersistAnalysis(fileName, persistedJson, llmJsonResult, nodeName);
                            chatController.addChatMessage("ai", buildDisplayText(mergedJson, nodeType, nodeName));
                        } catch (Exception e) {
                            persistAnalyzeFailure(fileName, persistedJson);
                            chatController.addChatMessage("ai", buildFileAnalysisFailedMessage(fileName));
                        }
                    });
                });
            } else {
                String displayText = buildDisplayText(persistedJson, nodeType, nodeName);
                Platform.runLater(() -> chatController.addChatMessage("ai", displayText));
            }
        } catch (Exception e) {
            Platform.runLater(() -> chatController.addChatMessage("ai", buildFileAnalysisFailedMessage(fileName)));
        }
    }

    private String loadPersistedAnalysisJson(String fileName, String fallbackJson) throws Exception {
        Path jsonPath = outputDirectory.resolve(projectImportService.toJsonFileName(fileName));
        if (Files.exists(jsonPath)) {
            String persistedJson = Files.readString(jsonPath, StandardCharsets.UTF_8);
            jsonCache.put(fileName, persistedJson);
            return persistedJson;
        }
        String normalizedFallbackJson = projectImportService.updateAnalyzeFlag(fallbackJson, true);
        jsonCache.put(fileName, normalizedFallbackJson);
        return normalizedFallbackJson;
    }

    private boolean hasMissingClassDescription(String jsonStr) throws Exception {
        JsonNode root = mapper.readTree(jsonStr);
        JsonNode classes = root.path("classes");
        if (!classes.isArray() || classes.isEmpty()) {
            return true;
        }

        for (JsonNode classNode : classes) {
            if (classNode.path("classDescription").asText("").trim().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private String attachFocusTarget(String astJson, String nodeType, String nodeName) throws Exception {
        JsonNode rootNode = mapper.readTree(astJson);
        if (rootNode instanceof ObjectNode objectNode) {
            objectNode.put(
                    "USER_FOCUS_TARGET",
                    "Please prioritize the clicked " + nodeType + " named " + nodeName
                            + ", and fill classDescription plus methods.description inside classes."
            );
            return mapper.writeValueAsString(objectNode);
        }
        return astJson;
    }

    private String mergeAndPersistAnalysis(String fileName, String baseJson, String llmJsonResult, String nodeName) throws Exception {
        JsonNode baseRootNode = mapper.readTree(baseJson);
        if (!(baseRootNode instanceof ObjectNode astRoot)) {
            return baseJson;
        }

        JsonNode llmRoot = mapper.readTree(cleanJsonPayload(llmJsonResult));
        JsonNode llmContent = extractLlmContent(llmRoot);
        mergeClassDescriptions(astRoot, llmContent, nodeName);

        astRoot.put("analyze", true);
        String mergedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(astRoot);
        projectImportService.writeJsonFile(outputDirectory.resolve(projectImportService.toJsonFileName(fileName)), mergedJson);
        jsonCache.put(fileName, mergedJson);
        return mergedJson;
    }

    private boolean isAnalyzeMarkedFailed(String jsonStr) throws Exception {
        JsonNode root = mapper.readTree(jsonStr);
        return root.has("analyze") && !root.path("analyze").asBoolean(true);
    }

    private void persistAnalyzeFailure(String fileName, String jsonStr) {
        try {
            String failedJson = projectImportService.updateAnalyzeFlag(jsonStr, false);
            projectImportService.writeJsonFile(outputDirectory.resolve(projectImportService.toJsonFileName(fileName)), failedJson);
            jsonCache.put(fileName, failedJson);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String cleanJsonPayload(String jsonStr) {
        return jsonStr.replaceAll("^```json\\s*", "").replaceAll("```$", "").trim();
    }

    private JsonNode extractLlmContent(JsonNode llmRoot) {
        if (llmRoot.has("response") && llmRoot.get("response").isObject()) {
            return llmRoot.get("response");
        }
        return llmRoot;
    }

    private void mergeClassDescriptions(ObjectNode astRoot, JsonNode llmContent, String nodeName) {
        JsonNode classesNode = astRoot.path("classes");
        if (!(classesNode instanceof ArrayNode classesArray)) {
            return;
        }

        ObjectNode targetClass = findTargetClass(classesArray, llmContent.path("className").asText(""), nodeName);
        if (targetClass == null) {
            return;
        }

        String classDescription = llmContent.path("classDescription").asText("").trim();
        if (!classDescription.isEmpty()) {
            targetClass.put("classDescription", classDescription);
        }

        mergeMethodDescriptions(targetClass, llmContent.path("methods"));
    }

    private ObjectNode findTargetClass(ArrayNode classesArray, String llmClassName, String nodeName) {
        for (JsonNode classNode : classesArray) {
            if (classNode instanceof ObjectNode objectNode) {
                String className = classNode.path("className").asText("");
                if (!llmClassName.isBlank() && llmClassName.equals(className)) {
                    return objectNode;
                }
                if (nodeName.equals(className)) {
                    return objectNode;
                }
            }
        }

        if (classesArray.size() == 1 && classesArray.get(0) instanceof ObjectNode objectNode) {
            return objectNode;
        }
        return null;
    }

    private void mergeMethodDescriptions(ObjectNode targetClass, JsonNode llmMethods) {
        if (!(targetClass.path("methods") instanceof ArrayNode astMethods) || !llmMethods.isArray()) {
            return;
        }

        for (JsonNode llmMethod : llmMethods) {
            String methodName = llmMethod.path("methodName").asText("");
            String description = llmMethod.path("description").asText("").trim();
            if (methodName.isEmpty() || description.isEmpty()) {
                continue;
            }

            for (JsonNode astMethod : astMethods) {
                if (astMethod instanceof ObjectNode astMethodObject
                        && methodName.equals(astMethod.path("methodName").asText(""))) {
                    astMethodObject.put("description", description);
                    break;
                }
            }
        }
    }

    private String buildDisplayText(String jsonStr, String nodeType, String nodeName) throws Exception {
        JsonNode root = mapper.readTree(jsonStr);
        JsonNode classes = root.path("classes");
        if (!classes.isArray() || classes.isEmpty()) {
            return formatLlmResult(jsonStr);
        }

        JsonNode targetClass = findDisplayClass(classes, nodeType, nodeName);
        if (targetClass == null) {
            targetClass = classes.get(0);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("### 📂 類別名稱: ").append(getField(targetClass, "className", "name")).append("\n");
        sb.append("**📝 類別職責**: ").append(getField(targetClass, "classDescription", "description", "說明")).append("\n\n");

        if ("method".equals(nodeType)) {
            JsonNode methodNode = findMethodNode(targetClass.path("methods"), nodeName);
            if (methodNode != null) {
                sb.append("#### ⚙ 方法詳細說明:\n");
                sb.append("- **").append(getField(methodNode, "methodName", "name")).append("()**\n");
                sb.append("  > ").append(getField(methodNode, "description", "說明")).append("\n");
                return sb.toString();
            }
        }

        sb.append("#### ⚙ 方法詳細說明:\n");
        JsonNode methods = targetClass.path("methods");
        if (methods.isArray()) {
            for (JsonNode methodNode : methods) {
                sb.append("- **").append(getField(methodNode, "methodName", "name")).append("()**\n");
                sb.append("  > ").append(getField(methodNode, "description", "說明")).append("\n\n");
            }
        }
        return sb.toString();
    }

    private JsonNode findDisplayClass(JsonNode classes, String nodeType, String nodeName) {
        if ("class".equals(nodeType)) {
            for (JsonNode classNode : classes) {
                if (nodeName.equals(classNode.path("className").asText(""))) {
                    return classNode;
                }
            }
        }

        if ("method".equals(nodeType)) {
            for (JsonNode classNode : classes) {
                if (findMethodNode(classNode.path("methods"), nodeName) != null) {
                    return classNode;
                }
            }
        }

        return classes.get(0);
    }

    private JsonNode findMethodNode(JsonNode methods, String nodeName) {
        if (!methods.isArray()) {
            return null;
        }

        for (JsonNode methodNode : methods) {
            if (nodeName.equals(methodNode.path("methodName").asText(""))) {
                return methodNode;
            }
        }
        return null;
    }

    private boolean isLlmAnalysisFailed(String llmJsonResult) {
        try {
            JsonNode root = mapper.readTree(cleanJsonPayload(llmJsonResult));
            return root.has("error");
        } catch (Exception e) {
            return true;
        }
    }

    private String buildFileAnalysisFailedMessage(String fileName) {
        return "【分析 '" + fileName + "' 失敗 】";
    }

    private String formatLlmResult(String jsonStr) {
        try {
            String cleanJson = jsonStr.replaceAll("^```json\\s*", "").replaceAll("```$", "").trim();
            JsonNode root = mapper.readTree(cleanJson);
            if (root.has("error")) return root.get("error").asText();

            JsonNode targetNode = root;
            if (root.has("response") && root.get("response").isObject()) {
                targetNode = root.get("response");
            } else if (root.has("classes") && root.get("classes").isArray() && root.get("classes").size() > 0) {
                targetNode = root.get("classes").get(0);
            } else if (root.has("分析結果") && root.get("分析結果").isObject()) {
                targetNode = root.get("分析結果");
            }

            String cName = getField(targetNode, "className", "類別名稱", "name");
            String cDesc = getField(targetNode, "classDescription", "類別職責", "description", "說明");

            if (cName.equals("未知") && root.has("response")) {
                return "🤖 分析報告：\n\n" + root.get("response").asText();
            }
            if (cName.equals("未知") && root.has("分析結果")) {
                return "🤖 分析報告：\n\n" + root.get("分析結果").asText();
            }

            StringBuilder sb = new StringBuilder();
            sb.append("### 📂 類別名稱: ").append(cName).append("\n");
            sb.append("**📝 類別職責**: ").append(cDesc).append("\n\n");
            sb.append("#### ⚙ 方法詳細說明:\n");

            JsonNode methods = targetNode.has("methods") ? targetNode.get("methods") : targetNode.get("方法清單");
            if (methods == null && targetNode.has("methods")) methods = targetNode.get("methods");

            if (methods != null && methods.isArray()) {
                for (JsonNode m : methods) {
                    String mName = getField(m, "methodName", "方法名稱", "name");
                    String mDesc = getField(m, "description", "功能描述", "說明", "解釋");
                    sb.append("- **").append(mName).append("()**\n");
                    sb.append("  > ").append(mDesc).append("\n\n");
                }
            } else if (targetNode.has("response")) {
                sb.append(targetNode.get("response").asText());
            }
            return sb.toString();
        } catch (Exception e) {
            return "❌ 無法解析回傳的資料:\n" + e.getMessage() + "\n\n【AI 原始輸出】:\n" + jsonStr;
        }
    }

    private String getField(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                return node.get(key).asText();
            }
        }
        return "未知";
    }
}
