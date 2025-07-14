package com.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Screen;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/connection-form.fxml"));
        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Skyport Client");

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        primaryStage.setHeight(screenBounds.getHeight());

        primaryStage.setX((screenBounds.getWidth() - primaryStage.getWidth()) / 2);
        primaryStage.setY(0); 

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
