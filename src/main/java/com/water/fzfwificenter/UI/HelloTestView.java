package com.water.fzfwificenter.UI;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class HelloTestView implements AppView {

    @Override
    public Scene createScene() {
        Label label = new Label("這是 Hello 測試畫面");

        VBox root = new VBox(label);
        root.setAlignment(Pos.CENTER);

        return new Scene(root, 800, 600);
    }
}

