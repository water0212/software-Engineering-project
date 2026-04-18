package com.water.fzfwificenter.UI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.water.fzfwificenter.analyzer.AnalyzerFactory;
import com.water.fzfwificenter.analyzer.LanguageAnalyzer;
import com.water.fzfwificenter.analyzer.ProgrammingLanguage;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import netscape.javascript.JSObject;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainScreen {

    private Stage stage;
    private WebEngine webEngine;
    private TextArea codeArea;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> fileCache = new HashMap<>();

    // 🚨 終極防護：宣告一個類別層級的變數來「死死抓住」橋樑，防止被 Java GC (垃圾回收)
    private JavaBridge javaBridge;

    public MainScreen(Stage stage) {
        this.stage = stage;
    }

    public Scene createScene() {
        BorderPane root = new BorderPane();

        // --- 1. 上方控制列 ---
        Label titleLabel = new Label("FZF Code Analyzer");
        titleLabel.getStyleClass().add("title-label");

        Button fileBtn = new Button("\uD83D\uDCC4 匯入檔案");
        fileBtn.getStyleClass().add("primary-btn");
        fileBtn.setOnAction(e -> handleImportFiles());

        Button dirBtn = new Button("\uD83D\uDCC1 匯入資料夾");
        dirBtn.getStyleClass().add("primary-btn");
        dirBtn.setOnAction(e -> handleImportDirectory());

        HBox topBar = new HBox(15, titleLabel, fileBtn, dirBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("top-bar");
        root.setTop(topBar);

        // --- 2. 中央：WebView ---
        WebView webView = new WebView();
        webEngine = webView.getEngine();
        webEngine.load(getClass().getResource("/index.html").toExternalForm());

        // 🚨 關鍵修正：將橋樑實體化並「存入我們宣告的變數」中，再傳給 JS
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) webEngine.executeScript("window");

                // 將實體存入全域變數，Java 就不會把它當垃圾丟掉
                this.javaBridge = new JavaBridge(this);
                window.setMember("javaApp", this.javaBridge);
            }
        });

        // 在 createScene 裡面加入這段，用來接收 JS 的 alert 除錯訊息
        webEngine.setOnAlert(event -> {
            System.out.println("[來自網頁的 Debug 訊息]: " + event.getData());
        });

        root.setCenter(webView);

        // --- 3. 右側：程式碼檢視區 ---
        codeArea = new TextArea();
        codeArea.setPromptText("// 點擊左側節點以檢視原始碼...");
        codeArea.setPrefWidth(400);
        codeArea.setEditable(false);
        root.setRight(codeArea);

        Scene scene = new Scene(root, 1200, 800);

        // 載入 CSS
        String cssUrl = getClass().getResource("/style.css").toExternalForm();
        scene.getStylesheets().add(cssUrl);

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
                        .map(Path::toFile)
                        .collect(Collectors.toList());
                processAndDisplay(files);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    // 實際串接邏輯
    private void processAndDisplay(List<File> files) {
        LanguageAnalyzer analyzer = AnalyzerFactory.getAnalyzer(ProgrammingLanguage.JAVA);
        List<Map<String, Object>> allElements = new ArrayList<>();
        fileCache.clear();

        for (File file : files) {
            try {
                String code = Files.readString(file.toPath());
                // 1. 丟給你的 JavaCodeAnalyzer 產生 JSON 字串
                String jsonStr = analyzer.analyze(code);

                // 2. 轉換為 Cytoscape 格式
                allElements.addAll(convertToGraphElements(jsonStr, file.getName()));

                // 暫存原始碼
                fileCache.put(file.getName(), code);

            } catch (Exception e) {
                System.err.println("分析失敗: " + file.getName());
            }
        }

        // 3. 轉換成最終 JSON 並丟給 WebView
        try {
            String finalElementsJson = mapper.writeValueAsString(allElements);
            webEngine.executeScript("renderGraph(" + finalElementsJson + ")");
        } catch (Exception e) { e.printStackTrace(); }
    }

    // 將你的 AnalysisResult 結構轉為點線結構
    private List<Map<String, Object>> convertToGraphElements(String jsonStr, String fileName) throws Exception {
        List<Map<String, Object>> elements = new ArrayList<>();
        JsonNode root = mapper.readTree(jsonStr);
        JsonNode classes = root.get("classes");

        if (classes != null && classes.isArray()) {
            for (JsonNode cls : classes) {
                String className = cls.get("className").asText();

                // 建立 Class 節點
                elements.add(createNode(className, className, "class", fileName));

                JsonNode methods = cls.get("methods");
                if (methods != null && methods.isArray()) {
                    for (JsonNode m : methods) {
                        String methodName = m.get("methodName").asText();
                        String methodId = className + "_" + methodName;

                        // 建立 Method 節點
                        elements.add(createNode(methodId, methodName, "method", fileName));
                        // 建立包含關係連線
                        elements.add(createEdge(className, methodId));
                    }
                }
            }
        }
        return elements;
    }

    private Map<String, Object> createNode(String id, String label, String type, String fileName) {
        Map<String, Object> node = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("id", id);
        data.put("label", label);
        data.put("type", type);
        data.put("fileName", fileName); // 存入檔名，方便點擊時找程式碼
        node.put("data", data);
        return node;
    }

    private Map<String, Object> createEdge(String source, String target) {
        Map<String, Object> edge = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("source", source);
        data.put("target", target);
        edge.put("data", data);
        return edge;
    }

    // 供 JavaScript 呼叫的方法：顯示程式碼
    public void showCodeInArea(String fileName) {
        String code = fileCache.get(fileName);
        if (code != null) {
            codeArea.setText(code);
        } else {
            // 如果找不到檔案，顯示錯誤訊息，避免畫面一片空白
            codeArea.setText("// 找不到對應的原始碼: " + fileName);
        }
    }
}