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
    private final PostingCache postingCache;
    private final DocumentBuffer buffer;

    protected TaskGroup CPUTasks;
    protected TaskGroup IOTasks;

    public Indexer() {
        this.CPUTasks = TaskManager.getTaskGroup(TaskType.COMPUTE);
        this.IOTasks = TaskManager.getTaskGroup(TaskType.IO);
        dictionary = new Dictionary();
        postingCache = new PostingCache(this);
        documentMap = new DocumentMap(this);
        buffer = new DocumentBuffer(this);
    }

    public void index(String docName, HashMap<String, Integer> terms) {
        buffer.addToBuffer(docName, terms);
    }

    public void invert(LinkedList<Document> documents) {
        //TODO: make a PostingFile factory in PostingCache ?
        PostingFile newPosting = new PostingFile();
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
    }
}
