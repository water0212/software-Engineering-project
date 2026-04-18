package com.water.fzfwificenter.analyzer;

public interface LanguageAnalyzer {
    ProgrammingLanguage getLanguage();

    /**
     * 接收 Java 原始碼字串，分析 class、method 與 parameter 關係，
     * 並回傳 JSON 格式結果。
     *
     * @param code Java 原始碼字串
     * @return JSON 格式分析結果
     * @throws AnalysisException 當輸入為空、解析失敗或 JSON 轉換失敗時拋出
     */
    String analyze(String code) throws AnalysisException;
}
