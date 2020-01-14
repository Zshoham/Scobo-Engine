package gui;

import indexer.*;
import indexer.Dictionary;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import parser.Parser;
import query.QueryProcessor;
import query.QueryResult;
import util.Configuration;
import util.Pair;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

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
    private FileChooser fileChooser;

    private Dictionary dictionary;
    private DocumentMap documentMap;
    private QueryProcessor queryProcessor;

    private final ObservableList<DictionaryEntry> viewableDictionary = FXCollections.observableArrayList();

    public void setStage(Stage stage) {
        this.stage = stage;

        configuration = Configuration.getInstance();

        corpusPath.setText(new File(configuration.getCorpusPath()).getAbsolutePath());
        indexPath.setText(new File(configuration.getIndexPath()).getAbsolutePath());
        logPath.setText(new File(configuration.getLogPath()).getAbsolutePath());
        useStemmer.selectedProperty().setValue(configuration.getUseStemmer());
        useSemantic.selectedProperty().setValue(configuration.getUseSemantic());
        useSemantic.selectedProperty().addListener((observable, oldValue, newValue) -> configuration.setUseSemantic(newValue));
        useStemmer.selectedProperty().addListener((observable, oldValue, newValue) -> {
            configuration.setUseStemmer(newValue);
            dictionary = null;
            documentMap = null;
            viewableDictionary.clear();
        });

        parserBatchSize.setText(String.valueOf(configuration.getParserBatchSize()));
        parserBatchSize.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d{1,4}"))
                parserBatchSize.setText(oldValue);
        });

        directoryChooser = new DirectoryChooser();
        fileChooser = new FileChooser();

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
        fileChooser.setTitle("Select Query File Path");
        File browsedFile = fileChooser.showOpenDialog(stage);
        if (browsedFile != null)
            queryPath.setText(browsedFile.getAbsolutePath());
    }

    @FXML
    public void onClickBrowseLog() {
        fileChooser.setTitle("Select Index Path");
        File browsedFile = fileChooser.showOpenDialog(stage);
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

        try {
            if (Files.notExists(Paths.get(indexPath.getText() + "/stop_words.txt"))) {
                Files.copy(Paths.get(corpusPath.getText() + "/stop_words.txt"),
                        Paths.get(indexPath.getText() + "/stop_words.txt"));
            }
        } catch (IOException e) {
            showAlert("ERROR", "could not copy stop_words.txt to index location");
        }
    }

    @FXML
    public void onClickRunQuery() {
        updateOptions();
        if (dictionary == null || documentMap == null) {
            showAlert("ERROR", "cannot process query when dictionary\nis not loaded.");
            return;
        }

        if (!Files.exists(Paths.get(configuration.getInvertedFilePath()))) {
            showAlert("ERROR", "no inverted index was found at the path provided.");
            return;
        }

        if (queryText.getText().isEmpty() && queryPath.getText().isEmpty()) {
            showAlert("ERROR", "no query was submitted.");
            return;
        }

        if (!queryText.getText().isEmpty())
            handleTextQuery();

        if (!queryPath.getText().isEmpty())
            handleFileQuery();

    }

    private void handleTextQuery() {
        QueryResult textResult;
        if (!queryText.getText().isEmpty()) {
            long t0 = System.currentTimeMillis();
            textResult = queryProcessor.query(queryText.getText());
            double queryTime = (System.currentTimeMillis() - t0) / 1000.0;
            showAlert("SUCCESS", "query results are ready!\n" +
                    " query processing took: " + queryTime);
            showQueryResult(textResult);
        }
    }

    private void handleFileQuery() {
        QueryResult fileResult;
        Pair<Integer, String>[] queries = getQueriesFromFile();
        if (queries != null) {
            long t0 = System.currentTimeMillis();
            fileResult = queryProcessor.query(queries);
            double queryTime = (System.currentTimeMillis() - t0) / 1000.0;
            showAlert("SUCCESS", "query results are ready!\n" +
                    "query processing took: " + queryTime);

            String resultPath = directoryChooser.showDialog(stage).getAbsolutePath() + "/results.txt";
            saveQueryResults(fileResult, resultPath);
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
            this.queryProcessor = new QueryProcessor(configuration.getIndexPath(), dictionary, documentMap);
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
    private Pair<Integer, String>[] getQueriesFromFile() {
        if (queryPath.getText().isEmpty())
            return null;
        Pair<Integer, String>[] queries = null;
        try {
            LinkedList<Pair<Integer, StringBuilder>> queryStrings = new LinkedList<>();
            BufferedReader reader = new BufferedReader(new FileReader(queryPath.getText()));
            String line;
            while ((line = reader.readLine()) != null) {
                // the syntax is "<num> Number: ddd" we want the "ddd"
                if (line.contains("<num> Number:")) {
                    String number = line.substring(line.indexOf(":") + 2).trim();
                    queryStrings.addLast(new Pair<>(Integer.parseInt(number), new StringBuilder()));
                }

                // the syntax is "<title> title string" we want the "title string"
                if (line.contains("<title>"))
                    queryStrings.getLast().second.append(line.substring(line.indexOf('>') + 1).trim());


                // the syntax is "<desc> Description: multiline string", we want the "multiline string"
                if (line.contains("<desc> Description:")) {
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty() || line.charAt(0) == '<')
                            break;
                        queryStrings.getLast().second.append(line).append("\n");
                    }
                }

                if (line == null) break;
                if (line.contains("<narr> Narrative: ")) {
                    while ((line = reader.readLine()) != null) {
                        if (line.isEmpty() || line.charAt(0) == '<')
                            break;
                        if (line.contains("not relevant"))
                            break;
                        queryStrings.getLast().second.append(line).append("\n");
                    }
                }
            }
            queries = new Pair[queryStrings.size()];
            int index = 0;
            for (Pair<Integer, StringBuilder> queryString : queryStrings)
                queries[index++] = new Pair<>(queryString.first, queryString.second.toString());

        } catch (IOException e) {
            showAlert("ERROR", "could not read query file");
        }

        return queries;
    }

    // saves the query results as a file.
    private void saveQueryResults(QueryResult result, String path) throws IllegalStateException {
        try {
            BufferedWriter resultsWriter = new BufferedWriter(new FileWriter(path));

            for (Map.Entry<Integer, int[]> query : result.sorted()) {
                for (int docID : query.getValue()) {
                    Optional<DocumentMap.DocumentMapping> dictTerm = documentMap.lookup(docID);
                    String docName = dictTerm.orElseThrow(
                            () -> new IllegalStateException("query result contained nonexistent document"))
                            .name;

                    resultsWriter.append(String.valueOf(query.getKey())).append(" ")
                            .append("0 ")
                            .append(docName).append(" ")
                            .append("10 ")
                            .append("9 ")
                            .append("mt\n");
                }
            }

            resultsWriter.close();
        }
        catch (IOException e) {
            showAlert("ERROR", "failed to open query results file");
        }



    }

    // shows the query result in a new window.
    private void showQueryResult(QueryResult result) {
        int[] docs = result.first();
        HashMap<String, List<Pair<String, Integer>>> docNames = new HashMap<>();
        for (int docID : docs) {
            Optional<DocumentMap.DocumentMapping> optionalMapping = documentMap.lookup(docID);
            optionalMapping.ifPresent(doc -> docNames.put(doc.name, doc.dominantEntities));
        }

        TableView<String> resultTable = new TableView<>();

        ObservableList<String> documents = FXCollections.observableArrayList(docNames.keySet());

        TableColumn<String, String> column = new TableColumn<>("DOCNO");
        resultTable.getColumns().add(column);

        column.setCellValueFactory(data -> new SimpleStringProperty(data.getValue()));
        resultTable.setItems(documents);

        Stage queryResultStage = new Stage();
        StackPane root = new StackPane();

        Button saveButton = new Button();
        saveButton.setText("save result");
        saveButton.setOnMouseClicked(mouseEvent -> {
            String resultPath = directoryChooser.showDialog(stage).getAbsolutePath() + "/results.txt";
            saveQueryResults(result, resultPath);
        });

        Button showButton = new Button();
        showButton.setText("show entities");
        showButton.setOnMouseClicked(mouseEvent -> {
            String docName = resultTable.getSelectionModel().getSelectedItem();
            if (docNames.containsKey(docName))
                showDocumentEntities(docName, docNames.get(docName));
        });

        HBox buttons = new HBox(saveButton, showButton);
        VBox base = new VBox(resultTable, buttons);
        buttons.setFillHeight(true);
        base.setFillWidth(true);
        root.setPadding(new Insets(10));
        root.setAlignment(Pos.CENTER);

        root.getChildren().add(base);
        Scene scene = new Scene(root);
        queryResultStage.setScene(scene);
        queryResultStage.show();
    }

    private void showDocumentEntities(String docName, List<Pair<String, Integer>> entities) {
        if (entities.isEmpty()) {
            showAlert("Could Not Find Entities in " + docName, "");
            return;
        }

        entities.sort(Comparator.comparingInt(entity -> entity.second));
        StringBuilder alertText = new StringBuilder();
        for (Pair<String, Integer> entity : entities) {
            alertText.append(entity.first.toUpperCase())
                    .append(" appeared: ")
                    .append(entity.second)
                    .append(" times")
                    .append("\n");
        }

        showAlert("Dominant Entities for " + docName, alertText.toString());
    }
}
