package com.water.fzfwificenter.analyzer;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;

public class JavaCodeAnalyzer implements LanguageAnalyzer {

    @Override
    public ProgrammingLanguage getLanguage() {
        return ProgrammingLanguage.JAVA;
    }

    @Override
    public String analyze(String code) throws AnalysisException {

        if (code == null || code.trim().isEmpty()) {
            throw new AnalysisException("程式碼不可為空", AnalysisErrorType.EMPTY_INPUT);
        }

        StringBuilder result = new StringBuilder();

        try {
            CompilationUnit cu = StaticJavaParser.parse(code);

            cu.getPackageDeclaration().ifPresent(pkg ->
                    result.append("Package: ").append(pkg.getNameAsString()).append("\n"));

            result.append("Imports:\n");
            cu.getImports().forEach(imp ->
                    result.append("- ").append(imp.getNameAsString()).append("\n"));

            result.append("Classes:\n");
            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(cls ->
                    result.append("- ").append(cls.getNameAsString()).append("\n"));

            result.append("Fields:\n");
            cu.findAll(FieldDeclaration.class).forEach(field ->
                    result.append("- ").append(field.toString()).append("\n"));

            result.append("Methods:\n");
            cu.findAll(MethodDeclaration.class).forEach(method ->
                    result.append("- ")
                            .append(method.getType())
                            .append(" ")
                            .append(method.getNameAsString())
                            .append("()\n"));

            return result.toString();

        } catch (Exception e) {
            throw new AnalysisException("Java 程式碼解析失敗", e, AnalysisErrorType.Analysis_ERROR);
        }
    }
}
