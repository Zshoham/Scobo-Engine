package indexer;

import util.Configuration;
import util.Logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
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
 *     <li>term frequency - number of times the term appears in the corpus</li>
 *     <li>document frequency - number of documents the term appears in</li>
 *     <li>posting pointer - index of the posting file this terms posting appears in</li>
 * </ul>
 *
 */
public final class Dictionary {

    // monitor to handle synchronization of adding terms
    private static final Object termMonitor = new Object();
    // monitor to handle synchronization of adding entities
    private static final Object entityMonitor = new Object();

    private static final int TERM_COUNT = 2097152; // 2^21
    private static final float LOAD_FACTOR = 0.75f; // termCount * loadFactor = 1,572,864 (max size before rehash)
    private static final int CONCURRENCY_LEVEL = Runtime.getRuntime().availableProcessors();

    // maps terms to their Term instance
    private Map<String, Term> dictionary;

    // maps entity to its term frequency.
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


    public void addTermFromDocument(String term, int frequency) {
        addTerm(term, 1, frequency);
    }

    /**
     * Adds the word to the dictionary, if the word was already contained
     * its statistics will be updated, otherwise the word will be added
     * to the dictionary.
     *
     * @param word a word to add to the dictionary.
     */
    protected void addWordFromDocument(String word, int frequency) {
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

        if (word.equalsIgnoreCase("1b"))
            System.out.println(word + "from words");

        String upperCaseTerm = word.toUpperCase();
        String lowerCaseTerm = word.toLowerCase();
        boolean isUpperCase = Character.isUpperCase(word.charAt(0));

        if (upperCaseTerm.equals(lowerCaseTerm)) {
            addTerm(lowerCaseTerm, 1, frequency);
            return;
        }

        if (isUpperCase) {
            final AtomicBoolean isPresent = new AtomicBoolean(false);
            dictionary.computeIfPresent(lowerCaseTerm, (key, value) -> {
                isPresent.set(true);
                value.termDocumentFrequency++;
                value.termFrequency += frequency;
                return value;
            });
            if (isPresent.get())
                return;

            addTerm(upperCaseTerm, 1, frequency);
            return;
        }

        synchronized (termMonitor) {
            if (dictionary.containsKey(upperCaseTerm)) {
                Term oldTerm = dictionary.remove(upperCaseTerm);
                oldTerm.term = lowerCaseTerm;
                oldTerm.termDocumentFrequency++;
                oldTerm.termFrequency += frequency;
                dictionary.put(lowerCaseTerm, oldTerm);
                return;
            }
        }

        addTerm(lowerCaseTerm, 1, frequency);
    }

    /**
     * Adds the entity to the dictionary, if entity term was already contained
     * its statistics will be updated, otherwise the entity will be added
     * to the dictionary.
     *
     * @param entity a term to add to the dictionary.
     */
    protected void addEntityFromDocument(String entity, int frequency) {
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
            value.termFrequency += frequency;
            return value;
        });
        if (isPresent.get())
            return;

        synchronized (entityMonitor) {
            if (entities.containsKey(entity)) {
                int count = entities.remove(entity);
                addTerm(entity, 2, count + frequency);
                return;
            }
        }

        entities.put(entity, frequency);
    }

    // helper function to add any term to the dictionary.
    // returns true if the term is new to the dictionary
    // false otherwise.
    private void addTerm(String term, int count, int frequency) {
        // compute the terms mapping.
        dictionary.merge(term, new Term(term, frequency, count, -1), (dictValue, newValue) -> {
            dictValue.termDocumentFrequency += count;
            dictValue.termFrequency += frequency;
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
        if (term.contains("dollars")) {
            term = term.replace("dollars", "Dollars");
            term = term.replace("m", "M");
        }
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
     * Returns the number of key-value mappings in this map.  If the
     * map contains more than <tt>Integer.MAX_VALUE</tt> elements, returns
     * <tt>Integer.MAX_VALUE</tt>.
     *
     * @return the number of key-value mappings in this map
     */
    public int size() {
        return this.dictionary.size();
    }

    /**
     * @return Collection of all the terms in the dictionary
     */
    public Collection<Term> getTerms() {
        return dictionary.values();
    }

    /**
     * Saves the {@code Dictionary} to the directory specified by {@link Configuration}
     */
    public void save()  {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(getPath()));
            for (Map.Entry<String, Term> entry : dictionary.entrySet()) {
                Term term = entry.getValue();
                writer.append(entry.getKey()).append("|");
                writer.append(String.valueOf(term.termFrequency)).append("|");
                writer.append(String.valueOf(term.termDocumentFrequency)).append("|");
                writer.append(String.valueOf(term.pointer)).append("\n");
            }

            writer.close();
        } catch (IOException e) {
            Logger.getInstance().error(e);
        }
    }

    /**
     * Removes all of the entries from the dictionary, and deletes
     * the dictionary file.
     *
     * @throws IOException if there is a problem deleting the file.
     */
    public void clear() throws IOException {
        dictionary.clear();
        entities.clear();
        Files.deleteIfExists(Paths.get(getPath()));
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
        lines = Files.readAllLines(Paths.get(getPath()));

        // concurrency level is set to 1 because the returned dictionary is should be immutable.
        Dictionary res = new Dictionary(lines.size(), LOAD_FACTOR, 1);

        for (String line : lines) {
            String[] contents = line.split("\\|");
            if (contents.length != 4)
                throw new IOException("Dictionary file is corrupted.");

            String term = contents[0];
            int termFrequency = Integer.parseInt(contents[1]);
            int documentFrequency = Integer.parseInt(contents[2]);
            int pointer = Integer.parseInt(contents[3]);
            res.dictionary.put(term, new Term(term, termFrequency, documentFrequency, pointer));
        }

        return res;
    }

    private static String getPath() {
        return Configuration.getInstance().getDictionaryPath();
    }

}
