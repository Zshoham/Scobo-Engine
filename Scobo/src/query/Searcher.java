package query;

import indexer.Term;
import parser.Document;
import util.Configuration;
import util.Logger;
import util.Pair;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Searcher implements Runnable {

    QueryProcessor manager;

    Query query;

    // maps document ids to pairs <term, frequency> pairs.
    ConcurrentHashMap<Integer, List<Pair<String, Integer>>> relevantDocuments;

    public Searcher(Query query, QueryProcessor manager) {
        this.manager = manager;
        this.query = query;
        relevantDocuments = new ConcurrentHashMap<>();
    }

    @Override
    public void run() {
        if (Configuration.getInstance().getUseSemantic())
            expandQuery();

        try { loadDocuments(); }
        catch (IOException e) {
            Logger.getInstance().error(e);
        }
    }

    private void expandQuery() {
        for (String term : query.terms.keySet()) {
            String[] sim = manager.gloSim.getOrDefault(term, null);
            if (sim != null)
                expandTerm(sim);
        }
    }

    private void expandTerm(String[] sim) {
        for (String word : sim) {
            if (manager.dictionary.lookupTerm(word).isPresent())
                query.addSemantic(word);
        }
    }

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
                    terms = new LinkedList<>();

                terms.add(new Pair<>(term, frequency));
                return terms;
            });
        }
    }
}
