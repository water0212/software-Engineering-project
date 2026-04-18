package com.water.fzfwificenter.model;

import java.util.ArrayList;
import java.util.List;

public class ClassInfo {

    private final String className;
    private final List<MethodInfo> methods = new ArrayList<>();

    public ClassInfo(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    public List<MethodInfo> getMethods() {
        return methods;
    }

    public void addMethod(MethodInfo method) {
        methods.add(method);
    }
}
