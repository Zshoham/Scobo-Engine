package indexer;

import parser.Document;
import util.Logger;
import util.TaskGroup;
import util.TaskManager;
import util.TaskManager.TaskType;
import util.TaskManager.TaskPriority;


import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;

/**
 * Manages the indexing of the documents produced by the {@link parser.Parser}
 * <p> indexing is done in two phases:
 * <ul>
 *     <li>
 *         first a batch of documents is taken from the parser and then is
 *         inverted into into term -> document mappings and then
 *         those mappings are written into a single posting file.
 *     </li>
 *     <li>
 *         after all the documents have been inverted they are all merged into a single
 *         inverted file where each line is a term -> documents mapping
 *     </li>
 *
 *     during this process a {@link Dictionary} and {@link DocumentMap} are created
 *     in order to later retrieve information from the inverted file.
 * </ul>
 *
 */
public class Indexer {

    private final Dictionary dictionary;
    private final DocumentMap documentMap;
    private final DocumentBuffer buffer;

    protected TaskGroup CPUTasks;
    protected TaskGroup IOTasks;

    private CountDownLatch latch;

    private int termCount;

    /**
     * Indexer constructor, initializes everything that the indexer
     * needs in order to operate.
     */
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

    /**
     * Callback meant to be used by the parser to notify the indexer
     * that the last of the documents has been parsed and the
     * indexer can now start entering it's second phase.
     */
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

    /**
     * Waits until all indexing is done.
     * when this method returns all posting files are gone
     * and the the inverted file, dictionary, document map are
     * ready to be used.
     */
    public void awaitIndex() {
        try { latch.await(); }
        catch (InterruptedException e) {
            Logger.getInstance().warn(e);
        }
    }

    /**
     * Adds a document to the inverted index.
     * @param document a document to be indexed.
     */
    public void index(Document document) {
        buffer.addToBuffer(document);
    }

    /**
     * Queues an invert task for the given list of documents.
     * @param documents a list of documents to invert.
     */
    void queueInvert(LinkedList<Document> documents) {
        this.CPUTasks.add(() -> invert(documents));
    }

    /*
    Inverts the given document list as described above
    and then queues a task to write the resulting map into
    a posting file.
     */
    private void invert(LinkedList<Document> documents) {
        PostingFile newPosting;
        Optional<PostingFile> optional = PostingCache.newPostingFile();
        if (!optional.isPresent())
            throw new IllegalStateException("failed to create posting file");

        newPosting = optional.get();

        for (Document doc : documents) {
            int docID = documentMap.addDocument(doc);

            invertNumbers(docID, newPosting, doc);
            invertWords(docID, newPosting, doc);
            invertEntities(docID, newPosting, doc);
        }

        newPosting.flush();
        CPUTasks.complete();
    }

    private void invertNumbers(int docID, PostingFile newPosting, Document document) {
        for (Map.Entry<String, Integer> term : document.numbers.entrySet()) {
            dictionary.addTermFromDocument(term.getKey(), term.getValue());
            Optional<Term> dictionaryTerm = dictionary.lookupTerm(term.getKey());
            if (!dictionaryTerm.isPresent())
                throw new IllegalStateException("term wasn't properly added to dictionary");

            // numbers are added in lower case to the posting file.
            newPosting.addTerm(term.getKey().toLowerCase(), docID, term.getValue());
        }
    }

    private void invertWords(int docID, PostingFile newPosting, Document document) {
        for (Map.Entry<String, Integer> term : document.terms.entrySet()) {
            dictionary.addWordFromDocument(term.getKey(), term.getValue());
            Optional<Term> dictionaryTerm = dictionary.lookupTerm(term.getKey());
            if (!dictionaryTerm.isPresent())
                throw new IllegalStateException("term wasn't properly added to dictionary");

            // words are added in lower case to the posting file.
            newPosting.addTerm(term.getKey().toLowerCase(), docID, term.getValue());
        }
    }

    private void invertEntities(int docID, PostingFile newPosting, Document document) {
        for (Map.Entry<String, Integer> entity : document.entities.entrySet()) {
            dictionary.addEntityFromDocument(entity.getKey(), entity.getValue());
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
