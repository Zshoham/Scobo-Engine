package gui;

import indexer.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.StackPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import parser.Parser;
import query.QueryProcessor;
import query.QueryResult;
import util.Configuration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

/**
 * Application Controller
 */
public class Controller {


    @FXML public TextField corpusPath;
    @FXML public TextField indexPath;
    @FXML public TextField logPath;
    @FXML public TextField parserBatchSize;
    @FXML public CheckBox useStemmer;
    @FXML public CheckBox useSemantic;
    @FXML public TextField queryText;
    @FXML public TextField queryPath;

    private Stage stage;

    private Configuration configuration;

    private DirectoryChooser directoryChooser;

    private Dictionary dictionary;
    private DocumentMap documentMap;

    private Indexer indexer;
    private QueryProcessor queryProcessor;

    private final ObservableList<DictionaryEntry> viewableDictionary = FXCollections.observableArrayList();

    public void setStage(Stage stage) {
        this.stage = stage;

        configuration = Configuration.getInstance();

        corpusPath.setText(new File(configuration.getCorpusPath()).getAbsolutePath());
        indexPath.setText(new File(configuration.getIndexPath()).getAbsolutePath());
        logPath.setText(new File(configuration.getLogPath()).getAbsolutePath());
        useStemmer.selectedProperty().setValue(configuration.getUseStemmer());
        useStemmer.selectedProperty().addListener((observable, oldValue, newValue) -> configuration.setUseStemmer(newValue));
        useSemantic.selectedProperty().addListener((observable, oldValue, newValue) -> configuration.setUseSemantic(newValue));

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
        configuration.setUseSemantic(useSemantic.selectedProperty().get());
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
    public void onClickBrowseQuery() {
        directoryChooser.setTitle("Select Query File Path");
        File browsedFile = directoryChooser.showDialog(stage);
        if (browsedFile != null)
            queryPath.setText(browsedFile.getAbsolutePath());
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
    public void onClickRunIndex() {
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
    public void onClickRunQuery() {
        updateOptions();
        if (dictionary == null || documentMap == null)
            showAlert("ERROR", "cannot process query when dictionary is not loaded.");
        if (!Files.exists(Paths.get(configuration.getInvertedFilePath())))
            showAlert("ERROR", "no inverted index was found at the path provided.");

        queryProcessor = new QueryProcessor(configuration.getIndexPath(), dictionary, documentMap);
        QueryResult textResult;
        if (!queryText.getText().isEmpty()) {
            long t0 = System.currentTimeMillis();
            textResult = queryProcessor.query(queryText.getText());
            double queryTime = (System.currentTimeMillis() - t0) / 1000.0;
            showAlert("SUCCESS", "query results are ready!\n" +
                    " query processing took: " + queryTime);
            showQueryResult(textResult.first());
        }

        QueryResult fileResult;
        String[] queries = getQueriesFromFile();
        if (queries != null) {
            long t0 = System.currentTimeMillis();
            fileResult = queryProcessor.query(queries);
            saveQueryResults(fileResult);
            double queryTime = (System.currentTimeMillis() - t0) / 1000.0;
            showAlert("SUCCESS", "query results are ready!\n" +
                    "query processing took: " + queryTime + "\n" +
                    "the query results were saved to file \"qrels.txt\" at: " +
                    queryPath.getText().substring(0, queryPath.getText().lastIndexOf("/")));
        }
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

    // parses the query file and returns an array of queries.
    private String[] getQueriesFromFile() {
        String[] queries = null;
        try {
            LinkedList<String> queryStrings = new LinkedList<>();
            List<String> lines = Files.readAllLines((Paths.get(queryPath.getText())));
            for (String line : lines) {
                if (line.contains("<title>"))
                    queryStrings.add(line.substring(line.indexOf('>') + 1));
            }

            queries = queryStrings.toArray(new String[0]);
        } catch (IOException e) {
            showAlert("ERROR", "could not read query file");
        }

        return queries;
    }

    // saves the query results as a file.
    private void saveQueryResults(QueryResult result) {

    }

    // shows the query result in a new window.
    private void showQueryResult(List<Integer> first) {

    }
}
