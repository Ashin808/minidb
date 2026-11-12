package minidb.demo;

import minidb.sql.Token;
import minidb.sql.TokenType;

public class TokenDemo {
    public static void main(String[] args) {
        System.out.println(TokenType.keywordOrNull("select"));  // SELECT
        System.out.println(TokenType.keywordOrNull("SeLeCt"));  // SELECT
        System.out.println(TokenType.keywordOrNull("users"));   // null
        System.out.println(TokenType.keywordOrNull("varchar")); // VARCHAR

        System.out.println(new Token(TokenType.SELECT, "select", 0));
        System.out.println(new Token(TokenType.IDENTIFIER, "users", 14));
        System.out.println(new Token(TokenType.INT_LITERAL, "42", 30));
    }
}