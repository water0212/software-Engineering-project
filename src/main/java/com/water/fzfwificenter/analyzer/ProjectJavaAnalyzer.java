package com.water.fzfwificenter.analyzer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.water.fzfwificenter.model.ClassInfo;
import com.water.fzfwificenter.model.ProjectAnalysisResult;
import com.water.fzfwificenter.model.ProjectSummaryResult;
import com.water.fzfwificenter.model.ProjectSymbolContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ProjectJavaAnalyzer {

    private final JavaCodeAnalyzer javaCodeAnalyzer = new JavaCodeAnalyzer();
    private final ProjectSummaryBuilder projectSummaryBuilder = new ProjectSummaryBuilder();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ProjectAnalysisResult analyzeProject(Path rootPath) {
        ProjectAnalysisResult result = new ProjectAnalysisResult();
        result.setProjectName(rootPath.getFileName().toString());

        try {
            Files.walk(rootPath)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> analyzeSingleFile(path, result));
        } catch (IOException e) {
            throw new RuntimeException("掃描專案失敗: " + rootPath, e);
        }

        ProjectSymbolContext context = buildProjectSymbolContext(result);

        // 之後這裡可以接：
        // resolveProjectLevelCalls(result, context);
        // result.setCallEdges(buildCallEdges(result, context));
        // result.setDependencyEdges(buildDependencyEdges(result, context));

        return result;
    }
    public ProjectSummaryResult analyzeProjectToSummary(Path rootPath) {
        ProjectAnalysisResult analysisResult = analyzeProject(rootPath);
        return projectSummaryBuilder.build(analysisResult);
    }

    public String analyzeProjectToJson(Path rootPath) {
        ProjectSummaryResult summaryResult = analyzeProjectToSummary(rootPath);

        try {
            return objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(summaryResult);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("專案摘要 JSON 轉換失敗", e);
        }
    }


    private void analyzeSingleFile(Path path, ProjectAnalysisResult result) {
        try {
            String code = Files.readString(path);
            List<ClassInfo> classInfos = javaCodeAnalyzer.extractClassRelations(code);

            for (ClassInfo classInfo : classInfos) {
                classInfo.setFilePath(path.toString());
                result.addClass(classInfo);
            }
        } catch (Exception e) {
            System.err.println("分析檔案失敗: " + path);
            e.printStackTrace();
        }
    }
    private ProjectSymbolContext buildProjectSymbolContext(ProjectAnalysisResult result) {
        ProjectSymbolContext context = new ProjectSymbolContext();

        for (ClassInfo classInfo : result.getClasses()) {
            context.getClassIndexByQualifiedName()
                    .put(classInfo.getQualifiedClassName(), classInfo);

            context.getClassIndexBySimpleName()
                    .computeIfAbsent(classInfo.getClassName(), key -> new ArrayList<>())
                    .add(classInfo);
        }

        return context;
    }

}
