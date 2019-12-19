package parser;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;

/**
 *
 */
public class Expression {
    //map all the months in the year to their numeric value
    public static HashMap<String, String> monthTable = buildMonthTable();
    //set of all the chars that stop a sentence
    public static HashSet<Character> stoppingChars = buildStoppingCharsTable();
    //set of all the dollar expressions
    public static HashSet<String> dollarExpressions = buildDollarExpressions();
    //map all the number postfixes to their numeric value
    public static HashMap<String, Double> numbersPostfixTable = buildNumbersPostfixTable();
    // maps distance postfixes to their desired form
    public static HashMap<String, String> distanceTable = buildDistanceTable();
    // maps wight postfixes to their desired for
    public static HashMap<String, String> wightTable = buildWightTable();

    private int startIndex;    // index to first char of the expression in the text
    private int endIndex;      // index to char after the last char of the expression in the text
    private String expression; // the string expression
    private String doc;        // the document

    //region contractors
    public Expression(int startIndex, int endIndex, String expression, String doc) {
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.expression = expression;
        this.doc = doc;
    }
    public Expression() {
        this(0, 0, "", "");
    }
    //endregion

    //region getters
    public int getStartIndex() { return startIndex; }
    public int getEndIndex() { return endIndex; }
    public String getExpression() { return expression; }
    public String getDoc() { return doc; }
    //endregion

    //region Check is part of
    //the functions in this region check if an expression is part of the group defined by their signature

    /**
     * check if expression is a percent expression<br>
     * legitimate percent expressions are:
     * <ul>
     *     <li>$</li>
     *     <li>percent</li>
     *     <li>percentage</li>
     * </ul>
     * @return true if expression is legitimate percent expression
     */
    public boolean isPercentExpression(){
        return expression.equals("%") || expression.equals("percent") || expression.equals("percentage");
    }
    /**
     * check if expression is a percent expression<br>
     * legitimate percent expressions is defined by the month table
     * built in the static function {@link #buildMonthTable()}
     * @return true if expression is legitimate month
     */
    public boolean isMonthExpression(){
        return monthTable.containsKey(expression);
    }
    /**
     * check if expression is a dollar expression<br>
     * legitimate dollar expressions is defined by the dollar table
     * built in the static function {@link #buildDollarExpressions()}
     * @return true if expression is legitimate dollar expression
     */
    public boolean isDollarExpression(){
        String exp = expression;
        if(expression.equals("U.S.") || expression.equals("u.s."))
            exp += " " + this.getNextExpression().expression;
        return dollarExpressions.contains(exp);
    }
    /**
     * check if expression is a postfix expression<br>
     * legitimate postfix expressions is defined by the numbers postfixes table
     * built in the static function {@link #buildNumbersPostfixTable()}
     * @return true if expression is legitimate postfix
     */
    public boolean isPostfixExpression(){
        return numbersPostfixTable.containsKey(this.expression);
    }

    /**
     * check if expression is weight expression<br>
     * legitimate weight expressions are defined by the weight table
     * built in the static function {@link #buildWightTable()}
     * @return true if expression is legitimate weight
     */
    public boolean isWeightExpression(){
        return wightTable.containsKey(this.expression);
    }
    /**
     * check if expression is distance expression<br>
     * legitimate distance expressions are defined by the distance table
     * built in the static function {@link #buildDistanceTable()} ()}
     * @return true if expression is legitimate distance
     */    public boolean isDistanceExpression(){
        return distanceTable.containsKey(this.expression);
    }

    //endregion

    //region Expression get Next/Prev
    /**
     * Get the next expression in the document<br>
     * the next expression is the string after this expression's space, until the next space in the text
     * @return Expression of the next expression, if exist. if not- empty expression
     */
    public Expression getNextExpression(){
        int start = this.startIndex;
        int end = this.endIndex;

        if(start == this.doc.length() || end  > this.doc.length() - 2)
            return new Expression();

        int nextSpaceIndex = end + 1;
        while (this.doc.charAt(nextSpaceIndex) != ' ' && nextSpaceIndex != this.doc.length() -1)
            nextSpaceIndex++;
        if (this.doc.charAt(nextSpaceIndex) != ' ' && nextSpaceIndex == this.doc.length() -1)
            nextSpaceIndex++;

        int startIndex = end;
        int endIndex = nextSpaceIndex;
        if (this.doc.charAt(end) == ' ')
            startIndex++;


        StringBuilder nextExpression = new StringBuilder(this.doc.substring(startIndex, endIndex));
        while (nextExpression.length() != 0 && stoppingChars.contains(nextExpression.charAt(nextExpression.length() - 1))){
            endIndex--;
            nextExpression.deleteCharAt(nextExpression.length() - 1);
        }

        return new Expression(startIndex, endIndex, nextExpression.toString(), this.doc);
    }

    /**
     * Get the previous expression in the expression's document<br>
     * the previous expression is the string before this expression's, until the previous space in the text
     * @return Expression of the preview expression, if exist. if not- empty expression
     */
    public Expression getPrevExpression(){
        int start = this.startIndex;
        int end = this.endIndex;

        if (start < 2 || end == 0)
            return new Expression();

        int prevSpaceIndex = start - 2;
        while (this.doc.charAt(prevSpaceIndex) != ' ' && prevSpaceIndex != 0)
            prevSpaceIndex--;
        int startIndex = prevSpaceIndex;
        int endIndex = start;
        if (this.doc.charAt(prevSpaceIndex) == ' ')
            startIndex += 1;
        if (this.doc.charAt(start - 1) == ' ')
            endIndex--;
        String expression = this.doc.substring(startIndex, endIndex);
        return new Expression(startIndex, endIndex, expression, this.doc);
    }

    /**
     * Get the expression in the document<br>
     * the next word is the string after this expression's space, until the next space in the text
     * @return Expression of the next expression, if exist. if not- empty expression
     */
    public Expression getNextWordExpression(){
        int start = this.startIndex;
        int end = this.endIndex;

        if(start == this.doc.length() || end  > this.doc.length() - 2)
            return new Expression();

        int nextSpaceIndex = end + 1;
        while ((Character.isDigit(this.doc.charAt(nextSpaceIndex)) || Character.isLetter(this.doc.charAt(nextSpaceIndex)) ||
                this.doc.charAt(nextSpaceIndex) == '-') && nextSpaceIndex != this.doc.length() -1)
            nextSpaceIndex++;
        if ((Character.isDigit(this.doc.charAt(nextSpaceIndex)) || Character.isLetter(this.doc.charAt(nextSpaceIndex))) &&
                nextSpaceIndex == this.doc.length() -1)
            nextSpaceIndex++;

        int startIndex = end;
        int endIndex = nextSpaceIndex;
        if (this.doc.charAt(end) == ' ')
            startIndex++;


        StringBuilder nextExpression = new StringBuilder(this.doc.substring(startIndex, endIndex));
        while (nextExpression.length() != 0 && stoppingChars.contains(nextExpression.charAt(nextExpression.length() - 1))){
            endIndex--;
            nextExpression.deleteCharAt(nextExpression.length() - 1);
        }

        return new Expression(startIndex, endIndex, nextExpression.toString(), this.doc);
    }
    //endregion


    /**
     * add exp to the end of this expression, with separating space
     * @param exp Expression you want to add to this
     */
    public void join(Expression exp) {
        this.endIndex = exp.endIndex;
        StringBuilder expression = new StringBuilder(this.expression);
        expression.append(" ").append(exp.expression);
        this.expression = expression.toString();
    }


    //region Object overrides
    @Override
    public String toString() {
        return expression;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Expression that = (Expression) o;
        return startIndex == that.startIndex &&
                endIndex == that.endIndex &&
                Objects.equals(expression, that.expression);
    }

    @Override
    public int hashCode() {
        return Objects.hash(startIndex, endIndex, expression);
    }
    //endregion

    //region build tables static functions
    //the functions below responsible to build the tables and sets defined in their signature
    private static HashSet<String> buildDollarExpressions() {
        HashSet<String> dollarExpressions = new HashSet<>();
        dollarExpressions.add("$");
        dollarExpressions.add("Dollars");
        dollarExpressions.add("dollars");
        dollarExpressions.add("U.S. Dollars");
        dollarExpressions.add("U.S. dollars");
        dollarExpressions.add("u.s. Dollars");
        dollarExpressions.add("u.s. dollars");
        return dollarExpressions;
    }
    private static HashMap<String, String> buildMonthTable() {
        HashMap<String, String> res = new HashMap<>();

        res.put("JANUARY", "01");
        res.put("January", "01");
        res.put("JAN", "01");
        res.put("Jan", "01");

        res.put("FEBRUARY", "02");
        res.put("February", "02");
        res.put("FEB", "02");
        res.put("Feb", "02");

        res.put("MARCH", "03");
        res.put("March", "03");
        res.put("MAR", "03");
        res.put("Mar", "03");

        res.put("APRIL", "04");
        res.put("April ", "04");
        res.put("APR", "04");
        res.put("Apr", "04");

        res.put("MAY", "05");
        res.put("May", "05");

        res.put("JUNE", "06");
        res.put("June", "06");

        res.put("JULY", "07");
        res.put("July", "07");

        res.put("AUGUST", "08");
        res.put("August", "08");
        res.put("AUG", "08");
        res.put("Aug", "08");

        res.put("SEPTEMBER", "09");
        res.put("September", "09");
        res.put("SEPT", "09");
        res.put("Sept", "09");

        res.put("OCTOBER", "10");
        res.put("October", "10");
        res.put("OCT", "10");
        res.put("Oct", "10");

        res.put("NOVEMBER", "11");
        res.put("November", "11");
        res.put("NOV", "11");
        res.put("Nov", "11");

        res.put("DECEMBER", "12");
        res.put("December", "12");
        res.put("DEC", "12");
        res.put("Dec", "12");

        return res;
    }
    private static HashSet<Character> buildStoppingCharsTable() {
        HashSet<Character> stoppingCharsTable = new HashSet<>();

        stoppingCharsTable.add('.');
        stoppingCharsTable.add(',');
        stoppingCharsTable.add('!');
        stoppingCharsTable.add(':');
        stoppingCharsTable.add('"');
        stoppingCharsTable.add('-');
        stoppingCharsTable.add('|');
        stoppingCharsTable.add(']');
        stoppingCharsTable.add('?');
        stoppingCharsTable.add(';');
        stoppingCharsTable.add('\\');
        stoppingCharsTable.add('/');
        stoppingCharsTable.add('>');
        stoppingCharsTable.add('<');
        stoppingCharsTable.add('[');
        stoppingCharsTable.add(']');

        return stoppingCharsTable;
    }
    private static HashMap<String, Double> buildNumbersPostfixTable(){
        HashMap<String, Double> table = new HashMap<>();

        table.put("Thousand", 1000.0);
        table.put("THOUSAND", 1000.0);
        table.put("thousand", 1000.0);
        table.put("K", 1000.0);
        table.put("k", 1000.0);

        table.put("Million", 1000000.0);
        table.put("MILLION", 1000000.0);
        table.put("million", 1000000.0);
        table.put("m", 1000000.0);
        table.put("M", 1000000.0);

        table.put("Billion", 1000000000.0);
        table.put("BILLION", 1000000000.0);
        table.put("billion", 1000000000.0);
        table.put("B", 1000000000.0);
        table.put("b", 1000000000.0);
        table.put("bn", 1000000000.0);
        table.put("Bn", 1000000000.0);
        table.put("BN", 1000000000.0);

        table.put("Trillion", 1000000000000.0);
        table.put("trillion", 1000000000000.0);
        table.put("TRILLION", 1000000000000.0);
        table.put("T", 1000000000000.0);
        table.put("t", 1000000000000.0);
        return table;
    }
    private static HashMap<String, String> buildDistanceTable() {
        HashMap<String, String> table = new HashMap<>();

        table.put("km", "km");
        table.put("KM", "km");
        table.put("Km", "km");
        table.put("cm", "cm");
        table.put("CM", "cm");
        table.put("mm", "mm");
        table.put("MM", "mm");
        table.put("nm", "nm");
        table.put("NM", "nm");

        return table;
    }
    private static HashMap<String, String> buildWightTable() {
        HashMap<String, String> table = new HashMap<>();

        table.put("kg", "kg");
        table.put("KG", "kg");
        table.put("Kg", "kg");
        table.put("g", "g");
        table.put("G", "g");
        table.put("mg", "mg");
        table.put("MG", "mg");

        return table;
    }
    //endregion
}
