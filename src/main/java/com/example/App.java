package com.example;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Rectangle2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Screen;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App extends Application {

    private static final Logger logger = LoggerFactory.getLogger(App.class);

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getResource("/connection-form.fxml"));
        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        primaryStage.setTitle("Skyport Client");

        Image icon = new Image(getClass().getResourceAsStream("/flash.png"));
        primaryStage.getIcons().add(icon);

        // Center window
        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        primaryStage.setX((screenBounds.getWidth() - root.prefWidth(-1)) / 2);
        primaryStage.setY((screenBounds.getHeight() - root.prefHeight(-1)) / 2);

        primaryStage.show();
        logger.info("UI loaded and shown.");
    }

    public static void main(String[] args) {
        launch();
    }
}
