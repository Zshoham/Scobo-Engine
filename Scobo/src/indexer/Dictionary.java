package indexer;

import util.Configuration;
import util.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Maps string representation of a term to a {@link Term} instance holding
 * the relevant term statistics and posting file pointer.
 *
 * <p> Dictionary file format:
 * Each line in the file represents a single entry in the Dictionary,
 * each line will look like so: [term]|[document frequency]|[posting file]\n
 * <ul>
 *     <li>term - string representation of the term</li>
 *     <li>document frequency - number of documents the term appears in</li>
 *     <li>posting file - index of the posting file this terms posting appears in</li>
 * </ul>
 *
 */
public final class Dictionary {

    private static final String PATH = Configuration.getInstance().getIndexPath() + "/dictionary.txt";

    private static final Object termMonitor = new Object();
    private static final Object entityMonitor = new Object();

    //TODO: optimize these values.
    private static final int TERM_COUNT = 2097152; // 2^21
    private static final float LOAD_FACTOR = 0.75f; // termCount * loadFactor = 1,572,864 (max size before rehash)
    private static final int CONCURRENCY_LEVEL = Runtime.getRuntime().availableProcessors();

    private Map<String, Term> dictionary;
    private Map<String, Integer> entities;


    /**
     * Constructs a {@code Dictionary} with default parameters.
     * This creates a <em>mutable</em> reference.
     */
    protected Dictionary() {
        this(TERM_COUNT, LOAD_FACTOR, CONCURRENCY_LEVEL);
    }

    private Dictionary(final int termCount, final float loadFactor, final int concurrencyLevel) {
        dictionary = new ConcurrentHashMap<>(termCount, loadFactor, concurrencyLevel);
        entities = new ConcurrentHashMap<>(termCount / 2, loadFactor, concurrencyLevel);
    }


    public void addTermFromDocument(String key) {
        addTerm(key, 1);
    }

    /**
     * Adds the term to the dictionary, if the term was already contained
     * its statistics will be updated, otherwise the term will be added
     * to the dictionary.
     *
     * @param term a term to add to the dictionary.
     */
    protected void addWordFromDocument(String term) {
        // if lower case equals upper case
        //      add as lower
        //
        // if upper case
        //      if lower case exists
        //          add as lower
        //      else
        //          add as upper
        //
        // if lower case and upper exists
        //      set upper case to lower case
        //
        // add to document

        String upperCaseTerm = term.toUpperCase();
        String lowerCaseTerm = term.toLowerCase();
        boolean isUpperCase = Character.isUpperCase(term.charAt(0));

        if (upperCaseTerm.equals(lowerCaseTerm)) {
            addTerm(lowerCaseTerm, 1);
            return;
        }

        if (isUpperCase) {
            final AtomicBoolean isPresent = new AtomicBoolean(false);
            dictionary.computeIfPresent(lowerCaseTerm, (key, value) -> {
                isPresent.set(true);
                value.termDocumentFrequency++;
                return value;
            });
            if (isPresent.get())
                return;

            addTerm(upperCaseTerm, 1);
            return;
        }

        synchronized (termMonitor) {
            if (dictionary.containsKey(upperCaseTerm)) {
                Term oldTerm = dictionary.remove(upperCaseTerm);
                oldTerm.term = lowerCaseTerm;
                oldTerm.termDocumentFrequency++;
                dictionary.put(lowerCaseTerm, oldTerm);
                return;
            }
        }

        addTerm(lowerCaseTerm, 1);
    }

    /**
     * Adds the entity to the dictionary, if entity term was already contained
     * its statistics will be updated, otherwise the entity will be added
     * to the dictionary.
     *
     * @param entity a term to add to the dictionary.
     */
    protected void addEntityFromDocument(String entity) {
        // if entity exists in dictionary
        //      add to dictionary
        //
        // if entity exists in entities
        //      remove and add to dictionary
        //
        // add to entities

        AtomicBoolean isPresent = new AtomicBoolean(false);
        dictionary.computeIfPresent(entity, (key, value) -> {
            isPresent.set(true);
            value.termDocumentFrequency++;
            return value;
        });
        if (isPresent.get())
            return;

        synchronized (entityMonitor) {
            if (entities.containsKey(entity)) {
                int count = entities.remove(entity);
                addTerm(entity, count + 1);
                return;
            }
        }

        entities.put(entity, 1);
    }

    // helper function to add any term to the dictionary.
    // returns true if the term is new to the dictionary
    // false otherwise.
    private void addTerm(String term, int count) {
        // compute the terms mapping.
        dictionary.merge(term, new Term(term, count, -1), (dictValue, newValue) -> {
            dictValue.termDocumentFrequency += count;
            return dictValue;
        });
    }

    /**
     * Retrieves information about a term via a {@link Term} object
     *
     * @param term string representation of the term
     * @return  an optional of a {@link Term}, will be empty if the
     *          term was not yet added to the dictionary or added with a null mapping.
     */
    public Optional<Term> lookupTerm(String term) {
        Optional<Term> optionalTerm = Optional.ofNullable(dictionary.get(term));
        if (optionalTerm.isPresent())
            return optionalTerm;

        String lowerCaseTerm = term.toLowerCase();
        String upperCaseTerm = term.toUpperCase();

        Optional<Term> lowerCase = Optional.ofNullable(dictionary.get(lowerCaseTerm));
        if (lowerCase.isPresent())
            return lowerCase;

        return Optional.ofNullable(dictionary.get(upperCaseTerm));
    }

    Optional<Term> lookupEntity(String entity) {
        return Optional.ofNullable(dictionary.get(entity));
    }

    /**
     * @param term string representation of the term
     * @return true if the term exists in the dictionary, false otherwise.
     */
    public boolean contains(String term) {
        return dictionary.containsKey(term);
    }

    /**
     * Saves the {@code Dictionary} to the directory specified by {@link Configuration}
     */
    public void save()  {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(PATH));
            for (Map.Entry<String, Term> entry : dictionary.entrySet()) {
                Term term = entry.getValue();
                writer.append(entry.getKey()).append("|");
                writer.append(String.valueOf(term.termDocumentFrequency)).append("|");
                writer.append(String.valueOf(term.pointer)).append("\n");
            }

            writer.close();
        } catch (IOException e) {
            Logger.getInstance().error(e);
        }
    }

    /**
     * Loads the Dictionary into memory from the directory specified by
     * {@link Configuration} and returns a reference to it.
     *
     * @return externally immutable reference to a {@code Dictionary}
     * @throws IOException if the dictionary file is corrupted or not found.
     */
    public synchronized static Dictionary loadDictionary() throws IOException {
        List<String> lines = null;
        try { lines = Files.readAllLines(Paths.get(PATH)); }
        catch (IOException e) {
           Logger.getInstance().error("cannot load dictionary file");
        }

        // concurrency level is set to 1 because the returned dictionary is should be immutable.
        Dictionary res = new Dictionary(lines.size(), LOAD_FACTOR, 1);

        for (String line : lines) {
            String[] contents = line.split("\\|");
            if (contents.length != 3)
                throw new IOException("Dictionary file is corrupted.");

            String term = contents[0];
            int documentFrequency = Integer.parseInt(contents[1]);
            int postingFile = Integer.parseInt(contents[2]);
            int pointer = Integer.parseInt(contents[3]);
            res.dictionary.put(term, new Term(term, documentFrequency, pointer));
        }

        return res;
    }
}
