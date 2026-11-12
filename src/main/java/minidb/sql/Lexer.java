package minidb.sql;

import java.util.ArrayList;
import java.util.List;

public class Lexer {
    private final String input;
    private int pos = 0;

    public Lexer(String input) {
        this.input = input;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            skipWhitespace();
            if (pos >= input.length()) {
                tokens.add(new Token(TokenType.EOF, "", pos));
                return tokens;
            }
            tokens.add(nextToken());
        }
    }

    private void skipWhitespace() {
        while (pos < input.length() && Character.isWhitespace(input.charAt(pos))) {
            pos++;
        }
    }

    private Token nextToken() {
        char c = input.charAt(pos);
        int start = pos;

        if (isWordStart(c)) return readWord();
        if (isDigit(c) || (c == '-' && pos + 1 < input.length() && isDigit(input.charAt(pos + 1)))) {
            return readNumber();
        }
        if (c == '\'') return readString();

        pos++; // consume the symbol character
        return switch (c) {
            case ',' -> new Token(TokenType.COMMA, ",", start);
            case '(' -> new Token(TokenType.LPAREN, "(", start);
            case ')' -> new Token(TokenType.RPAREN, ")", start);
            case '*' -> new Token(TokenType.STAR, "*", start);
            case ';' -> new Token(TokenType.SEMICOLON, ";", start);
            case '=' -> new Token(TokenType.EQ, "=", start);
            case '!' -> {
                if (match('=')) yield new Token(TokenType.NEQ, "!=", start);
                throw new SqlException("Unexpected character '!' at position " + start);
            }
            case '<' -> {
                if (match('=')) yield new Token(TokenType.LTE, "<=", start);
                if (match('>')) yield new Token(TokenType.NEQ, "<>", start);
                yield new Token(TokenType.LT, "<", start);
            }
            case '>' -> {
                if (match('=')) yield new Token(TokenType.GTE, ">=", start);
                yield new Token(TokenType.GT, ">", start);
            }
            default -> throw new SqlException("Unexpected character '" + c + "' at position " + start);
        };
    }

    private Token readWord() {
        int start = pos;
        while (pos < input.length() && isWordPart(input.charAt(pos))) {
            pos++;
        }
        String text = input.substring(start, pos);
        TokenType keyword = TokenType.keywordOrNull(text);
        return new Token(keyword != null ? keyword : TokenType.IDENTIFIER, text, start);
    }

    private Token readNumber() {
        int start = pos;
        if (input.charAt(pos) == '-') pos++;
        while (pos < input.length() && isDigit(input.charAt(pos))) {
            pos++;
        }
        String text = input.substring(start, pos);
        try {
            Integer.parseInt(text); // INT is 4 bytes, so reject anything that doesn't fit
        } catch (NumberFormatException e) {
            throw new SqlException("Number out of INT range at position " + start + ": " + text);
        }
        return new Token(TokenType.INT_LITERAL, text, start);
    }

    private Token readString() {
        int start = pos;
        pos++; // skip opening quote
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= input.length()) {
                throw new SqlException("Unterminated string starting at position " + start);
            }
            char c = input.charAt(pos);
            if (c == '\'') {
                if (pos + 1 < input.length() && input.charAt(pos + 1) == '\'') {
                    sb.append('\'');   // '' inside a string means one quote character
                    pos += 2;
                } else {
                    pos++;             // closing quote
                    break;
                }
            } else {
                sb.append(c);
                pos++;
            }
        }
        return new Token(TokenType.STRING_LITERAL, sb.toString(), start);
    }

    /** If the next character is `expected`, consume it and return true. */
    private boolean match(char expected) {
        if (pos < input.length() && input.charAt(pos) == expected) {
            pos++;
            return true;
        }
        return false;
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private static boolean isWordStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isWordPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}