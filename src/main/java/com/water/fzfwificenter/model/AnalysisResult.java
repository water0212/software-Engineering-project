package com.water.fzfwificenter.model;

import java.util.ArrayList;
import java.util.List;

public class AnalysisResult {

    private String language;
    private List<ClassInfo> classes = new ArrayList<>();

    public AnalysisResult() {
    }

    public AnalysisResult(String language, List<ClassInfo> classes) {
        this.language = language;
        this.classes = classes;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public List<ClassInfo> getClasses() {
        return classes;
    }

    public void setClasses(List<ClassInfo> classes) {
        this.classes = classes;
    }
}
