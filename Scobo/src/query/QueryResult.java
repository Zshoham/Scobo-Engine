package query;

import util.Pair;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class QueryResult implements Iterable<Map.Entry<Integer, int[]>> {

    ConcurrentHashMap<Integer, int[]> results;

    QueryResult(String... queries) {
        results = new ConcurrentHashMap<>(queries.length);
        for (String query : queries)
            results.put(query.hashCode(), new int[0]);
    }

    QueryResult(Pair<Integer, String>[] queries) {
        results = new ConcurrentHashMap<>(queries.length);
        for (Pair<Integer, String> query : queries)
            results.put(query.first, new int[0]);
    }

    void updateResult(int queryID, int[] ranking) {
        this.results.put(queryID, ranking);
    }

    public int[] resultOf(String query) {
        return results.get(query.hashCode());
    }

    public int[] first() {
        return results.values().iterator().next();
    }

    public Set<Map.Entry<Integer, int[]>> sorted() {
        return new TreeMap<>(results).entrySet();
    }

    @Override
    public Iterator<Map.Entry<Integer, int[]>> iterator() {
        return results.entrySet().iterator();
    }

    @Override
    public void forEach(Consumer<? super Map.Entry<Integer, int[]>> action) {
        results.entrySet().forEach(action);
    }

    @Override
    public Spliterator<Map.Entry<Integer, int[]>> spliterator() {
        return results.entrySet().spliterator();
    }
}
