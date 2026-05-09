package com.water.fzfwificenter.model;

import java.util.ArrayList;
import java.util.List;

public class ProjectAnalysisResult {

    private String projectName;
    private List<ClassInfo> classes = new ArrayList<>();

    public ProjectAnalysisResult() {
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public List<ClassInfo> getClasses() {
        return classes;
    }

    public void setClasses(List<ClassInfo> classes) {
        this.classes = classes;
    }

    public void addClass(ClassInfo classInfo) {
        this.classes.add(classInfo);
    }
}
