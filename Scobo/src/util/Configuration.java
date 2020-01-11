package util;

import java.io.*;
import java.util.Objects;
import java.util.Properties;

/**
 * Scobo Engine Configuration manager.
 * Handles the creation, loading, and updating
 * of the engine's configuration.
 */
public class Configuration {

    private static Configuration configuration;

    public static Configuration getInstance() {
        if (configuration == null)
            configuration = new Configuration();
        return configuration;
    }

    private static final String CONFIG_PATH = "scobo.properties";

    private String corpusPath;
    private static final String CORPUS_PATH_PROP = "CorpusPath";
    private static final String DEFAULT_CORPUS_PATH = "data";

    private String indexPath;
    private static final String INDEX_PATH_PROP = "IndexPath";
    private static final String DEFAULT_INDEX_PATH = "index";

    private int parserBatchSize;
    private static final String PARSER_BATCH_SIZE_PROP = "ParserBatchSize";
    private static final int DEFAULT_PARSER_BATCH_SIZE = 10;

    private String logPath;
    private static final String LOG_PATH_PROP = "LogPath";
    private static final String DEFAULT_LOG_PATH = "LOG.txt";

    private boolean useStemmer;
    private static final String USE_STEMMER_PROP = "UseStemmer";
    private static final boolean DEFAULT_USE_STEMMER = false;

    private boolean useSemantic;
    private static final String USE_SEMANTIC_PROP = "UseSemantic";
    private static final boolean DEFAULT_USE_SEMANTIC = false;

    private Configuration() {
        File configFile = new File(CONFIG_PATH);
        if (!configFile.exists())
            initConfiguration();
        else
            loadConfiguration();
    }

    // loads existing configuration file.
    private void loadConfiguration() {
        try {
            FileReader propReader = new FileReader(CONFIG_PATH);
            Properties properties = new Properties();
            properties.load(propReader);
            this.corpusPath = properties.getProperty(CORPUS_PATH_PROP);
            this.indexPath = properties.getProperty(INDEX_PATH_PROP);
            this.parserBatchSize = Integer.parseInt(properties.getProperty(PARSER_BATCH_SIZE_PROP));
            this.logPath = properties.getProperty(LOG_PATH_PROP);
            this.useStemmer = Boolean.parseBoolean(properties.getProperty(USE_STEMMER_PROP));
            this.useSemantic = Boolean.parseBoolean(properties.getProperty(USE_SEMANTIC_PROP));
        } catch (IOException e) {
            Logger.getInstance().error(e);
        }

    }

    // initializes a new configuration file.
    private void initConfiguration() {
        this.corpusPath = DEFAULT_CORPUS_PATH;
        this.indexPath = DEFAULT_INDEX_PATH;
        this.parserBatchSize = DEFAULT_PARSER_BATCH_SIZE;
        this.logPath = DEFAULT_LOG_PATH;
        this.useStemmer = DEFAULT_USE_STEMMER;
        this.useSemantic = DEFAULT_USE_SEMANTIC;
        updateConfig();
    }

    /**
    In order for the configuration changes to persist this method must be called
    explicitly otherwise the changed configuration will only apply to the current
    run.
     */
    public void updateConfig() {
        Properties properties = new Properties();
        properties.setProperty(CORPUS_PATH_PROP, this.corpusPath);
        properties.setProperty(INDEX_PATH_PROP, this.indexPath);
        properties.setProperty(PARSER_BATCH_SIZE_PROP, String.valueOf(this.parserBatchSize));
        properties.setProperty(LOG_PATH_PROP, this.logPath);
        properties.setProperty(USE_STEMMER_PROP, String.valueOf(this.useStemmer));
        properties.setProperty(USE_SEMANTIC_PROP, String.valueOf(this.useSemantic));

        try {
            FileWriter propWriter = new FileWriter(CONFIG_PATH);
            properties.store(propWriter, "Scobo Properties");
            propWriter.close();
        } catch (IOException e) {
            Logger.getInstance().error(e);
        }
    }

    /**
     * Changes the corpus path, this change only applies to
     * the current run of the engine, and will not persist
     * unless the {@link Configuration#updateConfig()} method is called.
     * @param corpusPath the size of the batch size.
     */
    public void setCorpusPath(String corpusPath) {
        this.corpusPath = corpusPath;
    }

    /**
     * Changes the index path, this change only applies to
     * the current run of the engine, and will not persist
     * unless the {@link Configuration#updateConfig()} method is called.
     * @param indexPath the size of the batch size.
     */
    public void setIndexPath(String indexPath) { this.indexPath = indexPath; }

    /**
     * Changes the parsers batch size, this change only applies to
     * the current run of the engine, and will not persist
     * unless the {@link Configuration#updateConfig()} method is called.
     * @param parserBatchSize the size of the batch size.
     */
    public void setParserBatchSize(int parserBatchSize) { this.parserBatchSize = parserBatchSize; }

    /**
     * Changes the path to the log file, this change only applies to
     * the current run of the engine, and will not persist
     * unless the {@link Configuration#updateConfig()} method is called.
     * @param logPath the new path to the log file.
     */
    public void setLogPath(String logPath) { this.logPath = logPath; }

    /**
     * Changes weather or not the engine will use a stemmer, this change only applies to
     * the current run of the engine, and will not persist
     * unless the {@link Configuration#updateConfig()} method is called.
     * @param useStemmer true if the engine should use a stemmer, false otherwise.
     */
    public void setUseStemmer(boolean useStemmer) { this.useStemmer = useStemmer; }

    /**
     * Changes weather or not the engine will use the semantic analysis, this change only applies to
     * the current run of the engine, and will not persist
     * unless the {@link Configuration#updateConfig()} method is called.
     * @param useSemantic true if the engine should use a stemmer, false otherwise.
     */
    public void setUseSemantic(boolean useSemantic) { this.useSemantic = useSemantic; }

    public String getCorpusPath() { return corpusPath; }
    public String getIndexPath() { return indexPath; }
    public int getParserBatchSize() { return parserBatchSize; }
    public String getLogPath() { return logPath; }
    public boolean getUseStemmer() { return useStemmer; }
    public boolean getUseSemantic() { return useSemantic; }

    public String getDictionaryPath() {
        return indexPath + "/"  + getUseStemmerPath() + "/dictionary.txt";
    }

    public String getDocumentMapPath() {
        return indexPath + "/"  + getUseStemmerPath() + "/document_map.txt";
    }

    public String getPostingFilePath() {
        return indexPath + "/"  + getUseStemmerPath() + "/postings/";
    }

    public String getInvertedFilePath() {
        return indexPath + "/"  + getUseStemmerPath() + "/inverted_file.txt";
    }

    public String getGloVeStemmedPath() {
        return Objects.requireNonNull(getClass().getClassLoader().getResource("GloSim.stemmed")).getPath();
    }

    public String getGloVeUnStemmedPath() {
        return Objects.requireNonNull(getClass().getClassLoader().getResource("GloSim.unstemmed")).getPath();
    }

    public String getDictSimPath() {
        return indexPath + "/" + getUseStemmerPath() + "/DictSim.txt";
    }

    // returns the correct folder name for the index according to the value of useStemmer
    private String getUseStemmerPath() {
        if (useStemmer) return "with_stemming";
        else return "without_stemming";
    }
}
