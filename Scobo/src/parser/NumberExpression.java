package parser;

//TODO: change name to NumericExpression
public class NumberExpression extends Expression {

    private double expressionValue;

    public NumberExpression(int startIndex, int endIndex, String expression, String doc) {
        super(startIndex, endIndex, expression, doc);
        expressionValue = translateValue();
    }
    public NumberExpression(Expression expression) {
        this(expression.getStartIndex(), expression.getEndIndex(), expression.getExpression(), expression.getDoc());
    }

    public double getValue() {
        return expressionValue;
    }

    public boolean isPartOfFraction() {
        if(this.isNumerator()) return true;
        else if(this.isDenominator()) return true;
        else if(this.isFullNumberOfMixedNumber()) return true;
        return false;
    }
    public boolean isNumerator() {
        String nextExpression = this.getNextExpression().getExpression();
        if(nextExpression.equals("/")) return false;
        int slashIndex = nextExpression.indexOf("/");
        if(slashIndex == 0){
            int endSubStr = nextExpression.length();
            if(nextExpression.charAt(nextExpression.length() - 1) == '.' ||
                    nextExpression.charAt(nextExpression.length() - 1) == ',' ||
                    nextExpression.charAt(nextExpression.length() - 1) == '!' ||
                    nextExpression.charAt(nextExpression.length() - 1) == ':' ||
                    nextExpression.charAt(nextExpression.length() - 1) == '\"' ||
                    nextExpression.charAt(nextExpression.length() - 1) == '-' ||
                    nextExpression.charAt(nextExpression.length() - 1) == '%' ||
                    nextExpression.charAt(nextExpression.length() - 1) == '$')
                endSubStr--;
            String potentialDenominatorStr = nextExpression.substring(1, endSubStr);
            if(isNumberExpression(potentialDenominatorStr))
                return true;
        }
        return false;
    }
    private boolean isDenominator() {
        String prevExpression = this.getPrevExpression().getExpression();
        int slashIndex = prevExpression.indexOf("/");
        if(!prevExpression.equals("") && slashIndex == prevExpression.length() - 1){
            String potentialDenominatorStr = prevExpression.substring(0, slashIndex);
            if(isNumberExpression(potentialDenominatorStr))
                return true;
        }
        return false;
    }
    private boolean isFullNumberOfMixedNumber(){
        Expression nextExpression = this.getNextExpression();
        String nextExpressionStr = nextExpression.getExpression();
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


    private double translateValue() {
        return translateValue(this);

    }

    @Override
    public String toString() {
        return getNumberString(this.getValue());
    }

    public static double translateValue(NumberExpression exp){
        StringBuilder number = new StringBuilder();
        String strExp = exp.getExpression();

        int index;
        for(index = 0; index < strExp.length() && strExp.charAt(index) != ' ' ; index++)
            if(strExp.charAt(index) != ',')
                number.append(strExp.charAt(index));

        double value = Double.parseDouble(number.toString());

        if (index < strExp.length() && strExp.charAt(index) == ' ') {
            index++;
            double numerator = Double.parseDouble(strExp.substring(index, strExp.indexOf("/")));
            index = strExp.indexOf("/") + 1;


            double denominator = new NumberExpression(0,0, strExp.substring(index), exp.getDoc()).getValue();

            value += numerator / denominator;
        }

        if(exp.getNextExpression().isPostfixExpression())
            value *= numbersPostfixTable.get(exp.getNextExpression().getExpression());
        return value;
    }
    public static boolean isNumberExpression(Expression exp) {
        return isNumberExpression(exp.getExpression());
    }
    public static boolean isNumberExpression(String strExp){
        if(strExp.length() == 0 || !Character.isDigit(strExp.charAt(0)))
            return false;
        int countPoints = 0;

        for (int i = 0; i < strExp.length(); i++) {
            char c = strExp.charAt(i);
            if (!Character.isDigit(strExp.charAt(i)) && strExp.charAt(i) != ',' && strExp.charAt(i) != '.')
                return false;
            if(strExp.charAt(i) == '.')
                countPoints++;
        }
        if(countPoints > 1)
            return false;
        return true;
    }

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
        if(potentialDenominator.getExpression().charAt(potentialDenominator.getExpression().length() - 1) == '.' ||
                potentialDenominator.getExpression().charAt(potentialDenominator.getExpression().length() - 1) == ',' ||
                potentialDenominator.getExpression().charAt(potentialDenominator.getExpression().length() - 1) == '!' ||
                potentialDenominator.getExpression().charAt(potentialDenominator.getExpression().length() - 1) == ':' ||
                potentialDenominator.getExpression().charAt(potentialDenominator.getExpression().length() - 1) == '\"' ||
                potentialDenominator.getExpression().charAt(potentialDenominator.getExpression().length() - 1) == '-' ||
                potentialDenominator.getExpression().charAt(potentialDenominator.getExpression().length() - 1) == '%' ||
                potentialDenominator.getExpression().charAt(potentialDenominator.getExpression().length() - 1) == '$')
            endSubStr--;
        String potentialDenominatorStr = potentialDenominator.getExpression().substring(0, endSubStr);
        fullExp.append(fullNumber).append(" ").append(numerator.getExpression()).
                append(potentialDenominatorStr);
        return new NumberExpression(fullExpStartIndex, fullExpEndIndex, fullExp.toString(), numerator.getDoc());
    }
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

}