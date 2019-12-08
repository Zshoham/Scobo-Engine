package parser;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Parse implements Runnable {

    private String document;

    private static final Pattern textPattern = Pattern.compile("<TEXT>(.+?)</TEXT>", Pattern.DOTALL);
    private static final Pattern numericPattern = Pattern.compile("\\d+([,]\\d)*([.]\\d+)?");
    private static final Pattern hyphenPattern = Pattern.compile("\\w+([-]\\w+)+");
    private static final Pattern wordPattern = Pattern.compile("(?<![-$])\\b\\w+\\b(?![-])");


    private Parser parser;
    private HashSet<String> uniqueTerms;
    private final HashSet<String> stopWords;

    protected Parse(String document, Parser parser) {
        this.document = document;
        this.parser = parser;
        this.stopWords = parser.getStopWords();
        this.uniqueTerms = parser.getUniqueTerms();
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
        text = text.replaceAll("[{}():\"|,!@#^&*+=_]", "");

        Matcher m = numericPattern.matcher(text);
        while (m.find())
            parseNumbers(new NumberExpression(m.start(), m.end(), m.group(0)));

        m = hyphenPattern.matcher(text);
        while (m.find())
            parseHyphenSeparatedExp(new Expression(m.start(), m.end(), m.group(0)));

        m = wordPattern.matcher(text);
        while (m.find())
            parseWords(new Expression(m.start(), m.end(), m.group(0)));
    }


    //TODO: move the capitalLetters and entities into Parser and convert all collections
    // to maps of term -> frequency
    static LinkedList<String> terms = new LinkedList<>();
    static LinkedList<String> capitalLettersTerms = new LinkedList<>();
    static LinkedList<String> entities = new LinkedList<>();

    // start is the first digit of the number, end is the last digit of the number
    private static void parseNumbers(NumberExpression numberExp) {
        boolean isPartOfFraction = false;
        boolean isNumerator = false;

        if (numberExp.isPartOfFraction()) {
            if (numberExp.isNumerator()) {
                numberExp = createMixedNumber(numberExp);
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
    private static void parseHyphenSeparatedExp(Expression exp){
        terms.addLast(exp.getExpression());
    }

    private static void parseWords(Expression word){
        if(!(word.isPostfixExpression() || word.isDollarExpression() ||
                word.isMonthExpression() || word.isPercentExpression() || NumberExpression.isNumberExpression(word))){
            if(tryCapitalLetters(word)) return;
            else terms.addLast(word.getExpression());
        }
    }

    private static boolean tryPlainNumeric(NumberExpression numberExp) {
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
        terms.addLast(plainNumber.toString());
        return true;
    }
    private static boolean tryDate(NumberExpression numberExp) {
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
        terms.addLast(date);
        return true;
    }
    private static boolean tryPercent(NumberExpression numberExp) {
        Expression next = numberExp.getNextExpression();
        if(next.isPostfixExpression())
            next = next.getNextExpression();
        if (next.isPercentExpression()){
            terms.addLast(NumberExpression.getNumberString(numberExp.getValue()) + "%");
            //TODO: System.out.println(numberExp.getExpression() + "%");
            return true;
        }
        return false;
    }
    private static boolean tryDollars(NumberExpression numberExp){
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
        terms.addLast(potentialTerm.toString());
        return true;
    }
    private static boolean tryBetweenFirst(NumberExpression numberExp){
        String potentialBetweenStr = numberExp.getPrevExpression().getExpression();
        if(potentialBetweenStr.equals("Between") || potentialBetweenStr.equals("BETWEEN") || potentialBetweenStr.equals("between"))
        {
            Expression potentialAndExp = numberExp.getNextExpression();
            String potentialAndStr = potentialAndExp.getExpression();
            if(potentialAndStr.equals("and") || potentialAndStr.equals("AND") || potentialAndStr.equals("And")) {
                Expression potentialNumberExp = potentialAndExp.getNextExpression();
                String potentialNumberStr = potentialNumberExp.getExpression();
                if(NumberExpression.isNumberExpression(potentialNumberStr)){
                    StringBuilder hyphenSeparatedNumbers = new StringBuilder();
                    hyphenSeparatedNumbers.append(numberExp.getExpression()).append("-").append(potentialNumberStr);
                    parseHyphenSeparatedExp(new Expression(numberExp.getStartIndex(), potentialNumberExp.getEndIndex(), hyphenSeparatedNumbers.toString()));
                    return true;
                }
            }
        }
        return false;
    }

    //capital letter words - entities or first words
    private static boolean tryCapitalLetters(Expression word){
        Expression next = word.getNextExpression();
        Expression prev = word.getPrevExpression();
        if(Character.isUpperCase(word.getExpression().charAt(0))){
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
                    word = word.join(next);
                    next = word.getNextExpression();
                }
                if(word.getExpression().charAt(word.getExpression().length() - 1) == '.' ||
                        word.getExpression().charAt(word.getExpression().length() - 1) == ',')
                    word = new Expression(word.getStartIndex(), word.getEndIndex() - 1, word.getExpression().substring(0, word.getExpression().length() - 1));

                if(isEntity)
                    entities.addLast(word.getExpression());
                else
                    capitalLettersTerms.addLast(word.getExpression());
            }
            return true;
        }
        return false;
    }

    //TODO: move to NumberExpression
    private static NumberExpression createMixedNumber(NumberExpression numerator){
        int fullExpStartIndex = numerator.getStartIndex();
        int fullExpEndIndex = numerator.getNextExpression().getEndIndex();
        StringBuilder fullExp = new StringBuilder();
        String fullNumber = "0";
        if(NumberExpression.isNumberExpression(numerator.getPrevExpression())){
            fullExpStartIndex = numerator.getPrevExpression().getStartIndex();
            fullNumber = numerator.getPrevExpression().getExpression();
        }
        fullExp.append(fullNumber).append(" ").append(numerator.getExpression()).
                append(numerator.getNextExpression().getExpression());
        return new NumberExpression(fullExpStartIndex, fullExpEndIndex, fullExp.toString());
    }
}
