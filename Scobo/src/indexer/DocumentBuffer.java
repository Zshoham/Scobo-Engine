package indexer;

import java.util.HashMap;
import java.util.LinkedList;

public class DocumentBuffer {

    private static final int BUFFER_TERM_CAPACITY = 10000;

    private LinkedList<Document> documents;
    private volatile int termCount;

    private final Indexer indexer;

    public DocumentBuffer(Indexer indexer) {
        this.documents = new LinkedList<>();
        termCount = 0;
        this.indexer = indexer;
    }

    public synchronized void addToBuffer(String docName, HashMap<String, Integer> docTerms) {
        documents.add(new Document(docName, docTerms));
        termCount += docTerms.size();
        if (termCount >= BUFFER_TERM_CAPACITY) {
            indexer.CPUTasks.add(() -> indexer.invert(documents));
            this.documents = new LinkedList<>();
        }
    }
}
