package query;

import indexer.DocumentMap.DocumentMapping;
import indexer.Term;
import util.Configuration;
import util.Pair;

import java.util.*;

/**
 * This class calculates the similarity between a query and documents
 * and ranks the documents according to said similarity.
 */
abstract class Ranker {

    private static final int QUERY_RESULT_SIZE = 50;

    protected Query query;
    protected QueryProcessor manager;
    // the queue contains <docID, similarity> mappings
    private PriorityQueue<Pair<Integer, Double>> ranking;

    // constructs a ranker for a given query and QueryProcessor.
    private Ranker(Query query, QueryProcessor manager) {
        this.query = query;
        this.manager = manager;
        ranking = new PriorityQueue<>(QUERY_RESULT_SIZE,
                Comparator.comparingDouble(doc -> doc.second));
    }

    // updates the ranking for a given docID that has the given similarity.
    protected void updateRanking(int docID, double sim) {
        if (ranking.size() >= QUERY_RESULT_SIZE) {
            Pair<Integer, Double> minDoc = ranking.peek();
            if (sim <= minDoc.second) return;
            ranking.poll();
        }

        ranking.offer(new Pair<>(docID, sim));
    }

    /**
     * @return ranking of the docID's where the doc at index 0
     * is the one ranked the highest.
     */
    public int[] getRanking() {
        int[] rankedResults = new int[QUERY_RESULT_SIZE];
        for (int i = ranking.size() - 1; i >= 0; i--)
            rankedResults[i] = Objects.requireNonNull(ranking.poll()).first;
        return rankedResults;
    }

    /**
     * Adds the given doc to the ranking.
     * @param docID the id of the document.
     * @param tf the term frequency pairs of the document.
     */
    public abstract void rank(int docID, Map<String, Integer> tf);

    /**
     * Creates a semantic Ranker.
     * @param query the query to be ranked.
     * @param manager the query processor in charge of the process.
     */
    public static Ranker semantic(Query query, QueryProcessor manager) {
        return new SemanticRanker(query, manager);
    }

    /**
     * Creates a bm25 Ranker.
     * @param query the query to be ranked.
     * @param manager the query processor in charge of the process.
     */
    public static Ranker bm25(Query query, QueryProcessor manager) {
        return new BM25Ranker(query, manager);
    }

    private static class SemanticRanker extends Ranker {

        // bm25 constants, magic numbers chosen through trial and error.
        private static final double k = 1;
        private static final double b = 0.3;

        // weights for the semantic parts of the query.
        private static final double ENTITIES_WEIGHT = 0.4;
        private static final double TERM_WEIGHT     = 0.4;
        private static final double NUMBERS_WEIGHT  = 0.1;
        private static final double SEMANTIC_WEIGHT = 0.1;

        private SemanticRanker(Query query, QueryProcessor manager) {
            super(query, manager);
        }

        @Override
        public void rank(int docID, Map<String, Integer> tf) {
            Optional<DocumentMapping> optional = manager.documentMap.lookup(docID);
            DocumentMapping doc = Objects.requireNonNull(optional.orElse(null));
            double avgDocLength = manager.documentMap.getAverageLength();
            double numDocuments = manager.documentMap.size();
            double sim =
                    rankTerms(query.terms, TERM_WEIGHT, doc, avgDocLength, numDocuments, tf)
                    + rankTerms(query.numbers, NUMBERS_WEIGHT, doc, avgDocLength, numDocuments, tf)
                    + rankTerms(query.entities, ENTITIES_WEIGHT, doc, avgDocLength, numDocuments, tf)
                    + rankTerms(query.semanticTerms, SEMANTIC_WEIGHT, doc, avgDocLength, numDocuments, tf);

            updateRanking(docID, sim);
        }

        // implements the bm25 function on a group of terms with all the needed arguments.
        private double rankTerms(Map<String, Integer> terms, double weight,
                                        DocumentMapping doc, double avgDocLength,
                                        double numDocuments, Map<String, Integer> tf) {
            double sim = 0;
            for (Map.Entry<String, Integer> term : tf.entrySet()) {
                Optional<Term> optionalTerm = manager.dictionary.lookupTerm(term.getKey());
                int df = 0;
                if (optionalTerm.isPresent())
                    df = optionalTerm.get().termDocumentFrequency;

                int cWQ = terms.getOrDefault(term.getKey(), 0);
                int cWD = term.getValue();

                // the bm25 function itself.
                sim +=
                        cWQ *
                                ((k + 1) * cWD)/
                                (cWD + k * (1-b + b * (doc.length / avgDocLength))) *
                        Math.log10(numDocuments / df);
            }

            return sim * weight;
        }
    }

    private static class BM25Ranker extends Ranker {

        private final double k;
        private final double b;

        private BM25Ranker(Query query, QueryProcessor manager) {
            super(query, manager);
            // here we choose k and b based on weather or not the stemmer
            // is used, again these numbers were chosen from trial and error.
            if (Configuration.getInstance().getUseStemmer()) {
                k = 0.4;
                b = 0.9;
            }
            else {
                k = 1;
                b =  0.3;
            }
        }

        @Override
        // implants the bm25 similarity function between the query and the given document.
        public void rank(int docID, Map<String, Integer> tf) {
            Optional<DocumentMapping> optional = manager.documentMap.lookup(docID);
            DocumentMapping doc = Objects.requireNonNull(optional.orElse(null));
            double avgDocLength = manager.documentMap.getAverageLength();
            double numDocuments = manager.documentMap.size();
            double sim = 0;
            for (Map.Entry<String, Integer> term : tf.entrySet()) {
                Optional<Term> optionalTerm = manager.dictionary.lookupTerm(term.getKey());
                double df = 0.0;
                if (optionalTerm.isPresent())
                    df = optionalTerm.get().termDocumentFrequency;

                double cWQ = query.get(term.getKey());
                double cWD = term.getValue();

                // the bm25 function itself.
                sim +=
                        cWQ *
                                (((k + 1) * cWD)/
                                (cWD + k * (1-b + (b * (doc.length / avgDocLength))))) *
                        Math.log10((numDocuments + 1) / df);
            }

            this.updateRanking(docID, sim);
        }
    }
}
