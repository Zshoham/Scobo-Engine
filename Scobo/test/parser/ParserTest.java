package parser;

import org.junit.Before;
import org.junit.Test;
import util.Timer;

import java.io.File;

public class ParserTest {

    private String corpusPath;
    private Parser parser;

    @Before
    public void setUp() {
        corpusPath = new File(getClass().getResource("/").getPath().split("Scobo/")[0] + "data/corpus").getPath();
        parser = new Parser(corpusPath);
    }

    @Test
    public void start() throws InterruptedException {
        Timer timer = new Timer();
        parser.start();
        parser.awaitRead();
        System.out.println("corpus read time : " + timer.time() + "ms");
        parser.awaitParse();
        System.out.println("parsing time : " + timer.time() + "ms");
        System.out.println("number of unique terms in corpus : " + parser.getUniqueTerms().size());
        System.out.println("number of words in the corpus : " + Parse.wordCount);
    }
}