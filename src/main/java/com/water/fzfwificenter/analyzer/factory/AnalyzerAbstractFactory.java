package com.water.fzfwificenter.analyzer.factory;

import com.water.fzfwificenter.analyzer.core.LanguageAnalyzer;
import com.water.fzfwificenter.analyzer.core.ProjectAnalyzer;

public interface AnalyzerAbstractFactory {

    LanguageAnalyzer createLanguageAnalyzer();

    ProjectAnalyzer createProjectAnalyzer();
}
