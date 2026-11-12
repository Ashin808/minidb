package minidb.sql;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum TokenType {
    // Keywords
    CREATE, TABLE, INDEX, ON, INSERT, INTO, VALUES,
    SELECT, FROM, WHERE, DELETE, AND, OR, INT, VARCHAR,

    // Literals and names
    IDENTIFIER, INT_LITERAL, STRING_LITERAL,

    // Symbols
    COMMA, LPAREN, RPAREN, STAR, SEMICOLON,
    EQ, NEQ, LT, LTE, GT, GTE,

    // End of input
    EOF;

    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();

    static {
        TokenType[] keywords = {
                CREATE, TABLE, INDEX, ON, INSERT, INTO, VALUES,
                SELECT, FROM, WHERE, DELETE, AND, OR, INT, VARCHAR
        };
        for (TokenType t : keywords) {
            KEYWORDS.put(t.name(), t);
        }
    }

    /** Returns the keyword type for a word (case-insensitive), or null if it's not a keyword. */
    public static TokenType keywordOrNull(String word) {
        return KEYWORDS.get(word.toUpperCase(Locale.ROOT));
    }
}