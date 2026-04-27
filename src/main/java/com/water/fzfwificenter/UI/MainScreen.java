package com.water.fzfwificenter.UI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.water.fzfwificenter.analyzer.AnalyzerFactory;
import com.water.fzfwificenter.analyzer.LanguageAnalyzer;
import com.water.fzfwificenter.analyzer.ProgrammingLanguage;
import com.water.fzfwificenter.llm.LLMService;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainScreen {
    private static final Path OUTPUT_DIRECTORY = Path.of("src", "files");

    private final Stage stage;
    private WebEngine webEngine;
    private WebEngine monacoEngine;
    private TextArea aiArea;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> fileCache = new HashMap<>();
    private final Map<String, String> jsonCache = new HashMap<>();

    private JavaBridge javaBridge;
    private final LLMService llmService = new LLMService();

    public MainScreen(Stage stage) {
        this.stage = stage;
    }

    public Scene createScene() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        Label titleLabel = new Label("FZF Code Analyzer");
        titleLabel.getStyleClass().add("title-label");

        Button fileBtn = new Button("\uD83D\uDCC4 匯入檔案");
        fileBtn.getStyleClass().add("primary-btn");
        fileBtn.setOnAction(e -> handleImportFiles());

        Button dirBtn = new Button("\uD83D\uDCC1 匯入資料夾");
        dirBtn.getStyleClass().add("primary-btn");
        dirBtn.setOnAction(e -> handleImportDirectory());

        Button resetBtn = new Button("重置視角");
        resetBtn.setOnAction(e -> webEngine.executeScript("cy.fit()"));

        HBox topBar = new HBox(15, titleLabel, fileBtn, dirBtn, resetBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("top-bar");
        root.setTop(topBar);

        WebView webView = new WebView();
        webView.setMinWidth(400);
        webEngine = webView.getEngine();
        webEngine.load(Objects.requireNonNull(getClass().getResource("/index.html")).toExternalForm());

        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) webEngine.executeScript("window");
                this.javaBridge = new JavaBridge(this);
                window.setMember("javaApp", javaBridge);
            }
        });

        aiArea = new TextArea();
        aiArea.setEditable(false);
        aiArea.setWrapText(true);
        aiArea.setPromptText("🤖 等待 AI 分析...");
        aiArea.setPrefHeight(220);
        aiArea.setStyle("-fx-control-inner-background: #1e252b; -fx-text-fill: #61afef; " +
                "-fx-font-family: 'Consolas'; -fx-font-size: 14px; -fx-padding: 10;");

        WebView monacoView = new WebView();
        monacoEngine = monacoView.getEngine();

        monacoEngine.setOnAlert(event -> {
            System.out.println("[右側編輯器 Debug]: " + event.getData());
        });

        try {
            String url = Objects.requireNonNull(getClass().getResource("/editor.html")).toExternalForm();
            monacoEngine.load(url);
        } catch (Exception e) {
            System.err.println("🚨 找不到 editor.html！請確認檔案放在 src/main/resources 之下");
        }

        BorderPane rightPane = new BorderPane();
        rightPane.setTop(aiArea);
        rightPane.setCenter(monacoView);

        SplitPane splitPane = new SplitPane(webView, rightPane);
        splitPane.setDividerPositions(0.65);

        root.setCenter(splitPane);

        Scene scene = new Scene(root, 1280, 850);
        scene.getStylesheets().add(Objects.requireNonNull(getClass().getResource("/style.css")).toExternalForm());
        return scene;
    }

    private void handleImportFiles() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java Files", "*.java"));
        List<File> files = chooser.showOpenMultipleDialog(stage);
        if (files != null) processAndDisplay(files);
    }

    private void handleImportDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        File dir = chooser.showDialog(stage);
        if (dir != null) {
            try (Stream<Path> paths = Files.walk(dir.toPath())) {
                List<File> files = paths.filter(p -> p.toString().endsWith(".java"))
                        .map(Path::toFile).collect(Collectors.toList());
                processAndDisplay(files);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void processAndDisplay(List<File> files) {
        LanguageAnalyzer analyzer = AnalyzerFactory.getAnalyzer(ProgrammingLanguage.JAVA);
        List<Map<String, Object>> allElements = new ArrayList<>();
        fileCache.clear();
        jsonCache.clear();
        prepareOutputDirectory();

        for (File file : files) {
            try {
                String code = Files.readString(file.toPath());
                String jsonStr = analyzer.analyze(code);
                saveAnalysisJson(file, jsonStr);
                allElements.addAll(convertToGraphElements(jsonStr, file.getName()));
                fileCache.put(file.getName(), code);
                jsonCache.put(file.getName(), jsonStr);
            } catch (Exception e) {
                System.err.println("分析失敗: " + file.getName());
            }
        }

        try {
            String finalJson = mapper.writeValueAsString(allElements);
            Platform.runLater(() -> webEngine.executeScript("renderGraph(" + finalJson + ")"));
        } catch (Exception e) { e.printStackTrace(); }
    }


    public void handleNodeSelectionWithAnalyzeStatus(String payload) {
        String[] parts = payload.split("\\|\\|\\|");
        String fileName = parts[0];
        String nodeType = parts.length > 1 ? parts[1] : "file";
        String nodeName = parts.length > 2 ? parts[2] : fileName;

        String code = fileCache.get(fileName);
        String fallbackJson = jsonCache.get(fileName);

        if (code == null) {
            Platform.runLater(() -> aiArea.setText(buildFileAnalysisFailedMessage(fileName)));
            return;
        }

        Platform.runLater(() -> {
            String displayType = nodeType.equals("method") ? "方法" : (nodeType.equals("class") ? "類別" : "檔案");
            aiArea.setText("正在針對 " + displayType + " [" + nodeName + "] 進行 AI 分析...");
            try {
                String base64Code = Base64.getEncoder().encodeToString(code.getBytes(StandardCharsets.UTF_8));
                monacoEngine.executeScript("setCodeFromBase64('" + base64Code + "')");
            } catch (Exception e) {
                System.err.println("Monaco 渲染失敗");
            }
        });

        try {
            String persistedJson = loadPersistedAnalysisJson(fileName, fallbackJson);
            if (isAnalyzeMarkedFailed(persistedJson)) {
                Platform.runLater(() -> aiArea.setText(buildFileAnalysisFailedMessage(fileName)));
                return;
            }

            if (hasMissingClassDescription(persistedJson)) {
                String focusedAstJson = attachFocusTarget(persistedJson, nodeType, nodeName);
                llmService.analyzeCodeAsync(code, focusedAstJson).thenAccept(llmJsonResult -> {
                    Platform.runLater(() -> {
                        try {
                            if (isLlmAnalysisFailed(llmJsonResult)) {
                                persistAnalyzeFailure(fileName, persistedJson);
                                aiArea.setText(buildFileAnalysisFailedMessage(fileName));
                                return;
                            }

                            String mergedJson = mergeAndPersistAnalysis(fileName, persistedJson, llmJsonResult, nodeName);
                            aiArea.setText(buildDisplayText(mergedJson, nodeType, nodeName));
                        } catch (Exception e) {
                            persistAnalyzeFailure(fileName, persistedJson);
                            aiArea.setText(buildFileAnalysisFailedMessage(fileName));
                        }
                    });
                });
            } else {
                String displayText = buildDisplayText(persistedJson, nodeType, nodeName);
                Platform.runLater(() -> aiArea.setText(displayText));
            }
        } catch (Exception e) {
            Platform.runLater(() -> aiArea.setText(buildFileAnalysisFailedMessage(fileName)));
        }
    }

    private List<Map<String, Object>> convertToGraphElements(String jsonStr, String fileName) throws Exception {
        List<Map<String, Object>> elements = new ArrayList<>();
        JsonNode root = mapper.readTree(jsonStr);
        JsonNode classes = root.path("classes");

        // 1. 建立 File 節點 (最外層，沒有 Parent)
        elements.add(createNode(fileName, fileName, "file", fileName, null, "none"));

        if (classes.isArray()) {
            for (JsonNode cls : classes) {
                String className = cls.path("className").asText();
                // 為了避免不同檔案有同名 Class 造成 ID 衝突，加上 fileName 前綴
                String classId = fileName + "_" + className;

                // 2. 建立 Class 節點 (Parent 設定為 fileName)
                elements.add(createNode(classId, className, "class", fileName, fileName, "none"));

                JsonNode methods = cls.path("methods");
                if (methods.isArray()) {
                    for (JsonNode m : methods) {
                        String mName = m.path("methodName").asText();
                        String mId = classId + "_" + mName;

                        // 3. 判斷資料流向 (基礎關鍵字判定)
                        String flow = "none";
                        String mNameLower = mName.toLowerCase();
                        if (mNameLower.startsWith("get") || mNameLower.contains("load") || mNameLower.contains("read")) {
                            flow = "input";
                        } else if (mNameLower.startsWith("set") || mNameLower.contains("save") || mNameLower.contains("write") || mNameLower.contains("render")) {
                            flow = "output";
                        }

                        // 4. 建立 Method 節點，並設定其 parent 為 classId
                        elements.add(createNode(mId, mName, "method", fileName, classId, flow));
                    }
                }
            }
        }
        return elements;
    }


    private void saveAnalysisJson(File sourceFile, String jsonStr) throws Exception {
        String normalizedJson = updateAnalyzeFlag(jsonStr, true);
        writeJsonFile(OUTPUT_DIRECTORY.resolve(toJsonFileName(sourceFile.getName())), normalizedJson);
    }

    private void prepareOutputDirectory() {
        try {
            if (Files.exists(OUTPUT_DIRECTORY)) {
                try (Stream<Path> paths = Files.walk(OUTPUT_DIRECTORY)) {
                    paths.sorted(Comparator.reverseOrder())
                            .filter(path -> !path.equals(OUTPUT_DIRECTORY))
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                }
            }

            Files.createDirectories(OUTPUT_DIRECTORY);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to prepare files directory", e);
        }
    }

    private String toJsonFileName(String sourceFileName) {
        return sourceFileName.endsWith(".java")
                ? sourceFileName.substring(0, sourceFileName.length() - 5) + ".json"
                : sourceFileName + ".json";
    }

    private void writeJsonFile(Path outputPath, String jsonStr) throws Exception {
        Files.writeString(
                outputPath,
                jsonStr,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        );
    }

    // 🚨 修改重點：新增 parent 和 flow 參數
    private String loadPersistedAnalysisJson(String fileName, String fallbackJson) throws Exception {
        Path jsonPath = OUTPUT_DIRECTORY.resolve(toJsonFileName(fileName));
        if (Files.exists(jsonPath)) {
            String persistedJson = Files.readString(jsonPath, StandardCharsets.UTF_8);
            jsonCache.put(fileName, persistedJson);
            return persistedJson;
        }
        String normalizedFallbackJson = updateAnalyzeFlag(fallbackJson, true);
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
        writeJsonFile(OUTPUT_DIRECTORY.resolve(toJsonFileName(fileName)), mergedJson);
        jsonCache.put(fileName, mergedJson);
        return mergedJson;
    }

    private boolean isAnalyzeMarkedFailed(String jsonStr) throws Exception {
        JsonNode root = mapper.readTree(jsonStr);
        return root.has("analyze") && !root.path("analyze").asBoolean(true);
    }

    private void persistAnalyzeFailure(String fileName, String jsonStr) {
        try {
            String failedJson = updateAnalyzeFlag(jsonStr, false);
            writeJsonFile(OUTPUT_DIRECTORY.resolve(toJsonFileName(fileName)), failedJson);
            jsonCache.put(fileName, failedJson);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String updateAnalyzeFlag(String jsonStr, boolean analyzeValue) throws Exception {
        JsonNode rootNode = mapper.readTree(jsonStr);
        if (rootNode instanceof ObjectNode objectNode) {
            objectNode.put("analyze", analyzeValue);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectNode);
        }
        return jsonStr;
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
        sb.append("🤖【分析結果】\n");
        sb.append("📂 類別名稱: ").append(getField(targetClass, "className", "name")).append("\n");
        sb.append("📝 類別職責: ").append(getField(targetClass, "classDescription", "description", "說明")).append("\n\n");

        if ("method".equals(nodeType)) {
            JsonNode methodNode = findMethodNode(targetClass.path("methods"), nodeName);
            if (methodNode != null) {
                sb.append("⚙方法詳細說明:\n");
                sb.append("  •").append(getField(methodNode, "methodName", "name")).append("()\n");
                sb.append("    ➔").append(getField(methodNode, "description", "說明")).append("\n");
                return sb.toString();
            }
        }

        sb.append("⚙方法詳細說明:\n");
        JsonNode methods = targetClass.path("methods");
        if (methods.isArray()) {
            for (JsonNode methodNode : methods) {
                sb.append("  •").append(getField(methodNode, "methodName", "name")).append("()\n");
                sb.append("    ➔").append(getField(methodNode, "description", "說明")).append("\n\n");
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

    private String[] parsePayload(String payload) {
        String[] parts = payload.split("\\|\\|\\|");
        String fileName = parts[0];
        String nodeType = parts.length > 1 ? parts[1] : "file";
        String nodeName = parts.length > 2 ? parts[2] : fileName;
        return new String[]{fileName, nodeType, nodeName};
    }

    private Map<String, Object> createNode(String id, String label, String type, String fileName, String parent, String flow) {
        Map<String, Object> n = new HashMap<>();
        Map<String, Object> d = new HashMap<>();
        d.put("id", id);
        d.put("label", label);
        d.put("type", type);
        d.put("fileName", fileName);
        if (parent != null) d.put("parent", parent);
        if (flow != null) d.put("flow", flow);
        n.put("data", d);
        return n;
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
            sb.append("📂 類別名稱: ").append(cName).append("\n");
            sb.append("📝 類別職責: ").append(cDesc).append("\n\n");
            sb.append("⚙ 方法詳細說明:\n");

            JsonNode methods = targetNode.has("methods") ? targetNode.get("methods") : targetNode.get("方法清單");
            if (methods == null && targetNode.has("methods")) methods = targetNode.get("methods");

            if (methods != null && methods.isArray()) {
                for (JsonNode m : methods) {
                    String mName = getField(m, "methodName", "方法名稱", "name");
                    String mDesc = getField(m, "description", "功能描述", "說明", "解釋");
                    sb.append("  • ").append(mName).append("()\n");
                    sb.append("    ➔ ").append(mDesc).append("\n\n");
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
