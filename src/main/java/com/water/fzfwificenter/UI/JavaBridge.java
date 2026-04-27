package com.water.fzfwificenter.UI;
public class JavaBridge {
    private final MainScreen mainScreen;
    // 1. 必須是獨立的 public class
    public JavaBridge(MainScreen mainScreen) {
        this.mainScreen = mainScreen;
    }
    // 2. 供 JS 呼叫的方法必須是 public
    public void log(String msg) {
        System.out.println("[前端 JS] " + msg);
    }
    // JS 呼叫 window.javaApp.showCode(...)
    public void showCode(String fileName) {
        System.out.println("[後端 Java 橋樑] 收到點擊事件，準備顯示檔名: " + fileName);
        mainScreen.handleNodeSelectionWithAnalyzeStatus(fileName);

    }

    // JS 呼叫 window.javaApp.logZoom(...)
    public void logZoom(double level) {
        // 在終端機打印，使用 \r 保持在同一行
        System.out.printf("[Zoom Detector] 當前倍率: %.4f \r",
                level);
    }
}
