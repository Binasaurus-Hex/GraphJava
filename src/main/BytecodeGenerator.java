package main;

import static Bytecode.InstructionSet.*;

import Bytecode.InstructionSet;
import SyntaxNodes.*;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class BytecodeGenerator {

    Map<String, Integer> externals = new HashMap<>();
    Map<String, Boolean> external_returns = new HashMap<>();

    class ProcedureCallLocation {
        String procedure;
        int program_index;
        public ProcedureCallLocation(String procedure, int program_index){
            this.procedure = procedure;
            this.program_index = program_index;
        }
    }
    List<ProcedureCallLocation> function_call_locations = new ArrayList<>();

    class Scope {
        Map<String, Node> types = new HashMap<>();
        Map<String, Integer> locals = new HashMap<>(); // variable name to stack offset
        Map<String, Integer> labels = new HashMap<>(); // function name to program index
        Map<String, ProcedureDeclaration> procedures = new HashMap<>();
    }

    class Context {
        Scope scope = new Scope();
        int stack_offset;
        int constant_offset = 50000;
    }

    int[] generate_bytecode(List<Node> program){

        Context global_context = new Context();
        Scope global_scope = new Scope();
        global_context.scope = global_scope;

        // declare externals
        int external_id = 0;
        for(Method method : Utils.get_external_procedures()){
            String name = Utils.external_name(method.getName());
            externals.put(name, external_id++);
            if(method.getReturnType().getName().equals("void")){
                external_returns.put(name, false);
            }
            else{
                external_returns.put(name, true);
            }
        }

        List<Integer> bytecode = new ArrayList<>();

        Node main_call = program.remove(program.size() - 1);
        generate_bytecode(Collections.singletonList(main_call), bytecode, global_context);
        bytecode.add(PROGRAM_EXIT.code());

        // procedures
        for(Node node : program){
            if(node instanceof ProcedureDeclaration){
                ProcedureDeclaration procedure = (ProcedureDeclaration) node;
                int program_line = bytecode.size();
                global_scope.labels.put(procedure.name, program_line);
                global_scope.procedures.put(procedure.name, procedure);
                Context context = new Context();
                int input_offset = -2;
                for(int i = procedure.inputs.size() - 1; i >= 0; i--){
                    VariableDeclaration declaration = (VariableDeclaration) procedure.inputs.get(i);
                    int size = get_size(declaration.type);
                    input_offset -= size;
                    context.scope.locals.put(declaration.name, input_offset);
                }
                for(int i = procedure.outputs.size() -1; i >= 0; i--){
                    Node type = procedure.outputs.get(i);
                    int size = 0;
                    if(type != null)size = get_size(type);
                    input_offset -= size;
                    context.scope.locals.put(String.format("<-%d", procedure.outputs.size() - i - 1), input_offset);
                }

                bytecode.add(PROCEDURE_HEADER.code());
                context.scope.labels.putAll(global_context.scope.labels); // add all the current functions in
                context.scope.procedures.putAll(global_context.scope.procedures);
                bytecode.add(ALLOCATE.code());
                int allocation_index = bytecode.size();
                bytecode.add(0); // will be overwritten with actual stack size

                generate_bytecode(procedure.block, bytecode, context);
                int stack_size = context.stack_offset;
                bytecode.set(allocation_index, stack_size);

                // failsafe return
                Return return_statement = new Return();
                return_statement.procedure = procedure;
                generate_bytecode(Collections.singletonList(return_statement), bytecode, context);
            }
        }

        // linking
        for(ProcedureCallLocation value: function_call_locations){
            int location = global_context.scope.labels.get(value.procedure);
            bytecode.set(value.program_index, location);
        }

        int[] final_bytecode = new int[bytecode.size()];
        for(int i = 0; i < bytecode.size(); i++){
            final_bytecode[i] = bytecode.get(i);
        }
        return final_bytecode;
    }

    int get_size(Node type){
        if(type instanceof LiteralType){
            return 1;
        }
        if(type instanceof ArrayType){
            ArrayType array = (ArrayType) type;
            return get_size(array.type) * array.size;
        }
        if(type instanceof StructDeclaration){
            StructDeclaration struct = (StructDeclaration) type;
            int total_size = 0;
            for(Node node : struct.body){
                VariableDeclaration field = (VariableDeclaration)node;
                total_size += get_size(field.type);
            }
            return total_size;
        }
        if(type instanceof PointerType || type instanceof Location){
            return 1;
        }
        System.out.println("error cant calculate size of type");
        System.exit(1);
        return -1;
    }

    int get_field_offset(StructDeclaration struct, String field_name){
        if(field_name.equals("velocity")){
            System.out.println();
        }
        int offset = 0;
        for(Node node : struct.body){
            VariableDeclaration field = (VariableDeclaration) node;
            if(field.name.equals(field_name)){
                return offset;
            }
            offset += get_size(field.type);
        }
        System.out.println("couldn't find field " + field_name + " for " + struct.name);
        System.exit(0);
        return -1;
    }

    void generate_bytecode(List<Node> program, List<Integer> bytecode, Context context){
        for(Node node : program){
            if(node instanceof VariableDeclaration){
                VariableDeclaration declaration = (VariableDeclaration) node;
                int size = get_size(declaration.type);
                int memory_address = context.stack_offset;
                context.stack_offset += size;
                context.scope.locals.put(declaration.name, memory_address);
                context.scope.types.put(declaration.name, declaration.type);
            }

            if(node instanceof VariableAssign){
                VariableAssign assign = (VariableAssign) node;
                Node type = context.scope.types.get(assign.variable_name);
                int memory_address = context.scope.locals.get(assign.variable_name);

                // location <- var          e.g. position.x = 3;
                // var <- location          e.g. height = position.x
                // location <- location     e.g. position.x = position.y

                // var <- expression        e.g. time = previous_time
                //                          e.g. time = 3+4
                //                          e.g. time = get_time()

                if(assign.value instanceof Literal){
                    if(type instanceof LiteralType){
                        int value = 0;
                        LiteralType literal_type = (LiteralType)type;
                        if(literal_type.type == null){
                            System.out.println();
                        }
                        switch (literal_type.type){
                            case INT -> {
                                Literal<Integer> int_literal = (Literal<Integer>) assign.value;
                                value = int_literal.value;
                            }
                            case FLOAT -> {
                                Literal<Float> float_literal = (Literal<Float>) assign.value;
                                value = Float.floatToIntBits(float_literal.value.floatValue());
                            }
                            case BOOL -> {
                                Literal<Boolean> bool_literal = (Literal<Boolean>) assign.value;
                                value = bool_literal.value? 1 : 0;
                            }
                        }
                        bytecode.add(ASSIGN_LITERAL.code());
                        bytecode.add(memory_address);
                        bytecode.add(value);
                    }
                    else {
                        Literal<String> literal = (Literal<String>)assign.value;
                        String string = literal.value;
                        StructDeclaration string_type = (StructDeclaration) type;
                        bytecode.add(MEMSET.code());
                        bytecode.add(context.constant_offset);
                        bytecode.add(string.length());
                        for(byte b : string.getBytes(StandardCharsets.UTF_8)){
                            bytecode.add((int)b);
                        }

                        // data
                        int data_offset = get_field_offset(string_type, "data");
                        bytecode.add(ASSIGN_LITERAL.code());
                        bytecode.add(memory_address + data_offset);
                        bytecode.add(context.constant_offset);

                        // length
                        int length_offset = get_field_offset(string_type, "length");
                        bytecode.add(ASSIGN_LITERAL.code());
                        bytecode.add(memory_address + length_offset);
                        bytecode.add(string.length());

                        context.constant_offset += string.length();
                    }
                }
                else if(assign.value instanceof VariableCall){
                    VariableCall variable_call = (VariableCall) assign.value;
                    int size = get_size(variable_call.type);

                    int code = ASSIGN_MEMORY.code();
                    if(assign.location && !(variable_call.type instanceof Location)){
                        code = ASSIGN_VAR_TO_LOCATION.code();
                    }
                    else if(!assign.location && variable_call.type instanceof Location) {
                        code = ASSIGN_VAR_FROM_LOCATION.code();
                        Location location = (Location) variable_call.type;
                        size = get_size(location.type);
                    }
                    else if (assign.top_level && assign.location && variable_call.type instanceof Location){
                        code = ASSIGN_TO_LOCATION_FROM_LOCATION.code();
                        Location location = (Location) variable_call.type;
                        size = get_size(location.type);
                    }

                    bytecode.add(code);
                    bytecode.add(memory_address);
                    bytecode.add(context.scope.locals.get(variable_call.name));
                    if(size > 1){
                        System.out.printf("");
                    }
                    bytecode.add(size);
                }
                else if(assign.value instanceof RawData){
                    RawData raw_data = (RawData) assign.value;
                    bytecode.add(ASSIGN_RAW_DATA.code());
                    bytecode.add(memory_address);
                    bytecode.add(raw_data.data.size());
                    bytecode.addAll(raw_data.data);
                }
                else if(assign.value instanceof BinaryOperator){
                    BinaryOperator operator = (BinaryOperator) assign.value;
                    VariableCall a = (VariableCall)operator.left;
                    VariableCall b = (VariableCall)operator.right;

                    Node value_type = a.type; // assumed to be the same type

                    LiteralType literal_value_type = null;
                    if(value_type instanceof LiteralType){
                        literal_value_type = (LiteralType) value_type;
                    }

                    int mem_a = context.scope.locals.get(a.name);
                    if(b == null){
                        System.out.println();
                    }

                    int mem_b;

                    boolean pointer_value = false;

                    ArrayType array = null;
                    if(operator.operation == BinaryOperator.Operation.INDEX){
                        if(a.type instanceof PointerType){
                            PointerType pointer = (PointerType) a.type;
                            array = (ArrayType) pointer.type;
                            pointer_value = true;
                        }
                        else if(a.type instanceof Location){
                            Location location = (Location) a.type;
                            array = (ArrayType) location.type;
                            pointer_value = true;
                        }
                        else{
                            array = (ArrayType) a.type;
                        }
                    }

                    if(operator.operation == BinaryOperator.Operation.DOT){
                        StructDeclaration struct;
                        if(a.type instanceof PointerType){
                            PointerType pointer = (PointerType) a.type;
                            struct = (StructDeclaration) pointer.type;
                            pointer_value = true;
                        }
                        else if(a.type instanceof Location){
                            Location location = (Location) a.type;
                            struct = (StructDeclaration) location.type;
                            pointer_value = true;
                        }
                        else{
                            struct = (StructDeclaration) a.type;
                        }
                        mem_b = get_field_offset(struct, b.name);
                    }
                    else{
                        mem_b = context.scope.locals.get(b.name);
                    }

                    int code;
                    switch (operator.operation){
                        case LESS_THAN -> {
                            if(literal_value_type.type == LiteralType.Type.INT)code = LESS_THAN.code();
                            else code = FLOAT_LESS_THAN.code();
                        }
                        case GREATER_THAN -> {
                            if(literal_value_type.type == LiteralType.Type.INT)code = GREATER_THAN.code();
                            else code = FLOAT_GREATER_THAN.code();
                        }
                        case AND -> code = AND.code();
                        case OR -> code = OR.code();
                        case EQUALS -> {
                            if(a.type instanceof PointerType && b.type instanceof PointerType ||  literal_value_type.type == LiteralType.Type.INT)code = EQUALS.code();
                            else code = FLOAT_EQUALS.code();
                        }
                        case ADD -> {
                            if(literal_value_type.type == LiteralType.Type.INT) code = ADD.code();
                            else code = FLOAT_ADD.code();
                        }
                        case SUBTRACT -> {
                            if(literal_value_type.type == LiteralType.Type.INT) code = SUBTRACT.code();
                            else code = FLOAT_SUBTRACT.code();
                        }
                        case MULTIPLY -> {
                            if(literal_value_type.type == LiteralType.Type.INT) code = MULTIPLY.code();
                            else code = FLOAT_MULTIPLY.code();
                        }
                        case DIVIDE -> {
                            if(literal_value_type.type == LiteralType.Type.INT) code = DIVIDE.code();
                            else code = FLOAT_DIVIDE.code();
                        }
                        case INDEX -> {
                            code = pointer_value? ARRAY_PTR_LOCATION.code() : ARRAY_LOCATION.code();
                        }
                        case DOT -> {
                            code = pointer_value? STRUCT_PTR_LOCATION.code() : STRUCT_LOCATION.code();
                        }
                        default -> code = -1;
                    };

                    bytecode.add(code);
                    bytecode.add(memory_address);
                    bytecode.add(mem_a);
                    bytecode.add(mem_b);
                    if(array != null){
                        bytecode.add(get_size(array.type));
                    }
                }
                else if(assign.value instanceof UnaryOperator){
                    UnaryOperator operator = (UnaryOperator) assign.value;
                    VariableCall variable = (VariableCall) operator.node;
                    switch (operator.operation){
                        case REFERENCE -> {
                            if(variable.type instanceof Location){
                                bytecode.add(ASSIGN_MEMORY.code());
                                bytecode.add(memory_address);
                                bytecode.add(context.scope.locals.get(variable.name));
                                bytecode.add(1);
                            }
                            else{
                                bytecode.add(ASSIGN_ADDRESS.code());
                                bytecode.add(memory_address);
                                bytecode.add(context.scope.locals.get(variable.name));
                            }
                        }
                        case DEREFERENCE -> {
                            bytecode.add(ASSIGN_DEREFERENCE.code());
                            bytecode.add(memory_address);
                            bytecode.add(context.scope.locals.get(variable.name));
                        }
                    }
                }

                else if(assign.value instanceof ProcedureCall){
                    ProcedureCall proc_call = (ProcedureCall) assign.value;
                    generate_bytecode(Collections.singletonList(proc_call), bytecode, context);
                    bytecode.add(ASSIGN_POP.code());
                    bytecode.add(memory_address);
                    bytecode.add(get_size(proc_call.procedure.outputs.get(0)));
                }
            }

            if(node instanceof ProcedureCall){
                ProcedureCall call = (ProcedureCall)node;

                if(!call.external){
                    ProcedureDeclaration procedure = call.procedure;
                    for(Node output : procedure.outputs){
                        int size = get_size(output);
                        bytecode.add(ALLOCATE.code());
                        bytecode.add(size);
                    }
                }
                else if(external_returns.get(call.name)){
                    bytecode.add(ALLOCATE.code());
                    bytecode.add(1); // we only support single size returns from externals
                }

                for(Node input : call.inputs){
                    VariableCall variable_call = (VariableCall) input; // assumed

                    bytecode.add(PUSH_MEMORY.code());
                    bytecode.add(context.scope.locals.get(variable_call.name));
                    bytecode.add(get_size(variable_call.type));
                }

                if(call.external){
                    bytecode.add(CALL_EXTERNAL.code());
                    bytecode.add(externals.get(call.name));
                }
                else{
                    bytecode.add(CALL_PROCEDURE.code());
                    int location = bytecode.size();
                    function_call_locations.add(new ProcedureCallLocation(call.name, location));
                    bytecode.add(-22);
                }
            }

            if(node instanceof If){
                If if_statement = (If) node;
                VariableCall var_call = (VariableCall) if_statement.condition;

                bytecode.add(JUMP_IF_NOT.code());
                int jump_to_end_index = bytecode.size();
                bytecode.add(-22);
                bytecode.add(context.scope.locals.get(var_call.name));

                generate_bytecode(if_statement.block, bytecode, context);
                bytecode.set(jump_to_end_index, (int) bytecode.size());
            }

            if(node instanceof While){
                While while_statement = (While) node;

                bytecode.add(JUMP.code());

                int jump_condition_index = bytecode.size(); // jump to condition
                bytecode.add(-22);

                int block_label = bytecode.size();

                generate_bytecode(while_statement.block, bytecode, context);

                int condition_label = bytecode.size();
                bytecode.set(jump_condition_index, condition_label);

                generate_bytecode(while_statement.condition_block, bytecode, context);

                bytecode.add(JUMP_IF.code());
                bytecode.add(block_label);

                VariableCall condition_var = (VariableCall) while_statement.condition;
                bytecode.add(context.scope.locals.get(condition_var.name));

                /*

                jmp :condition
 block :        z = x + 2
                print(z)
 condition :    x = 3           <-|
                y = 4             |
                cmp x < y       <-|
                if eq jmp :block
  while end     ...
                 */

            }

            if(node instanceof Return){
                Return return_statement = (Return) node;
                VariableCall value = (VariableCall) return_statement.value;
                if(value != null) {
                    bytecode.add(ASSIGN_MEMORY.code());
                    int return_location = context.scope.locals.get("<-0");
                    bytecode.add(return_location);
                    bytecode.add(context.scope.locals.get(value.name));
                    bytecode.add(get_size(value.type));
                }

                bytecode.add(DEALLOCATE.code());
                bytecode.add(context.stack_offset);

                bytecode.add(RETURN.code());
                int inputs_size = 0;
                for(Node input : return_statement.procedure.inputs){
                    VariableDeclaration decl = (VariableDeclaration)input;
                    inputs_size += get_size(decl.type);
                }
                bytecode.add(inputs_size);
            }
        }
    }
}
