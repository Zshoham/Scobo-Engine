package parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Parse implements Runnable {


    private String document; // The document being parsed

    // REGEX pattern to split the text from the document
    private static final Pattern textPattern = Pattern.compile(Pattern.quote("<TEXT>") + "(.+?)" + Pattern.quote("</TEXT>"), Pattern.DOTALL);
    // REGEX pattern to get the name of the document
    private static final Pattern namePattern = Pattern.compile(Pattern.quote("<DOCNO>") + "(.+?)" + Pattern.quote("</DOCNO>"), Pattern.DOTALL);
    // REGEX pattern to get all numbers in the document
    private static final Pattern numericPattern = Pattern.compile("\\d+([,]\\d)*([.]\\d+)?");
    //REGEX pattern to get all the hyphen separated words and numbers in the document
    private static final Pattern hyphenPattern = Pattern.compile("\\w+([-]\\w+)+");
    //REGEX pattern to get all the words in the dictionary
    //   word is has least one letter, without the chars -$<> before or after
    private static final Pattern wordPattern = Pattern.compile("(?<![-$<])\\b\\w+\\b(?![->])");

    // Maximum amount of words in entity
    private static final int MAX_ENTITY_SIZE = 2;

    private Parser parser;
    private Document documentData;


    protected Parse(String document, Parser parser) {
        this.document = document;
        this.parser = parser;
        this.documentData = new Document(genDocName(document));
    }

    private String genDocName(String document) {
        Matcher m = namePattern.matcher(document);
        if (m.find())
            return m.group(1);

        throw new IllegalArgumentException("The document does not have a <DOCNO> tag");
    }


    /**
     * Start the parsing on doc
     */
    @Override
    public void run() {
        final Matcher matcher = textPattern.matcher(document);
        while (matcher.find())
            parseText(matcher.group());

        parser.onFinishedParse(this.documentData);
        this.documentData = null;
        parser.CPUTasks.complete();
    }


    /**
     * Parse every paragraph in the document
     * @param text String of the text in the doc
     */
    private void parseText(String text) {
        text = text.replaceAll("\\s+", " ");
        text = text.replaceAll("[{}()|@#^&*+=_']", "");

        // Find all the numbers in the text and apply the rules on them
        Matcher m = numericPattern.matcher(text);
        while (m.find())
            parseNumbers(new NumberExpression(m.start(), m.end(), m.group(0), text));

        // Find all the hyphen Separated words\numbers in the text and apply the rules on them
        m = hyphenPattern.matcher(text);
        while (m.find())
            parseHyphenSeparatedExp(new Expression(m.start(), m.end(), m.group(0), text));

        // Find all the words in the text and apply the rules on them
        m = wordPattern.matcher(text);
        while (m.find())
            parseWords(new Expression(m.start(), m.end(), m.group(0), text), m);
    }


    /**
     * Parse numbers from the document
     * @param numberExp Expression of the number
     */
    private void parseNumbers(NumberExpression numberExp) {
        if (numberExp.isPartOfMixedNumber()) {
            if (numberExp.isNumerator()) {
                numberExp = NumberExpression.createMixedNumber(numberExp);
                if (tryDollars(numberExp)) return;
                else if (tryPercent(numberExp)) return;
                else if (tryPlainNumeric(numberExp)) return;
            }
        }
        else if (tryDate(numberExp)) return;
        else if (tryPercent(numberExp)) return;
        else if (tryDollars(numberExp)) return;
        else if (tryBetweenFirst(numberExp)) return;
        else if (tryPlainNumeric(numberExp)) return;
    }

    /**
     * parse all the hyphen separated words or numbers
     * @param exp words or numbers separated by hyphens
     */
    private void parseHyphenSeparatedExp(Expression exp) {
        documentData.addTerm(exp.getExpression());
    }

    /**
     * Parse every word in the document
     * @param word an expression of the word in the doc
     * @param m mather to skip unwanted words
     */
    private void parseWords(Expression word, Matcher m) {
        if (!(word.isPostfixExpression() || word.isDollarExpression() ||
                word.isMonthExpression() || word.isPercentExpression() || NumberExpression.isNumberExpression(word))) {
            if (tryCapitalLetters(word, m)) return;
            else if (!parser.isStopWord(word.getExpression())) {
                String stemWord = parser.stemWord(word.getExpression().toLowerCase());
                documentData.addTerm(stemWord);
                if (documentData.terms.containsKey(stemWord.toUpperCase()))
                    moveUpperToLower(stemWord.toUpperCase());
            }
        }
    }

    //region try functions
    // Try functions tries to match the rules to the given expression
    // return true if the expression is catch by the rule, and add to the dictionary according to the specified rule

    /** Check the date rules
     * date is:
     * <ul>
     *     <li>DD Month,   add as MM-DD</li>
     *     <li>Month DD,   add as MM-DD</li>
     *     <li>Month YYYY, add as YYYY-MM</li>
     * </ul>
     */
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
        documentData.addNumber(date);
        return true;
    }

    /**
     * Check percent rule
     * legal percent term is:
     * <ul>
     *     <li>Number%</li>
     *     <li>Number percent</li>
     *     <li>Number percentage</li>
     * </ul>
     * If number following this rules, it added to the dictionary as number%
     */
    private boolean tryPercent(NumberExpression numberExp) {
        Expression next = numberExp.getNextExpression();
        if (next.isPostfixExpression())
            next = next.getNextExpression();
        if (next.isPercentExpression()) {
            documentData.addNumber(NumberExpression.getNumberString(numberExp.getValue()) + "%");
            return true;
        }
        return false;
    }

    /**
     * Check if number is part of price rules
     * if number is below million the rules are:
     * <ul>
     *     <li>price Dollars</li>
     *     <li>price frac Dollars</li>
     *     <li>$price</li>
     * </ul>
     * adds to the dictionary as number Dollar
     * <br>
     * if number is above million the rules are:
     * <ul>
     *     <li>price Dollars</li>
     *     <li>$price</li>
     *     <li>$price million</li>
     *     <li>$price billion</li>
     *     <li>price postfix dollars</li>
     *     <li>price million U.S. Dollars</li>
     *     <li>price billion U.S. Dollars</li>
     *     <li>price trillion U.S. Dollars</li>
     * </ul>
     * adds to the dictionary as number M Dollars
     */
    private boolean tryDollars(NumberExpression numberExp) {
        StringBuilder potentialTerm = new StringBuilder();
        Expression nextExp = numberExp.getNextExpression();
        Expression prevExp = numberExp.getPrevExpression();

        if (nextExp.isPostfixExpression())
            nextExp = nextExp.getNextExpression();
        if (!(nextExp.isDollarExpression() || prevExp.isDollarExpression()))
            return false;

        if (numberExp.getValue() > 1000000)
            potentialTerm.append(NumberExpression.getNumberString(numberExp.getValue() / 1000000)).append(" M Dollars");
        else
            potentialTerm.append(numberExp.getExpression()).append(" Dollars");
        documentData.addNumber(potentialTerm.toString());
        return true;
    }

    /**
     * Check if number is plain number, without any addition
     * plain number is every string contains only numeric chars or ,/.
     * with or without decimal point or fraction
     * <br>
     * adds to the dictionary as:
     * <ul>
     *     <li>if number is bellow 1000- as it appears</li>
     *     <li>if number is 1K <= number < 100000- as numberK</li>
     *     <li>if number is 1M <= number < 1bn- as numberM</li>
     *     <li>if number is 1bn <= number - as numberB</li>
     * </ul>
     * if the number has decimal point- adds to the dictionary with maximum 3 decimal numbers
     */
    private boolean tryPlainNumeric(NumberExpression numberExp) {
        StringBuilder plainNumber = new StringBuilder();
        if (numberExp.getValue() >= 1000000000)
            plainNumber.append(NumberExpression.getNumberString(numberExp.getValue() / 1000000000.0)).append("B");
        else if (numberExp.getValue() >= 1000000)
            plainNumber.append(NumberExpression.getNumberString(numberExp.getValue() / 1000000.0)).append("M");
        else if (numberExp.getValue() >= 1000)
            plainNumber.append(NumberExpression.getNumberString(numberExp.getValue() / 1000.0)).append("K");
        else {
            if (numberExp.getExpression().contains("/"))
                plainNumber.append(numberExp.getExpression());
            else
                plainNumber.append(NumberExpression.getNumberString(numberExp.getValue()));
        }
        documentData.addNumber(plainNumber.toString());
        return true;
    }

    /**
     * Check if number is part of bigger expression in the format<br>
     *    &#09; between number and number<br>
     * If it is- add to the dictionary as as hyphen separated numbers<br>
     *    &#09; number-number
     */
    private boolean tryBetweenFirst(NumberExpression numberExp) {
        String potentialBetweenStr = numberExp.getPrevExpression().getExpression();
        if (potentialBetweenStr.equals("Between") || potentialBetweenStr.equals("BETWEEN") || potentialBetweenStr.equals("between")) {
            Expression potentialAndExp = numberExp.getNextExpression();
            String potentialAndStr = potentialAndExp.getExpression();
            if (potentialAndStr.equals("and") || potentialAndStr.equals("AND") || potentialAndStr.equals("And")) {
                Expression potentialNumberExp = potentialAndExp.getNextExpression();
                String potentialNumberStr = potentialNumberExp.getExpression();
                if (NumberExpression.isNumberExpression(potentialNumberStr)) {
                    StringBuilder hyphenSeparatedNumbers = new StringBuilder();
                    hyphenSeparatedNumbers.append(numberExp.getExpression()).append("-").append(potentialNumberStr);
                    parseHyphenSeparatedExp(new Expression(numberExp.getStartIndex(), potentialNumberExp.getEndIndex(), hyphenSeparatedNumbers.toString(), this.document));
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Check if word first char is upper case
     * If it is- add to the dictionary by the rules of capital letters words:
     *      &#09; if exist in dictionary in low case- add in low case
     *      &#09; else- add whole word in capital letters<br>
     * check if the next word is also in capital,<br>
     * if it is, apply the same rule, and create an entity
     * when the entity is big enough or the capital letters words are finished- add entity to dictionary
     */
    private boolean tryCapitalLetters(Expression word, Matcher m) {
        if (Character.isUpperCase(word.getExpression().charAt(0))) {
            handleSingleCapital(word);
            boolean isEntity = false;
            int countEntity = 1;
            Expression next = word.getNextWordExpression();
            // add next capital word to the word, until:
            //     - next is not capital
            //     - last char is not space- stop sentence
            //     - next word not exist
            //     - entity is big enough
            while (word.getDoc().charAt(word.getEndIndex()) == ' ' && next.getExpression().length() > 0 &&
                    Character.isUpperCase(next.getExpression().charAt(0)) && countEntity < MAX_ENTITY_SIZE) {
                isEntity = true;
                word.join(next);

                if (!next.getExpression().contains("-")) {
                    handleSingleCapital(next);
                    if(m.find())
                        next = word.getNextWordExpression();
                    else break;
                }
                else
                    next = word.getNextWordExpression();
                countEntity++;
            }
            if (isEntity)
                documentData.addEntity(word.getExpression());
            return true;
        }
        return false;
    }
    //endregion

    //check if low case word already added as capital word
    // if it is- add as lower, and remove the capital
    private void moveUpperToLower(String upperCaseWord) {
        documentData.terms.computeIfPresent(upperCaseWord.toLowerCase(), (s, integer) -> integer + documentData.terms.get(upperCaseWord));
        documentData.terms.remove(upperCaseWord);
    }

    /**
     * if word exist in low case, add as low cas
     * else- add as capital word
     * @param word capital letters word
     */
    private void handleSingleCapital(Expression word) {
        if (!parser.isStopWord(word.getExpression().toLowerCase())) {
            String stemWord = parser.stemWord(word.getExpression().toLowerCase());
            if (documentData.terms.containsKey(stemWord))
                documentData.addTerm(stemWord);
            else
                documentData.addTerm(stemWord.toUpperCase());
        }
    }
}
