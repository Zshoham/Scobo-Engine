package indexer;

import java.util.concurrent.ConcurrentHashMap;

public class PostingCache {

    private static final int CACHE_SIZE = 10;

    private ConcurrentHashMap<Integer, PostingFile> postingFiles;
    private ConcurrentHashMap<Integer, PostingFile> postingCache;

    private Indexer indexer;

    public PostingCache(Indexer indexer) {
        this.postingFiles = new ConcurrentHashMap<>();
        this.postingCache = new ConcurrentHashMap<>(CACHE_SIZE);

        this.indexer = indexer;
    }

    public synchronized static PostingFile getRef(short postingFile) {
        return null;
    }

    public synchronized void requestPostingFile(short postingFile) {

    }

    public synchronized void releasePostingFile(short postingFile) {

    }

}
