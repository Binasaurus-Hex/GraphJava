package SyntaxNodes;

public class LiteralType implements Node {
    public enum Type {
        FLOAT,INT,BOOL,STRUCT
    };
    public Type type;

    public static LiteralType INT(){
        LiteralType literal = new LiteralType();
        literal.type = Type.INT;
        return literal;
    }
}
