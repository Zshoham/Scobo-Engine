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
import java.util.stream.Stream;

public class Parser {

    private static final int BATCH_SIZE = Configuration.getInstance().getParserBatchSize();

    private String corpusPath;
    private HashSet<String> stopWords;
    private Stemmer stemmer;

    protected TaskGroup IOTasks;
    protected TaskGroup CPUTasks;
    protected Logger LOG = Logger.getInstance();

    private Indexer indexer;

    public Parser(String path, Indexer indexer) {
        IOTasks = TaskManager.getTaskGroup(TaskManager.TaskType.IO);
        CPUTasks = TaskManager.getTaskGroup(TaskManager.TaskType.COMPUTE);
        this.corpusPath = path + "/corpus";
        this.stemmer = new Stemmer();
        loadStopWords(path);

        this.indexer = indexer;
    }

    private void loadStopWords(String path) {
        try {
            Stream<String> lines = Files.lines(Paths.get(path + "/stop words.txt"));
            stopWords = lines.collect(HashSet::new, HashSet::add, HashSet::addAll);
        }
        catch (IOException e) { LOG.error(e); }
    }

    protected synchronized boolean isStopWord(String word) {
        return stopWords.contains(word);
    }

    protected synchronized String stemWord(String word) {
        if(!Configuration.getInstance().getUseStemmer())
            return word;
        for (int i = 0; i < word.length(); i++)
            stemmer.add(word.charAt(i));
        stemmer.stem();
        return stemmer.toString();
    }

    protected void onFinishedParse(Document document) {
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


    protected int getBatchSize() { return Parser.BATCH_SIZE; }
    protected HashSet<String> getStopWords() { return stopWords; }


}
