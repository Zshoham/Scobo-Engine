package indexer;

import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;

public class PostingFile {

    private static short runningID = 0;

    private short postingFileID;

    private ConcurrentLinkedQueue<TermPosting> postings;

    public PostingFile() {
        postingFileID = runningID++;
        postings = new ConcurrentLinkedQueue<>();
    }

    public void addTerm(Term term) {
        term.termPosting.setPostingFile(this);
        this.postings.offer(term.termPosting);
    }

    public static PostingFile load(int id) {
        return null;
    }

}
