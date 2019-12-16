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
     * pointer to the terms line in the inverted file.
     */
    public int pointer;

    public Term(String term, int termDocumentFrequency, int pointer) {
        this.term = term;
        this.termDocumentFrequency = termDocumentFrequency;
        this.pointer = pointer;
    }
}
