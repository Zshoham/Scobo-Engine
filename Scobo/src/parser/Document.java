package parser;

import java.util.HashMap;

public class Document {

    public String name;
    public HashMap<String, Integer> terms;
    public HashMap<String, Integer> entities;
    public int maxFrequency;
    public int length;

    public Document(String name) {
        this.name = name;
        this.terms = new HashMap<>();
        this.entities = new HashMap<>();
        this.maxFrequency = 1;
        this.length = -1;
    }

    public void addTerm(String term) {
        if (term.length() < 2) return;
        length++;
        terms.compute(term, this::computeAdd);
    }

    public void addEntity(String entity) {
        length++;
        entities.compute(entity.toUpperCase(), this::computeAdd);
    }

    private Integer computeAdd(String term, Integer frequency) {
        if (frequency == null)
            return 1;

        if (maxFrequency == frequency)
            maxFrequency++;

        return frequency + 1;
    }
}
