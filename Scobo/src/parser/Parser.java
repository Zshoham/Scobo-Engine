package parser;

import util.Logger;
import util.TaskGroup;
import util.TaskManager;

import java.util.HashSet;

public class Parser {

    private static final int BATCH_SIZE = 10;

    private String corpusPath;

    protected TaskGroup IOTasks;
    protected TaskGroup CPUTasks;

    protected Logger LOG = Logger.getInstance();

    private HashSet<String> uniqueTerms;

    public Parser(String path) {
        IOTasks = TaskManager.getTaskGroup(TaskManager.TaskType.IO);
        CPUTasks = TaskManager.getTaskGroup(TaskManager.TaskType.COMPUTE);
        this.corpusPath = path;
        uniqueTerms = new HashSet<>();
    }

    public void start() { new ReadFile(corpusPath, this); }

    public void awaitRead() {
        IOTasks.awaitCompletion();
    }

    public void awaitParse() {
        CPUTasks.awaitCompletion();
    }

    protected int getBatchSize() { return Parser.BATCH_SIZE; }
    public HashSet<String> getUniqueTerms() { return this.uniqueTerms; }
}
