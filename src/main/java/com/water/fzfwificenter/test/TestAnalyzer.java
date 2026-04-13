package com.water.fzfwificenter.test;
import com.water.fzfwificenter.analyzer.CodeAnalyzer;
public class TestAnalyzer {
    public static void main(String[] args) {
        String code = """
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

        CodeAnalyzer analyzer = new CodeAnalyzer();
        String result = analyzer.analyze(code);

        System.out.println(result);
    }
}
