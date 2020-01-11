package query;

import parser.Document;

import java.util.*;

public class Query extends Document implements Iterable<Map.Entry<String, Integer>> {

    public HashMap<String, Integer> semanticTerms;

    public Query(Document document) {
        super(document.name);
        this.terms = document.terms;
        this.numbers = document.numbers;
        this.entities = document.entities;
        this.length = document.length;
        this.maxFrequency = document.maxFrequency;

        semanticTerms = new HashMap<>();
    }

    public void addSemantic(String term) {
        semanticTerms.compute(term, (key, frequency) -> {
            if (frequency == null)
                return 1;

            return frequency + 1;
        });
    }

    public int size() {
        return length + semanticTerms.size();
    }

    @Override
    public Iterator<Map.Entry<String, Integer>> iterator() {
        return new QueryIterator(this);
    }

    private static class QueryIterator implements Iterator<Map.Entry<String, Integer>> {

        Iterator<Map.Entry<String, Integer>> termIterator;
        Iterator<Map.Entry<String, Integer>> numberIterator;
        Iterator<Map.Entry<String, Integer>> entityIterator;
        Iterator<Map.Entry<String, Integer>> semanticIterator;

        public QueryIterator(Query query) {
            this.termIterator = query.terms.entrySet().iterator();
            this.numberIterator = query.numbers.entrySet().iterator();
            this.entityIterator = query.entities.entrySet().iterator();
            this.semanticIterator = query.semanticTerms.entrySet().iterator();
        }


        @Override
        public boolean hasNext() {
            return termIterator.hasNext() ||
                    numberIterator.hasNext() ||
                    entityIterator.hasNext() ||
                    semanticIterator.hasNext();
        }

        @Override
        public Map.Entry<String, Integer> next() {
            if (termIterator.hasNext())
                return termIterator.next();

            if (numberIterator.hasNext())
                return numberIterator.next();

            if (entityIterator.hasNext())
                return entityIterator.next();

            return semanticIterator.next();
        }
    }
}
