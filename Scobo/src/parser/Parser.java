package parser;

import util.Configuration;
import util.Logger;
import util.TaskGroup;
import util.TaskManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.stream.Stream;

public class Parser {

    private static final int BATCH_SIZE = Configuration.getInstance().getParserBatchSize();

    private String corpusPath;
    private HashSet<String> stopWords;
    private Stemmer stemmer;

    protected TaskGroup IOTasks;
    protected TaskGroup CPUTasks;
    protected Logger LOG = Logger.getInstance();

    private HashSet<String> uniqueTerms;

    public Parser(String path) {
        IOTasks = TaskManager.getTaskGroup(TaskManager.TaskType.IO);
        CPUTasks = TaskManager.getTaskGroup(TaskManager.TaskType.COMPUTE);
        this.corpusPath = path + "/corpus";
        this.stemmer = new Stemmer();
        loadStopWords(path);
        uniqueTerms = new HashSet<>();
    }

    private void loadStopWords(String path) {
        try {
            Stream<String> lines = Files.lines(Paths.get(path + "/stop words.txt"));
            stopWords = lines.collect(HashSet::new, HashSet::add, HashSet::addAll);
        }
        catch (IOException e) { LOG.error(e); }
    }

    private String stemWord(String word) {
        stemmer.add(word.toCharArray(), word.length());
        stemmer.stem();
        return stemmer.toString();
    }

    public void start() {
        new ReadFile(corpusPath, this);

        new Thread(() -> finish(), "parse waiter").start();
    }

    public void finish() {
        this.awaitParse();
        handleCapitals();
    }

    public void handleCapitals() {
        //TODO: handle capitals here.
    }

    public void awaitRead() {
        IOTasks.awaitCompletion();
    }

    public void awaitParse() {
        CPUTasks.openGroup();
        CPUTasks.awaitCompletion();
    }


    protected int getBatchSize() { return Parser.BATCH_SIZE; }
    public HashSet<String> getUniqueTerms() { return uniqueTerms; }
    protected HashSet<String> getStopWords() { return stopWords; }
}
