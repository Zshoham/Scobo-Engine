package parser;

import util.Configuration;
import util.Logger;
import util.TaskGroup;
import util.TaskManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;

public class Parser {

    private static final int BATCH_SIZE = 10;

    private String corpusPath;
    private HashSet<String> stopWords;

    protected TaskGroup IOTasks;
    protected TaskGroup CPUTasks;

    protected Logger LOG = Logger.getInstance();

    private HashSet<String> uniqueTerms;

    private Stemmer stemmer;

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
            String[] words = Files.readString(Paths.get(path + "/stop words.txt")).split("\r\n");
            stopWords = new HashSet<>();
            stopWords.addAll(Arrays.asList(words));
        }
        catch (IOException e) { LOG.error(e); }
    }

    private String stemWord(String word) {
        stemmer.add(word.toCharArray(), word.length());
        stemmer.stem();
        return stemmer.toString();
    }

    public void start() { new ReadFile(corpusPath, this); }

    public void awaitRead() {
        IOTasks.awaitCompletion();
    }

    public void awaitParse() {
        CPUTasks.awaitCompletion();
    }

    protected int getBatchSize() { return Parser.BATCH_SIZE; }
    public HashSet<String> getUniqueTerms() { return uniqueTerms; }
    protected HashSet<String> getStopWords() { return stopWords; }
}
