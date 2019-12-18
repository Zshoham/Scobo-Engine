package parser;

import java.util.HashMap;

public class Document {

    public String name;
    public HashMap<String, Integer> terms;
    public HashMap<String, Integer> words;
    public HashMap<String, Integer> entities;
    public int maxFrequency;
    public int length;

    public Document(String name) {
        this.name = name;
        this.terms = new HashMap<>();
        this.entities = new HashMap<>();
        this.words = new HashMap<>();
        this.maxFrequency = 1;
        this.length = -1;
    }

    public void addTerm(String term) {
        length++;
        terms.compute(term, this::computeAdd);
    }

    public void addWord(String word) {
        if (isWordNumber(word))
            return;
        if (word.length() < 2) return;
        length++;
        words.compute(word, this::computeAdd);
    }

    private boolean isWordNumber(String word) {
        if (word.length() < 2)
            return false;
        String potentialPostFix = word.substring(word.length() - 1);
        String potentialNumber = word.substring(0, word.length() - 1);
        return NumberExpression.isNumberExpression(potentialNumber) &&
                Expression.numbersPostfixTable.containsKey(potentialPostFix);
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
