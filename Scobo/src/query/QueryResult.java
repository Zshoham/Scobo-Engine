package query;

import util.Pair;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Holds query results possibly of multiple queries,
 * if only one query was requested, use {@code first() } in order
 * to retrieve the ranking.
 */
public class QueryResult implements Iterable<Map.Entry<Integer, int[]>> {

    ConcurrentHashMap<Integer, int[]> results;

    // constructs query result from free text queries.
    QueryResult(String... queries) {
        results = new ConcurrentHashMap<>(queries.length);
        for (String query : queries)
            results.put(query.hashCode(), new int[0]);
    }

    // constructs query result from structured queries.
    QueryResult(Pair<Integer, String>[] queries) {
        results = new ConcurrentHashMap<>(queries.length);
        for (Pair<Integer, String> query : queries)
            results.put(query.first, new int[0]);
    }

    // sets the result for the given queryID.
    void updateResult(int queryID, int[] ranking) {
        this.results.put(queryID, ranking);
    }

    /**
     * @return ranking for the given query if it exists.
     */
    public Optional<int[]> resultOf(String query) {
        return Optional.ofNullable(results.get(query.hashCode()));
    }

    /**
     * @return ranking for the given queryID if it exists.
     */
    public Optional<int[]> resultOf(int queryID) {
        return Optional.ofNullable(results.get(queryID));
    }

    /**
     * @return one of the available results, if only one is available
     * it will be returned.
     */
    public int[] get() {
        return results.values().iterator().next();
    }

    /**
     * @return a sorted view of the results.
     */
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
