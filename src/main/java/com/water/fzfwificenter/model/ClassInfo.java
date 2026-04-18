package com.water.fzfwificenter.model;

import java.util.ArrayList;
import java.util.List;

public class ClassInfo {

    private String className;
    private String type;
    private String extendsClass;
    private List<String> extendsInterfaces = new ArrayList<>();
    private List<String> implementsInterfaces = new ArrayList<>();
    private List<String> annotations = new ArrayList<>();
    private List<FieldInfo> fields = new ArrayList<>();
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

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getExtendsClass() {
        return extendsClass;
    }

    public void setExtendsClass(String extendsClass) {
        this.extendsClass = extendsClass;
    }

    public List<String> getExtendsInterfaces() {
        return extendsInterfaces;
    }

    public void setExtendsInterfaces(List<String> extendsInterfaces) {
        this.extendsInterfaces = extendsInterfaces;
    }

    public void addExtendsInterface(String interfaceName) {
        this.extendsInterfaces.add(interfaceName);
    }

    public List<String> getImplementsInterfaces() {
        return implementsInterfaces;
    }

    public void setImplementsInterfaces(List<String> implementsInterfaces) {
        this.implementsInterfaces = implementsInterfaces;
    }

    public void addImplementsInterface(String interfaceName) {
        this.implementsInterfaces.add(interfaceName);
    }

    public List<String> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(List<String> annotations) {
        this.annotations = annotations;
    }

    public void addAnnotation(String annotationName) {
        this.annotations.add(annotationName);
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

    public List<FieldInfo> getFields() {
        return fields;
    }

    public void setFields(List<FieldInfo> fields) {
        this.fields = fields;
    }

    public void addField(FieldInfo field) {
        fields.add(field);
    }

}
