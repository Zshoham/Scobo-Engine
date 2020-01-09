package query;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class QueryResult implements Iterable<List<Integer>> {

    ConcurrentHashMap<Integer, List<Integer>> results;

    QueryResult(String... queries) {
        results = new ConcurrentHashMap<>(queries.length);
        for (String query : queries)
            results.put(query.hashCode(), Collections.emptyList());
    }

    void updateResult(String query, List<Integer> rankings) {
        this.results.put(query.hashCode(), rankings);
    }

    public List<Integer> resultOf(String query) {
        return results.get(query.hashCode());
    }

    public List<Integer> first() {
        return results.values().iterator().next();
    }

    @Override
    public Iterator<List<Integer>> iterator() {
        return results.values().iterator();
    }

    @Override
    public void forEach(Consumer<? super List<Integer>> action) {
        results.values().forEach(action);
    }

    @Override
    public Spliterator<List<Integer>> spliterator() {
        return results.values().spliterator();
    }
}
