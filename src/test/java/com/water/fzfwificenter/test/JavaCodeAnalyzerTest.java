package com.water.fzfwificenter.test;

import com.water.fzfwificenter.analyzer.AnalysisErrorType;
import com.water.fzfwificenter.analyzer.AnalysisException;
import com.water.fzfwificenter.analyzer.AnalyzerFactory;
import com.water.fzfwificenter.analyzer.JavaCodeAnalyzer;
import com.water.fzfwificenter.analyzer.LanguageAnalyzer;
import com.water.fzfwificenter.analyzer.ProgrammingLanguage;
import com.water.fzfwificenter.model.AnalysisResult;
import com.water.fzfwificenter.model.ClassInfo;
import com.water.fzfwificenter.model.MethodInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class JavaCodeAnalyzerTest {

    private final LanguageAnalyzer analyzer =
            AnalyzerFactory.getAnalyzer(ProgrammingLanguage.JAVA);

    private final JavaCodeAnalyzer javaAnalyzer = new JavaCodeAnalyzer();

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

        assertEquals(AnalysisErrorType.ANALYSIS_ERROR, exception.getErrorType());
    }

    @Test
    void analyze_validCode_shouldSucceed() {
        String validCode = """
                package demo;

                public class Sample {
                    public void hello(String name) {
                        System.out.println(name);
                    }
                }
                """;

        String result = analyzer.analyze(validCode);

        assertNotNull(result);
        assertFalse(result.isBlank());
        assertTrue(result.contains("Class: Sample"));
        assertTrue(result.contains("Method: void hello(String name)"));
    }

    @Test
    void extractClassRelations_shouldExtractClassMethodsAndParameters() {
        String code = """
                public class UserService {
                    public String login(String username, String password) {
                        return "ok";
                    }

                    private void printUser(User user, int level) {
                    }
                }
                """;

        List<ClassInfo> classes = javaAnalyzer.extractClassRelations(code);

        assertEquals(1, classes.size());

        ClassInfo classInfo = classes.get(0);
        assertEquals("UserService", classInfo.getClassName());
        assertEquals(2, classInfo.getMethods().size());

        MethodInfo loginMethod = classInfo.getMethods().get(0);
        assertEquals("login", loginMethod.getMethodName());
        assertEquals("String", loginMethod.getReturnType());
        assertEquals(2, loginMethod.getParameters().size());

        assertEquals("String", loginMethod.getParameters().get(0).getType());
        assertEquals("username", loginMethod.getParameters().get(0).getName());

        assertEquals("String", loginMethod.getParameters().get(1).getType());
        assertEquals("password", loginMethod.getParameters().get(1).getName());

        MethodInfo printUserMethod = classInfo.getMethods().get(1);
        assertEquals("printUser", printUserMethod.getMethodName());
        assertEquals("void", printUserMethod.getReturnType());
        assertEquals(2, printUserMethod.getParameters().size());

        assertEquals("User", printUserMethod.getParameters().get(0).getType());
        assertEquals("user", printUserMethod.getParameters().get(0).getName());

        assertEquals("int", printUserMethod.getParameters().get(1).getType());
        assertEquals("level", printUserMethod.getParameters().get(1).getName());
    }

    @Test
    void extractClassRelations_shouldHandleMethodWithoutParameters() {
        String code = """
                public class Demo {
                    public void run() {
                    }
                }
                """;

        List<ClassInfo> classes = javaAnalyzer.extractClassRelations(code);

        assertEquals(1, classes.size());
        assertEquals("Demo", classes.get(0).getClassName());
        assertEquals(1, classes.get(0).getMethods().size());

        MethodInfo method = classes.get(0).getMethods().get(0);
        assertEquals("run", method.getMethodName());
        assertEquals("void", method.getReturnType());
        assertEquals(0, method.getParameters().size());
    }

    @Test
    void extractClassRelations_shouldHandleMultipleClasses() {
        String code = """
                public class A {
                    public void methodA(String name) {
                    }
                }

                class B {
                    public int methodB(int age) {
                        return age;
                    }
                }
                """;

        List<ClassInfo> classes = javaAnalyzer.extractClassRelations(code);

        assertEquals(2, classes.size());

        assertEquals("A", classes.get(0).getClassName());
        assertEquals("B", classes.get(1).getClassName());

        assertEquals(1, classes.get(0).getMethods().size());
        assertEquals(1, classes.get(1).getMethods().size());

        assertEquals("methodA", classes.get(0).getMethods().get(0).getMethodName());
        assertEquals("methodB", classes.get(1).getMethods().get(0).getMethodName());
    }
    @Test
    void analyzeToJson_shouldReturnValidJson() {
        String code = """
            public class UserService {
                public String login(String username, String password) {
                    return "ok";
                }
            }
            """;

        JavaCodeAnalyzer analyzer = new JavaCodeAnalyzer();
        String json = analyzer.analyzeToJson(code);

        assertNotNull(json);
        assertFalse(json.isBlank());
        assertTrue(json.contains("\"language\""));
        assertTrue(json.contains("\"JAVA\""));
        assertTrue(json.contains("\"className\""));
        assertTrue(json.contains("\"UserService\""));
        assertTrue(json.contains("\"methodName\""));
        assertTrue(json.contains("\"login\""));
        assertTrue(json.contains("\"parameters\""));
        assertTrue(json.contains("\"username\""));
        assertTrue(json.contains("\"password\""));
    }
    @Test
    void analyzeToResult_shouldReturnStructuredResult() {
        String code = """
            public class UserService {
                public String login(String username, String password) {
                    return "ok";
                }
            }
            """;

        JavaCodeAnalyzer analyzer = new JavaCodeAnalyzer();
        AnalysisResult result = analyzer.analyzeToResult(code);

        assertEquals("JAVA", result.getLanguage());
        assertEquals(1, result.getClasses().size());
        assertEquals("UserService", result.getClasses().get(0).getClassName());
        assertEquals(1, result.getClasses().get(0).getMethods().size());
    }

}
