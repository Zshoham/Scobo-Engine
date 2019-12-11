package indexer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PostingFile {

    private final int postingFileID;

    private Map<String, TermPosting> postings;

    private static final int BUFFER_MAX_SIZE = 50000;
    private volatile AtomicInteger bufferSize;
    private volatile AtomicBoolean isHolding;

    public PostingFile(int postingFileID) {
        this.postingFileID = postingFileID;
        this.postings = new ConcurrentHashMap<>();
        bufferSize = new AtomicInteger(0);
        isHolding = new AtomicBoolean(false);
    }

    public synchronized void addTerm(Term term) {
        term.termPosting.setPostingFile(this);
        this.postings.put(term.term, term.termPosting);
    }

    public void hold() {
        this.isHolding.set(true);
    }

    public int getPostingCount() {
        return this.postings.size();
    }

    public synchronized void onDocumentAdded() {
        int currSize = this.bufferSize.getAndIncrement();

        if (currSize >= BUFFER_MAX_SIZE && !isHolding.get())
            flush();
    }

    public int getID() {
        return postingFileID;
    }

    public synchronized void flush() {
        isHolding.set(false);
        PostingCache.queuePostingFileUpdate(this.postingFileID, postings);
        postings = new ConcurrentHashMap<>();
        bufferSize.set(0);
    }
}
