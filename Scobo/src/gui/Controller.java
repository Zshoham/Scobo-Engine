package gui;

import indexer.*;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import parser.Parser;
import util.Configuration;

import java.io.File;
import java.io.IOException;

/**
 * Application Controller
 */
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

    private final ObservableList<DictionaryEntry> viewableDictionary = FXCollections.observableArrayList();

    public void setStage(Stage stage) {
        this.stage = stage;

        configuration = Configuration.getInstance();

        corpusPath.setText(new File(configuration.getCorpusPath()).getAbsolutePath());
        indexPath.setText(new File(configuration.getIndexPath()).getAbsolutePath());
        logPath.setText(new File(configuration.getLogPath()).getAbsolutePath());
        useStemmer.selectedProperty().setValue(configuration.getUseStemmer());
        useStemmer.selectedProperty().addListener((observable, oldValue, newValue) -> configuration.setUseStemmer(newValue));

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
        double readTime = (System.currentTimeMillis() - t0) / 1000.0;
        parser.awaitParse();
        double parseTime = (System.currentTimeMillis() - t0) / 1000.0;
        indexer.awaitIndex();
        double indexTime = (System.currentTimeMillis() - t0) / 1000.0;
        String message = "number of documents indexed: " + parser.getDocumentCount() + "\n" +
                "number of unique terms identified: " + indexer.getTermCount() + "\n" +
                "time to read the corpus: " + readTime +"sec\n" +
                "time to parse all documents: " + parseTime + "sec\n" +
                "total indexing time: " + indexTime + "sec";
        showAlert("indexing completed successfully", message);
    }

    @FXML
    public void onClickReset() {
        try {
            if (dictionary != null) dictionary.clear();
            if (documentMap != null) documentMap.clear();
            PostingCache.deleteInvertedFile();
            viewableDictionary.clear();
        } catch (IOException e) {
            showAlert("ERROR", "error deleting the dictionary files");
            return;
        }
        finally {
            System.gc();
        }

        showAlert("SUCCESS", "memory and disk have been successfully cleared");
    }

    @FXML
    public void onClickLoadDict() {
        try {
            this.dictionary = Dictionary.loadDictionary();
            this.documentMap = DocumentMap.loadDocumentMap();
            viewableDictionary.clear();
        } catch (IOException e) {
            showAlert("ERROR", "could not load dictionaries");
            return;
        }

        showAlert("SUCCESS", "dictionary successfully loaded");
    }

    @FXML
    public void onClickShowDict() {
        if (dictionary == null || documentMap == null) {
            showAlert("ERROR", "the dictionary is not loaded");
            return;
        }
        
        if (viewableDictionary.isEmpty())
            makeViewable();

        // create TableView that will contain the dictionary
        TableView<DictionaryEntry> dictionaryTable = new TableView<>();

        TableColumn termColumn = new TableColumn("term");
        termColumn.setMinWidth(100);
        termColumn.setCellValueFactory(new PropertyValueFactory("term"));

        TableColumn frequencyColumn = new TableColumn("frequency");
        frequencyColumn.setMinWidth(100);
        frequencyColumn.setCellValueFactory(new PropertyValueFactory("frequency"));

        dictionaryTable.getColumns().addAll(termColumn, frequencyColumn);
        dictionaryTable.setItems(viewableDictionary);

        // create the new windows stage and scene
        Stage dictionaryStage = new Stage();
        StackPane root = new StackPane();
        root.setPadding(new Insets(10));
        root.setAlignment(Pos.CENTER);

        root.getChildren().addAll(dictionaryTable);
        Scene scene = new Scene(root, 400, 800);
        dictionaryStage.setScene(scene);
        dictionaryStage.show();
    }

    // creates a sorted view of the dictionary.
    private void makeViewable() {
        for (Term term : dictionary.getTerms())
            viewableDictionary.add(new DictionaryEntry(term.term, term.termFrequency));

        viewableDictionary.sort(DictionaryEntry.comparator);
    }

    // shows alert with given text and message
    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
