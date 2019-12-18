package parser;

import indexer.Indexer;
import util.Configuration;
import util.Logger;
import util.TaskGroup;
import util.TaskManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Manages documents parsing.
 */
public class Parser {

    private static final int BATCH_SIZE = Configuration.getInstance().getParserBatchSize();

    private String corpusPath;
    private HashSet<String> stopWords;

    TaskGroup IOTasks;
    TaskGroup CPUTasks;
    Logger LOG = Logger.getInstance();

    private volatile AtomicInteger documentCount;

    private Indexer indexer;

    /**
     * Constructor for the parser object
     * <p>
     *     initialise the task groups
     *     give values to the parser elements
     *     loads the stop words list
     * </p>
     * @param path string path to the corpus directory
     * @param indexer pointer to the main indexer of the engine
     */
    public Parser(String path, Indexer indexer) {
        IOTasks = TaskManager.getTaskGroup(TaskManager.TaskType.IO);
        CPUTasks = TaskManager.getTaskGroup(TaskManager.TaskType.COMPUTE);
        this.corpusPath = path + "/corpus";
        this.indexer = indexer;
        this.documentCount = new AtomicInteger(0);

        loadStopWords(path);
    }

    //loads the stop words list- all the words to ignore from
    private void loadStopWords(String path) {
        try {
            Stream<String> lines = Files.lines(Paths.get(path + "/stop words.txt"));
            stopWords = lines.collect(HashSet::new, HashSet::add, HashSet::addAll);
        }
        catch (IOException e) { LOG.error(e); }
    }

    /**
     * check if given word is stop word
     * @param word The word to check if is a stop word
     * @return true if word is stop word, false otherwise
     */
    boolean isStopWord(String word) {
        return stopWords.contains(word);
    }

    /**
     * If configured to stem- stem a given word
     * otherwise, return the same word
     * @param word the word to stem
     * @return stemmed word or the same word
     */
    String stemWord(String word) {
        if(!Configuration.getInstance().getUseStemmer())
            return word;
        Stemmer stemmer = new Stemmer();
        for (int i = 0; i < word.length(); i++)
            stemmer.add(word.charAt(i));
        stemmer.stem();
        return stemmer.toString();
    }

    /**
     * notify the parser that document parse is finished
     * @param document which document is finished parsing
     */
    void onFinishedParse(Document document) {
        this.documentCount.incrementAndGet();
        indexer.index(document);
    }

    public void start() {
        new ReadFile(corpusPath, this);

        new Thread(this::finish, "parse waiter").start();
    }

    private void finish() {
        this.awaitParse();
        indexer.onFinishParser();
    }

    public void awaitRead() {
        IOTasks.awaitCompletion();
    }

    public void awaitParse() {
        CPUTasks.openGroup();
        CPUTasks.awaitCompletion();
    }

    public int getDocumentCount() {
        return this.documentCount.get();
    }

    int getBatchSize() { return Parser.BATCH_SIZE; }
}
