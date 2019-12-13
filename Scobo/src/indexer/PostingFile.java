package indexer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class PostingFile {

    private final int postingFileID;

    private ConcurrentHashMap<String, TermPosting> postings;

    private static final int BUFFER_MAX_SIZE = 32768; //2^15
    private volatile AtomicInteger bufferSize;
    private volatile AtomicBoolean isHolding;
    protected volatile boolean isWritten;

    public PostingFile(int postingFileID) {
        this.postingFileID = postingFileID;
        this.postings = new ConcurrentHashMap<>(BUFFER_MAX_SIZE);
        this.bufferSize = new AtomicInteger(0);
        this.isHolding = new AtomicBoolean(false);
        this.isWritten = false;
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
        this.postings = new ConcurrentHashMap<>(BUFFER_MAX_SIZE);
        bufferSize.set(0);
    }
}
