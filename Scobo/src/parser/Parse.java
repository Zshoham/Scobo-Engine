package parser;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Parse implements Runnable{

    private String document;

    private static final Pattern textPattern = Pattern.compile("<TEXT>(.+?)</TEXT>", Pattern.DOTALL);
    private static final Pattern splitPattern = Pattern.compile("\\s+");


    private HashSet<String> uniqueTerms;

    public Parse(String document, HashSet<String> uniqueTerms) {
        this.document = document;
        this.uniqueTerms = uniqueTerms;
    }


    public static int wordCount = 0;

    @Override
    public void run() {
        final Matcher matcher = textPattern.matcher(document);
        while (matcher.find()) {
            parseText(matcher.group());
        }
    }

    private static final Object monitor = new Object();

    private void parseText(String text) {
        String[] words = splitPattern.split(text);
        for (String word : words) {
            synchronized (monitor) {
                uniqueTerms.add(word);
                wordCount++;
            }
        }
    }
}
