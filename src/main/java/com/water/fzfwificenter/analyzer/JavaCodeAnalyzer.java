package com.water.fzfwificenter.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.water.fzfwificenter.model.ClassInfo;
import com.water.fzfwificenter.model.MethodInfo;
import com.water.fzfwificenter.model.ParameterInfo;

import java.util.ArrayList;
import java.util.List;

public class JavaCodeAnalyzer implements LanguageAnalyzer {

    @Override
    public ProgrammingLanguage getLanguage() {
        return ProgrammingLanguage.JAVA;
    }

    @Override
    public String analyze(String code) throws AnalysisException {
        validateCode(code);

        try {
            List<ClassInfo> classInfos = extractClassRelations(code);
            return formatClassRelations(classInfos);
        } catch (AnalysisException e) {
            throw e;
        } catch (Exception e) {
            throw new AnalysisException("Java 程式碼解析失敗", e, AnalysisErrorType.ANALYSIS_ERROR_TYPE);
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
            throw new AnalysisException("Java 程式碼解析失敗", e, AnalysisErrorType.ANALYSIS_ERROR_TYPE);
        }
    }

    private void validateCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new AnalysisException("程式碼不可為空", AnalysisErrorType.EMPTY_INPUT);
        }
    }

    private String formatClassRelations(List<ClassInfo> classInfos) {
        StringBuilder result = new StringBuilder();

        if (classInfos.isEmpty()) {
            result.append("未找到任何 class 或 interface。\n");
            return result.toString();
        }

        for (ClassInfo classInfo : classInfos) {
            result.append("Class: ").append(classInfo.getClassName()).append("\n");

            if (classInfo.getMethods().isEmpty()) {
                result.append("  Methods: 無\n");
            } else {
                for (MethodInfo method : classInfo.getMethods()) {
                    result.append("  Method: ")
                            .append(method.getReturnType())
                            .append(" ")
                            .append(method.getMethodName())
                            .append("(");

                    List<ParameterInfo> parameters = method.getParameters();
                    for (int i = 0; i < parameters.size(); i++) {
                        ParameterInfo parameter = parameters.get(i);
                        result.append(parameter.getType())
                                .append(" ")
                                .append(parameter.getName());

                        if (i < parameters.size() - 1) {
                            result.append(", ");
                        }
                    }

                    result.append(")\n");
                }
            }

            result.append("\n");
        }

        return result.toString();
    }
}
