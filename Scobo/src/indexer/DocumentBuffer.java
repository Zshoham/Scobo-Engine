package indexer;

import parser.Document;

import java.util.LinkedList;

class DocumentBuffer {

    private static final int BUFFER_TERM_CAPACITY = 1024; //2^10

    private LinkedList<Document> documents;
    private volatile int termCount;

    private final Indexer indexer;

    public DocumentBuffer(Indexer indexer) {
        this.documents = new LinkedList<>();
        termCount = 0;
        this.indexer = indexer;
    }

    public synchronized void addToBuffer(Document document) {
        documents.add(document);
        termCount += document.entities.size() + document.entities.size();
        if (termCount >= BUFFER_TERM_CAPACITY)
            flush();
    }

    public synchronized void flush() {
        indexer.queueInvert(documents);
        this.documents = new LinkedList<>();
        this.termCount = 0;
    }

    public void flushNow() {
        indexer.invert(documents);
        this.documents = new LinkedList<>();
        this.termCount = 0;
    }
}
