package com.water.fzfwificenter.analyzer;

import com.water.fzfwificenter.model.ClassInfo;
import com.water.fzfwificenter.model.FieldInfo;
import com.water.fzfwificenter.model.MethodCallInfo;
import com.water.fzfwificenter.model.MethodInfo;
import com.water.fzfwificenter.model.ParameterInfo;
import com.water.fzfwificenter.model.ProjectAnalysisResult;
import com.water.fzfwificenter.model.ProjectFileSummary;
import com.water.fzfwificenter.model.ProjectSummaryResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static java.beans.Introspector.decapitalize;

public class ProjectSummaryBuilder {

    public ProjectSummaryResult build(ProjectAnalysisResult analysisResult) {
        ProjectSummaryResult summaryResult = new ProjectSummaryResult();
        summaryResult.setProjectName(analysisResult.getProjectName());

        Map<String, ProjectFileSummary> fileMap = new LinkedHashMap<>();

        for (ClassInfo classInfo : analysisResult.getClasses()) {
            String filePath = classInfo.getFilePath();
            String fileName = Path.of(filePath).getFileName().toString();

            ProjectFileSummary fileSummary = fileMap.computeIfAbsent(
                    filePath,
                    key -> new ProjectFileSummary(fileName)
            );

            fileSummary.addClass(classInfo.getClassName());

            Set<String> methodSet = new LinkedHashSet<>(fileSummary.getMethods());
            Set<String> dependencySet = new LinkedHashSet<>(fileSummary.getDependencies());

            for (MethodInfo method : classInfo.getMethods()) {
                if (shouldIncludeMethod(method, classInfo)) {
                    methodSet.add(method.getMethodName());
                }

                for (ParameterInfo parameter : method.getParameters()) {
                    addDependencyIfValid(dependencySet, parameter.getType(), classInfo.getClassName());
                }

                for (MethodCallInfo call : method.getCalledMethods()) {
                    addDependencyIfValid(dependencySet, call.getTargetClass(), classInfo.getClassName());
                }
            }

            for (FieldInfo field : classInfo.getFields()) {
                addDependencyIfValid(dependencySet, field.getType(), classInfo.getClassName());
            }

            addDependencyIfValid(dependencySet, classInfo.getExtendsClass(), classInfo.getClassName());

            for (String interfaceName : classInfo.getExtendsInterfaces()) {
                addDependencyIfValid(dependencySet, interfaceName, classInfo.getClassName());
            }

            for (String interfaceName : classInfo.getImplementsInterfaces()) {
                addDependencyIfValid(dependencySet, interfaceName, classInfo.getClassName());
            }

            fileSummary.setMethods(new ArrayList<>(methodSet));
            fileSummary.setDependencies(new ArrayList<>(dependencySet));
        }

        summaryResult.setFiles(new ArrayList<>(fileMap.values()));
        return summaryResult;
    }

    private boolean shouldIncludeMethod(MethodInfo method, ClassInfo classInfo) {
        String methodName = method.getMethodName();

        if (isGetter(method, classInfo)) {
            return false;
        }

        if (isSetter(method, classInfo)) {
            return false;
        }

        return true;
    }
    private boolean isGetter(MethodInfo method, ClassInfo classInfo) {
        String methodName = method.getMethodName();

        if (methodName == null) {
            return false;
        }

        if (!method.getParameters().isEmpty()) {
            return false;
        }

        if ("void".equals(method.getReturnType())) {
            return false;
        }

        if (methodName.startsWith("get") && methodName.length() > 3) {
            return matchesFieldName(methodName.substring(3), classInfo);
        }

        if (methodName.startsWith("is") && methodName.length() > 2) {
            return matchesFieldName(methodName.substring(2), classInfo);
        }

        return false;
    }
    private boolean isSetter(MethodInfo method, ClassInfo classInfo) {
        String methodName = method.getMethodName();

        if (methodName == null) {
            return false;
        }

        if (!methodName.startsWith("set") || methodName.length() <= 3) {
            return false;
        }

        if (method.getParameters().size() != 1) {
            return false;
        }

        return matchesFieldName(methodName.substring(3), classInfo);
    }
    private boolean matchesFieldName(String methodSuffix, ClassInfo classInfo) {
        if (methodSuffix == null || methodSuffix.isBlank()) {
            return false;
        }

        String normalized = decapitalize(methodSuffix);

        for (FieldInfo field : classInfo.getFields()) {
            if (field.getName().equals(normalized)) {
                return true;
            }
        }

        return false;
    }





    private void addDependencyIfValid(Set<String> dependencySet, String candidate, String selfClassName) {
        if (candidate == null || candidate.isBlank()) {
            return;
        }
        String cleaned = cleanTypeName(candidate);
        if ("UNKNOWN".equals(cleaned)) {
            return;
        }

        if (candidate.equals(selfClassName)) {
            return;
        }
        if(isIgnoredType(cleaned)) {
            return;
        }
        dependencySet.add(cleaned);
    }

    private boolean isIgnoredType(String typeName) {
        return typeName.equals("String")
                || typeName.equals("Integer")
                || typeName.equals("Long")
                || typeName.equals("Boolean")
                || typeName.equals("Double")
                || typeName.equals("List")
                || typeName.equals("Map")
                || typeName.equals("Set")
                || typeName.equals("Object");
    }

    private String cleanTypeName(String typeName) {
        String result = typeName;

        if (result.contains("<")) {
            result = result.substring(0, result.indexOf("<"));
        }

        if (result.contains(".")) {
            result = result.substring(result.lastIndexOf(".") + 1);
        }

        if (result.endsWith("[]")) {
            result = result.substring(0, result.length() - 2);
        }

        return result.trim();
    }
}
