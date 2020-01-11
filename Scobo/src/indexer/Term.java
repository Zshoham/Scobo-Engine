package indexer;

/**
 * Holds information about a term.
 */
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
     * number of times the term occurred in the corpus.
     */
    public int termFrequency;

    /**
     * pointer to the terms line in the inverted file.
     */
    public long pointer;

    public Term(String term,int termFrequency, int termDocumentFrequency, long pointer) {
        this.term = term;
        this.termFrequency = termFrequency;
        this.termDocumentFrequency = termDocumentFrequency;
        this.pointer = pointer;
    }
}
