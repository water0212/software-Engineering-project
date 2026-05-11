package com.water.fzfwificenter.test;
import com.water.fzfwificenter.analyzer.exception.AnalysisException;
import com.water.fzfwificenter.analyzer.factory.AnalyzerFactory;
import com.water.fzfwificenter.analyzer.core.LanguageAnalyzer;
import com.water.fzfwificenter.analyzer.type.ProgrammingLanguage;

public class TestAnalyzer {
    public static void main(String[] args) {
        String result = "";
        String Javacode = """
                package demo;

                import java.util.List;

                public class Sample {
                    private int age;

                    public void hello(String name) {
                        int x = 10;
                        System.out.println(name);
                    }
                }
                """;
        try {
            LanguageAnalyzer analyzer = AnalyzerFactory.getAnalyzer(ProgrammingLanguage.JAVA);
            result = analyzer.analyze(Javacode);
        }catch(AnalysisException e){
            switch (e.getErrorType()) {
                case EMPTY_INPUT -> result = ("請先輸入程式碼");
                case ANALYSIS_ERROR -> result = ("程式碼格式有誤，無法解析");
                case UNSUPPORTED_LANGUAGE -> result = ("目前不支援此語言");
            }
        }

        System.out.println(result);
    }
}
