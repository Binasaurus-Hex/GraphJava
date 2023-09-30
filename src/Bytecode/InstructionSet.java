package Bytecode;

public enum InstructionSet {
    ADD,
    SUBTRACT,
    MULTIPLY,
    DIVIDE,
    LESS_THAN,
    EQUALS,
    GREATER_THAN,

    FLOAT_ADD,
    FLOAT_SUBTRACT,
    FLOAT_MULTIPLY,
    FLOAT_DIVIDE,
    FLOAT_LESS_THAN,
    FLOAT_EQUALS,
    FLOAT_GREATER_THAN,

    JUMP_IF,
    JUMP_IF_NOT,

    PROCEDURE_HEADER,
    SET_BASE_PTR,


    JUMP,
    CALL_PROCEDURE,
    RETURN,
    CALL_EXTERNAL,
    ALLOCATE,
    DEALLOCATE,
    ASSIGN_LITERAL,
    ASSIGN_MEMORY,
    ASSIGN_POP,
    ASSIGN_ADDRESS,
    ASSIGN_DEREFERENCE,
    ASSIGN_ARRAY_INDEX,
    ASSIGN_STRUCT_FIELD,
    ASSIGN_PTR_STRUCT_FIELD,
    ASSIGN_POINTER_FROM_STRUCT,

    ASSIGN_POINTER_FROM_STRUCT_PTR,

    ARRAY_ASSIGN,
    STRUCT_FIELD_ASSIGN,
    PTR_STRUCT_FIELD_ASSIGN,
    PUSH_MEMORY;

    public long code(){
        return (long)ordinal();
    }
}
