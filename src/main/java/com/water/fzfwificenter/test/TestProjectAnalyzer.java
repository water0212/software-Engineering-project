package com.water.fzfwificenter.test;

import com.water.fzfwificenter.analyzer.core.ProjectJavaAnalyzer;

import java.nio.file.Path;

public class TestProjectAnalyzer {
        public static void main(String[] args) {
            ProjectJavaAnalyzer analyzer = new ProjectJavaAnalyzer();
            String json = analyzer.analyzeProjectToJson(Path.of("C:\\Users\\water\\Desktop\\Java project\\software-Engineering-project\\src\\main"));
            System.out.println(json);
        }

}
