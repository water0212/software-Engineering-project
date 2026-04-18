package com.water.fzfwificenter.analyzer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.ThisExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.SuperExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;


import com.water.fzfwificenter.model.AnalysisResult;
import com.water.fzfwificenter.model.ClassInfo;
import com.water.fzfwificenter.model.MethodInfo;
import com.water.fzfwificenter.model.FieldInfo;
import com.water.fzfwificenter.model.ParameterInfo;
import com.water.fzfwificenter.model.MethodCallInfo;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JavaCodeAnalyzer implements LanguageAnalyzer {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ProgrammingLanguage getLanguage() {
        return ProgrammingLanguage.JAVA;
    }
    /**
     * 接收 原始碼字串，分析 class、method 與 parameter 關係，
     * 並回傳 JSON 格式結果。
     *
     * @param code 原始碼字串
     * @return JSON 格式分析結果
     * @throws AnalysisException 當輸入為空、解析失敗或 JSON 轉換失敗時拋出
     */
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
    /**
     * 接收 Java 原始碼字串，分析 class、method 與 parameter 關係，
     * 並回傳 JSON 格式結果。
     *
     * @param code Java 原始碼字串
     * @return JSON 格式分析結果
     * @throws AnalysisException 當輸入為空、解析失敗或 JSON 轉換失敗時拋出
     */
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

            List<ClassOrInterfaceDeclaration> classDeclarations = cu.findAll(ClassOrInterfaceDeclaration.class);
            List<String> allClassNames = new ArrayList<>();

            for (ClassOrInterfaceDeclaration classDecl : classDeclarations) {
                allClassNames.add(classDecl.getNameAsString());
            }

            for (ClassOrInterfaceDeclaration classDecl : classDeclarations) {
                ClassInfo classInfo = new ClassInfo(classDecl.getNameAsString());

                if (classDecl.isInterface()) {
                    classInfo.setType("interface");
                } else {
                    classInfo.setType("class");
                }

                for (AnnotationExpr annotation : classDecl.getAnnotations()) {
                    classInfo.addAnnotation(annotation.getNameAsString());
                }

                if (classDecl.isInterface()) {
                    for (ClassOrInterfaceType extendedType : classDecl.getExtendedTypes()) {
                        classInfo.addExtendsInterface(extendedType.getNameAsString());
                    }
                } else {
                    if (!classDecl.getExtendedTypes().isEmpty()) {
                        classInfo.setExtendsClass(classDecl.getExtendedTypes().get(0).getNameAsString());
                    }

                    for (ClassOrInterfaceType implementedType : classDecl.getImplementedTypes()) {
                        classInfo.addImplementsInterface(implementedType.getNameAsString());
                    }
                }

                for (FieldInfo fieldInfo : extractFields(classDecl)) {
                    classInfo.addField(fieldInfo);
                }

                List<String> currentClassMethodNames = getMethodNames(classDecl);

                for (MethodDeclaration methodDecl : classDecl.getMethods()) {
                    MethodInfo methodInfo = new MethodInfo(
                            methodDecl.getNameAsString(),
                            methodDecl.getType().asString()
                    );

                    for (AnnotationExpr annotation : methodDecl.getAnnotations()) {
                        methodInfo.addAnnotation(annotation.getNameAsString());
                    }

                    for (Parameter parameter : methodDecl.getParameters()) {
                        ParameterInfo parameterInfo = new ParameterInfo(
                                parameter.getType().asString(),
                                parameter.getNameAsString()
                        );
                        for (AnnotationExpr annotation : parameter.getAnnotations()) {
                            parameterInfo.addAnnotation(annotation.getNameAsString());
                        }

                        methodInfo.addParameter(parameterInfo);
                    }

                    Map<String, String> visibleVariableTypeMap = buildVisibleVariableTypeMap(classDecl, methodDecl);

                    for (MethodCallExpr methodCallExpr : methodDecl.findAll(MethodCallExpr.class)) {
                        MethodCallInfo methodCallInfo = resolveMethodCall(
                                methodCallExpr,
                                classDecl,
                                allClassNames,
                                visibleVariableTypeMap,
                                currentClassMethodNames
                        );
                        methodInfo.addCalledMethod(methodCallInfo);
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


    private MethodCallInfo resolveMethodCall(
            MethodCallExpr methodCallExpr,
            ClassOrInterfaceDeclaration currentClassDecl,
            List<String> knownClassNames,
            Map<String, String> visibleVariableTypeMap,
            List<String> currentClassMethodNames
    ) {
        String methodName = methodCallExpr.getNameAsString();
        Optional<Expression> scopeOptional = methodCallExpr.getScope();

        String scope = null;
        String targetClass = null;
        String callType = "unresolved";
        boolean resolved = false;

        if (scopeOptional.isPresent()) {
            Expression scopeExpr = scopeOptional.get();
            scope = scopeExpr.toString();

            if (scopeExpr instanceof ThisExpr) {
                targetClass = currentClassDecl.getNameAsString();
                callType = "this";
                resolved = true;
            } else if (scopeExpr instanceof SuperExpr) {
                if (!currentClassDecl.getExtendedTypes().isEmpty()) {
                    targetClass = currentClassDecl.getExtendedTypes().get(0).getNameAsString();
                    callType = "super";
                    resolved = true;
                }
            } else if (scopeExpr instanceof NameExpr) {
                String scopeName = ((NameExpr) scopeExpr).getNameAsString();

                if (visibleVariableTypeMap.containsKey(scopeName)) {
                    targetClass = visibleVariableTypeMap.get(scopeName);
                    callType = "instance";
                    resolved = true;
                } else if (scopeName.equals(currentClassDecl.getNameAsString()) || isKnownClassName(scopeName, knownClassNames)) {
                    targetClass = scopeName;
                    callType = "static";
                    resolved = true;
                }
            }
        } else {
            if (currentClassMethodNames.contains(methodName)) {
                targetClass = currentClassDecl.getNameAsString();
                callType = "implicit-this";
                resolved = true;
            } else if (!currentClassDecl.getExtendedTypes().isEmpty()) {
                targetClass = currentClassDecl.getExtendedTypes().get(0).getNameAsString();
                callType = "implicit-parent";
                resolved = false;
            }
        }

        if (targetClass == null) {
            targetClass = "UNKNOWN";
        }

        return new MethodCallInfo(
                methodName,
                scope,
                targetClass,
                methodName,
                callType,
                resolved
        );
    }




    private List<String> getMethodNames(ClassOrInterfaceDeclaration classDecl) {
        List<String> methodNames = new ArrayList<>();
        for (MethodDeclaration method : classDecl.getMethods()) {
            methodNames.add(method.getNameAsString());
        }
        return methodNames;
    }

    private Map<String, String> buildFieldTypeMap(ClassOrInterfaceDeclaration classDecl) {
        Map<String, String> fieldTypeMap = new HashMap<>();

        for (FieldDeclaration fieldDecl : classDecl.getFields()) {
            for (VariableDeclarator variable : fieldDecl.getVariables()) {
                fieldTypeMap.put(variable.getNameAsString(), variable.getType().asString());
            }
        }

        return fieldTypeMap;
    }

    private Map<String, String> buildVisibleVariableTypeMap(
            ClassOrInterfaceDeclaration classDecl,
            MethodDeclaration methodDecl
    ) {
        Map<String, String> variableTypeMap = new HashMap<>();

        // class fields
        variableTypeMap.putAll(buildFieldTypeMap(classDecl));

        // method parameters
        for (Parameter parameter : methodDecl.getParameters()) {
            variableTypeMap.put(parameter.getNameAsString(), parameter.getType().asString());
        }

        // local variables
        for (VariableDeclarator variable : methodDecl.findAll(VariableDeclarator.class)) {
            variableTypeMap.put(variable.getNameAsString(), variable.getType().asString());
        }

        return variableTypeMap;
    }

    private boolean isKnownClassName(String name, List<String> classNames) {
        return classNames.contains(name);
    }
    private List<FieldInfo> extractFields(ClassOrInterfaceDeclaration classDecl) {
        List<FieldInfo> fields = new ArrayList<>();

        for (FieldDeclaration fieldDecl : classDecl.getFields()) {
            for (VariableDeclarator variable : fieldDecl.getVariables()) {
                FieldInfo fieldInfo = new FieldInfo(
                        variable.getNameAsString(),
                        variable.getType().asString()
                );

                // modifiers
                fieldDecl.getModifiers().forEach(modifier ->
                        fieldInfo.addModifier(modifier.getKeyword().asString())
                );

                // annotations
                for (AnnotationExpr annotation : fieldDecl.getAnnotations()) {
                    fieldInfo.addAnnotation(annotation.getNameAsString());
                }

                fields.add(fieldInfo);
            }
        }

        return fields;
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
            sb.append(classInfo.getType()).append(": ").append(classInfo.getClassName()).append("\n");

            if (classInfo.getExtendsClass() != null) {
                sb.append("  Extends Class: ").append(classInfo.getExtendsClass()).append("\n");
            }

            if (!classInfo.getExtendsInterfaces().isEmpty()) {
                sb.append("  Extends Interfaces: ").append(classInfo.getExtendsInterfaces()).append("\n");
            }

            if (!classInfo.getImplementsInterfaces().isEmpty()) {
                sb.append("  Implements: ").append(classInfo.getImplementsInterfaces()).append("\n");
            }

            if (!classInfo.getAnnotations().isEmpty()) {
                sb.append("  Annotations: ").append(classInfo.getAnnotations()).append("\n");
            }

            if (!classInfo.getFields().isEmpty()) {
                sb.append("  Fields:\n");
                for (FieldInfo field : classInfo.getFields()) {
                    sb.append("    - ");

                    if (!field.getModifiers().isEmpty()) {
                        sb.append(field.getModifiers()).append(" ");
                    }

                    sb.append(field.getType())
                            .append(" ")
                            .append(field.getName());

                    if (!field.getAnnotations().isEmpty()) {
                        sb.append(" | annotations=").append(field.getAnnotations());
                    }

                    sb.append("\n");
                }
            }

            for (MethodInfo method : classInfo.getMethods()) {
                sb.append("  Method: ")
                        .append(method.getReturnType())
                        .append(" ")
                        .append(method.getMethodName())
                        .append("\n");

                if (!method.getCalledMethods().isEmpty()) {
                    sb.append("    Calls:\n");
                    for (MethodCallInfo call : method.getCalledMethods()) {
                        sb.append("      - ")
                                .append(call.getMethodName())
                                .append(" | scope=").append(call.getScope())
                                .append(" | targetClass=").append(call.getTargetClass())
                                .append(" | callType=").append(call.getCallType())
                                .append(" | resolved=").append(call.isResolved())
                                .append("\n");
                    }
                }
            }
        }

        return sb.toString();
    }




}
