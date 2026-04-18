package com.water.fzfwificenter.model;

import java.util.ArrayList;
import java.util.List;

public class ParameterInfo {

    private String type;
    private String name;
    private List<String> annotations = new ArrayList<>();

    public ParameterInfo() {
    }

    public ParameterInfo(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(List<String> annotations) {
        this.annotations = annotations;
    }

    public void addAnnotation(String annotationName) {
        annotations.add(annotationName);
    }
}
