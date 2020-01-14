package parser;

import java.util.HashMap;

/**
 * Holds the information of a document including numbers, terms, and entities
 */
public class Document {

    public String name;
    public HashMap<String, Integer> numbers;
    public HashMap<String, Integer> terms;
    public HashMap<String, Integer> entities;
    public int maxFrequency;
    public int length;

    public Document(String name) {
        this.name = name;
        this.numbers = new HashMap<>();
        this.entities = new HashMap<>();
        this.terms = new HashMap<>();
        this.maxFrequency = 1;
        this.length = 0;
    }

    public void addNumber(String term) {
        length++;
        numbers.compute(term, this::computeAdd);
    }

    public void addTerm(String word) {
        if (isWordNumber(word))
            return;
        if (word.length() < 2) return;
        length++;
        terms.compute(word, this::computeAdd);
    }

    // check to see if the word is a number with a postfix like 10m or 10M.
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
