package test;

import parser.Parse;
import parser.Parser;

import java.sql.Time;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Test {


    public static void main(String[] args) throws InterruptedException {
        Parser parser = new Parser("C:\\Users\\Hod\\Desktop\\bgu\\Year3\\Sem5\\Information Retrival\\corpus\\corpus");
        parser.start();
        parser.awaitRead();
        System.out.println("finished reading");
        parser.awaitParse();
        System.out.println(parser.getUniqueTerms().size());
        System.out.println(Parse.wordCount);
    }
}
