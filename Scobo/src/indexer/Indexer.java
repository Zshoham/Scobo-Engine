package indexer;

import parser.Document;
import util.TaskGroup;
import util.TaskManager;
import util.TaskManager.TaskType;
import util.TaskManager.TaskPriority;


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
        this.CPUTasks = TaskManager.getTaskGroup(TaskType.COMPUTE, TaskPriority.HIGH);
        CPUTasks.openGroup();
        this.IOTasks = TaskManager.getTaskGroup(TaskType.IO, TaskPriority.HIGH);
        IOTasks.openGroup();
        this.dictionary = new Dictionary();
        PostingCache.initCache(this);
        this.documentMap = new DocumentMap(this);
        this.buffer = new DocumentBuffer(this);
    }

    public void onFinishParser() {
        CPUTasks.closeGroup();
        buffer.flushNow();
        dictionary.save();
        IOTasks.closeGroup();
    }

    public void awaitIndex() {
        CPUTasks.awaitCompletion();
        IOTasks.awaitCompletion();
    }

    public void index(Document document) {
        buffer.addToBuffer(document);
    }

    public void queueInvert(LinkedList<Document> documents) {
        this.IOTasks.add(() -> invert(documents));
    }

    public void invert(LinkedList<Document> documents) {
        PostingFile newPosting;
        Optional<PostingFile> optional = PostingCache.newPostingFile();
        if (!optional.isPresent())
            throw new IllegalStateException("failed to create posting file");

        newPosting = optional.get();

        for (Document doc : documents) {
            int docID = documentMap.addDocument(doc);

            invertTerms(docID, newPosting, doc);
            invertEntities(docID, newPosting, doc);
        }

        newPosting.flush();
        IOTasks.complete();
    }

    private void invertTerms(int docID, PostingFile newPosting, Document document) {
        for (Map.Entry<String, Integer> term : document.terms.entrySet()) {
            dictionary.addTermFromDocument(term.getKey());
            Optional<Term> dictionaryTerm = dictionary.lookupTerm(term.getKey());
            if (!dictionaryTerm.isPresent())
                throw new IllegalStateException("term wasn't properly added to dictionary");

            newPosting.addTerm(term.getKey(), docID, term.getValue());
        }
    }

    private void invertEntities(int docID, PostingFile newPosting, Document document) {
        for (Map.Entry<String, Integer> entity : document.entities.entrySet()) {
            dictionary.addEntityFromDocument(entity.getKey());
            Optional<Term> dictionaryEntity = dictionary.lookupEntity(entity.getKey());

            if (dictionaryEntity.isPresent()) {

                newPosting.addTerm(entity.getKey(), docID, entity.getValue());
            }
        }
    }
}
