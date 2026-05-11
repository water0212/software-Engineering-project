package com.water.fzfwificenter.UI;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.water.fzfwificenter.analyzer.factory.AnalyzerFactoryProvider;
import com.water.fzfwificenter.analyzer.core.LanguageAnalyzer;
import com.water.fzfwificenter.analyzer.core.ProjectAnalyzer;
import com.water.fzfwificenter.analyzer.type.ProgrammingLanguage;
import com.water.fzfwificenter.model.ProjectFileSummary;
import com.water.fzfwificenter.model.ProjectSummaryResult;

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
        // 如果多個檔案屬於同一個資料夾，改用 ProjectAnalyzer 產生 projectJson，再由 summary 組出節點
        prepareOutputDirectory();

        try {
            // 檢查是否所有檔案都來自同一個父目錄
            var parents = files.stream()
                    .map(f -> f.toPath().getParent())
                    .distinct()
                    .toList();

            List<Map<String, Object>> allElements = new ArrayList<>();
            Map<String, String> importedFileCache = new HashMap<>();
            Map<String, String> importedJsonCache = new HashMap<>();

            if (files.size() > 1 && parents.size() == 1) {
                Path projectRoot = parents.get(0);
                ProjectAnalyzer projectAnalyzer = AnalyzerFactoryProvider.getFactory(ProgrammingLanguage.JAVA).createProjectAnalyzer();
                String projectJson = projectAnalyzer.analyzeProjectToJson(projectRoot);

                // 解析 summary JSON
                ProjectSummaryResult summary = mapper.readValue(projectJson, ProjectSummaryResult.class);

                for (ProjectFileSummary fileSummary : summary.getFiles()) {
                    String fileName = fileSummary.getFileName();
                    allElements.add(createNode(fileName, fileName, "file", fileName, null, "none"));

                    for (String className : fileSummary.getClasses()) {
                        String classId = fileName + "_" + className;
                        allElements.add(createNode(classId, className, "class", fileName, fileName, "none"));
                    }

                    // 嘗試讀取原始碼（若存在）
                    Path candidate = projectRoot.resolve(fileName);
                    if (!Files.exists(candidate)) {
                        // 有時 fileName 包含路徑分隔，嘗試直接使用 Path.of
                        candidate = projectRoot.resolve(Path.of(fileName));
                    }
                    String code = "";
                    try {
                        if (Files.exists(candidate)) code = Files.readString(candidate);
                    } catch (Exception ignored) {}
                    importedFileCache.put(fileName, code);

                    // 建立最小化的 per-file JSON 供其他模組使用（含 classes 和 methods 列表）
                    ObjectNode fileJson = mapper.createObjectNode();
                    ArrayNode classesArray = mapper.createArrayNode();
                    for (String className : fileSummary.getClasses()) {
                        ObjectNode cls = mapper.createObjectNode();
                        cls.put("className", className);
                        ArrayNode methodsArray = mapper.createArrayNode();
                        // project summary 提供每個檔案的 methods 清單
                        for (String m : fileSummary.getMethods()) {
                            ObjectNode mn = mapper.createObjectNode();
                            mn.put("methodName", m);
                            methodsArray.add(mn);
                        }
                        cls.set("methods", methodsArray);
                        classesArray.add(cls);
                    }
                    fileJson.set("classes", classesArray);
                    String perFileJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(fileJson);
                    importedJsonCache.put(fileName, perFileJson);

                    // 寫入到 outputDirectory 下的 files（確保 UI 其他部分能夠讀取）
                    try {
                        writeJsonFile(outputDirectory.resolve(toJsonFileName(fileName)), perFileJson);
                    } catch (Exception e) {
                        System.err.println("無法寫入 per-file JSON: " + fileName + " -> " + e.getMessage());
                    }
                }

                // 產生 dep.json，格式: { "files": [ { "fileName": "...", "dependencies": [ ... ] }, ... ] }
                ArrayNode depsArray = mapper.createArrayNode();
                for (ProjectFileSummary fs : summary.getFiles()) {
                    ObjectNode depNode = mapper.createObjectNode();
                    depNode.put("fileName", fs.getFileName());
                    ArrayNode deps = mapper.createArrayNode();
                    for (String d : fs.getDependencies()) deps.add(d);
                    depNode.set("dependencies", deps);
                    depsArray.add(depNode);
                }
                ObjectNode depRoot = mapper.createObjectNode();
                depRoot.set("files", depsArray);
                String depJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(depRoot);
                try {
                    writeJsonFile(outputDirectory.resolve("dep.json"), depJson);
                } catch (Exception e) {
                    System.err.println("無法寫入 dep.json -> " + e.getMessage());
                }

                String finalJson = mapper.writeValueAsString(allElements);
                return new ImportResult(finalJson, importedFileCache, importedJsonCache);
            }

            // 否則維持逐檔分析（舊流程）
            LanguageAnalyzer analyzer = AnalyzerFactoryProvider.getFactory(ProgrammingLanguage.JAVA).createLanguageAnalyzer();

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
