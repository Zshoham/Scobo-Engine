package query;

import parser.Document;

import java.util.*;
import java.util.function.Function;

/**
 * Query represents a parsed query, a query is a specialized document
 * where we add additional id and semanticTerms fields.
 */
class Query extends Document implements Iterable<Map.Entry<String, Integer>> {

    public HashMap<String, Integer> semanticTerms;
    public int id;

    /**
     * Creates a query from the document representing it.
     * @param document a document that represents a query.
     */
    public Query(Document document) {
        super(document.name);
        this.id = Integer.parseInt(this.name);
        this.terms = document.terms;
        this.numbers = document.numbers;
        this.entities = document.entities;
        this.length = document.length;
        this.maxFrequency = document.maxFrequency;

        semanticTerms = new HashMap<>();
    }

    /**
     * adds a semantic field to the query, these are fields that do not
     * count as normal terms and are used in the ranking.
     * @param field the
     */
    public void addSemantic(String field) {
        semanticTerms.compute(field, (key, frequency) -> {
            if (frequency == null)
                return 1;

            return frequency + 1;
        });
    }

    /**
     * @return the frequency of the term in the query, including semantic terms,
     * exists as both a semantic field and a regular term its regular frequency
     * will be returned.
     */
    public int get(String term) {
        int res = terms.getOrDefault(term,0);
        if (res != 0)
            return res;

        res = numbers.getOrDefault(term, 0);
        if (res != 0)
            return res;

        res = entities.getOrDefault(term, 0);
        if (res != 0)
            return res;

        return semanticTerms.getOrDefault(term, 0);
    }

    /**
     * @return length of the query (semantic fields included).
     */
    public int length() {
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
