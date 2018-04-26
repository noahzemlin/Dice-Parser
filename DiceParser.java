import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class DiceParser {

    private static SecureRandom random = new SecureRandom();

    private static DiceToken currentToken;
    private static DiceToken lookahead;

    private static ArrayList<ArrayList<Integer>> diceRolls = new ArrayList<ArrayList<Integer>>();

    private static ArrayList<DiceToken> tokens = new ArrayList<DiceToken>();
    private static int currentTokenIndex;

    static int parse(String str) throws DiceParserException {

        tokens.clear();
        diceRolls.clear();

        str = str.toLowerCase();

        Pattern p = Pattern.compile("([0-9]+|[a-z]+|\\+|-|\\*|/|\\(|\\))");
        Matcher m = p.matcher(str);

        while (m.find()) {
            tokens.add(new DiceToken(m.group(1)));
        }

        currentTokenIndex = 0;
        currentToken = tokens.get(currentTokenIndex);

        if (tokens.size() >= 2) {
            lookahead = tokens.get(currentTokenIndex + 1);
        } else {
            lookahead = null;
        }

        return expression();

    }

    private static void match(DiceTokenType type) throws DiceParserException {

        if (currentToken != null && currentToken.type == type) {
            consume();
        } else if (currentToken != null) {
            throw new DiceParserException("Expected " + type.name() + ", got " + currentToken.data);
        } else {
            throw new DiceParserException("Expected " + type.name() + ", got null");
        }
    }

    private static void consume() throws DiceParserException {
        currentTokenIndex++;
        if (tokens.size() > currentTokenIndex){
            currentToken = tokens.get(currentTokenIndex);
            if (tokens.size() > currentTokenIndex + 1) {
                lookahead = tokens.get(currentTokenIndex + 1);
            } else {
                lookahead = null;
            }
        }
        else {
            currentToken = null;
        }
    }

    private static int expression() throws DiceParserException{
        return termTail(term());
    }

    private static int termTail(int term) throws DiceParserException{

        if (currentToken == null) {
            return term;
        }

        if (currentToken.type == DiceTokenType.rparen) {
            match(DiceTokenType.rparen);
            return term;
        }

        if (currentToken.type == DiceTokenType.plus) {
            match(DiceTokenType.plus);
            return term + termTail(term());
        }

        if (currentToken.type == DiceTokenType.minus) {
            match(DiceTokenType.minus);
            return term - termTail(term());
        }

        throw new DiceParserException("Expected termTail, got " + currentToken.data);
    }

    private static int term() throws DiceParserException {
        if (currentToken.type == DiceTokenType.lparen || currentToken.type == DiceTokenType.dollar ||currentToken.type == DiceTokenType.constant || currentToken.type == DiceTokenType.d)
            return factorTail(factor());

        throw new DiceParserException("Expected term, got " + currentToken.data);
    }

    private static int factor() throws DiceParserException{

        if (currentToken == null) {
            throw new DiceParserException("Expected factor, got null");
        }

        if (currentToken.type == DiceTokenType.minus) {
            match(DiceTokenType.minus);
            return factor();
        }

        if (currentToken.type == DiceTokenType.lparen) {
            match(DiceTokenType.lparen);
            return expression();
        }

        if (currentToken.type == DiceTokenType.d) {
            return roll();
        }

        if (currentToken.type == DiceTokenType.constant) {

            if (lookahead != null && lookahead.type == DiceTokenType.d)
                return roll();

            DiceToken token = currentToken;
            match(DiceTokenType.constant);
            return Integer.parseInt(token.data);
        }

        throw new DiceParserException("Expected factor, got " + currentToken.data);
    }

    private static int factorTail(int factor) throws DiceParserException{

        if (currentToken == null || currentToken.type == DiceTokenType.plus || currentToken.type == DiceTokenType.minus || currentToken.type == DiceTokenType.rparen) {
            return factor;
        }

        if (currentToken.type == DiceTokenType.times) {
            match(DiceTokenType.times);
            return factor * factorTail(factor());
        }

        if (currentToken.type == DiceTokenType.div) {
            match(DiceTokenType.div);
            return factor / factorTail(factor());
        }

        throw new DiceParserException("Expected factorTail, got " + currentToken.data);
    }

    private static int roll() throws DiceParserException{
        if (currentToken.type == DiceTokenType.constant) {
            DiceToken token1 = currentToken;
            match(DiceTokenType.constant);
            match(DiceTokenType.d);
            DiceToken token2 = currentToken;
            match(DiceTokenType.constant);

            ArrayList<Integer> rolls = modTail(rollDice(Integer.parseInt(token1.data), Integer.parseInt(token2.data)));

            int sum = 0;

            for (int roll : rolls) {
                sum += roll;
            }

            return sum;
        }

        if (currentToken.type == DiceTokenType.d) {
            match(DiceTokenType.d);
            DiceToken token2 = currentToken;
            match(DiceTokenType.constant);

            ArrayList<Integer> rolls = modTail(rollDice(1, Integer.parseInt(token2.data)));

            int sum = 0;

            for (int roll : rolls) {
                sum += roll;
            }

            return sum;
        }

        throw new DiceParserException("Expected roll, got " + currentToken.data);
    }

    private static ArrayList<Integer> rollDice(int num, int type) throws DiceParserException {

        if (type <= 0) {
            throw new DiceParserException("Can not roll a dice with 0 or fewer faces");
        }

        if (num <= 0) {
            throw new DiceParserException("Can not roll 0 or fewer dice");
        }

        ArrayList<Integer> ret = new ArrayList<Integer>();
        for (int i = 0; i < num; i++) {
            ret.add(random.nextInt(type) + 1);
        }
        diceRolls.add(ret);
        return ret;
    }

    private static ArrayList<Integer> modTail(ArrayList<Integer> dice) throws DiceParserException{
        if (currentToken == null) {
            return dice;
        }

        if (currentToken.type == DiceTokenType.d || currentToken.type == DiceTokenType.t) {
            return modTail(mod(dice));
        }
        if (currentToken.type == DiceTokenType.plus || currentToken.type == DiceTokenType.minus || currentToken.type == DiceTokenType.times || currentToken.type == DiceTokenType.div || currentToken.type == DiceTokenType.rparen) {
            return dice;
        }

        throw new DiceParserException("Expected modTail, got " + currentToken.data);
    }

    private static ArrayList<Integer> mod(ArrayList<Integer> dice) throws DiceParserException{

        dice = (ArrayList<Integer>)dice.clone();

        if (currentToken.type == DiceTokenType.d) {

            match(DiceTokenType.d);

            int toDrop;

            if (currentToken != null) {
                toDrop = Integer.parseInt(currentToken.data);
            } else {
                throw new DiceParserException("No parameter given for drop");
            }

            Collections.sort(dice);

            for (int i = 0; i < toDrop; i++) {
                dice.remove(0);
            }

            match(DiceTokenType.constant);

            return dice;
        }

        if (currentToken.type == DiceTokenType.t) {

            match(DiceTokenType.t);

            int toDrop;

            if (currentToken != null) {
                toDrop = dice.size() - Integer.parseInt(currentToken.data);
            } else {
                throw new DiceParserException("No parameter given for top");
            }

            Collections.sort(dice);

            for (int i = 0; i < toDrop; i++) {
                dice.remove(0);
            }

            match(DiceTokenType.constant);

            return dice;
        }

        throw new DiceParserException("Expected mod, got " + currentToken.data);
    }

    static ArrayList<ArrayList<Integer>> getRolls() {
        return diceRolls;
    }
}

enum DiceTokenType {plus, minus, times, div, constant, lparen, rparen, dollar, d, t, unknown};

class DiceToken {
    DiceTokenType type;
    String data;

    DiceToken(String in) {
        data = in;

        if (in.equals("+")) {
            type = DiceTokenType.plus;
        } else if (in.equals("-")) {
            type = DiceTokenType.minus;
        } else if (in.equals("*")) {
            type = DiceTokenType.times;
        } else if (in.equals("/")) {
            type = DiceTokenType.div;
        } else if (in.equals("(")) {
            type = DiceTokenType.lparen;
        } else if (in.equals(")")) {
            type = DiceTokenType.rparen;
        } else if (in.equals("d")) {
            type = DiceTokenType.d;
        } else if (in.equals("t")) {
            type = DiceTokenType.t;
        } else if (in.matches("[0-9]{1,}")) {
            type = DiceTokenType.constant;
        } else {
            type = DiceTokenType.unknown;
        }
    }
}

class DiceParserException extends Exception{

    private String message;

    DiceParserException() {
        this("Generic dice parse error");
    }

    DiceParserException(String msg) {
        message = msg;
    }

    @Override
    public String getMessage() {
        return message;
    }
}