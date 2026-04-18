package com.water.fzfwificenter.test;

import com.water.fzfwificenter.analyzer.AnalysisErrorType;
import com.water.fzfwificenter.analyzer.AnalysisException;
import com.water.fzfwificenter.analyzer.AnalyzerFactory;
import com.water.fzfwificenter.analyzer.LanguageAnalyzer;
import com.water.fzfwificenter.analyzer.ProgrammingLanguage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class JavaCodeAnalyzerTest {

    private final LanguageAnalyzer analyzer =
            AnalyzerFactory.getAnalyzer(ProgrammingLanguage.JAVA);

    @Test
    void analyze_emptyString_shouldThrowEmptyInput() {
        AnalysisException exception = assertThrows(
                AnalysisException.class,
                () -> analyzer.analyze("")
        );

        assertEquals(AnalysisErrorType.EMPTY_INPUT, exception.getErrorType());
    }

    @Test
    void analyze_blankString_shouldThrowEmptyInput() {
        AnalysisException exception = assertThrows(
                AnalysisException.class,
                () -> analyzer.analyze("   ")
        );

        assertEquals(AnalysisErrorType.EMPTY_INPUT, exception.getErrorType());
    }

    @Test
    void analyze_invalidSyntax_shouldThrowParseError() {
        String invalidCode = """
                public class Sample {
                    public void hello( {
                        System.out.println("test");
                    }
                }
                """;

        AnalysisException exception = assertThrows(
                AnalysisException.class,
                () -> analyzer.analyze(invalidCode)
        );

        assertEquals(AnalysisErrorType.Analysis_ERROR, exception.getErrorType());
    }

    @Test
    void analyze_validCode_shouldSucceed() {
        String validCode = """
                package demo;

                public class Sample {
                    private int age;

                    public void hello(String name) {
                        System.out.println(name);
                    }
                }
                """;

        String result = analyzer.analyze(validCode);

        assertNotNull(result);
        assertFalse(result.isBlank());
    }
}
