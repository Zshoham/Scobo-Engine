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

/**
 * Manages the querying process, may only process one request at a time,
 * though a request may consist of multiple queries.
 */
public class QueryProcessor implements Parser.Consumer {

    private String indexPath;

    Dictionary dictionary;
    DocumentMap documentMap;

    HashMap<String, String[]> gloSim;

    QueryResult currentResult;

    TaskGroup searchTasks;

    /**
     * Initializes the query processor with the given dictionary and document map,
     * this constructor blocks while loading the Similarity file.
     * @param indexPath path to the index root.
     * @param dictionary dictionary that will be used for querying.
     * @param documentMap document map that will be used for querying.
     */
    public QueryProcessor(String indexPath, Dictionary dictionary, DocumentMap documentMap) {
        this.indexPath = indexPath;
        this.dictionary = dictionary;
        this.documentMap = documentMap;
        searchTasks = TaskManager.getTaskGroup(TaskManager.TaskType.COMPUTE);

        loadGloSim();
    }

    // loads the similarity vectors.
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

    /**
     * Request for a group of queries to be processed, where the queries may be any
     * free text, the query result can later be used to see the documents most similar to
     * each of the queries.
     * @param queries set of queries to be processed.
     * @return QueryResult containing the results for all the queries.
     */
    public QueryResult query(String... queries) {
        currentResult = new QueryResult(queries);
        searchTasks.openGroup();
        Parser parser = new Parser(indexPath, this, () -> asDocuments(queries));
        parser.start();

        searchTasks.awaitCompletion();
        return currentResult;
    }

    /**
     * Request for a group of queries to be processed, where the queries may be any
     * free text, but are provided with a query id, the query result can later be used to see the
     * documents most similar to each of the queries.
     * @param queries set of queries to be processed.
     * @return QueryResult containing the results for all the queries.
     */
    public QueryResult query(Pair<Integer, String>[] queries) {
        currentResult = new QueryResult(queries);
        searchTasks.openGroup();
        Parser parser = new Parser(indexPath, this, () -> asDocuments(queries));
        parser.start();

        searchTasks.awaitCompletion();
        return currentResult;
    }

    /**
     * initiates search using the given query.
     * @param document document representing a query.
     */
    @Override
    public void consume(Document document) {
        if (document.length > 0)
            searchTasks.add(new Searcher(new Query(document), this));
    }

    /**
     * notifies the query processor that parsing of the queries is complete
     * and no new consume calls will be made.
     */
    @Override
    public void onFinishParser() {
        searchTasks.closeGroup();
    }

    // creates queries that fit the format our parser expects from the given free text queries.
    private static List<String> asDocuments(String... queries) {
        LinkedList<String> docs = new LinkedList<>();
        for (String query : queries) {
            String doc = "<DOCNO>" + query.hashCode() + "</DOCNO>" + "\n" +
                    "<TEXT>" + query + "</TEXT>";
            docs.add(doc);
        }

        return docs;
    }

    // creates queries that fit the format our parser expects from the given <queryID, query> pairs.
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
