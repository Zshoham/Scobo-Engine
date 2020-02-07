package indexer;

import parser.Document;

import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a buffer of documents that builds up until a term
 * limit is reached (or exceeded) at which point the buffer is flushed
 * and queued to be inverted.
 */
class DocumentBuffer {

    /*
    This is a magic number that describes the size of each posting file
    in terms of Term number, this number has been chosen based on repeated testing
    and it seems that it leads to posting files with sizes of about 2MB - 4MB
    which yields the best runtime results on the test corpus.
     */
    private static final int BUFFER_TERM_CAPACITY = 65536; //2^16

    private LinkedList<Document> documents;
    private volatile int termCount;

    private final Indexer indexer;

    DocumentBuffer(Indexer indexer) {
        this.documents = new LinkedList<>();
        termCount = 0;
        this.indexer = indexer;
    }

    synchronized void addToBuffer(Document document) {
        documents.add(document);
        termCount += document.length;
        if (termCount >= BUFFER_TERM_CAPACITY)
            flush();
    }

    void flush() {
        indexer.queueInvert(documents);
        this.documents = new LinkedList<>();
        this.termCount = 0;
    }
}
