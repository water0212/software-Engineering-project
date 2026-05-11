package com.water.fzfwificenter.analyzer.core;

import com.water.fzfwificenter.analyzer.type.ProgrammingLanguage;
import com.water.fzfwificenter.model.ProjectAnalysisResult;
import com.water.fzfwificenter.model.ProjectSummaryResult;

import java.nio.file.Path;

public interface ProjectAnalyzer {

    ProgrammingLanguage getLanguage();

    ProjectAnalysisResult analyzeProject(Path rootPath);

    ProjectSummaryResult analyzeProjectToSummary(Path rootPath);

    String analyzeProjectToJson(Path rootPath);
}
