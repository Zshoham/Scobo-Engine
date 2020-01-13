package query;

import indexer.Dictionary;
import indexer.DocumentMap;
import parser.Document;
import parser.Parser;
import util.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class QueryProcessor implements Parser.Consumer {

    private String indexPath;

    Dictionary dictionary;
    DocumentMap documentMap;

    HashMap<String, String[]> gloSim;

    QueryResult currentResult;

    TaskGroup CPUTasks;
    TaskGroup IOTasks;

    public QueryProcessor(String indexPath, Dictionary dictionary, DocumentMap documentMap) {
        this.indexPath = indexPath;
        this.dictionary = dictionary;
        this.documentMap = documentMap;
        CPUTasks = TaskManager.getTaskGroup(TaskManager.TaskType.COMPUTE);
        IOTasks = TaskManager.getTaskGroup(TaskManager.TaskType.IO);

        loadGloSim();
    }

    private void loadGloSim() {
        gloSim = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(Paths.get(Configuration.getInstance().getDictSimPath()));
            for (String line : lines) {
                String[] content = line.split(",");
                gloSim.put(content[0], Arrays.copyOfRange(content, 1, content.length));
            }

        } catch (IOException e) {
            Logger.getInstance().error(e);
        }
    }

    public QueryResult query(String... queries) {
        currentResult = new QueryResult(queries);
        CPUTasks.openGroup();
        Parser parser = new Parser(indexPath, this, () -> asDocuments(queries));
        parser.start();

        CPUTasks.awaitCompletion();
        return currentResult;
    }

    public QueryResult query(Pair<Integer, String>[] queries) {
        currentResult = new QueryResult(queries);
        CPUTasks.openGroup();
        Parser parser = new Parser(indexPath, this, () -> asDocuments(queries));
        parser.start();

        CPUTasks.awaitCompletion();
        return currentResult;
    }

    @Override
    public void consume(Document document) {
        CPUTasks.add(new Searcher(new Query(document), this));
    }

    @Override
    public void onFinishParser() {
        CPUTasks.closeGroup();
    }

    private static List<String> asDocuments(String... queries) {
        LinkedList<String> docs = new LinkedList<>();
        for (String query : queries) {
            String doc = "<DOCNO>" + query.hashCode() + "</DOCNO>" + "\n" +
                    "<TEXT>" + query + "</TEXT>";
            docs.add(doc);
        }

        return docs;
    }

    private static List<String> asDocuments(Pair<Integer, String>[] queries) {
        LinkedList<String> docs = new LinkedList<>();
        for (Pair<Integer, String> query : queries) {
            String doc = "<DOCNO>" + query.first + "</DOCNO>" + "\n" +
                    "<TEXT>" + query.second + "</TEXT>";
            docs.add(doc);
        }

        return docs;
    }
}
