package indexer;

import java.util.*;

/**
 * Represents a posting file while its in memory.
 */
public class PostingFile {

    private final int postingFileID;
    private final Map<String, TermPosting> postingDictionary;

    /**
     * Creates a posting file with the given id
     * @param postingFileID id of the posting file.
     */
    PostingFile(int postingFileID) {
        this.postingFileID = postingFileID;
        postingDictionary = new HashMap<>();
    }

    /**
     * Adds a term -> document mapping to the posting file.
     * @param term the term.
     * @param documentID the document.
     * @param documentFrequency the terms frequency in the document.
     */
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

    /**
     * @return an alphabetically sorted array of the term postings in the posting file.
     */
    TermPosting[] getPostings() {
        TermPosting[] res = new TermPosting[postingDictionary.size()];
        postingDictionary.values().toArray(res);
        Arrays.sort(res, (t1, t2) -> t1.getTerm().compareToIgnoreCase(t2.getTerm()));
        //Arrays.sort(res, Comparator.comparing(TermPosting::getTerm));
        return res;
    }

    public int getID() {
        return postingFileID;
    }

    /**
     * Writes the posting file to a file.
     */
    public void flush() {
        PostingCache.queuePostingFlush(this);
    }

}
