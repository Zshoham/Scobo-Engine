package parser;

import java.util.concurrent.atomic.AtomicInteger;

public class Parse implements Runnable{
    private String document;

    private static AtomicInteger count = new AtomicInteger();

    public Parse(String document) {
        this.document = document;
    }

    @Override
    public void run() {
        count.incrementAndGet();
        if (count.get() > 946000) System.out.println(count.get());
        else if (count.get() % 10000 == 0) System.out.println(count.get());
    }
}
