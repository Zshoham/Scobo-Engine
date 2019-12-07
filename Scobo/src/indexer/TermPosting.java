package indexer;



import java.util.concurrent.ConcurrentHashMap;

class TermPosting {

    private String term;

    // maps documents ids to document frequency.
    private ConcurrentHashMap<Integer, Integer> documents;

    private PostingFile postingFile;

    public TermPosting(String term, short postingFile) {
        this.term = term;
        this.documents = new ConcurrentHashMap<>();
        this.postingFile = PostingCache.getRef(postingFile);
    }

    public TermPosting(String term) {
        this.term = term;
        this.documents = new ConcurrentHashMap<>();
        this.postingFile = null;
    }

    public PostingFile getPostingFile() {
        return postingFile;
    }

    public void addDocument(int documentID, int termFrequency) {
        documents.compute(documentID, (docID, frequency) -> {
            if (frequency == null)
                return termFrequency;

            return frequency + termFrequency;
        });
    }

    public void setPostingFile(PostingFile postingFile) {
        this.postingFile = postingFile;
    }

}
