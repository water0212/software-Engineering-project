package com.water.fzfwificenter.Visualizer.Object;

// 1. 建立一個橋樑類別
public class JavaScriptBridge {
    // 這個方法稍後會被網頁裡的 JS 呼叫
    public void onNodeClicked(String nodeId) {
        System.out.println("Java 收到了來自網頁的點擊！你點了: " + nodeId);

        // 這裡可以寫邏輯：根據 nodeId 去找出對應的原始碼
        // 然後更新 JavaFX 右側的 TextArea 或 CodeArea 來顯示程式碼
    }
}