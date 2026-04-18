package com.water.fzfwificenter.analyzer;

public class AnalyzerFactory {

    public static LanguageAnalyzer getAnalyzer(ProgrammingLanguage language) {

        if (language == null) {
            throw new AnalysisException("語言類型不可為 null", AnalysisErrorType.UNSUPPORTED_LANGUAGE);
        }

        return switch (language) {
            case JAVA -> new JavaCodeAnalyzer();
            default -> throw new AnalysisException("尚未支援的語言: ", AnalysisErrorType.UNSUPPORTED_LANGUAGE);
        };
    }
}
