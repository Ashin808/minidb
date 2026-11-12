package minidb.sql;

public enum Operator {
    EQ("="), NEQ("!="), LT("<"), LTE("<="), GT(">"), GTE(">=");

    private final String symbol;

    Operator(String symbol) {
        this.symbol = symbol;
    }

    public String symbol() {
        return symbol;
    }

    /** Maps a comparison token to its operator, or null if the token isn't a comparison. */
    public static Operator fromToken(TokenType type) {
        return switch (type) {
            case EQ -> EQ;
            case NEQ -> NEQ;
            case LT -> LT;
            case LTE -> LTE;
            case GT -> GT;
            case GTE -> GTE;
            default -> null;
        };
    }
}