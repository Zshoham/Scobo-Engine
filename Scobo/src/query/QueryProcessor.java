package query;

import indexer.Dictionary;
import indexer.DocumentMap;
import parser.Document;
import parser.Parser;
import util.Configuration;
import util.Logger;
import util.TaskGroup;
import util.TaskManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class QueryProcessor implements Parser.Consumer {

    private String corpusPath;

    Dictionary dictionary;
    DocumentMap documentMap;

    HashMap<String, String[]> gloSim;

    QueryResult currentResult;

    TaskGroup CPUTasks;
    TaskGroup IOTasks;

    public QueryProcessor(String corpusPath, Dictionary dictionary, DocumentMap documentMap) {
        this.corpusPath = corpusPath;
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
        Parser parser = new Parser(corpusPath, this, () -> asDocuments(queries));
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
        return Arrays.stream(queries)
                .map(s -> "<DOCNO>+" + s.hashCode() +"</DOCNO>\n <TEXT>" + s + "</TEXT>")
                .collect(Collectors.toList());
    }
}
