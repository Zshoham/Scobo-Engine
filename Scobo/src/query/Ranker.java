package query;

abstract class Ranker implements Runnable {

    static Ranker semantic() {
       return new SemanticRanker();
    }

    static Ranker bm25() {
        return new BM25Ranker();
    }

    private Ranker() { }

    private static class SemanticRanker extends Ranker {

        @Override
        public void run() {

        }
    }

    private static class BM25Ranker extends Ranker {

        @Override
        public void run() {

        }
    }
}
