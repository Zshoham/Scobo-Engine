package indexer;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/*
Posting File is no longer treated as a buffer, rather it is filled with
postings and then flushed right away and not loaded until the end of the inverting
phase.
once all the documents have been inverted we load pairs of posting files and
merge them together.
aa | ab | aa
ac | ac | ab
dg | bf | ac1 + ac2
          bf
          dg
 */

public class PostingFile {

    private final int postingFileID;

    private Map<String, TermPosting> postings;

    public PostingFile(int postingFileID) {
        this.postingFileID = postingFileID;
        //TODO: find better comparator.
        this.postings = new HashMap<>();
    }

    public void addTerm(Term term) {
        term.termPosting.setPostingFile(this);
        // the key of the posting should be the TermPosting's
        // representation of the term science it might be different
        // than the Term's
        this.postings.put(term.termPosting.getTerm(), term.termPosting);
    }

    public int getPostingCount() {
        return this.postings.size();
    }

    public int getID() {
        return postingFileID;
    }

    public void flush() {
        PostingCache.queuePostingFlush(this.postingFileID, postings);
    }
}
