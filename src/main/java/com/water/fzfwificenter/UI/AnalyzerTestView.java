package com.water.fzfwificenter.UI;

import com.water.fzfwificenter.analyzer.AnalyzerFactory;
import com.water.fzfwificenter.analyzer.LanguageAnalyzer;
import com.water.fzfwificenter.analyzer.ProgrammingLanguage;
import com.water.fzfwificenter.analyzer.AnalysisException;
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

        LanguageAnalyzer analyzer = AnalyzerFactory.getAnalyzer(ProgrammingLanguage.JAVA);

        analyzeButton.setOnAction(event -> {
            try{
                String code = inputArea.getText();
                String result = analyzer.analyze(code);
                outputArea.setText(result);
            }catch(AnalysisException e){
                switch (e.getErrorType()) {
                    case EMPTY_INPUT -> outputArea.setText("請先輸入程式碼");
                    case ANALYSIS_ERROR_TYPE -> outputArea.setText("程式碼格式有誤，無法解析");
                    case UNSUPPORTED_LANGUAGE -> outputArea.setText("目前不支援此語言");
                }
            }catch (Exception e) {
                outputArea.setText("系統發生未預期錯誤");
                e.printStackTrace();
            }

        });

        VBox root = new VBox(10, inputArea, analyzeButton, outputArea);
        root.setPadding(new Insets(15));

        return new Scene(root, 800, 600);
    }
}
