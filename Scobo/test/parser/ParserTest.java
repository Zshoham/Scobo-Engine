package parser;

import org.junit.Before;
import org.junit.Test;
import util.Logger;
import util.Timer;

import java.io.File;

public class ParserTest {

    private String corpusPath;
    private Parser parser;
    private Logger LOG = Logger.getInstance();

    @Before
    public void setUp() {
        corpusPath = new File(getClass().getResource("/").getPath().split("Scobo/")[0] + "data").getPath();
        parser = new Parser(corpusPath);
    }

    @Test
    public void start() {
        LOG.message("Starting parser test");
        Timer timer = new Timer();
        parser.start();
        parser.awaitRead();
        System.out.println("corpus read time : " + timer.time() + "ms");
        parser.awaitParse();
        System.out.println("parsing time : " + timer.time() + "ms");
        System.out.println("unique terms in the corpus: " + parser.getUniqueTerms().size());
        Logger.getInstance().flushLog();
    }
}