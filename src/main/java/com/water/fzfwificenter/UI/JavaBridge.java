package com.water.fzfwificenter.UI;

import javafx.application.Platform;

// 1. 必須是獨立的 public class
public class JavaBridge {

    // 用來參考我們的主畫面，這樣才能控制文字框
    private final MainScreen mainScreen;

    public JavaBridge(MainScreen mainScreen) {
        this.mainScreen = mainScreen;
    }

    // 2. 供 JS 呼叫的方法必須是 public
    public void log(String msg) {
        System.out.println("[前端 JS] " + msg);
    }

    // 3. 供 JS 呼叫的方法必須是 public
    public void showCode(String fileName) {
        System.out.println("[後端 Java 橋樑] 收到點擊事件，準備顯示檔名: " + fileName);

        // 確保切換回 UI 執行緒
        Platform.runLater(() -> {
            mainScreen.showCodeInArea(fileName);
        });
    }
}