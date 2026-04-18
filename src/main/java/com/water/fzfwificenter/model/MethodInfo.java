package com.water.fzfwificenter.model;

import java.util.ArrayList;
import java.util.List;

public class MethodInfo {

    private String methodName;
    private String returnType;
    private List<ParameterInfo> parameters = new ArrayList<>();

    public MethodInfo() {
    }

    public MethodInfo(String methodName, String returnType) {
        this.methodName = methodName;
        this.returnType = returnType;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public List<ParameterInfo> getParameters() {
        return parameters;
    }

    public void setParameters(List<ParameterInfo> parameters) {
        this.parameters = parameters;
    }

    public void addParameter(ParameterInfo parameter) {
        parameters.add(parameter);
    }
}
