package com.water.fzfwificenter.model;

public class ParameterInfo {

    private final String type;
    private final String name;

    public ParameterInfo(String type, String name) {
        this.type = type;
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public String getName() {
        return name;
    }
}
