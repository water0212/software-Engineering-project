package com.water.fzfwificenter.model;

import java.util.ArrayList;
import java.util.List;

public class ProjectFileSummary {

    private String fileName;
    private List<String> classes = new ArrayList<>();
    private List<String> dependencies = new ArrayList<>();
    private List<String> methods = new ArrayList<>();

    public ProjectFileSummary() {
    }

    public ProjectFileSummary(String fileName) {
        this.fileName = fileName;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public List<String> getClasses() {
        return classes;
    }

    public void setClasses(List<String> classes) {
        this.classes = classes;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies;
    }

    public List<String> getMethods() {
        return methods;
    }

    public void setMethods(List<String> methods) {
        this.methods = methods;
    }

    public void addClass(String className) {
        this.classes.add(className);
    }

    public void addDependency(String dependency) {
        this.dependencies.add(dependency);
    }

    public void addMethod(String methodName) {
        this.methods.add(methodName);
    }
}
