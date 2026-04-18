package com.water.fzfwificenter.model;

import java.util.ArrayList;
import java.util.List;

public class FieldInfo {

    private String name;
    private String type;
    private List<String> modifiers = new ArrayList<>();
    private List<String> annotations = new ArrayList<>();

    public FieldInfo() {
    }

    public FieldInfo(String name, String type) {
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getModifiers() {
        return modifiers;
    }

    public void setModifiers(List<String> modifiers) {
        this.modifiers = modifiers;
    }

    public void addModifier(String modifier) {
        this.modifiers.add(modifier);
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(List<String> annotations) {
        this.annotations = annotations;
    }

    public void addAnnotation(String annotation) {
        this.annotations.add(annotation);
    }
}
