package com.water.fzfwificenter.UI;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.water.fzfwificenter.llm.LLMService;
import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainScreen {
    private static final Path OUTPUT_DIRECTORY = Path.of("src", "files");
    private static final String DEFAULT_PROJECT_SUMMARY_QUESTION = "這個專案有哪些核心功能？";

    private final Stage stage;
    private WebEngine webEngine;
    private WebEngine chatEngine;
    private ChatController chatController;
    private NodeAnalysisController nodeAnalysisController;
    private VBox loadingOverlay;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, String> fileCache = new HashMap<>();
    private final Map<String, String> jsonCache = new HashMap<>();

    private JavaBridge javaBridge;
    private final LLMService llmService = new LLMService();
    private final ProjectImportService projectImportService = new ProjectImportService(OUTPUT_DIRECTORY, mapper);

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

        Button clearBtn = new Button("🧹 清除對話");
        clearBtn.setOnAction(e -> chatEngine.executeScript("clearChat()"));

        HBox topBar = new HBox(15, titleLabel, fileBtn, dirBtn, resetBtn, clearBtn);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.getStyleClass().add("top-bar");
        root.setTop(topBar);

        // --- 左側：Cytoscape 視窗 ---
        WebView webView = new WebView();
        webView.setMinWidth(540);
        webEngine = webView.getEngine();
        webEngine.load(Objects.requireNonNull(getClass().getResource("/index.html")).toExternalForm());

        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) webEngine.executeScript("window");
                if (this.javaBridge == null) {
                    this.javaBridge = new JavaBridge(this);
                }
                window.setMember("javaApp", javaBridge);
            }
        });

        // --- 🚨 右側：全螢幕聊天室視窗 ---
        WebView chatView = new WebView();
        chatView.setMinWidth(400);
        chatView.setPrefWidth(500);
        chatView.setMaxWidth(580);
        chatEngine = chatView.getEngine();

        // 🚨 關鍵修正：確保聊天室引擎也能夠認識 JavaBridge，這樣 chat.html 的 JS 才能呼叫 Java！
        chatEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) chatEngine.executeScript("window");
                if (this.javaBridge == null) {
                    this.javaBridge = new JavaBridge(this);
                }
                window.setMember("javaApp", javaBridge);
            }
        });

        try {
            String url = Objects.requireNonNull(getClass().getResource("/chat.html")).toExternalForm();
            chatEngine.load(url);
        } catch (Exception e) {
            System.err.println("🚨 找不到 chat.html！請確認檔案放在 src/main/resources 之下");
        }

        SplitPane splitPane = new SplitPane(webView, chatView);
        splitPane.setDividerPositions(0.76);

        loadingOverlay = createLoadingOverlay();
        StackPane centerPane = new StackPane(splitPane, loadingOverlay);
        root.setCenter(centerPane);

        chatController = new ChatController(chatEngine, webEngine, llmService, mapper);
        nodeAnalysisController = new NodeAnalysisController(
                OUTPUT_DIRECTORY,
                mapper,
                llmService,
                projectImportService,
                chatController,
                fileCache,
                jsonCache
        );

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
        showLoadingOverlay();
        chatController.showImportLoading();

        CompletableFuture.supplyAsync(() -> projectImportService.analyzeImportedFiles(files))
                .thenCompose(importResult -> {
                    String globalContext = importResult.globalContext();
                    if (globalContext.isEmpty() || globalContext.equals("[]")) {
                        return CompletableFuture.completedFuture(new ImportDisplayResult(
                                importResult,
                                new AiDisplayResponse("沒有可分析的 Java 專案內容。", "[]"),
                                Collections.emptyList()
                        ));
                    }

                    CompletableFuture<AiDisplayResponse> summaryFuture =
                            chatController.requestAiDisplayResponse(DEFAULT_PROJECT_SUMMARY_QUESTION, globalContext);
                    CompletableFuture<List<String>> questionsFuture =
                            chatController.suggestQuestions(globalContext);

                    return summaryFuture.thenCombine(questionsFuture,
                            (summary, questions) -> new ImportDisplayResult(importResult, summary, questions));
                })
                .whenComplete((displayResult, throwable) -> Platform.runLater(() -> {
                    if (throwable != null) {
                        hideLoadingOverlay();
                        chatController.hideImportLoading();
                        chatController.addChatMessage("ai", "匯入或 AI 分析失敗：" + throwable.getMessage());
                        return;
                    }

                    try {
                        fileCache.clear();
                        fileCache.putAll(displayResult.importResult().fileCache());
                        jsonCache.clear();
                        jsonCache.putAll(displayResult.importResult().jsonCache());

                        webEngine.executeScript("renderGraph(" + displayResult.importResult().graphJson() + ")");
                        chatController.addChatMessage("ai", displayResult.summary().displayText());
                        chatController.setChatChips(displayResult.questions());

                        chatController.highlightClasses(displayResult.summary().targetClassesJson());
                    } finally {
                        chatController.hideImportLoading();
                        hideLoadingOverlay();
                    }
                }));
    }

    public void handleNodeSelectionWithAnalyzeStatus(String payload) {
        nodeAnalysisController.handleNodeSelectionWithAnalyzeStatus(payload);
    }

    public void handleUserChatQuery(String query) {
        chatController.askAiAndDisplayResponse(query, jsonCache.values().toString());
    }

    private VBox createLoadingOverlay() {
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(48, 48);

        Label label = new Label("正在分析專案...");
        label.setStyle("-fx-text-fill: #d4d4d4; -fx-font-size: 14px; -fx-font-weight: bold;");

        VBox overlay = new VBox(14, spinner, label);
        overlay.setAlignment(Pos.CENTER);
        overlay.setStyle("-fx-background-color: rgba(30, 30, 30, 0.78);");
        overlay.setVisible(false);
        overlay.setManaged(false);
        overlay.setMouseTransparent(false);
        return overlay;
    }

    private void showLoadingOverlay() {
        if (loadingOverlay != null) {
            loadingOverlay.setManaged(true);
            loadingOverlay.setVisible(true);
            loadingOverlay.toFront();
        }
    }

    private void hideLoadingOverlay() {
        if (loadingOverlay != null) {
            loadingOverlay.setVisible(false);
            loadingOverlay.setManaged(false);
        }
    }
}
