package com.water.fzfwificenter.analyzer;

public interface LanguageAnalyzer {
    ProgrammingLanguage getLanguage();
    String analyze(String code) throws AnalysisException;
}
