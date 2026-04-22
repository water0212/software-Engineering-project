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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainScreen {

    private final Stage stage;
    private WebEngine webEngine;      // 左側 Cytoscape 引擎
    private WebEngine monacoEngine;   // 右側 Monaco 編輯器引擎
    private TextArea aiArea;          // 右上方 AI 分析顯示區

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> fileCache = new HashMap<>(); // 存原始碼
    private final Map<String, String> jsonCache = new HashMap<>(); // 存 AST 結構

    // 🚨 終極防護：保持強引用，防止橋樑被 GC 垃圾回收
    private JavaBridge javaBridge;
    private final LLMService llmService = new LLMService();

    public MainScreen(Stage stage) {
        this.stage = stage;
    }

    public Scene createScene() {
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");

        // --- 1. 上方控制列 ---
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

        // --- 2. 左側：WebView (Cytoscape 圖表區) ---
        WebView webView = new WebView();
        webView.setMinWidth(400);
        webEngine = webView.getEngine();
        webEngine.load(Objects.requireNonNull(getClass().getResource("/index.html")).toExternalForm());

        // 注入 JavaBridge 並建立連結
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) webEngine.executeScript("window");
                this.javaBridge = new JavaBridge(this); // 存入變數防止 GC
                window.setMember("javaApp", javaBridge);
            }
        });

        // --- 3. 右側：AI 分析區 (上) + Monaco 編輯器 (下) ---

        // AI 顯示區
        aiArea = new TextArea();
        aiArea.setEditable(false);
        aiArea.setWrapText(true);
        aiArea.setPromptText("🤖 等待 AI 分析...");
        aiArea.setPrefHeight(220); // 調整高度
        aiArea.setStyle("-fx-control-inner-background: #1e252b; -fx-text-fill: #61afef; " +
                "-fx-font-family: 'Consolas'; -fx-font-size: 14px; -fx-padding: 10;");

        // Monaco 編輯器 WebView
        WebView monacoView = new WebView();
        monacoEngine = monacoView.getEngine();

        // 🚨 關鍵：加入除錯監聽
        monacoEngine.setOnAlert(event -> {
            System.out.println("[右側編輯器 Debug]: " + event.getData());
        });

        // 載入 HTML
        try {
            String url = Objects.requireNonNull(getClass().getResource("/editor.html")).toExternalForm();
            monacoEngine.load(url);
        } catch (Exception e) {
            System.err.println("🚨 找不到 editor.html！請確認檔案放在 src/main/resources 之下");
        }

        // 使用 BorderPane 組合右側面板
        BorderPane rightPane = new BorderPane();
        rightPane.setTop(aiArea);
        rightPane.setCenter(monacoView);

        // --- 4. SplitPane 分隔中央視窗 ---
        SplitPane splitPane = new SplitPane(webView, rightPane);
        splitPane.setDividerPositions(0.65); // 初始比例

        root.setCenter(splitPane);

        Scene scene = new Scene(root, 1280, 850);
        // 載入 CSS 樣式
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

        for (File file : files) {
            try {
                String code = Files.readString(file.toPath());
                String jsonStr = analyzer.analyze(code); // 執行靜態分析

                // 轉換為 Cytoscape 格式
                allElements.addAll(convertToGraphElements(jsonStr, file.getName()));

                // 存入快取
                fileCache.put(file.getName(), code);
                jsonCache.put(file.getName(), jsonStr);
            } catch (Exception e) {
                System.err.println("分析失敗: " + file.getName());
            }
        }

        try {
            String finalJson = mapper.writeValueAsString(allElements);
            // 確保 UI 更新在 JavaFX 執行緒
            Platform.runLater(() -> webEngine.executeScript("renderGraph(" + finalJson + ")"));
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 核心更新邏輯：處理 Monaco 載入與 LLM 分析
    public void updateCodeArea(String fileName) {
        String code = fileCache.get(fileName);
        String astJson = jsonCache.get(fileName);

        if (code != null) {
            Platform.runLater(() -> {
                // 1. 更新 AI 區狀態
                aiArea.setText("⏳ 正在結合靜態分析與原始碼進行 AI 解析 [" + fileName + "]...");

                // 2. 將程式碼安全地送到 Monaco (使用 Base64 防止字元衝突)
                try {
                    String base64Code = Base64.getEncoder().encodeToString(code.getBytes(StandardCharsets.UTF_8));
                    monacoEngine.executeScript("setCodeFromBase64('" + base64Code + "')");
                } catch (Exception e) {
                    System.err.println("Monaco 渲染失敗");
                }
            });

            // 3. 非同步呼叫 LLM (同時餵入代碼與結構)
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

    private List<Map<String, Object>> convertToGraphElements(String jsonStr, String fileName) throws Exception {
        List<Map<String, Object>> elements = new ArrayList<>();
        JsonNode root = mapper.readTree(jsonStr);
        JsonNode classes = root.get("classes");

        if (classes != null && classes.isArray()) {
            for (JsonNode cls : classes) {
                String className = cls.get("className").asText();
                elements.add(createNode(className, className, "class", fileName));
                JsonNode methods = cls.get("methods");
                if (methods != null && methods.isArray()) {
                    for (JsonNode m : methods) {
                        String mName = m.get("methodName").asText();
                        String mId = className + "_" + mName;
                        elements.add(createNode(mId, mName, "method", fileName));
                        elements.add(createEdge(className, mId));
                    }
                }
            }
        }
        return elements;
    }

    private Map<String, Object> createNode(String id, String label, String type, String fileName) {
        Map<String, Object> n = new HashMap<>();
        Map<String, Object> d = new HashMap<>();
        d.put("id", id); d.put("label", label); d.put("type", type); d.put("fileName", fileName);
        n.put("data", d);
        return n;
    }

    private Map<String, Object> createEdge(String s, String t) {
        Map<String, Object> e = new HashMap<>();
        Map<String, Object> d = new HashMap<>();
        d.put("source", s); d.put("target", t);
        e.put("data", d);
        return e;
    }

    private String formatLlmResult(String jsonStr) {
        try {
            // 1. 清理可能的 Markdown 標籤與雜訊
            String cleanJson = jsonStr.replaceAll("^```json\\s*", "").replaceAll("```$", "").trim();
            JsonNode root = mapper.readTree(cleanJson);

            // 處理錯誤訊息
            if (root.has("error")) return root.get("error").asText();

            // 2. 🚨 深度挖掘目標節點 (處理包在 response 或 classes 陣列的情況)
            JsonNode targetNode = root;
            if (root.has("response") && root.get("response").isObject()) {
                targetNode = root.get("response");
            } else if (root.has("classes") && root.get("classes").isArray() && root.get("classes").size() > 0) {
                targetNode = root.get("classes").get(0);
            } else if (root.has("分析結果") && root.get("分析結果").isObject()) {
                targetNode = root.get("分析結果");
            }

            // 3. 🚨 模糊欄位提取 (解決 AI 自作聰明翻譯 Key 的問題)
            String cName = getField(targetNode, "className", "類別名稱", "name");
            String cDesc = getField(targetNode, "classDescription", "類別職責", "description", "說明");

            // 4. 🚨 最終防呆：如果還是沒抓到核心資訊，顯示原始 response 內容
            if (cName.equals("未知") && root.has("response")) {
                return "🤖 AI 智慧分析報告：\n\n" + root.get("response").asText();
            }

            if (cName.equals("未知") && root.has("分析結果")) {
                return "🤖 AI 智慧分析報告：\n\n" + root.get("分析結果").asText();
            }

            // 5. 正式排版輸出
            StringBuilder sb = new StringBuilder();
            sb.append("📂 類別名稱: ").append(cName).append("\n");
            sb.append("📝 類別職責: ").append(cDesc).append("\n\n");
            sb.append("⚙️ 方法詳細說明:\n");

            // 處理方法列表的模糊匹配
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
                // 如果沒有方法數組但有長文本，把文本補在後面
                sb.append(targetNode.get("response").asText());
            }

            return sb.toString();

        } catch (Exception e) {
            return "❌ 無法解析 AI 回傳的資料 (格式嚴重跑掉):\n" + e.getMessage() + "\n\n【AI 原始輸出】:\n" + jsonStr;
        }
    }

    // 🚨 新增：模糊欄位獲取小工具
    private String getField(JsonNode node, String... keys) {
        for (String key : keys) {
            if (node.has(key) && !node.get(key).isNull()) {
                return node.get(key).asText();
            }
        }
        return "未知";
    }
}