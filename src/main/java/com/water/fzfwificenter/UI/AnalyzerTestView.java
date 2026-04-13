package com.water.fzfwificenter.UI;

import com.water.fzfwificenter.analyzer.CodeAnalyzer;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;

public class AnalyzerTestView implements AppView {

    @Override
    public Scene createScene() {
        TextArea inputArea = new TextArea();
        inputArea.setPromptText("請輸入 Java 程式碼...");
        inputArea.setPrefHeight(250);

        Button analyzeButton = new Button("分析");

        TextArea outputArea = new TextArea();
        outputArea.setPromptText("分析結果會顯示在這裡...");
        outputArea.setEditable(false);
        outputArea.setPrefHeight(250);

        CodeAnalyzer analyzer = new CodeAnalyzer();

        analyzeButton.setOnAction(event -> {
            String code = inputArea.getText();
            String result = analyzer.analyze(code);
            outputArea.setText(result);
        });

        VBox root = new VBox(10, inputArea, analyzeButton, outputArea);
        root.setPadding(new Insets(15));

        return new Scene(root, 800, 600);
    }
}
