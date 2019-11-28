package util;

import java.io.*;
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

    private int parserBatchSize;
    private static final String PARSER_BATCH_SIZE_PROP = "ParserBatchSize";
    private static final int DEFAULT_PARSER_BATCH_SIZE = 10;

    private String logPath;
    private static final String LOG_PATH_PROP = "LogPath";
    private static final String DEFAULT_LOG_PATH = "LOG.txt";

    private boolean useStemmer;
    private static final String USE_STEMMER_PROP = "UseStemmer";
    private static final boolean DEFAULT_USE_STEMMER = true;

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
            this.parserBatchSize = Integer.parseInt(properties.getProperty(PARSER_BATCH_SIZE_PROP));
            this.logPath = properties.getProperty(LOG_PATH_PROP);
            this.useStemmer = Boolean.parseBoolean(properties.getProperty(USE_STEMMER_PROP));
        } catch (IOException e) {
            Logger.getInstance().error(e);
        }

    }

    // initializes a new configuration file.
    private void initConfiguration() {
        this.parserBatchSize = DEFAULT_PARSER_BATCH_SIZE;
        this.logPath = DEFAULT_LOG_PATH;
        this.useStemmer = DEFAULT_USE_STEMMER;
        updateConfig();
    }

    /**
    In order for the configuration changes to persist this method must be called
    explicitly otherwise the changed configuration will only apply to the current
    run.
     */
    public void updateConfig() {
        Properties properties = new Properties();
        properties.setProperty(PARSER_BATCH_SIZE_PROP, String.valueOf(this.parserBatchSize));
        properties.setProperty(LOG_PATH_PROP, this.logPath);
        properties.setProperty(USE_STEMMER_PROP, String.valueOf(this.useStemmer));
        
        try {
            FileWriter propWriter = new FileWriter(CONFIG_PATH);
            properties.store(propWriter, "Scobo Properties");
            propWriter.close();
        } catch (IOException e) {
            Logger.getInstance().error(e);
        }
    }

    /**
     * Changes the parsers batch size, this change only apply's to
     * the current run of the engine, and will not persist
     * unless the {@link Configuration#updateConfig()} method is called.
     * @param parserBatchSize the size of the batch size.
     */
    public void setParserBatchSize(int parserBatchSize) { this.parserBatchSize = parserBatchSize; }

    /**
     * Changes the path to the log file, this change only apply's to
     * the current run of the engine, and will not persist
     * unless the {@link Configuration#updateConfig()} method is called.
     * @param logPath the new path to the log file.
     */
    public void setLogPath(String logPath) { this.logPath = logPath; }

    /**
     * Changes weather or not the engine will use a stemmer, this change only apply's to
     * the current run of the engine, and will not persist
     * unless the {@link Configuration#updateConfig()} method is called.
     * @param useStemmer true if the engine should use a stemmer, false otherwise.
     */
    public void setUseStemmer(boolean useStemmer) { this.useStemmer = useStemmer; }

    public int getParserBatchSize() { return parserBatchSize; }
    public String getLogPath() { return logPath; }
    public boolean isUseStemmer() { return useStemmer; }
}
