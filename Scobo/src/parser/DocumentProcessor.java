package parser;

import test.Test;

public class DocumentProcessor implements Runnable {

    private String document;

    private static int docs = 0;

    public DocumentProcessor(String document) {
        this.document = document;
    }

    @Override
    public void run() {
        docs++;
        if (docs % 10000 == 0) System.out.println(docs);
        if (docs > 940000) System.out.println(docs);
        if (docs == 946864) Test.stop();
    }
}
