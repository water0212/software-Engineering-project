package com.water.fzfwificenter.analyzer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.water.fzfwificenter.model.AnalysisResult;
import com.water.fzfwificenter.model.ClassInfo;
import com.water.fzfwificenter.model.MethodInfo;
import com.water.fzfwificenter.model.ParameterInfo;

import java.util.ArrayList;
import java.util.List;

public class JavaCodeAnalyzer implements LanguageAnalyzer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ProgrammingLanguage getLanguage() {
        return ProgrammingLanguage.JAVA;
    }

    @Override
    public String analyze(String code) throws AnalysisException {
//        AnalysisResult result = analyzeToResult(code);
//        return formatResult(result);
        return analyzeToJson(code);
    }

    public AnalysisResult analyzeToResult(String code) {
        validateCode(code);

        try {
            List<ClassInfo> classes = extractClassRelations(code);
            return new AnalysisResult(getLanguage().name(), classes);
        } catch (AnalysisException e) {
            throw e;
        } catch (Exception e) {
            throw new AnalysisException("Java 程式碼解析失敗", e, AnalysisErrorType.ANALYSIS_ERROR);
        }
    }

    public String analyzeToJson(String code) {
        AnalysisResult result = analyzeToResult(code);

        try {
            return objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new AnalysisException("JSON 轉換失敗", e, AnalysisErrorType.ANALYSIS_ERROR);
        }
    }

    public List<ClassInfo> extractClassRelations(String code) {
        validateCode(code);

        try {
            CompilationUnit cu = StaticJavaParser.parse(code);
            List<ClassInfo> classInfos = new ArrayList<>();

            for (ClassOrInterfaceDeclaration classDecl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
                ClassInfo classInfo = new ClassInfo(classDecl.getNameAsString());

                for (MethodDeclaration methodDecl : classDecl.getMethods()) {
                    MethodInfo methodInfo = new MethodInfo(
                            methodDecl.getNameAsString(),
                            methodDecl.getType().asString()
                    );

                    for (Parameter parameter : methodDecl.getParameters()) {
                        ParameterInfo parameterInfo = new ParameterInfo(
                                parameter.getType().asString(),
                                parameter.getNameAsString()
                        );
                        methodInfo.addParameter(parameterInfo);
                    }

                    classInfo.addMethod(methodInfo);
                }

                classInfos.add(classInfo);
            }

            return classInfos;
        } catch (Exception e) {
            throw new AnalysisException("Java 程式碼解析失敗", e, AnalysisErrorType.ANALYSIS_ERROR);
        }
    }

    private void validateCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new AnalysisException("程式碼不可為空", AnalysisErrorType.EMPTY_INPUT);
        }
    }

    private String formatResult(AnalysisResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("Language: ").append(result.getLanguage()).append("\n");

        for (ClassInfo classInfo : result.getClasses()) {
            sb.append("Class: ").append(classInfo.getClassName()).append("\n");

            for (MethodInfo method : classInfo.getMethods()) {
                sb.append("  Method: ")
                        .append(method.getReturnType())
                        .append(" ")
                        .append(method.getMethodName())
                        .append("(");

                List<ParameterInfo> parameters = method.getParameters();
                for (int i = 0; i < parameters.size(); i++) {
                    ParameterInfo p = parameters.get(i);
                    sb.append(p.getType()).append(" ").append(p.getName());

                    if (i < parameters.size() - 1) {
                        sb.append(", ");
                    }
                }

                sb.append(")\n");
            }
        }

        return sb.toString();
    }
}
