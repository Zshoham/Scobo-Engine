package parser;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parse implements Runnable{

    private String document;

    private HashSet<String> uniqueTerms;

    public Parse(String document, HashSet uniqueTerms) {
        this.document = document;
        this.uniqueTerms = uniqueTerms;
    }


    public static int wordCount = 0;

    @Override
    public void run() {
        final Pattern pattern = Pattern.compile("<TEXT>(.+?)</TEXT>", Pattern.DOTALL);
        final Matcher matcher = pattern.matcher(document);
        while (matcher.find()) {
            parseText(matcher.group());
        }
    }

    private static Object monitor = new Object();

    private void parseText(String text) {
        String[] words = text.split("\\s+");
        for (int i = 0; i < words.length; i++) {
            synchronized (monitor) {
                uniqueTerms.add(words[i]);
                wordCount++;
            }
        }
    }
}
