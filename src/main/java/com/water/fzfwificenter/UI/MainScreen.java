package com.water.fzfwificenter.UI;

import com.water.fzfwificenter.analyzer.AnalysisException;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import com.water.fzfwificenter.analyzer.AnalyzerFactory;
import com.water.fzfwificenter.analyzer.LanguageAnalyzer;
import com.water.fzfwificenter.analyzer.ProgrammingLanguage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MainScreen {

    private Stage stage;

    public MainScreen(Stage stage) {
        this.stage = stage;
    }

    public Scene createScene() {
        Label titleLabel = new Label("Java 程式碼分析工具");
        titleLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        // 1. 選擇「檔案」的按鈕
        Button importFileBtn = new Button("匯入單一/多個檔案");
        importFileBtn.setOnAction(event -> handleImportFiles());

        // 2. 選擇「資料夾」的按鈕
        Button importDirBtn = new Button("匯入整個資料夾");
        importDirBtn.setOnAction(event -> handleImportDirectory());

        HBox buttonBox = new HBox(15, importFileBtn, importDirBtn);
        buttonBox.setAlignment(Pos.CENTER);

        VBox layout = new VBox(30, titleLabel, buttonBox);
        layout.setAlignment(Pos.CENTER);

        return new Scene(layout, 600, 400);
    }

    // 處理選擇檔案 (支援多選)
    private void handleImportFiles() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("請選擇 Java 檔案");
        // 限制只能選 .java 檔
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Java Files", "*.java")
        );

        List<File> selectedFiles = fileChooser.showOpenMultipleDialog(stage);

        if (selectedFiles != null && !selectedFiles.isEmpty()) {
            System.out.println("選取了 " + selectedFiles.size() + " 個檔案，開始處理...");
            processFiles(selectedFiles);
        }
    }

    // 處理選擇資料夾
    private void handleImportDirectory() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("請選擇 Java 專案資料夾");
        File selectedDirectory = directoryChooser.showDialog(stage);

        if (selectedDirectory != null) {
            System.out.println("選取了資料夾: " + selectedDirectory.getAbsolutePath());
            try {
                // 找出資料夾內所有的 .java 檔
                List<File> javaFiles;
                try (Stream<Path> paths = Files.walk(selectedDirectory.toPath())) {
                    javaFiles = paths
                            .filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".java"))
                            .map(Path::toFile)
                            .collect(Collectors.toList());
                }

                System.out.println("在資料夾中找到 " + javaFiles.size() + " 個 Java 檔案，開始處理...");
                processFiles(javaFiles);

            } catch (Exception e) {
                System.err.println("讀取資料夾失敗: " + e.getMessage());
            }
        }
    }

    // 核心處理邏輯：將檔案轉成 String 並丟給 Analyzer
    private void processFiles(List<File> files) {
        // 使用你的工廠取得 Java 解析器
        LanguageAnalyzer analyzer = AnalyzerFactory.getAnalyzer(ProgrammingLanguage.JAVA);

        for (File file : files) {
            try {
                // 1. 將檔案讀取為 String
                String codeString = Files.readString(file.toPath());

                // 2. 丟給你寫好的 JavaCodeAnalyzer 處理
                String jsonResult = analyzer.analyze(codeString);

                // 3. 印出結果 (或後續丟給 UI 顯示)
                System.out.println("=== 檔案: " + file.getName() + " 分析結果 ===");
                System.out.println(jsonResult);
                System.out.println("------------------------------------------------");

            } catch (AnalysisException e) {
                String errorMessage = e.getMessage();

                if (e.getCause() != null && e.getCause().getMessage() != null) {
                    errorMessage += " | 原因: " + e.getCause().getMessage();
                }
                System.err.println("處理檔案 " + file.getName() + " 時發生錯誤: " + errorMessage);
            } catch (Exception e){
                    System.err.println("處理檔案 " + file.getName() + " 時發生錯誤: " + e.getMessage());
                }
            }
    }
}