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
        dictionary = new Dictionary();
        PostingCache.initCache(this);
        documentMap = new DocumentMap(this);
        buffer = new DocumentBuffer(this);
    }

    public void onFinishParser() {
        CPUTasks.closeGroup();
        PostingCache.onFinishedParse();
        this.buffer.flushNow();
        PostingCache.updateAll();
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
        this.CPUTasks.add(() -> invert(documents));
    }

    public synchronized void invert(LinkedList<Document> documents) {
        PostingFile newPosting;
        Optional<PostingFile> optional = PostingCache.newPostingFile();
        if (!optional.isPresent())
            throw new IllegalStateException("failed to create posting file");

        newPosting = optional.get();

        // we want to hold the new posting file until the
        // batch of documents is processed and flush it afterwards.
        newPosting.hold();

        for (Document doc : documents) {
            int docID = documentMap.addDocument(doc);

            // add all the terms
            for (Map.Entry<String, Integer> term : doc.terms.entrySet()) {
                boolean isNew = dictionary.addTermFromDocument(term.getKey());
                Optional<Term> dictionaryTerm = dictionary.lookupTerm(term.getKey());
                if (!dictionaryTerm.isPresent())
                    throw new IllegalStateException("term wasn't properly added to dictionary");

                // if the term is new to the dictionary add it to the new posting file
                if (isNew)
                    dictionaryTerm.get().termPosting.setPostingFile(newPosting);

                // update the terms posting and posting file
                dictionaryTerm.get().termPosting.addDocument(docID, term.getValue());
                dictionaryTerm.get().termPosting.getPostingFile().addTerm(dictionaryTerm.get());
            }

            // add all the entities
            for (Map.Entry<String, Integer> entity : doc.entities.entrySet()) {
                boolean isNew = dictionary.addEntityFromDocument(entity.getKey());
                Optional<Term> dictionaryEntity = dictionary.lookupTerm(entity.getKey());

                if (dictionaryEntity.isPresent()) {
                    // if the entity is new to the dictionary add it to the new posting file
                    if (isNew)
                        dictionaryEntity.get().termPosting.setPostingFile(newPosting);

                    // update the entities posting and posting file
                    dictionaryEntity.get().termPosting.addDocument(docID, entity.getValue());
                    dictionaryEntity.get().termPosting.getPostingFile().addTerm(dictionaryEntity.get());
                }
            }
        }

        PostingCache.handleFirstFlush(newPosting);
        CPUTasks.complete();
    }
}
