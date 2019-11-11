package test;

import parser.Parser;

import java.sql.Time;
import java.util.ArrayList;
import java.util.List;

public class Test {


    public static void main(String[] args) throws InterruptedException {
        Parser parser = new Parser("C:\\Users\\Hod\\Desktop\\bgu\\Year3\\Sem5\\Information Retrival\\corpus\\corpus");
        parser.start();
        parser.setOnFinishRead(() -> System.out.println("finished reading"));
        parser.setOnFinishParse(() -> System.out.println("finished parsing !"));
        parser.awaitRead();
        parser.awaitParse();
    }
}
