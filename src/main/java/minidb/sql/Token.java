package minidb.sql;

public record Token(TokenType type, String text, int position) {
    @Override
    public String toString() {
        return type + "('" + text + "')@" + position;
    }
}