package query;

import indexer.Term;
import parser.Document;
import util.Configuration;
import util.Logger;
import util.Pair;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Searcher implements Runnable {

    QueryProcessor manager;

    Document queryDocument;

    // maps document ids to pairs <term, frequency> pairs.
    ConcurrentHashMap<Integer, List<Pair<String, Integer>>> relevantDocuments;

    public Searcher(Document queryDocument, QueryProcessor manager) {
        this.manager = manager;
        this.queryDocument = queryDocument;
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
        for (String term : queryDocument.terms.keySet()) {
            String[] sim = manager.gloSim.getOrDefault(term, null);
            if (sim != null)
                expandTerm(term, sim);
        }
    }

    private void expandTerm(String term, String[] sim) {
        for (String word : sim) {
            if (manager.dictionary.lookupTerm(word).isPresent())
                queryDocument.addTerm(word);
        }
    }

    private void loadDocuments() throws IOException {
        Configuration config = Configuration.getInstance();
        BufferedReader termReader = new BufferedReader(new FileReader(config.getInvertedFilePath()));
        ArrayList<Integer> lines = getLines(queryDocument.numbers.keySet());
        lines.addAll(getLines(queryDocument.terms.keySet()));
        lines.addAll(getLines(queryDocument.entities.keySet()));
        lines.sort(Integer::compareTo);

        int currLine = 0;
        for (int lineNumber : lines) {
            String line = "";
            while (currLine != lineNumber) {
                line = termReader.readLine();
                currLine++;
            }
            addDocuments(line);
        }
    }

    private void addDocuments(String line) {
        // the format of the line in the inverted file is as follows t(|d,f)+
        String[] content = line.split("|");
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

    private ArrayList<Integer> getLines(Collection<String> terms) {
        ArrayList<Integer> lines = new ArrayList<>(terms.size());
        for (String term : terms) {
            Optional<Term> res = manager.dictionary.lookupTerm(term);
            res.ifPresent(dictTerm -> lines.add(dictTerm.pointer));
        }

        return lines;
    }

}
