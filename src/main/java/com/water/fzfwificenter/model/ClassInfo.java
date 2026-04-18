package com.water.fzfwificenter.model;

import java.util.ArrayList;
import java.util.List;

public class ClassInfo {

    private String className;
    private List<MethodInfo> methods = new ArrayList<>();

    public ClassInfo() {
    }

    public ClassInfo(String className) {
        this.className = className;
    }

    public String getClassName() {
        return className;
    }

    public void setClassName(String className) {
        this.className = className;
    }

    public List<MethodInfo> getMethods() {
        return methods;
    }

    public void setMethods(List<MethodInfo> methods) {
        this.methods = methods;
    }

    public void addMethod(MethodInfo method) {
        methods.add(method);
    }
}
