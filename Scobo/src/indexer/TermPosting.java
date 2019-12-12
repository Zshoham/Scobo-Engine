package indexer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class TermPosting {

    private String term;

    // maps documents ids to document frequency.
    private Map<Integer, Integer> documents;

    private PostingFile postingFile;

    public TermPosting(String term, int postingFile) {
        this.term = term;
        this.documents = new ConcurrentHashMap<>();
        this.postingFile = PostingCache.getPostingFileByID(postingFile).orElse(null);
    }

    public TermPosting(String term) {
        this.term = term;
        this.documents = new ConcurrentHashMap<>();
        this.postingFile = null;
    }

    public Optional<PostingFile> getPostingFile() {
        return Optional.ofNullable(postingFile);
    }

    public synchronized void addDocument(int documentID, int termFrequency) {
        documents.compute(documentID, (docID, frequency) -> {
            if (postingFile != null)
                postingFile.onDocumentAdded();

            if (frequency == null)
                return termFrequency;

            return frequency + termFrequency;
        });
    }

    public void addAll(Map<Integer, Integer> documents) {
        for (Map.Entry<Integer, Integer> document : documents.entrySet()) {
            addDocument(document.getKey(), document.getValue());
        }
    }

    protected synchronized Map<Integer, Integer> getDocuments() {
        return this.documents;
    }

    public void setPostingFile(PostingFile postingFile) {
        this.postingFile = postingFile;
    }

    public String getTerm() { return this.term; }

    public synchronized String dump() {
        StringBuilder posting = new StringBuilder(this.term);
        for (Map.Entry<Integer, Integer> doc : documents.entrySet()) {
            posting.append("|").append(doc.getKey()).append(",").append(doc.getValue());
        }

        this.documents = new HashMap<>();
        return posting.toString();
    }

    /*
    posting format: term(|document id, term frequency)*\n
     */
    public static TermPosting loadPosting(String postingLine) {
        String[] values = postingLine.split("\\|");
        TermPosting res = new TermPosting(values[0]);
        res.documents = new HashMap<>(values.length - 2);
        for (String value : values) {
            String[] kvPair = value.split(",");
            res.documents.put(Integer.parseInt(kvPair[0]), Integer.parseInt(kvPair[1]));
        }

        return res;
    }
}
