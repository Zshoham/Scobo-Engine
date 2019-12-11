package parser;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Parse implements Runnable {

    private String document;

    private static final Pattern textPattern = Pattern.compile("<TEXT>(.+?)</TEXT>", Pattern.DOTALL);
    private static final Pattern numericPattern = Pattern.compile("\\d+([,]\\d)*([.]\\d+)?");
    private static final Pattern hyphenPattern = Pattern.compile("\\w+([-]\\w+)+");
    private static final Pattern wordPattern = Pattern.compile("(?<![-$])\\b\\w+\\b(?![-])");

    private static final  int MAX_ENTITY_SIZE = 2;


    private Parser parser;
    private HashSet<String> uniqueTerms;
    private final HashSet<String> stopWords;

    protected Parse(String document, Parser parser) {
        this.document = document;
        this.parser = parser;
        this.stopWords = parser.getStopWords();
    }


    public static int wordCount = 0;

    @Override
    public void run() {
        final Matcher matcher = textPattern.matcher(document);
        int i = 0;
        while (matcher.find())
            parseText(matcher.group());
        parser.CPUTasks.complete();
    }

    private static final Object monitor = new Object();

    private void parseText(String text) {
        text = text.replaceAll("\\s+", " ");
        text = text.replaceAll("[{}():\"|,!@#^&*+=_]", ""); //TODO: update regex to delete <>[]'...

        Matcher m = numericPattern.matcher(text);
        while (m.find())
            parseNumbers(new NumberExpression(m.start(), m.end(), m.group(0), text));

        m = hyphenPattern.matcher(text);
        while (m.find())
            parseHyphenSeparatedExp(new Expression(m.start(), m.end(), m.group(0), text));

        m = wordPattern.matcher(text);
        while (m.find())
            parseWords(new Expression(m.start(), m.end(), m.group(0), text), m);
    }


    public static ConcurrentHashMap<String, Integer> terms = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Integer> capitalLettersTerms = new ConcurrentHashMap<>();
    public static ConcurrentHashMap<String, Integer> entities = new ConcurrentHashMap<>();

    private void addTerm(String term) {
        terms.compute(term, (term1, count) -> {
            if (count == null)
                return 1;
            return count + 1;
        });
    }
    private void addCapital(String term) {
        capitalLettersTerms.compute(term, (term1, count) -> {
            if (count == null)
                return 1;
            return count + 1;
        });
    }
    private void addEntity(String term) {
        entities.compute(term, (term1, count) -> {
            if (count == null)
                return 1;
            return count + 1;
        });
    }

    // start is the first digit of the number, end is the last digit of the number
    private void parseNumbers(NumberExpression numberExp) {
        if (numberExp.isPartOfFraction()) {
            if (numberExp.isNumerator()) {
                numberExp = NumberExpression.createMixedNumber(numberExp);
                if(tryDollars(numberExp)) return;
                else if(tryPercent(numberExp)) return;
                else if(tryPlainNumeric(numberExp)) return;
            }
        }
        else if (tryDate(numberExp)) return;
        else if(tryPercent(numberExp)) return;
        else if(tryDollars(numberExp)) return;
        else if(tryBetweenFirst(numberExp)) return;
        else if(tryPlainNumeric(numberExp)) return;
    }
    private void parseHyphenSeparatedExp(Expression exp){
        addTerm(exp.getExpression());
    }
    private void parseWords(Expression word, Matcher m){
        if(!(word.isPostfixExpression() || word.isDollarExpression() ||
                word.isMonthExpression() || word.isPercentExpression() || NumberExpression.isNumberExpression(word))){
            if(tryCapitalLetters(word, m)) return;
            else if(!parser.isStopWord(word.getExpression())) {
                String stemWord = parser.stemWord(word.getExpression().toLowerCase());
                addTerm(stemWord);
                synchronized (monitor) {
                    if (capitalLettersTerms.containsKey(stemWord.toUpperCase()))
                        moveUpperToLower(stemWord.toUpperCase());
                }
            }
        }
    }

    private void moveUpperToLower(String capLetWord) {
        terms.computeIfPresent(capLetWord.toLowerCase(), (s, integer) -> integer + capitalLettersTerms.get(capLetWord));
        capitalLettersTerms.remove(capLetWord);
    }

    private boolean tryPlainNumeric(NumberExpression numberExp) {
        StringBuilder plainNumber = new StringBuilder();
        if(numberExp.getValue() >= 1000000000)
            plainNumber.append(NumberExpression.getNumberString(numberExp.getValue() / 1000000000.0)).append("B");
        else if(numberExp.getValue() >= 1000000)
            plainNumber.append(NumberExpression.getNumberString(numberExp.getValue() / 1000000.0)).append("M");
        else if(numberExp.getValue() >= 1000)
            plainNumber.append(NumberExpression.getNumberString(numberExp.getValue() / 1000.0)).append("K");
        else {
            if(numberExp.getExpression().contains("/"))
                plainNumber.append(numberExp.getExpression());
            else
                plainNumber.append(NumberExpression.getNumberString(numberExp.getValue()));
        }
        addTerm(plainNumber.toString());
        return true;
    }
    private boolean tryDate(NumberExpression numberExp) {
        boolean isYear = false;
        if (numberExp.getExpression().length() == 4) isYear = true;
        else if (numberExp.getExpression().length() <= 2) isYear = false;
        else return false;

        Expression prevExpression = numberExp.getPrevExpression();
        Expression nextExpression = numberExp.getNextExpression();

        Expression month;
        if (prevExpression.isMonthExpression()) month = prevExpression;
        else if (nextExpression.isMonthExpression()) month = nextExpression;
        else return false;

        String date;
        if (isYear)
            date = numberExp.getExpression() + "-" + Expression.monthTable.get(month.getExpression());
        else if (numberExp.getExpression().length() == 1)
            date = Expression.monthTable.get(month.getExpression()) + "-0" + numberExp.getExpression();
        else
            date = Expression.monthTable.get(month.getExpression()) + "-" + numberExp.getExpression();
        addTerm(date);
        return true;
    }
    private boolean tryPercent(NumberExpression numberExp) {
        Expression next = numberExp.getNextExpression();
        if(next.isPostfixExpression())
            next = next.getNextExpression();
        if (next.isPercentExpression()) {
            addTerm(NumberExpression.getNumberString(numberExp.getValue()) + "%");
            return true;
        }
        return false;
    }
    private boolean tryDollars(NumberExpression numberExp){
        StringBuilder potentialTerm = new StringBuilder();
        Expression nextExp = numberExp.getNextExpression();
        Expression prevExp = numberExp.getPrevExpression();

        if(nextExp.isPostfixExpression())
            nextExp = nextExp.getNextExpression();
        if(!(nextExp.isDollarExpression() || prevExp.isDollarExpression()))
            return false;

        if(numberExp.getValue() > 1000000)
            potentialTerm.append(NumberExpression.getNumberString(numberExp.getValue() / 1000000)).append(" M Dollars");
        else{
            if(numberExp.getExpression().contains("/"))
                potentialTerm.append(numberExp.getExpression());
            else
                potentialTerm.append(NumberExpression.getNumberString(numberExp.getValue()));
            potentialTerm.append(" Dollars");
        }
        addTerm(potentialTerm.toString());
        return true;
    }
    private boolean tryBetweenFirst(NumberExpression numberExp){
        String potentialBetweenStr = numberExp.getPrevExpression().getExpression();
        if(potentialBetweenStr.equals("Between") || potentialBetweenStr.equals("BETWEEN") || potentialBetweenStr.equals("between"))
        {
            Expression potentialAndExp = numberExp.getNextExpression();
            String potentialAndStr = potentialAndExp.getExpression();
            if(potentialAndStr.equals("and") || potentialAndStr.equals("AND") || potentialAndStr.equals("And")) {
                Expression potentialNumberExp = potentialAndExp.getNextExpression();
                String potentialNumberStr = potentialNumberExp.getExpression();
                if(NumberExpression.isNumberExpression(potentialNumberStr)) {
                    StringBuilder hyphenSeparatedNumbers = new StringBuilder();
                    hyphenSeparatedNumbers.append(numberExp.getExpression()).append("-").append(potentialNumberStr);
                    parseHyphenSeparatedExp(new Expression(numberExp.getStartIndex(), potentialNumberExp.getEndIndex(), hyphenSeparatedNumbers.toString(), this.document));
                    return true;
                }
            }
        }
        return false;
    }

    //capital letter words - entities or first words
    /*
    private boolean tryCapitalLetters(Expression word){
        Expression next = word.getNextExpression();
        Expression prev = word.getPrevExpression();
        if(Character.isUpperCase(word.getExpression().charAt(0))) {
            boolean isEntity = false;
            if(prev.getExpression().length() == 0 ||
                    !Character.isUpperCase(prev.getExpression().charAt(0)) ||
                    prev.getExpression().charAt(prev.getExpression().length() - 1) == '.' ||
                    prev.getExpression().charAt(prev.getExpression().length() - 1) == ',') {

                while (next.getExpression().length() > 0 && Character.isUpperCase(next.getExpression().charAt(0))) {
                    isEntity = true;
                    if(word.getExpression().charAt(word.getExpression().length() - 1) == '.' ||
                            word.getExpression().charAt(word.getExpression().length() - 1) == ',')
                        break;
                    word.join(next);
                    next = word.getNextExpression();
                }
                if(word.getExpression().charAt(word.getExpression().length() - 1) == '.' ||
                        word.getExpression().charAt(word.getExpression().length() - 1) == ',')
                    word = new Expression(word.getStartIndex(), word.getEndIndex() - 1, word.getExpression().substring(0, word.getExpression().length() - 1), this.document);

                if(isEntity)
                    addEntity(word.getExpression());
                else if(!parser.isStopWord(word.getExpression().toLowerCase())) {
                    String stemWord = parser.stemWord(word.getExpression().toLowerCase());
                    if(terms.containsKey(stemWord))
                        addTerm(stemWord);
                    else
                        addCapital(stemWord.toUpperCase());
                }
            }
            return true;
        }
        return false;
    }
     */
    private boolean tryCapitalLetters(Expression word, Matcher m){
        handleSingleCapital(word);
        Expression next = word.getNextExpression();
        if(Character.isUpperCase(word.getExpression().charAt(0))) {
            boolean isEntity = false;
            int countEntity = 1;
            while (next.getExpression().length() > 0 && Character.isUpperCase(next.getExpression().charAt(0)) && countEntity < MAX_ENTITY_SIZE) {
                isEntity = true;
                handleSingleCapital(next);
                if(word.getExpression().charAt(word.getExpression().length() - 1) == '.' ||
                        word.getExpression().charAt(word.getExpression().length() - 1) == ',')
                    break;
                word.join(next);
                next = word.getNextExpression();
                m.find();
                countEntity++;
            }
            if(word.getExpression().charAt(word.getExpression().length() - 1) == '.' ||
                    word.getExpression().charAt(word.getExpression().length() - 1) == ',')
                word = new Expression(word.getStartIndex(), word.getEndIndex() - 1, word.getExpression().substring(0, word.getExpression().length() - 1), this.document);
            if(isEntity)
                addEntity(word.getExpression());
            return true;
        }
        return false;
    }


    private void handleSingleCapital(Expression word) {
        if (!parser.isStopWord(word.getExpression().toLowerCase())) {
            String stemWord = parser.stemWord(word.getExpression().toLowerCase());
            if (terms.containsKey(stemWord))
                addTerm(stemWord);
            else
                addCapital(stemWord.toUpperCase());
        }
    }
}
