package indexer;

import java.util.HashMap;
import java.util.Map;

class TermPosting {

    private String term;

    // maps documents ids to document frequency.
    private Map<Integer, Integer> documents;

    public TermPosting(String term) {
        this.term = term;
        this.documents = new HashMap<>();
    }

    public void addDocument(int documentID, int termFrequency) {
        documents.compute(documentID, (docID, frequency) -> {
            if (frequency == null)
                return termFrequency;

            return frequency + termFrequency;
        });
    }

    public String getTerm() { return this.term; }

    public String dump() {
        StringBuilder posting = new StringBuilder(this.term);
        for (Map.Entry<Integer, Integer> doc : documents.entrySet()) {
            posting.append("|").append(doc.getKey()).append(",").append(doc.getValue());
        }
        posting.append("\n");

        this.documents = new HashMap<>();
        return posting.toString();
    }

    public static TermPosting loadPosting(String postingLine) {
        String[] values = postingLine.split("\\|");
        TermPosting res = new TermPosting(values[0]);
        res.documents = new HashMap<>(Math.max(values.length - 2, 0));
        for (int i = 1; i < values.length; i++) {
            String[] kvPair = values[i].split(",");
            res.documents.put(Integer.parseInt(kvPair[0]), Integer.parseInt(kvPair[1]));
        }

        return res;
    }
}
