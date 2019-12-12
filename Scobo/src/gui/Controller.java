package gui;

import indexer.Indexer;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import parser.Parser;
import util.Configuration;

import java.io.File;

public class Controller {


    @FXML public TextField corpusPath;
    @FXML public TextField indexPath;
    @FXML public TextField logPath;
    @FXML public TextField parserBatchSize;
    @FXML public CheckBox useStemmer;

    private Stage stage;

    private Configuration configuration;

    private Parser parser;
    private DirectoryChooser directoryChooser;

    public void setStage(Stage stage) {
        this.stage = stage;

        configuration = Configuration.getInstance();

        corpusPath.setText(new File(configuration.getCorpusPath()).getAbsolutePath());
        indexPath.setText(new File(configuration.getIndexPath()).getAbsolutePath());
        logPath.setText(new File(configuration.getLogPath()).getAbsolutePath());
        useStemmer.selectedProperty().setValue(configuration.getUseStemmer());

        parserBatchSize.setText(String.valueOf(configuration.getParserBatchSize()));
        parserBatchSize.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d{1,4}"))
                parserBatchSize.setText(oldValue);
        });

        directoryChooser = new DirectoryChooser();
    }

    private void updateOptions() {
        configuration.setCorpusPath(corpusPath.getText());
        configuration.setIndexPath(indexPath.getText());
        configuration.setLogPath(logPath.getText());
        configuration.setUseStemmer(useStemmer.selectedProperty().get());
        configuration.setParserBatchSize(Integer.parseInt(parserBatchSize.getText()));
    }

    @FXML
    public void onClickBrowseCorpus() {
        directoryChooser.setTitle("Select Corpus Path");
        File browsedFile = directoryChooser.showDialog(stage);
        if (browsedFile != null)
            corpusPath.setText(browsedFile.getAbsolutePath());
    }

    @FXML
    public void onClickBrowseIndex() {
        directoryChooser.setTitle("Select Index Path");
        File browsedFile = directoryChooser.showDialog(stage);
        if (browsedFile != null)
            indexPath.setText(browsedFile.getAbsolutePath());
    }

    @FXML
    public void onClickBrowseLog() {
        directoryChooser.setTitle("Select Index Path");
        File browsedFile = directoryChooser.showDialog(stage);
        if (browsedFile != null)
            logPath.setText(browsedFile.getAbsolutePath());

    }

    @FXML
    public void onClickSaveOptions() {
        updateOptions();
        configuration.updateConfig();
    }

    @FXML
    public void onClickRun() {
        updateOptions();
        parser = new Parser(configuration.getCorpusPath(), new Indexer());
        long t = System.currentTimeMillis();
        parser.start();
        parser.awaitRead();
        System.out.println(System.currentTimeMillis() - t);
        parser.awaitParse();
        t = System.currentTimeMillis() - t;
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText("parse runtime = " + t);
        alert.showAndWait();
    }

    @FXML
    public void onClickReset() {

    }

    @FXML
    public void onClickLoadDict() {

    }

    @FXML
    public void onClickShowDict() {

    }
}
