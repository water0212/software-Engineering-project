package com.water.fzfwificenter;

//UI
import com.water.fzfwificenter.UI.AnalyzerTestView;
import com.water.fzfwificenter.UI.AppView;


import com.water.fzfwificenter.UI.MainScreen;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

public class MainApp extends Application {

    @Override
    public void start(Stage stage) {
        //AppView view = new AnalyzerTestView();
        MainScreen view = new MainScreen(stage);
        stage.setTitle("JavaFX 測試入口");
        stage.setScene(view.createScene());
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
