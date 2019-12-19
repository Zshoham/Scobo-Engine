package parser;

/**
 * Extension of the Expression class
 * <br>
 * holds also the numeric value of the number
 */
public class NumberExpression extends Expression {

    private double expressionValue; //numeric value of the number expression

    //region constructors
    public NumberExpression(int startIndex, int endIndex, String expression, String doc) {
        super(startIndex, endIndex, expression, doc);
        expressionValue = translateValue();
    }
    public NumberExpression(Expression expression) {
        this(expression.getStartIndex(), expression.getEndIndex(), expression.getExpression(), expression.getDoc());
    }
    //endregion

    public double getValue() {
        return expressionValue;
    }

    //region fractions

    /**
     * Check if number is part of mixed number
     * mixed number is: number numerator/denominator
     * @return true if numberExpression is part of bigger numeric expression
     */
    protected boolean isPartOfMixedNumber() {
        if(this.isNumerator()) return true;
        else if(this.isDenominator()) return true;
        else if(this.isFullNumberOfMixedNumber()) return true;
        return false;
    }
    /**
     * Check if number is the numerator of a mixed number expresion
     * @return true if number is a numerator
     */
    protected boolean isNumerator() {
        String nextExpression = this.getNextExpression().getExpression();
        if(nextExpression.equals("/")) return false;
        int slashIndex = nextExpression.indexOf("/");
        if(slashIndex == 0){
            int endSubStr = nextExpression.length();

            String potentialDenominatorStr = nextExpression.substring(1, endSubStr);
            if(isNumberExpression(potentialDenominatorStr))
                return true;
        }
        return false;
    }
    /**
     * Check if number is the denominator of a mixed number expresion
     * @return true if number is a denominator
     */
    protected boolean isDenominator() {
        //for prev to be a numerator- it should be number/
        String prevExp = this.getPrevExpression().getExpression();
        if(prevExp.length() == 0)
            return false;

        String potentialNumeratorStr = prevExp;
        int slashIndex = potentialNumeratorStr.indexOf("/");
        if(slashIndex == potentialNumeratorStr.length() - 1){
            potentialNumeratorStr = potentialNumeratorStr.substring(0, slashIndex); // remove / char
            if(isNumberExpression(potentialNumeratorStr))
                return true;
        }
        return false;
    }
    /**
     * Check if number is the the full number part of a mixed number expresion
     */
    protected boolean isFullNumberOfMixedNumber(){
        Expression nextExpression = this.getNextExpression();
        String nextExpressionStr = nextExpression.getExpression(); //Should be fraction: numerator/denominator
        int slashIndex = nextExpressionStr.indexOf("/");

        if(slashIndex > 0){
            String potentialNumeratorStr = nextExpressionStr.substring(0, slashIndex);
            int potentialNumeratorStart = nextExpression.getStartIndex();
            int potentialNumeratorEnd = potentialNumeratorStart + slashIndex;
            Expression potentialNumerator = new Expression(potentialNumeratorStart, potentialNumeratorEnd, potentialNumeratorStr, this.getDoc());
            if(isNumberExpression(potentialNumerator))
                if((new NumberExpression(potentialNumerator)).isNumerator())
                    return true;
        }
        return false;
    }
    //endregion

    //translate the value of the numeric string to number
    private double translateValue() {
        return translateValue(this);
    }

    @Override
    public String toString() {
        return getNumberString(this.getValue());
    }

    //region static functions

    /**
     * Translate the numeric expression to true number value,
     * in order to assign the value to the number expression object
     * @param exp number expression
     * @return double value of the expression
     */
    public static double translateValue(NumberExpression exp){
        StringBuilder number = new StringBuilder();
        String strExp = exp.getExpression();

        int index;
        for(index = 0; index < strExp.length() && strExp.charAt(index) != ' ' ; index++) //run over all the number until space
            if(strExp.charAt(index) != ',') //remove commas for calculation
                number.append(strExp.charAt(index));

        double value = Double.parseDouble(number.toString()); //value of the full number

        if (index < strExp.length() && strExp.charAt(index) == ' ') { //if the number is mixed number
            index++;
            int slashIndex = strExp.indexOf("/");
            double numerator = Double.parseDouble(strExp.substring(index, slashIndex)); //numerator numeric value
            //TODO: index = slashIndex + 1;?

            //TODO: double denominator = new NumberExpression(0,0, strExp.substring(index), exp.getDoc()).getValue();?
            double denominator = Double.parseDouble(strExp.substring(slashIndex + 1));

            value += numerator / denominator;
        }

        //calculate the real number value, with the postfix addition
        if(exp.getNextExpression().isPostfixExpression())
            value *= numbersPostfixTable.get(exp.getNextExpression().getExpression());
        return value;
    }

    /**
     * Check if Expression is numeric expression
     * @param exp Expression to check
     * @return true if the given expression is numeric
     */
    public static boolean isNumberExpression(Expression exp) {
        return isNumberExpression(exp.getExpression());
    }
    /**
     * Check if string is numeric expression
     * @param strExp String expression to check
     * @return true if the given expression is numeric
     */
    public static boolean isNumberExpression(String strExp){
        if(strExp.length() == 0 || !Character.isDigit(strExp.charAt(0)))
            return false;
        int countPoints = 0;

        for (int i = 0; i < strExp.length(); i++) {
            if (!Character.isDigit(strExp.charAt(i)) && strExp.charAt(i) != ',' && strExp.charAt(i) != '.')
                return false;
            if(strExp.charAt(i) == '.')
                countPoints++;
        }
        if(countPoints > 1) //number cant have more than two decimal points
            return false;
        return true;
    }

    /**
     * Join fullNumber (if exist), numerator and denominator to one mixed number expression<br>
     *     if no full number exist- only fraction, the number expression returned will be: 0 numerator/denominator
     * @param numerator number expression checked to be legitimate numerator
     * @return NumberExpression of the mixed number
     */
    public static NumberExpression createMixedNumber(NumberExpression numerator){
        int fullExpStartIndex = numerator.getStartIndex();
        int fullExpEndIndex = numerator.getNextExpression().getEndIndex();
        StringBuilder fullExp = new StringBuilder();
        String fullNumber = "0";
        Expression potentialFullNumber = numerator.getNextExpression();
        if(NumberExpression.isNumberExpression(potentialFullNumber)){
            fullExpStartIndex = potentialFullNumber.getStartIndex();
            fullNumber = potentialFullNumber.getExpression();
        }
        Expression potentialDenominator = numerator.getNextExpression();

        int endSubStr = potentialDenominator.getExpression().length();

        String potentialDenominatorStr = potentialDenominator.getExpression().substring(0, endSubStr);
        fullExp.append(fullNumber).append(" ").append(numerator.getExpression()).
                append(potentialDenominatorStr);
        return new NumberExpression(fullExpStartIndex, fullExpEndIndex, fullExp.toString(), numerator.getDoc());
    }

    /**
     * return string of the number expression, according to the rules-
     * show only maximum three decimal numbers, if exist
     * @param number plain double number
     * @return String value of the number
     */
    public static String getNumberString(double number){
        StringBuilder numberStr = new StringBuilder();
        String num = Double.toString(number);
        int fPointIndex = -1;
        int eIndex = -1;
        int power = 0;

        for (int i = 0; i < num.length() && eIndex == -1; i++) {
            if(Character.isDigit(num.charAt(i)))
                numberStr.append(num.charAt(i));
            else if(num.charAt(i) == '.')
                fPointIndex = i;
            else if(num.charAt(i) == 'E')
                eIndex = i;
        }
        if(fPointIndex == -1)
            fPointIndex = numberStr.toString().length();
        if(eIndex != -1 )
            power = Integer.parseInt(num.substring(eIndex + 1));

        fPointIndex += power;
        if(fPointIndex > numberStr.toString().length())
            for (int i = numberStr.toString().length(); i < fPointIndex; i++)
                numberStr.append('0');
        else if(fPointIndex < 0) {
            for (; fPointIndex < 1; fPointIndex++)
                numberStr.insert(0, '0');
        }
        numberStr.insert(fPointIndex, '.');
        numberStr.append("000");

        numberStr.delete(fPointIndex + 4, numberStr.toString().length());

        for (int i = 0; i < 4 ; i++) {
            if(numberStr.charAt(numberStr.length() - 1) == '0')
                numberStr.deleteCharAt(numberStr.length() - 1);
            else if(numberStr.charAt(numberStr.length() - 1) == '.') {
                numberStr.deleteCharAt(numberStr.length() - 1);
                break;
            }
            else break;
        }
        return numberStr.toString();
    }
    //endregion

}