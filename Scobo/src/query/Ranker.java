package query;

import indexer.DocumentMap.DocumentMapping;
import indexer.Term;
import util.Pair;

import java.util.*;

abstract class Ranker {

    private static final int QUERY_RESULT_SIZE = 50;

    protected Query query;
    protected QueryProcessor manager;
    //                         docID    sim
    private PriorityQueue<Pair<Integer, Double>> ranking;

    private Ranker(Query query, QueryProcessor manager) {
        this.query = query;
        this.manager = manager;
        ranking = new PriorityQueue<>(QUERY_RESULT_SIZE,
                Comparator.comparingDouble(doc -> doc.second));
    }

    protected void updateRanking(int docID, double sim) {
        if (ranking.size() >= QUERY_RESULT_SIZE) {
            Pair<Integer, Double> minDoc = ranking.peek();
            if (sim <= minDoc.second) return;
            ranking.poll();
        }

        ranking.offer(new Pair<>(docID, sim));
    }

    public int[] getRanking() {
        int[] rankedResults = new int[QUERY_RESULT_SIZE];
        for (int i = QUERY_RESULT_SIZE - 1; i >= 0; i--)
            rankedResults[i] = Objects.requireNonNull(ranking.poll()).first;
        return rankedResults;
    }

    public abstract void rank(int docID, Map<String, Integer> tf);

    public static Ranker semantic(Query query, QueryProcessor manager) {
        return new SemanticRanker(query, manager);
    }

    public static Ranker bm25(Query query, QueryProcessor manager) {
        return new BM25Ranker(query, manager);
    }


    private static class SemanticRanker extends Ranker {

        private static final double k = 1.5;
        private static final double b = 0.75;

        private static final double ENTITIES_WEIGHT = 0.5;
        private static final double TERM_WEIGHT     = 0.3;
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

        private static final double k = 0.4;
        private static final double b = 0.999;

        private BM25Ranker(Query query, QueryProcessor manager) {
            super(query, manager);
        }

        @Override
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
