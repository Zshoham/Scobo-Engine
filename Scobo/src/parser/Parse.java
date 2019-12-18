package parser;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Parse implements Runnable {

    private String document;

    private static final Pattern textPattern = Pattern.compile(Pattern.quote("<TEXT>") + "(.+?)" + Pattern.quote("</TEXT>"), Pattern.DOTALL);
    private static final Pattern namePattern = Pattern.compile(Pattern.quote("<DOCNO>") + "(.+?)" + Pattern.quote("</DOCNO>"), Pattern.DOTALL);
    private static final Pattern numericPattern = Pattern.compile("\\d+([,]\\d)*([.]\\d+)?");
    private static final Pattern hyphenPattern = Pattern.compile("\\w+([-]\\w+)+");
    private static final Pattern wordPattern = Pattern.compile("(?<![-$<])\\b\\w+\\b(?![->])");

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


    @Override
    public void run() {
        final Matcher matcher = textPattern.matcher(document);
        while (matcher.find())
            parseText(matcher.group());

        parser.onFinishedParse(this.documentData);
        this.documentData = null;
        parser.CPUTasks.complete();
    }

    private void parseText(String text) {
        text = text.replaceAll("\\s+", " ");
        text = text.replaceAll("[{}()|@#^&*+=_']", ""); //TODO: update regex to delete []

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

    // start is the first digit of the number, end is the last digit of the number
    private void parseNumbers(NumberExpression numberExp) {
        if (numberExp.isPartOfFraction()) {
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
    private void parseHyphenSeparatedExp(Expression exp) {
        documentData.addWord(exp.getExpression());
    }
    private void parseWords(Expression word, Matcher m) {
        if (!(word.isPostfixExpression() || word.isDollarExpression() ||
                word.isMonthExpression() || word.isPercentExpression() || NumberExpression.isNumberExpression(word))) {
            if (tryCapitalLetters(word, m)) return;
            else if (!parser.isStopWord(word.getExpression())) {
                String stemWord = parser.stemWord(word.getExpression().toLowerCase());
                documentData.addWord(stemWord);
                if (documentData.terms.containsKey(stemWord.toUpperCase()))
                    moveUpperToLower(stemWord.toUpperCase());
            }
        }
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
        documentData.addTerm(date);
        return true;
    }

    private boolean tryPercent(NumberExpression numberExp) {
        Expression next = numberExp.getNextExpression();
        if (next.isPostfixExpression())
            next = next.getNextExpression();
        if (next.isPercentExpression()) {
            documentData.addTerm(NumberExpression.getNumberString(numberExp.getValue()) + "%");
            return true;
        }
        return false;
    }

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
        documentData.addTerm(potentialTerm.toString());
        return true;
    }

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
        documentData.addTerm(plainNumber.toString());
        return true;
    }

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

    private boolean tryCapitalLetters(Expression word, Matcher m) {
        if (Character.isUpperCase(word.getExpression().charAt(0))) {
            handleSingleCapital(word);
            boolean isEntity = false;
            int countEntity = 1;
            Expression next = word.getNextWordExpression();
            while (word.getDoc().charAt(word.getEndIndex()) == ' ' && next.getExpression().length() > 0 && Character.isUpperCase(next.getExpression().charAt(0)) && countEntity < MAX_ENTITY_SIZE) {
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

                //next = word.getNextExpression();

                countEntity++;
            }

            if (isEntity)
                documentData.addEntity(word.getExpression());
            return true;
        }
        return false;
    }


    private void moveUpperToLower(String upperCaseWord) {
        documentData.terms.computeIfPresent(upperCaseWord.toLowerCase(), (s, integer) -> integer + documentData.terms.get(upperCaseWord));
        documentData.terms.remove(upperCaseWord);
    }

    private void handleSingleCapital(Expression word) {
        if (!parser.isStopWord(word.getExpression().toLowerCase())) {
            String stemWord = parser.stemWord(word.getExpression().toLowerCase());
            if (documentData.terms.containsKey(stemWord))
                documentData.addWord(stemWord);
            else
                documentData.addWord(stemWord.toUpperCase());
        }
    }
}
