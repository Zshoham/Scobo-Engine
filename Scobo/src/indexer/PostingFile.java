package indexer;

import java.util.*;

public class PostingFile {

    private final int postingFileID;
    private final Map<String, TermPosting> postingDictionary;

    public PostingFile(int postingFileID) {
        this.postingFileID = postingFileID;
        postingDictionary = new HashMap<>();
    }

    public void addTerm(String term, int documentID, int documentFrequency) {
        postingDictionary.compute(term, (termStr, termPosting) -> {
            if(termPosting == null) {
                TermPosting newPosting = new TermPosting(term);
                newPosting.addDocument(documentID, documentFrequency);
                return newPosting;
            }

            termPosting.addDocument(documentID, documentFrequency);
            return termPosting;
        });
    }

    protected TermPosting[] getPostings(){
        TermPosting[] res = new TermPosting[postingDictionary.size()];
        postingDictionary.values().toArray(res);
        Arrays.sort(res, Comparator.comparing(TermPosting::getTerm));
        return res;
    }

    public int getID() {
        return postingFileID;
    }

    public void flush() {
        PostingCache.queuePostingFlush(this);
    }

}
