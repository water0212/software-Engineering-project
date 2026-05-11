package com.water.fzfwificenter.analyzer.factory;

import com.water.fzfwificenter.analyzer.core.JavaCodeAnalyzer;
import com.water.fzfwificenter.analyzer.core.LanguageAnalyzer;
import com.water.fzfwificenter.analyzer.core.ProjectAnalyzer;
import com.water.fzfwificenter.analyzer.core.ProjectJavaAnalyzer;

/***
 * JAVA二層工廠，調用個別輸出與專案輸出。
 */
public class JavaAnalyzerFactory implements AnalyzerAbstractFactory {

    @Override
    public LanguageAnalyzer createLanguageAnalyzer() {
        return new JavaCodeAnalyzer();
    }

    @Override
    public ProjectAnalyzer createProjectAnalyzer() {
        return new ProjectJavaAnalyzer();
    }
}
