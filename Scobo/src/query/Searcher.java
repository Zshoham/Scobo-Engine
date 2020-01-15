package query;

import indexer.Term;
import util.Configuration;
import util.Logger;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the retrieval of results for a single query
 */
public class Searcher implements Runnable {

    QueryProcessor manager;
    Query query;
    Ranker ranker;

    // maps document ids to pairs <term, frequency> pairs.
    ConcurrentHashMap<Integer, Map<String, Integer>> relevantDocuments;

    /**
     * Constructs Searcher for a given query that is managed by the
     * given manager.
     * @param query the query the search documents for.
     * @param manager the query processor that manages the searcher.
     */
    public Searcher(Query query, QueryProcessor manager) {
        this.manager = manager;
        this.query = query;
        relevantDocuments = new ConcurrentHashMap<>();
    }

    @Override
    public void run() {
        if (Configuration.getInstance().getUseSemantic()) {
            expandQuery();
            ranker = Ranker.semantic(query, manager);
        }
        else ranker = Ranker.bm25(query, manager);

        try { loadDocuments(); }
        catch (IOException e) {
            Logger.getInstance().error(e);
        }

        relevantDocuments.forEach((docID, tf) -> ranker.rank(docID, tf));
        manager.currentResult.updateResult(query.id, ranker.getRanking());

        manager.searchTasks.complete();
    }

    // semantically expands the query, adding semantic fields.
    private void expandQuery() {
        for (String term : query.terms.keySet()) {
            String[] sim = manager.gloSim.getOrDefault(term, null);
            if (sim != null)
                expandTerm(sim);
        }
    }

    // semantically expands the term, adding fields that are semantically similar to the term.
    private void expandTerm(String[] sim) {
        int countAdded = 0;
        for (String word : sim) {
            if (manager.dictionary.lookupTerm(word).isPresent()) {
                query.addSemantic(word);
                countAdded++;
            }
            if (countAdded == 2)
                break;
        }
    }

    // loads all the documents that might be relevant to the query, from the inverted file.
    private void loadDocuments() throws IOException {
        Configuration config = Configuration.getInstance();
        RandomAccessFile termReader = new RandomAccessFile(config.getInvertedFilePath(), "r");

        ArrayList<Long> linePointers = new ArrayList<>(query.length);
        for (Map.Entry<String, Integer> term : query) {
            Optional<Term> res = manager.dictionary.lookupTerm(term.getKey());
            res.ifPresent(dictTerm -> linePointers.add(dictTerm.pointer));
        }

        linePointers.sort(Long::compareTo);

        for (long linePointer : linePointers) {
            termReader.seek(linePointer);
            String line = termReader.readLine();
            addDocuments(line);
        }
    }

    // parses a line of the inverted file, adding the documents in it to the relevant documents.
    private void addDocuments(String line) {
        // the format of the line in the inverted file is as follows t(|d,f)+
        String[] content = line.split("\\|");
        String term = content[0];
        for (int i = 1; i < content.length; i++) {
            int splitIndex = content[i].indexOf(",");
            int docID = Integer.parseInt(content[i].substring(0, splitIndex));
            int frequency = Integer.parseInt(content[i].substring(splitIndex + 1));
            relevantDocuments.compute(docID, (docID1, terms) -> {
                if (terms == null)
                    terms = new HashMap<>();

                terms.put(term, frequency);
                return terms;
            });
        }
    }
}
