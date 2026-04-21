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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainScreen {

    private final Stage stage;
    private WebEngine webEngine;
    private TextArea codeArea;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> fileCache = new HashMap<>();

    // 🚨 重要：保持強引用，防止橋樑被 GC 掉
    private JavaBridge javaBridge;

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

        // --- 2. WebView (圖表區) ---
        WebView webView = new WebView();
        webView.setMinWidth(400);
        webEngine = webView.getEngine();
        webEngine.load(Objects.requireNonNull(getClass().getResource("/index.html")).toExternalForm());

        // 注入 JavaBridge
        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) webEngine.executeScript("window");
                this.javaBridge = new JavaBridge(this);
                window.setMember("javaApp", javaBridge);
            }
        });

        // --- 3. 程式碼檢視區 ---
        codeArea = new TextArea();
        codeArea.getStyleClass().add("text-area");
        codeArea.setPromptText("// 點擊節點檢視原始碼...");
        codeArea.setEditable(false);
        codeArea.setMinWidth(300);

        // --- 4. SplitPane 分隔面板 ---
        SplitPane splitPane = new SplitPane(webView, codeArea);
        splitPane.setDividerPositions(0.7);
        // 當雙擊分隔線時重置比例
        Platform.runLater(() -> {
            splitPane.lookupAll(".split-pane-divider").forEach(div -> {
                div.setOnMouseClicked(e -> { if (e.getClickCount() == 2) splitPane.setDividerPositions(0.7); });
            });
        });

        root.setCenter(splitPane);

        Scene scene = new Scene(root, 1200, 800);
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

        for (File file : files) {
            try {
                String code = Files.readString(file.toPath());
                String jsonStr = analyzer.analyze(code);
                allElements.addAll(convertToGraphElements(jsonStr, file.getName()));
                fileCache.put(file.getName(), code);
            } catch (Exception e) {
                System.err.println("分析失敗: " + file.getName());
            }
        }

        try {
            String finalJson = mapper.writeValueAsString(allElements);
            Platform.runLater(() -> webEngine.executeScript("renderGraph(" + finalJson + ")"));
        } catch (Exception e) { e.printStackTrace(); }
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

    public void updateCodeArea(String fileName) {
        String code = fileCache.getOrDefault(fileName, "// 找不到原始碼: " + fileName);
        Platform.runLater(() -> codeArea.setText(code));
    }
}