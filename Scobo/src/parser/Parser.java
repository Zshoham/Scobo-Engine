package parser;

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
 * Manages file reading from the corpus,
 * and documents parsing.
 */
public class Parser {

    // Control the amount of files to read and separate every time
    private static final int BATCH_SIZE = Configuration.getInstance().getParserBatchSize();

    private String corpusPath;
    private HashSet<String> stopWords; // List of all the stop words- the words we ignore while parsing documents

    TaskGroup IOTasks;  // Group all the IO tasks
    TaskGroup CPUTasks; // Group all the CPU tasks
    Logger LOG = Logger.getInstance();

    private volatile AtomicInteger documentCount; // Count how many documents have been parsed

    private Consumer consumer; // Pointer to the consumer

    private DocumentProvider provider; // Provides documents to parse

    /**
     * Constructs a parser using the corpus path
     * and a Consumer
     * <p>
     *     initialise the task groups
     *     give values to the parser elements
     *     loads the stop words list
     * </p>
     * @param path string path to the corpus directory
     * @param consumer pointer to the consumer that will be supplied with the
     *                 parsed documents.
     */
    public Parser(String path, Consumer consumer) {
        IOTasks = TaskManager.getTaskGroup(TaskManager.TaskType.IO);
        CPUTasks = TaskManager.getTaskGroup(TaskManager.TaskType.COMPUTE);
        this.corpusPath = path + "/corpus";
        this.consumer = consumer;
        this.documentCount = new AtomicInteger(0);

        loadStopWords(path);
    }

    /**
     * Constructs a parser using a DocumentProvider
     * in place of using the corpus path in order to construct the
     * documents.
     * @param stopWordsPath path to where a stop words file will reside.
     * @param consumer the consumer that will use the parsers output.
     * @param provider provides the documents for parsing.
     */
    public Parser(String stopWordsPath, Consumer consumer, DocumentProvider provider) {
        this(stopWordsPath, consumer);
        this.provider = provider;
    }

    //loads the stop words list- all the words that need to be ignored
    private void loadStopWords(String path) {
        try {
            Stream<String> lines = Files.lines(Paths.get(path + "/stop_words.txt"));
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
        consumer.consume(document);
    }

    /**
     * Start the parsing process, if no DocumentProvider was set
     * then read files from the corpus path.
     */
    public void start() {
        CPUTasks.openGroup();
        if (provider != null) {
            for (String document : provider.getDocuments())
                CPUTasks.add(new Parse(document, this));

            CPUTasks.closeGroup();
        }
        else
            new ReadFile(corpusPath, this); //Start reading files

        new Thread(this::finish, "parse waiter").start(); // Start new thread to wait for parsing to finish
    }

    /**
     * What to do when the parsing process is done
     */
    private void finish() {
        this.awaitParse();          // Wait until parsing is done
        consumer.onFinishParser();  // Wait acknowledge the consumer parsing is done and he can now wait for indexing to finish
    }

    /**
     * Wait until finished reading all the corpus files
     */
    public void awaitRead() {
        IOTasks.awaitCompletion();
    }

    /**
     * Wait until parsing is done
     */
    public void awaitParse() {
        CPUTasks.awaitCompletion();
    }

    public int getDocumentCount() {
        return this.documentCount.get();
    }

    int getBatchSize() { return Parser.BATCH_SIZE; }


    public interface DocumentProvider {
        Iterable<String> getDocuments();
    }

    public interface Consumer {
        void consume(Document document);
        void onFinishParser();

    }
}
