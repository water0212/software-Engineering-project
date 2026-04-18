package com.water.fzfwificenter.model;

import java.util.ArrayList;
import java.util.List;

public class MethodInfo {

    private final String methodName;
    private final String returnType;
    private final List<ParameterInfo> parameters = new ArrayList<>();

    public MethodInfo(String methodName, String returnType) {
        this.methodName = methodName;
        this.returnType = returnType;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getReturnType() {
        return returnType;
    }

    public List<ParameterInfo> getParameters() {
        return parameters;
    }

    public void addParameter(ParameterInfo parameter) {
        parameters.add(parameter);
    }
}
