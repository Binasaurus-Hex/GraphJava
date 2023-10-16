package SyntaxNodes;

import java.util.List;

public class ProcedureCall implements Node {
    public String name;
    public List<Node> inputs;
    public boolean external;

    public ProcedureDeclaration procedure = null; // filled in later
}
