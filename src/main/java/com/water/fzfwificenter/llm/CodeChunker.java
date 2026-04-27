package com.water.fzfwificenter.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

public class CodeChunker {
    private static final int MAX_CHARS_PER_CHUNK = 3000; // 預估 Token 限制（約 3k-4k 字元）

    public static List<String> splitByMethods(String code, String astJson) {
        List<String> chunks = new ArrayList<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(astJson);
            JsonNode classes = root.path("classes");

            if (classes.isArray() && !classes.isEmpty()) {
                JsonNode firstClass = classes.get(0);
                String className = firstClass.path("className").asText();

                // 1. 建立「類別骨架」作為每個 Chunk 的共通 Context
                String skeleton = "Class: " + className + " (Method Implementation follows...)\n";

                JsonNode methods = firstClass.path("methods");
                StringBuilder currentChunk = new StringBuilder(skeleton);

                for (JsonNode m : methods) {
                    String methodName = m.path("methodName").asText();
                    // 這裡理想上是從 code 中截取該方法的實作區塊
                    // 目前簡單模擬：將方法名稱作為標記，若檔案超大，建議在此精確截取 body
                    String methodMarker = "\n[Method Content: " + methodName + "]\n";

                    if (currentChunk.length() + methodMarker.length() > MAX_CHARS_PER_CHUNK) {
                        chunks.add(currentChunk.toString());
                        currentChunk = new StringBuilder(skeleton);
                    }
                    currentChunk.append(methodMarker);
                }
                chunks.add(currentChunk.toString());
            }
        } catch (Exception e) {
            chunks.add(code); // 失敗則回傳完整程式碼
        }
        return chunks;
    }
}