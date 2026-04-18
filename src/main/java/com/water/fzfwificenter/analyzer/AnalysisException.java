package com.water.fzfwificenter.analyzer;

public class AnalysisException extends RuntimeException {

    private final AnalysisErrorType errorType;

    public AnalysisException(String message, AnalysisErrorType errorType) {
        super(message);
        this.errorType = errorType;
    }

    public AnalysisException(String message, Throwable cause, AnalysisErrorType errorType) {
        super(message, cause);
        this.errorType = errorType;
    }
    public AnalysisErrorType getErrorType() {
        return errorType;
    }
}
