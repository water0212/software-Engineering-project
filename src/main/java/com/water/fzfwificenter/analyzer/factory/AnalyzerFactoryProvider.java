package com.water.fzfwificenter.analyzer.factory;

import com.water.fzfwificenter.analyzer.exception.AnalysisErrorType;
import com.water.fzfwificenter.analyzer.exception.AnalysisException;
import com.water.fzfwificenter.analyzer.type.ProgrammingLanguage;

/***
 * 最上層的工廠。用於調用不同語言的二層工廠
 * 呼叫方式
 * AnalyzerAbstractFactory factory = AnalyzerFactoryProvider.getFactory(ProgrammingLanguage.JAVA);
 *
 * LanguageAnalyzer singleAnalyzer = factory.createLanguageAnalyzer();
 * String singleJson = singleAnalyzer.analyze(code);
 *
 * ProjectAnalyzer projectAnalyzer = factory.createProjectAnalyzer();
 * String projectJson = projectAnalyzer.analyzeProjectToJson(path);
 *
 *
 */
public class AnalyzerFactoryProvider {

    public static AnalyzerAbstractFactory getFactory(ProgrammingLanguage language) {
        if (language == null) {
            throw new AnalysisException("語言類型不可為 null", AnalysisErrorType.UNSUPPORTED_LANGUAGE);
        }

        switch (language) {
            case JAVA:
                return new JavaAnalyzerFactory();
            default:
                throw new AnalysisException("不支援的語言: " + language, AnalysisErrorType.UNSUPPORTED_LANGUAGE);
        }
    }
}
