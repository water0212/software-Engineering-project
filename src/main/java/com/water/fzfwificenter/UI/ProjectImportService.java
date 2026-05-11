package com.water.fzfwificenter.UI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.water.fzfwificenter.analyzer.AnalyzerFactory;
import com.water.fzfwificenter.analyzer.LanguageAnalyzer;
import com.water.fzfwificenter.analyzer.ProgrammingLanguage;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

class ProjectImportService {
    private final Path outputDirectory;
    private final ObjectMapper mapper;

    ProjectImportService(Path outputDirectory, ObjectMapper mapper) {
        this.outputDirectory = outputDirectory;
        this.mapper = mapper;
    }

    ImportResult analyzeImportedFiles(List<File> files) {
        LanguageAnalyzer analyzer = AnalyzerFactory.getAnalyzer(ProgrammingLanguage.JAVA);
        List<Map<String, Object>> allElements = new ArrayList<>();
        Map<String, String> importedFileCache = new HashMap<>();
        Map<String, String> importedJsonCache = new HashMap<>();
        prepareOutputDirectory();

        for (File file : files) {
            try {
                String code = Files.readString(file.toPath());
                String jsonStr = analyzer.analyze(code);
                saveAnalysisJson(file, jsonStr);
                allElements.addAll(convertToGraphElements(jsonStr, file.getName()));
                importedFileCache.put(file.getName(), code);
                importedJsonCache.put(file.getName(), jsonStr);
            } catch (Exception e) {
                System.err.println("分析失敗: " + file.getName());
            }
        }

        try {
            String finalJson = mapper.writeValueAsString(allElements);
            return new ImportResult(finalJson, importedFileCache, importedJsonCache);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    String toJsonFileName(String sourceFileName) {
        return sourceFileName.endsWith(".java")
                ? sourceFileName.substring(0, sourceFileName.length() - 5) + ".json"
                : sourceFileName + ".json";
    }

    void writeJsonFile(Path outputPath, String jsonStr) throws Exception {
        Files.writeString(outputPath, jsonStr, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    String updateAnalyzeFlag(String jsonStr, boolean analyzeValue) throws Exception {
        JsonNode rootNode = mapper.readTree(jsonStr);
        if (rootNode instanceof ObjectNode objectNode) {
            objectNode.put("analyze", analyzeValue);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(objectNode);
        }
        return jsonStr;
    }

    private List<Map<String, Object>> convertToGraphElements(String jsonStr, String fileName) throws Exception {
        List<Map<String, Object>> elements = new ArrayList<>();
        JsonNode root = mapper.readTree(jsonStr);
        JsonNode classes = root.path("classes");

        elements.add(createNode(fileName, fileName, "file", fileName, null, "none"));

        if (classes.isArray()) {
            for (JsonNode cls : classes) {
                String className = cls.path("className").asText();
                String classId = fileName + "_" + className;

                elements.add(createNode(classId, className, "class", fileName, fileName, "none"));

                JsonNode methods = cls.path("methods");
                if (methods.isArray()) {
                    for (JsonNode m : methods) {
                        String mName = m.path("methodName").asText();
                        String mNameLower = mName.toLowerCase();
                        String flow = "none";
                        if (mNameLower.startsWith("get") || mNameLower.contains("load") || mNameLower.contains("read")) {
                            flow = "input";
                        } else if (mNameLower.startsWith("set") || mNameLower.contains("save") || mNameLower.contains("write") || mNameLower.contains("render")) {
                            flow = "output";
                        }
                    }
                }
            }
        }
        return elements;
    }

    private void saveAnalysisJson(File sourceFile, String jsonStr) throws Exception {
        String normalizedJson = updateAnalyzeFlag(jsonStr, true);
        writeJsonFile(outputDirectory.resolve(toJsonFileName(sourceFile.getName())), normalizedJson);
    }

    private void prepareOutputDirectory() {
        try {
            if (Files.exists(outputDirectory)) {
                try (Stream<Path> paths = Files.walk(outputDirectory)) {
                    paths.sorted(Comparator.reverseOrder())
                            .filter(path -> !path.equals(outputDirectory))
                            .forEach(path -> {
                                try {
                                    Files.deleteIfExists(path);
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
                }
            }
            Files.createDirectories(outputDirectory);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to prepare files directory", e);
        }
    }

    private Map<String, Object> createNode(String id, String label, String type, String fileName, String parent, String flow) {
        Map<String, Object> n = new HashMap<>();
        Map<String, Object> d = new HashMap<>();
        d.put("id", id);
        d.put("label", label);
        d.put("type", type);
        d.put("fileName", fileName);
        if (parent != null) d.put("parent", parent);
        if (flow != null) d.put("flow", flow);
        n.put("data", d);
        return n;
    }
}
