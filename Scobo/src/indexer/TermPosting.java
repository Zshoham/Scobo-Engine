package indexer;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a terms posting (a line) in a posting file.
 */
class TermPosting {

    private String term;

    // maps documents ids to document frequency.
    private Map<Integer, Integer> documents;

    /**
     * Constructs a term posting with the given term.
     * @param term the term.
     */
    public TermPosting(String term) {
        this.term = term;
        this.documents = new HashMap<>();
    }

    /**
     * Add a document to the posting.
     * @param documentID the document.
     * @param termFrequency the frequency of the term in the document.
     */
    public void addDocument(int documentID, int termFrequency) {
        documents.compute(documentID, (docID, frequency) -> {
            if (frequency == null)
                return termFrequency;

            return frequency + termFrequency;
        });
    }

    public String getTerm() { return this.term; }


    /**
     * @return  a string containing all the contents of this term posting.
     */
    @Override
    public String toString() {
        StringBuilder posting = new StringBuilder(this.term);
        for (Map.Entry<Integer, Integer> doc : documents.entrySet()) {
            posting.append("|").append(doc.getKey()).append(",").append(doc.getValue());
        }
        posting.append("\n");
        return posting.toString();
    }
}
