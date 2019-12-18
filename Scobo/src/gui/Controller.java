package gui;

import com.sun.org.apache.xalan.internal.xsltc.dom.DocumentCache;
import indexer.Dictionary;
import indexer.DocumentMap;
import indexer.Indexer;
import indexer.PostingCache;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import parser.Parser;
import util.Configuration;

import java.io.File;
import java.io.IOException;

public class Controller {


    @FXML public TextField corpusPath;
    @FXML public TextField indexPath;
    @FXML public TextField logPath;
    @FXML public TextField parserBatchSize;
    @FXML public CheckBox useStemmer;

    private Stage stage;

    private Configuration configuration;

    private DirectoryChooser directoryChooser;

    private Dictionary dictionary;
    private DocumentMap documentMap;

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
        Indexer indexer = new Indexer();
        Parser parser = new Parser(configuration.getCorpusPath(), indexer);
        long t0 = System.currentTimeMillis();
        parser.start();
        parser.awaitRead();
        long readTime = System.currentTimeMillis() - t0;
        parser.awaitParse();
        long parseTime = System.currentTimeMillis() - t0;
        indexer.awaitIndex();
        long indexTime = System.currentTimeMillis() - t0;
        String message = "number of documents indexed: " + parser.getDocumentCount() + "\n" +
                "number of unique terms identified: " + indexer.getTermCount() + "\n" +
                "time to read the corpus: " + readTime +"\n" +
                "time to parse all documents: " + parseTime + "\n" +
                "total indexing time: " + indexTime;
        showAlert("indexing completed successfully", message);
    }

    @FXML
    public void onClickReset() {
        try {
            dictionary.clear();
            documentMap.clear();
            PostingCache.deleteInvertedFile();
        } catch (IOException e) {
            showAlert("ERROR", "error deleting the dictionary files");
        }
        System.gc();

        showAlert("SUCCESS", "memory and disk have been successfully cleared");
    }

    @FXML
    public void onClickLoadDict() {
        try {
            this.dictionary = Dictionary.loadDictionary();
            this.documentMap = DocumentMap.loadDocumentMap();
        } catch (IOException e) {
            showAlert("ERROR", "could not load dictionaries");
        }

        showAlert("SUCCESS", "dictionary successfully loaded");
    }

    @FXML
    public void onClickShowDict() {

    }


    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
