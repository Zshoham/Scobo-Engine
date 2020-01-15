package gui;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import util.Configuration;
import util.Logger;

import java.io.*;
import java.util.Objects;

public class GUI extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader fxmlLoader = new FXMLLoader(Objects.requireNonNull(getClass().getClassLoader().getResource("gui.fxml")));
        Parent root = fxmlLoader.load();
        primaryStage.setTitle("Scobo Engine");
        primaryStage.setScene(new Scene(root, 700, 480));
        primaryStage.setResizable(false);
        primaryStage.show();

        // flush the log whenever the application exists.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> Logger.getInstance().flushLog()));

        Controller controller = fxmlLoader.getController();
        controller.setStage(primaryStage);

    }
}
