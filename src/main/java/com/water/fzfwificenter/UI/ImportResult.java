package com.water.fzfwificenter.UI;

import java.util.Map;

record ImportResult(String graphJson, Map<String, String> fileCache, Map<String, String> jsonCache) {
    String globalContext() {
        return jsonCache.values().toString();
    }
}
