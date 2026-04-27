package com.water.fzfwificenter.UI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainScreen {
    private static final Path RESOURCE_DIRECTORY = Path.of("src");
    private static final Path SOURCE_DIRECTORY = Path.of("src");

    private final Stage stage;
    private WebEngine webEngine;
    private WebEngine monacoEngine;
    private TextArea aiArea;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> fileCache = new HashMap<>();
    private final Map<String, String> jsonCache = new HashMap<>();

    private JavaBridge javaBridge;
    private final LLMService llmService = new LLMService();

    private enum SaveMode {
        FILE,
        DIRECTORY
    }

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
        if (files != null) processAndDisplay(files, SaveMode.FILE, null);
    }

    private void handleImportDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        File dir = chooser.showDialog(stage);
        if (dir != null) {
            try (Stream<Path> paths = Files.walk(dir.toPath())) {
                List<File> files = paths.filter(p -> p.toString().endsWith(".java"))
                        .map(Path::toFile).collect(Collectors.toList());
                processAndDisplay(files, SaveMode.DIRECTORY, dir.toPath());
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void processAndDisplay(List<File> files, SaveMode saveMode, Path sourceDirectory) {
        LanguageAnalyzer analyzer = AnalyzerFactory.getAnalyzer(ProgrammingLanguage.JAVA);
        List<Map<String, Object>> allElements = new ArrayList<>();
        fileCache.clear();
        jsonCache.clear();

        for (File file : files) {
            try {
                String code = Files.readString(file.toPath());
                String jsonStr = analyzer.analyze(code);
                saveAnalysisJson(file, jsonStr, saveMode, sourceDirectory);
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

    public void updateCodeArea(String fileName) {
        String code = fileCache.get(fileName);
        String astJson = jsonCache.get(fileName);

        if (code != null) {
            Platform.runLater(() -> {
                aiArea.setText("⏳ 正在結合靜態分析與原始碼進行 AI 解析 [" + fileName + "]...");
                try {
                    String base64Code = Base64.getEncoder().encodeToString(code.getBytes(StandardCharsets.UTF_8));
                    monacoEngine.executeScript("setCodeFromBase64('" + base64Code + "')");
                } catch (Exception e) {
                    System.err.println("Monaco 渲染失敗");
                }
            });

            llmService.analyzeCodeAsync(code, astJson).thenAccept(llmJsonResult -> {
                Platform.runLater(() -> {
                    try {
                        String formattedAnalysis = formatLlmResult(llmJsonResult);
                        aiArea.setText("🤖 【AI 智慧分析結果】\n\n" + formattedAnalysis);
                    } catch (Exception e) {
                        aiArea.setText("❌ AI 回傳資料解析失敗\n回傳內容：" + llmJsonResult);
                    }
                });
            });
        } else {
            Platform.runLater(() -> aiArea.setText("❌ 找不到原始碼: " + fileName));
        }
    }

    // 🚨 修改重點：加入父子節點關係與 Flow 屬性
    private List<Map<String, Object>> convertToGraphElements(String jsonStr, String fileName) throws Exception {
        List<Map<String, Object>> elements = new ArrayList<>();
        JsonNode root = mapper.readTree(jsonStr);
        JsonNode classes = root.path("classes");

        if (classes.isArray()) {
            for (JsonNode cls : classes) {
                String className = cls.path("className").asText();
                // 1. 建立 Class 節點 (沒有 Parent)
                elements.add(createNode(className, className, "class", fileName, null, "none"));

                JsonNode methods = cls.path("methods");
                if (methods.isArray()) {
                    for (JsonNode m : methods) {
                        String mName = m.path("methodName").asText();
                        String mId = className + "_" + mName;

                        // 2. 判斷資料流向 (基礎關鍵字判定)
                        String flow = "none";
                        String mNameLower = mName.toLowerCase();
                        if (mNameLower.startsWith("get") || mNameLower.contains("load") || mNameLower.contains("read")) {
                            flow = "input";
                        } else if (mNameLower.startsWith("set") || mNameLower.contains("save") || mNameLower.contains("write") || mNameLower.contains("render")) {
                            flow = "output";
                        }

                        // 3. 建立 Method 節點，並設定其 parent 為 className
                        elements.add(createNode(mId, mName, "method", fileName, className, flow));

                        // 注意：因為已經被包在 Class 複合節點內了，就不再需要 Edge，畫面會更乾淨！
                    }
                }
            }
        }
        return elements;
    }

    private void saveAnalysisJson(File sourceFile, String jsonStr, SaveMode saveMode, Path sourceDirectory) throws Exception {
        if (saveMode == SaveMode.FILE) {
            saveSingleJsonFile(sourceFile.getName(), jsonStr);
            return;
        }

        saveDirectoryJsonFile(sourceFile.getName(), jsonStr, resolveDirectoryOutputPath(sourceDirectory));
    }

    private void saveSingleJsonFile(String sourceFileName, String jsonStr) throws Exception {
        Files.createDirectories(RESOURCE_DIRECTORY);
        writeJsonFile(RESOURCE_DIRECTORY.resolve(toJsonFileName(sourceFileName)), jsonStr);
    }

    private void saveDirectoryJsonFile(String sourceFileName, String jsonStr, Path outputDirectory) throws Exception {
        Files.createDirectories(outputDirectory);
        writeJsonFile(outputDirectory.resolve(toJsonFileName(sourceFileName)), jsonStr);
    }

    private Path resolveDirectoryOutputPath(Path sourceDirectory) {
        String folderName = sourceDirectory != null ? sourceDirectory.getFileName().toString() : "output";
        return SOURCE_DIRECTORY.resolve(folderName);
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
                return "🤖 AI 智慧分析報告：\n\n" + root.get("response").asText();
            }
            if (cName.equals("未知") && root.has("分析結果")) {
                return "🤖 AI 智慧分析報告：\n\n" + root.get("分析結果").asText();
            }

            StringBuilder sb = new StringBuilder();
            sb.append("📂 類別名稱: ").append(cName).append("\n");
            sb.append("📝 類別職責: ").append(cDesc).append("\n\n");
            sb.append("⚙️ 方法詳細說明:\n");

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
            return "❌ 無法解析 AI 回傳的資料:\n" + e.getMessage() + "\n\n【AI 原始輸出】:\n" + jsonStr;
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
