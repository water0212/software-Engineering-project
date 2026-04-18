package com.water.fzfwificenter.model;

public class MethodCallInfo {

    private String methodName;
    private String scope;
    private String targetClass;
    private String targetMethod;
    private String callType;
    private boolean resolved;

    public MethodCallInfo() {
    }

    public MethodCallInfo(String methodName, String scope, String targetClass,
                          String targetMethod, String callType, boolean resolved) {
        this.methodName = methodName;
        this.scope = scope;
        this.targetClass = targetClass;
        this.targetMethod = targetMethod;
        this.callType = callType;
        this.resolved = resolved;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getTargetClass() {
        return targetClass;
    }

    public void setTargetClass(String targetClass) {
        this.targetClass = targetClass;
    }

    public String getTargetMethod() {
        return targetMethod;
    }

    public void setTargetMethod(String targetMethod) {
        this.targetMethod = targetMethod;
    }

    public String getCallType() {
        return callType;
    }

    public void setCallType(String callType) {
        this.callType = callType;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }
}
