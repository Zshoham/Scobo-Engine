package indexer;

import util.TaskGroup;
import util.TaskManager;
import util.TaskManager.TaskType;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;

public class Indexer {

    private final Dictionary dictionary;
    private final DocumentMap documentMap;
    private final DocumentBuffer buffer;

    protected TaskGroup CPUTasks;
    protected TaskGroup IOTasks;

    public Indexer() {
        this.CPUTasks = TaskManager.getTaskGroup(TaskType.COMPUTE);
        this.IOTasks = TaskManager.getTaskGroup(TaskType.IO);
        dictionary = new Dictionary();
        PostingCache.initCache(this);
        documentMap = new DocumentMap(this);
        buffer = new DocumentBuffer(this);
    }

    public void index(String docName, HashMap<String, Integer> terms) {
        buffer.addToBuffer(docName, terms);
    }

    public void invert(LinkedList<Document> documents) {
        PostingFile newPosting;
        Optional<PostingFile> optional = PostingCache.newPostingFile();
        if (!optional.isPresent())
            throw new IllegalStateException("failed to create posting file");

        newPosting = optional.get();

        //we want to hold the new posting file until the
        // batch of documents is processed and flush it afterwards.
        newPosting.hold();

        for (Document doc : documents) {
            //TODO: move the mapping to the document buffer ?
            int docID = documentMap.addDocument(doc.name);

            for (Map.Entry<String, Integer> term : doc.terms.entrySet()) {
                boolean isNew = dictionary.addTermFromDocument(term.getKey());
                Optional<Term> dictionaryTerm = dictionary.lookup(term.getKey());
                if (!dictionaryTerm.isPresent())
                    throw new IllegalStateException("term wasn't properly added to dictionary");

                dictionaryTerm.get().termPosting.addDocument(docID, term.getValue());
                if (!isNew)
                    newPosting.addTerm(dictionaryTerm.get());

            }
        }

        PostingCache.handleFirstFlush(newPosting);
    }
}
