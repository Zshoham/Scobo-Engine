package indexer;

import parser.Document;
import util.Configuration;
import util.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Maps document names to document ID's and generates said ID's.
 * {@code DocumentMap} can be in one of two modes:
 * <ul>
 *     <li>ADD mode - meant to be used internally by the indexer, when in ADD mode
 *     it is possible to add documents to the map and receive their ID's </li>
 *     <li>LOOKUP mode - is meant to be used externally after indexing, when LOOKUP mode
 *     it is impossible to add new documents but looking up document names by ID is available</li>
 * </ul>
 *
 * <p> The distinction between the modes is made because while indexing there is no need to be able to
 * lookup documents after adding them, hence when in ADD mode after reaching a certain threshold the
 * documents added to the map will be saved to the map file.
 * While in LOOKUP all the mappings are available in memory and thus it is possible to preform lookups
 * for any document.
 *
 * <p><em>{@code DocumentMap} is externally immutable meaning that it is immutable outside of
 * the scope of its package (indexer)</em>
 *
 * <p> Document Map file format:
 * Each line in the file represents a document ID -> document data mapping
 * each line line will look like so: [document ID]|[(document data)]\n
 * <ul>
 *     <li>document ID - id given to the document by the map</li>
 *     <li>document data - csv  data about the document including document name </li>
 * </ul>
 *
 */
public final class DocumentMap {

    private static final String PATH = Configuration.getInstance().getIndexPath() + "/docmap.txt";

    // provides synchronization for writing to the document map file
    private static final Object fileMonitor = new Object();

    //TODO: optimize these values.
    private static final int LOADED_MAP_SIZE = 1024;
    private static final float LOAD_FACTOR = 0.75f;

    private ConcurrentHashMap<Integer, DocumentMapping> documents;
    private volatile AtomicInteger size;
    private volatile AtomicInteger runningID;
    private BufferedWriter fileWriter;

    private enum MODE {ADD, LOOKUP}
    private MODE mode;

    private Indexer indexer;


    /**
     * Creates a {@code DocumentMap} in ADD mode.
     * This creates a <em>mutable</em> reference.
     *
     * @param indexer the indexer using this map.
     */
    protected DocumentMap(Indexer indexer) {
        this(MODE.ADD, LOADED_MAP_SIZE, LOAD_FACTOR);

        try {
            File mapFile = new File(Configuration.getInstance().getIndexPath());
            if (!mapFile.exists())
                mapFile.mkdirs();

            this.fileWriter = new BufferedWriter(new FileWriter(PATH));
        }
        catch (IOException e) {
            Logger.getInstance().error(e);
        }
        this.indexer = indexer;
        this.size = new AtomicInteger(0);
        this.runningID = new AtomicInteger(0);
    }

    // private initialization constructor used by the package constructor and the
    // external loadDocumentMap function.
    private DocumentMap(MODE mode, final int mapSize, final float loadFactor) {
        this.mode = mode;
        this.documents = new ConcurrentHashMap<>(mapSize, loadFactor, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Adds a document to the map, giving it a unique ID.
     *
     * @param document the document to be added
     * @return a positive integer representing the docID, -1 if the Map is in LOOKUP mode
     * and cannot accept more documents.
     */
    protected int addDocument(final Document document) {
        if (mode == MODE.LOOKUP)
            return -1;

        int docID = runningID.getAndIncrement();

        documents.computeIfAbsent(docID, integer -> new DocumentMapping(document));

        synchronized (this) {
            if (size.get() >= LOADED_MAP_SIZE * LOAD_FACTOR) {
                queueDump(indexer, documents, fileWriter);
                this.documents = new ConcurrentHashMap<>(LOADED_MAP_SIZE, LOAD_FACTOR);
                size.set(0);
            }
        }

        size.incrementAndGet();
        return docID;
    }


    /**
     * Gets the document mapping of the given document ID.
     *
     * @param docID a docID generated by this map
     * @return the document name associated with the given docID.
     */
    public Optional<DocumentMapping> lookup(int docID) {
        if (mode == MODE.ADD)
            return Optional.empty();

        return Optional.ofNullable(documents.get(docID));
    }

    public void dumpNow() {
        dump(indexer, documents, fileWriter);
        this.documents = new ConcurrentHashMap<>();
        size.set(0);
    }

    /**
     * Loads the document map in LOOKUP mode
     * into memory and returns a reference to it.
     *
     * @return externally immutable reference to a {@code DocumentMap}
     * @throws IOException if the document map file is corrupted or not found.
     */
    public static DocumentMap loadDocumentMap() throws IOException {
        List<String> lines;
        synchronized (fileMonitor) {
            lines = Files.readAllLines(Paths.get(PATH));
        }
        DocumentMap res = new DocumentMap(MODE.LOOKUP, lines.size(), LOAD_FACTOR);

        for (String line : lines) {
            int startOfMapping = line.indexOf("|") + 1;
            int docID = Integer.parseInt(line.substring(0, startOfMapping));
            res.documents.put(docID, new DocumentMapping(line.substring(startOfMapping)));
        }

        return res;
    }

    // queues an IO task for dumping the new document mappings to the file.
    private static void queueDump(Indexer indexer, Map<Integer, DocumentMapping> documents, BufferedWriter writer) {
        indexer.IOTasks.add(() -> dump(indexer, documents, writer));
    }

    // Writes the newly added mappings to the file according to the file format specified in the class
    // documentation.
    private static void dump(Indexer indexer, Map<Integer, DocumentMapping> documents, BufferedWriter writer) {
        try {
            for (Map.Entry<Integer, DocumentMapping> entry : documents.entrySet())
                writer.append(String.valueOf(entry.getKey())).append("|")
                        .append(entry.getValue().toString()).append("\n");

            synchronized (fileMonitor) { writer.flush(); }
        } catch (IOException e) {
            Logger.getInstance().error(e);
        }
        finally {
            indexer.IOTasks.complete();
        }
    }

    /**
     * Holds the data of the documents mapping:
     * <ul>
     *     <li>name - the name of the document (DOCNO)</li>
     *     <li>maxFrequency - frequency of the term or entity that is most frequent in the document</li>
     *     <li>length - number of terms or entities that appear the document (not unique)</li>
     * </ul>
     */
    public static class DocumentMapping {

        public String name;

        public int maxFrequency;
        public int length;

        public DocumentMapping(Document document) {
            this.name = document.name;
            this.maxFrequency = document.maxFrequency;
            this.length = document.length;
        }

        public DocumentMapping(String strMapping) {
            String[] contents = strMapping.split(",");
            if (contents.length != 3)
                throw new IllegalStateException("Document Map file is corrupted");

            this.name = contents[0];
            this.maxFrequency = Integer.parseInt(contents[1]);
            this.length = Integer.parseInt(contents[2]);
        }

        @Override
        public String toString() {
            return name + "," + maxFrequency + "," + length;
        }
    }
}
