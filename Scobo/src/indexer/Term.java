package indexer;

public class Term {

    /**
     * string representation of the term.
     */
    public String term;

    /**
     * number of documents the term has appeared in.
     */
    public int termDocumentFrequency;

    /**
     * pointer to the start of the posting file entry
     * belonging to this term.
     */
    public TermPosting termPosting;

    //TODO: do this instead of term posting
    public int pointer;

    public Term(String term, int termDocumentFrequency, TermPosting posting) {
        this.term = term;
        this.termDocumentFrequency = termDocumentFrequency;
        this.termPosting = posting;
    }
}
