package com.water.fzfwificenter.model;


import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProjectSymbolContext {
    private Map<String, List<ClassInfo>> classIndexBySimpleName = new HashMap<>();
    private Map<String, ClassInfo> classIndexByQualifiedName = new HashMap<>();
    private Map<String, List<MethodInfo>> methodIndexByName = new HashMap<>();
    private Map<String, MethodInfo> methodIndexByQualifiedSignature = new HashMap<>();
    private Map<String, List<MethodInfo>> methodIndexByClass = new HashMap<>();


    public Map<String, List<ClassInfo>> getClassIndexBySimpleName() {
        return classIndexBySimpleName;
    }

    public Map<String, ClassInfo> getClassIndexByQualifiedName() {
        return classIndexByQualifiedName;
    }
}

