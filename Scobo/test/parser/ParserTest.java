package parser;

import org.junit.Before;
import org.junit.Test;
import util.Logger;
import util.Timer;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ParserTest {

    private String corpusPath;
    private Parser parser;
    private Logger LOG = Logger.getInstance();

    @Before
    public void setUp() throws IOException {
        //corpusPath = new File(getClass().getResource("/").getPath().split("Scobo/")[0] + "data").getPath();
        corpusPath = "C:\\Users\\Hod\\Desktop\\bgu\\Year3\\Sem5\\Information Retrival\\corpus";
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
        System.out.println("terms - " + Parse.terms.size()); //664,417
        System.out.println("capitals  - " + Parse.capitalLettersTerms.size()); //505,879
        System.out.println("entities - " + Parse.entities.size()); //1,656,909
        Logger.getInstance().flushLog();
    }
}