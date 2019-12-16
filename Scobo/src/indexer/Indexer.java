package indexer;

import parser.Document;
import util.Logger;
import util.TaskGroup;
import util.TaskManager;
import util.TaskManager.TaskType;
import util.TaskManager.TaskPriority;


import java.text.DecimalFormat;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

public class Indexer {

    private final Dictionary dictionary;
    private final DocumentMap documentMap;
    private final DocumentBuffer buffer;

    protected TaskGroup CPUTasks;
    protected TaskGroup IOTasks;

    private CountDownLatch latch;

    public Indexer() {
        this.CPUTasks = TaskManager.getTaskGroup(TaskType.COMPUTE, TaskPriority.HIGH);
        CPUTasks.openGroup();
        this.IOTasks = TaskManager.getTaskGroup(TaskType.IO, TaskPriority.HIGH);
        IOTasks.openGroup();
        this.dictionary = new Dictionary();
        PostingCache.initCache(this);
        this.documentMap = new DocumentMap(this);
        this.buffer = new DocumentBuffer(this);
        this.latch = new CountDownLatch(1);
    }

    public void onFinishParser() {
        CPUTasks.closeGroup();
        buffer.flushNow();
        IOTasks.closeGroup();
        IOTasks.awaitCompletion();
        PostingCache.merge(dictionary);
        dictionary.save();
        documentMap.dumpNow();
        latch.countDown();
    }

    public void awaitIndex() {
        CPUTasks.awaitCompletion();
        IOTasks.awaitCompletion();
        try { latch.await(); }
        catch (InterruptedException e) {
            Logger.getInstance().warn(e);
        }
    }

    public void index(Document document) {
        buffer.addToBuffer(document);
    }

    public void queueInvert(LinkedList<Document> documents) {
        this.CPUTasks.add(() -> invert(documents));
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
        CPUTasks.complete();
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
