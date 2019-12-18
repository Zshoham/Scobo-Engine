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

    private int termCount;

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
        this.termCount = 0;
    }

    public void onFinishParser() {
        CPUTasks.closeGroup();
        buffer.flush();
        IOTasks.closeGroup();
        CPUTasks.awaitCompletion();
        IOTasks.awaitCompletion();
        PostingCache.merge(dictionary);
        this.termCount = dictionary.size();
        dictionary.save();
        documentMap.dumpNow();
        PostingCache.clean();
        latch.countDown();
    }

    public void awaitIndex() {
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
            invertWords(docID, newPosting, doc);
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

            // numbers are added as is to the posting file.
            newPosting.addTerm(term.getKey().toLowerCase(), docID, term.getValue());
        }
    }

    private void invertWords(int docID, PostingFile newPosting, Document document) {
        for (Map.Entry<String, Integer> term : document.words.entrySet()) {
            dictionary.addWordFromDocument(term.getKey());
            Optional<Term> dictionaryTerm = dictionary.lookupTerm(term.getKey());
            if (!dictionaryTerm.isPresent())
                throw new IllegalStateException("term wasn't properly added to dictionary");

            // words are added in lower case to the posting file.
            newPosting.addTerm(term.getKey().toLowerCase(), docID, term.getValue());
        }
    }

    private void invertEntities(int docID, PostingFile newPosting, Document document) {
        for (Map.Entry<String, Integer> entity : document.entities.entrySet()) {
            dictionary.addEntityFromDocument(entity.getKey());
            Optional<Term> dictionaryEntity = dictionary.lookupEntity(entity.getKey());

            // entities are added in lower case to the posting file.
            if (dictionaryEntity.isPresent())
                newPosting.addTerm(entity.getKey().toLowerCase(), docID, entity.getValue());
        }
    }

    public int getTermCount() {
        return this.termCount;
    }
}
