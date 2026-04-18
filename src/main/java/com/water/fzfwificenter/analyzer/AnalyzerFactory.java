package com.water.fzfwificenter.analyzer;

public class AnalyzerFactory {
    /**
     * 選擇分析器的分析語言
     * @param language 選擇ProgrammingLanguage中的哪種語言
     */
    public static LanguageAnalyzer getAnalyzer(ProgrammingLanguage language) {

        if (language == null) {
            throw new AnalysisException("語言類型不可為 null", AnalysisErrorType.UNSUPPORTED_LANGUAGE);
        }

        switch (language) {
            case JAVA:
                return new JavaCodeAnalyzer();
            default:
                throw new AnalysisException("尚未支援的語言: " + language, AnalysisErrorType.UNSUPPORTED_LANGUAGE);
        }
    }
}
