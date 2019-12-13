package indexer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class TermPosting {

    private String term;

    // maps documents ids to document frequency.
    private Map<Integer, Integer> documents;

    private PostingFile postingFile;
    private int postingFileId;

    public TermPosting(String term, int postingFile) {
        this.term = term;
        this.documents = new ConcurrentHashMap<>();
        this.postingFileId = postingFile;
        this.postingFile = PostingCache.getPostingFileByID(postingFile).orElse(null);
    }

    public TermPosting(String term) {
        this.term = term;
        this.documents = new ConcurrentHashMap<>();
        this.postingFile = null;
        postingFileId = -1;
    }

    public int getPostingFileID() {
        return postingFileId;
    }

    public PostingFile getPostingFile() {
        return this.postingFile;
    }


    public void addDocument(int documentID, int termFrequency) {
        documents.compute(documentID, (docID, frequency) -> {
            if (postingFile != null)
                postingFile.onDocumentAdded();

            if (frequency == null)
                return termFrequency;

            return frequency + termFrequency;
        });
    }

    public synchronized void addAll(Map<Integer, Integer> documents) {
        for (Map.Entry<Integer, Integer> document : documents.entrySet()) {
            addDocument(document.getKey(), document.getValue());
        }
    }

    protected synchronized Map<Integer, Integer> getDocuments() {
        return this.documents;
    }

    public void setPostingFile(PostingFile postingFile) {
        this.postingFile = postingFile;
        this.postingFileId = postingFile.getID();
    }

    public String getTerm() { return this.term; }

    public synchronized String dump() {
        StringBuilder posting = new StringBuilder(this.term);
        for (Map.Entry<Integer, Integer> doc : documents.entrySet()) {
            posting.append("|").append(doc.getKey()).append(",").append(doc.getValue());
        }

        this.documents = new ConcurrentHashMap<>();
        return posting.toString();
    }

    /*
    posting format: term(|document id, term frequency)*\n
     */
    public static TermPosting loadPosting(String postingLine) {
        String[] values = postingLine.split("\\|");
        TermPosting res = new TermPosting(values[0]);
        res.documents = new ConcurrentHashMap<>(Math.max(values.length - 2, 0));
        for (int i = 1; i < values.length; i++) {
            String[] kvPair = values[i].split(",");
            res.documents.put(Integer.parseInt(kvPair[0]), Integer.parseInt(kvPair[1]));
        }

        return res;
    }
}
