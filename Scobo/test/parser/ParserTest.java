package parser;

import indexer.Indexer;
import org.junit.Before;
import org.junit.Test;
import util.Configuration;
import util.Logger;
import util.Timer;

import java.io.File;

public class ParserTest {

    private String corpusPath;
    private String miniCorpusPath;
    private Parser parser;
    private Indexer indexer;

    @Before
    public void setUp() {
        corpusPath = new File(getClass().getResource("/").getPath().split("Scobo/")[0] + "data").getPath();
        miniCorpusPath = new File(getClass().getResource("/").getPath().split("Scobo/")[0] + "mini").getPath();
        indexer = new Indexer();
        Configuration.getInstance().setUseStemmer(true);
    }

    @Test
    public void start() {
        parser = new Parser(corpusPath, indexer);
        Timer timer = new Timer();
        parser.start();
        parser.awaitRead();
        System.out.println("corpus read time : " + timer.time() + "ms");
        parser.awaitParse();
        System.out.println("parsing time : " + timer.time() + "ms");
        indexer.awaitIndex();
        System.out.println("indexing time : " + timer.time() + "ms");
        Logger.getInstance().flushLog();
    }

    @Test
    public void startMini() {
        parser = new Parser(miniCorpusPath, indexer);
        Timer timer = new Timer();
        parser.start();
        parser.awaitRead();
        System.out.println("corpus read time : " + timer.time() + "ms");
        parser.awaitParse();
        System.out.println("parsing time : " + timer.time() + "ms");
        indexer.awaitIndex();
        System.out.println("indexing time : " + timer.time() + "ms");
        Logger.getInstance().flushLog();
    }
}