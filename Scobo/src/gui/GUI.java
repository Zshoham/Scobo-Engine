package gui;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import util.Configuration;

public class GUI extends Application {

    @FXML public TextField corpusPath;
    @FXML public TextField indexPath;
    @FXML public CheckBox useStemmer;

    private Configuration configuration;

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(getClass().getClassLoader().getResource("gui.fxml"));
        primaryStage.setTitle("Scobo Engine");
        primaryStage.setScene(new Scene(root, 600, 350));
        primaryStage.setResizable(false);
        primaryStage.show();

        configuration = Configuration.getInstance();

        useStemmer = new CheckBox();

        useStemmer.selectedProperty().addListener((observableValue, aBoolean, t1) -> configuration.setUseStemmer(!configuration.getUseStemmer()));
    }

    @FXML
    public void onClickBrowseCorpus() {
        System.out.println("onClickBrowseCorpus");
    }

    @FXML
    public void onClickBrowseIndex() {
        System.out.println("onClickBrowseIndex");
    }

    @FXML
    public void onClickReset() {
        System.out.println("onClickReset");

    }

    @FXML
    public void onClickRun() {
        System.out.println("onClickRun");

    }

    @FXML
    public void onClickLoadDict() {
        System.out.println("onClickLoadDict");

    }

    @FXML
    public void onClickShowDict() {
        System.out.println("onClickShowDict");
    }
}
