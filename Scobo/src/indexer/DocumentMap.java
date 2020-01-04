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
 * Maps document IDs to document data and generates said IDs.
 *
 * <p> Document Map file format:
 * Each line in the file represents a document ID -> document data mapping.
 * each line will look like so: [document ID]|[(document data)]\n
 * <ul>
 *     <li>document ID - id given to the document by the map</li>
 *     <li>document data - csv data about the document including document name </li>
 * </ul>
 *
 * <p><em>{@code DocumentMap} is externally immutable meaning that it is immutable outside of
 * the scope of its package (indexer)</em>
 */
public final class DocumentMap {

    // provides synchronization for writing to the document map file
    private static final Object fileMonitor = new Object();

    private static final int MAP_SIZE = 524288;
    private static final float LOAD_FACTOR = 0.75f;

    private ConcurrentHashMap<Integer, DocumentMapping> documents;
    private volatile AtomicInteger runningID;


    /**
     * Creates a {@code DocumentMap} in ADD mode.
     * This creates a <em>mutable</em> reference.
     *
     */
    protected DocumentMap() {
        this(MAP_SIZE, LOAD_FACTOR);

        File mapFile = new File(Configuration.getInstance().getIndexPath());
        if (!mapFile.exists())
            mapFile.mkdirs();

        this.runningID = new AtomicInteger(0);
    }

    // private initialization constructor used by the package constructor and the
    // external loadDocumentMap function.
    private DocumentMap(final int mapSize, final float loadFactor) {
        this.documents = new ConcurrentHashMap<>(mapSize, loadFactor, Runtime.getRuntime().availableProcessors());
    }

    /**
     * Adds a document to the map, giving it a unique ID.
     *
     * @param document the document to be added
     * @return a positive integer representing the docID, -1 if the Map is in LOOKUP mode
     * and cannot accept more documents.
     */
    int addDocument(final Document document) {
        int docID = runningID.getAndIncrement();
        documents.computeIfAbsent(docID, integer -> new DocumentMapping(document));
        return docID;
    }


    /**
     * Gets the document mapping of the given document ID.
     *
     * @param docID a docID generated by this map
     * @return the document name associated with the given docID.
     */
    public Optional<DocumentMapping> lookup(int docID) {
        return Optional.ofNullable(documents.get(docID));
    }

    /**
     * Parses the term posting string and updates all the
     * documents that the entity appears in.
     * If the entities frequency is high enough in some document
     * it will entered into the list of most dominant entries in the document.
     *
     * @param termPostingStr string representation of the term posting.
     */
    void updateEntity(String termPostingStr, Runnable onComplete) {
        String[] postingContent = termPostingStr.split("[|,]");
        String entity = postingContent[0];
        int index = 1;
        while (index < postingContent.length) {
            int docID = Integer.parseInt(postingContent[index++]);
            int frequency = Integer.parseInt(postingContent[index++]);
            documents.get(docID).updateEntity(entity, frequency);
        }
        onComplete.run();
    }

    /**
     * dumps the document map into the document map file.
     */
    void save() {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(getPath()));
            StringBuilder fileDump = new StringBuilder();
            for (Map.Entry<Integer, DocumentMapping> entry : documents.entrySet())
                fileDump.append(entry.getKey()).append("|")
                        .append(entry.getValue().toString()).append("\n");

            synchronized (fileMonitor) {
                writer.append(fileDump);
                writer.close();
            }
        }
        catch (IOException e) {
            Logger.getInstance().error(e);
        }
    }

    /**
     * Removes all of the document mappings from the map, and
     * deletes the document map file.
     *
     * @throws IOException if there is a problem deleting the file.
     */
    public void clear() throws IOException {
        documents.clear();
        Files.deleteIfExists(Paths.get(getPath()));
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
            lines = Files.readAllLines(Paths.get(getPath()));
        }
        DocumentMap res = new DocumentMap(lines.size(), LOAD_FACTOR);

        for (String line : lines) {
            int startOfMapping = line.indexOf("|");
            int docID = Integer.parseInt(line.substring(0, startOfMapping));
            res.documents.put(docID, new DocumentMapping(line.substring(startOfMapping)));
        }

        return res;
    }

    // returns the path to the dictionary file as specified by Configuration
    private static String getPath() {
        return Configuration.getInstance().getDocumentMapPath();
    }

    /**
     * Holds the data of the documents mapping:
     * <ul>
     *     <li>name - the name of the document (DOCNO)</li>
     *     <li>maxFrequency - frequency of the term or entity that is most frequent in the document</li>
     *     <li>length - number of terms or entities that appear the document (not unique)</li>
     *     <li>dominant entities - list of up to {@code DOMINANT_ENTITIES_COUNT} pairs of entities and their
     *     frequency in the document, the list represents the most common entities in the document.</li>
     * </ul>
     */
    private static class DocumentMapping {

        private static final int DOMINANT_ENTITIES_COUNT = 5;

        public String name;
        public int maxFrequency;
        public int length;
        public ArrayList<Map.Entry<String, Integer>> dominantEntities;
        private Map.Entry<String, Integer> minEntity;

        public DocumentMapping(Document document) {
            this.name = document.name;
            this.maxFrequency = document.maxFrequency;
            this.length = document.length;
            this.dominantEntities = new ArrayList<>(5);
            minEntity = new HashMap.SimpleEntry<>("", Integer.MAX_VALUE);
        }

        public DocumentMapping(String strMapping) {
            String[] contents = strMapping.split(",");
            if (contents.length < 3 || contents.length > 3 + (DOMINANT_ENTITIES_COUNT * 2))
                throw new IllegalStateException("Document Map file is corrupted");

            this.name = contents[0];
            this.maxFrequency = Integer.parseInt(contents[1]);
            this.length = Integer.parseInt(contents[2]);
            dominantEntities = new ArrayList<>(DOMINANT_ENTITIES_COUNT);
            if (contents.length > 3){
                int i = 3;
                while (i < contents.length) {
                    String entity = contents[i++];
                    int frequency = Integer.parseInt(contents[i++]);
                    dominantEntities.add(new HashMap.SimpleEntry<>(entity, frequency));
                }
            }
        }

        //updates the dominantEntities list with a new entity and its frequency.
        synchronized void updateEntity(String entity, int frequency) {
            Map.Entry<String, Integer> newEntry = new HashMap.SimpleEntry<>(entity, frequency);
            // if the entity list is still not full
            // add the new entity to the list and update the min entity.
            if (dominantEntities.size() < DOMINANT_ENTITIES_COUNT) {
                dominantEntities.add(newEntry);
                if (frequency < minEntity.getValue())
                    minEntity = newEntry;
            }
            else {
                // otherwise remove the min entity, add the new entity
                // and find the new min entity.
                if (frequency < minEntity.getValue()) return;
                dominantEntities.remove(minEntity);
                dominantEntities.add(newEntry);
                minEntity = new HashMap.SimpleEntry<>("", Integer.MAX_VALUE);
                for (Map.Entry<String, Integer> dominantEntity : dominantEntities) {
                    if (dominantEntity.getValue() < minEntity.getValue())
                        minEntity = dominantEntity;
                }
            }
        }

        @Override
        public String toString() {
            StringBuilder res = new StringBuilder(name).append(",");
            res.append(maxFrequency).append(",");
            res.append(length);
            synchronized (this) {
                for (Map.Entry<String, Integer> entity: dominantEntities)
                    res.append(",").append(entity.getKey()).append(",").append(entity.getValue());
            }
            return res.toString();
        }
    }
}
