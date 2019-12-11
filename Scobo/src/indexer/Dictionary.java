package indexer;

import util.Configuration;
import util.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
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

    private static final String PATH = Configuration.getInstance().getIndexPath() + "dictionary.txt";

    //TODO: optimize these values.
    private static final int TERM_COUNT = 2097152; // 2^21
    private static final float LOAD_FACTOR = 0.75f; // termCount * loadFactor = 1,572,864 (max size before rehash)
    private static final int CONCURRENCY_LEVEL = Runtime.getRuntime().availableProcessors();

    private ConcurrentHashMap<String, Term> dictionary;


    /**
     * Constructs a {@code Dictionary} with default parameters.
     * This creates a <em>mutable</em> reference.
     */
    protected Dictionary() {
        this(TERM_COUNT, LOAD_FACTOR, CONCURRENCY_LEVEL);
    }

    private Dictionary(final int termCount, final float loadFactor, final int concurrencyLevel) {
        dictionary = new ConcurrentHashMap<>(termCount, loadFactor, concurrencyLevel);
    }

    /**
     * Adds the term to the dictionary, if the term was already contained
     * its statistics will be updated, otherwise the term will be added
     * to the dictionary.
     *
     * @param term a term to add to the dictionary.
     * @return true if the dictionary already contained the term, false otherwise.
     */
    protected boolean addTermFromDocument(String term) {
        // if upper case and lower case exists
        //      add as lower
        //
        // if lower case and upper exists
        //      set upper case to lower case
        //
        // add to document

        String upperCaseTerm = term.toUpperCase();
        String lowerCaseTerm = term.toLowerCase();
        boolean isUpperCase = Character.isUpperCase(term.charAt(0));

        synchronized (this) {
            if (isUpperCase && dictionary.containsKey(lowerCaseTerm))
                return addTerm(lowerCaseTerm);
        }

        synchronized (this) {
            if (!isUpperCase && dictionary.containsKey(upperCaseTerm)) {
                Term oldTerm = dictionary.remove(upperCaseTerm);
                oldTerm.term = lowerCaseTerm;
                dictionary.put(term, oldTerm);
                return true;
            }
        }

        return addTerm(term);
    }

    // helper function to add a term to the dictionary.
    private boolean addTerm(String term) {
        final AtomicBoolean isNew = new AtomicBoolean(true);
        // compute the terms mapping.
        dictionary.compute(term, (key, value) -> {
            if (value == null)
                return new Term(key, 1, new TermPosting(key));

            value.termDocumentFrequency++;
            isNew.set(false);
            return value;
        });

        return isNew.get();
    }

    /**
     * Retrieves information about a term via a {@link Term} object
     *
     * @param term string representation of the term
     * @return an optional of a {@link Term}, will be empty if the
     * term was not yet added to the dictionary or added with a null mapping.
     */
    public Optional<Term> lookup(String term) {
        return Optional.ofNullable(dictionary.get(term));
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
        StringBuilder file = new StringBuilder();
        for (Map.Entry<String, Term> entry : dictionary.entrySet()) {
            Term term = entry.getValue();
            file.append(entry.getKey()).append("|");
            file.append(term.termDocumentFrequency).append("|");
            file.append(term.termPosting.getPostingFile()).append("\n");
        }

        try { Files.write(Paths.get(PATH), file.toString().getBytes()); }
        catch (IOException e) {
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
        byte[] fileBytes = new byte[0];
        try { fileBytes = Files.readAllBytes(Paths.get(PATH)); }
        catch (IOException e) {
            e.printStackTrace();
        }
        String fileString = new String(fileBytes);
        String[] lines = fileString.split("\n");
        // concurrency level is set to 1 because the returned dictionary is should be immutable.
        Dictionary res = new Dictionary(lines.length, LOAD_FACTOR, 1);

        for (String line : lines) {
            String[] contents = line.split("\\|");
            if (contents.length != 3)
                throw new IOException("Dictionary file is corrupted.");

            String term = contents[0];
            int documentFrequency = Integer.parseInt(contents[1]);
            int postingFile = Integer.parseInt(contents[2]);

            TermPosting posting = new TermPosting(term, postingFile);
            res.dictionary.put(term, new Term(term, documentFrequency, posting));
        }

        return res;
    }
}
