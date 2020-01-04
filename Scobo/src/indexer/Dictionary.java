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
 * <p><em>{@code Dictionary} is externally immutable meaning that it is immutable outside of
 * the scope of its package (indexer)</em>
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

    //maps entity terms to their Term instance
    private Map<String, Term> entityDictionary;

    // maps entity to its term frequency.
    private Map<String, Integer> entities;


    /**
     * Constructs a {@code Dictionary} with default parameters.
     * This creates a <em>mutable</em> reference.
     */
    protected Dictionary() {
        this(TERM_COUNT, LOAD_FACTOR, CONCURRENCY_LEVEL);
        entityDictionary = new ConcurrentHashMap<>(TERM_COUNT, LOAD_FACTOR, CONCURRENCY_LEVEL);
        entities = new ConcurrentHashMap<>(TERM_COUNT, LOAD_FACTOR, CONCURRENCY_LEVEL);
    }

    private Dictionary(final int termCount, final float loadFactor, final int concurrencyLevel) {
        dictionary = new ConcurrentHashMap<>(termCount, loadFactor, concurrencyLevel);
    }


    /**
     * Adds the number to the dictionary, if the number was already contained
     * its statistics will be updated, otherwise the number will be added
     * to the dictionary.
     *
     * @param number a number to add to the dictionary.
     * @param frequency the frequency of the number in the document.
     */
    protected void addNumberFromDocument(String number, int frequency) {
        addTerm(number, frequency);
    }

    /**
     * Adds the term to the dictionary, if the word was already contained
     * its statistics will be updated, otherwise the term will be added
     * to the dictionary.
     *
     * @param term a term to add to the dictionary.
     * @param frequency the frequency of the term in the document.
     */
    protected void addTermFromDocument(String term, int frequency) {
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
            addTerm(lowerCaseTerm, frequency);
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

            addTerm(upperCaseTerm, frequency);
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

        addTerm(lowerCaseTerm, frequency);
    }

    /**
     * Adds the entity to the dictionary, if entity term was already contained
     * its statistics will be updated, otherwise the entity will be added
     * to the dictionary.
     *
     * @param entity a term to add to the dictionary.
     * @param frequency the frequency of the entity in the document.
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
        entityDictionary.computeIfPresent(entity, (key, value) -> {
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
                entityDictionary.merge(entity, new Term(entity, frequency, count, -1), (dictValue, newValue) -> {
                    dictValue.termDocumentFrequency += count;
                    dictValue.termFrequency += frequency;
                    return dictValue;
                });
                return;
            }
        }

        entities.put(entity, frequency);
    }

    /*
    helper function to add a term to the dictionary.
    where frequency is the number of times the term
    appears in the new document.
     */
    private void addTerm(String term, int frequency) {
        // compute the terms mapping.
        dictionary.merge(term, new Term(term, frequency, 1, -1), (dictValue, newValue) -> {
            dictValue.termDocumentFrequency += 1;
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
        //TODO: fix bad design.
        if (term.contains("dollars")) {
            term = term.replace("dollars", "Dollars");
            term = term.replace("m", "M");
        }
        // if the entity dictionary exists then check if the term is an entity.
        if (entityDictionary != null) {
            Optional<Term> optionalEntity = this.lookupEntity(term.toUpperCase());
            if (optionalEntity.isPresent())
                return optionalEntity;
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

    /**
     * Retrieves information about an entity via a {@link Term} object
     *
     * @param entity string representation of the term
     * @return  an optional of a {@link Term}, will be empty if the
     *          term was not yet added to the dictionary or added with a null mapping.
     */
    Optional<Term> lookupEntity(String entity) {
        return Optional.ofNullable(entityDictionary.get(entity));
    }

    /**
     * @param entity potential entity.
     * @return true if the string is a valid entity, false otherwise.
     */
    boolean isEntity(String entity) {
        return entityDictionary.containsKey(entity.toUpperCase());
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
    void save()  {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(getPath()));

            saveDictionary(this.dictionary, writer);
            if (entityDictionary != null)
                saveDictionary(entityDictionary, writer);

            writer.close();

            // if the dictionary is saved then the entities are not needed anymore.
            entities = null;
        } catch (IOException e) {
            Logger.getInstance().error(e);
        }
    }

    private static void saveDictionary(Map<String, Term> dictionary, BufferedWriter writer) throws IOException {
        for (Map.Entry<String, Term> entry : dictionary.entrySet()) {
            Term term = entry.getValue();
            writer.append(entry.getKey()).append("|");
            writer.append(String.valueOf(term.termFrequency)).append("|");
            writer.append(String.valueOf(term.termDocumentFrequency)).append("|");
            writer.append(String.valueOf(term.pointer)).append("\n");
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
        List<String> lines = Files.readAllLines(Paths.get(getPath()));

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

    // returns the path to the dictionary file as specified by Configuration
    private static String getPath() {
        return Configuration.getInstance().getDictionaryPath();
    }

}
