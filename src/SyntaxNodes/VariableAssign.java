package SyntaxNodes;

public class VariableAssign implements Node {
    public String variable_name;
    public Node value;
    public boolean location;
    public boolean top_level;
}
