import java.util.*;

public abstract class AST {
    public void error(String msg) {
        System.err.println(msg);
        System.exit(-1);
    }
};

/* Expressions are similar to arithmetic expressions in the impl
   language: the atomic expressions are just Signal (similar to
   variables in expressions) and they can be composed to larger
   expressions with And (Conjunction), Or (Disjunction), and
   Not (Negation) */

abstract class Expr extends AST {
    abstract public boolean eval(Environment env);

    abstract public String[] getDependentVariableNames();

    protected String[] getDependentVariableNames(Expr... expressions) {
        ArrayList<String> dependentVariables = new ArrayList<>();
        for (Expr expression : expressions) {
            dependentVariables.addAll(Arrays.asList(expression.getDependentVariableNames()));
        }
        return dependentVariables.toArray(new String[0]);
    }
}

class Conjunction extends Expr {
    Expr e1, e2;

    Conjunction(Expr e1, Expr e2) {
        this.e1 = e1;
        this.e2 = e2;
    }

    public boolean eval(Environment env) {
        return e1.eval(env) && e2.eval(env);
    }

    @Override
    public String[] getDependentVariableNames() {
        return getDependentVariableNames(e1, e2);
    }
}

class Disjunction extends Expr {
    Expr e1, e2;

    Disjunction(Expr e1, Expr e2) {
        this.e1 = e1;
        this.e2 = e2;
    }

    public boolean eval(Environment env) {
        return e1.eval(env) || e2.eval(env);
    }

    @Override
    public String[] getDependentVariableNames() {
        return getDependentVariableNames(e1, e2);
    }
}

class Negation extends Expr {
    Expr e;

    Negation(Expr e) {
        this.e = e;
    }

    public boolean eval(Environment env) {
        return !e.eval(env);
    }

    @Override
    public String[] getDependentVariableNames() {
        return e.getDependentVariableNames();
    }
}

class Signal extends Expr {
    String varname; // a signal is just identified by a name

    Signal(String varname) {
        this.varname = varname;
    }

    public boolean eval(Environment env) {
        if (!env.hasVariable(varname)) {
            error("Variable not defined: " + varname);
        }
        return env.getVariable(varname);
    }

    @Override
    public String[] getDependentVariableNames() {
        String[] subExpressionName = new String[1];
        subExpressionName[0] = varname;
        return subExpressionName;
    }
}

// Latches have an input and output signal

class Latch extends AST {
    String inputname;
    String outputname;

    Latch(String inputname, String outputname) {
        this.inputname = inputname;
        this.outputname = outputname;
    }

    public void initialize(Environment env) {
        env.setVariable(outputname, false);
    }

    public void nextCycle(Environment env) {
        env.setVariable(outputname, env.getVariable(inputname));
    }
}

// An Update is any of the lines " signal = expression "
// in the .update section

class Update extends AST {
    String name;
    Expr e;

    Update(String name, Expr e) {
        this.e = e;
        this.name = name;
    }

    public void eval(Environment env) {
        env.setVariable(name, e.eval(env));
    }
}

/* A Trace is a signal and an array of Booleans, for instance each
   line of the .simulate section that specifies the traces for the
   input signals of the circuit. It is suggested to use this class
   also for the output signals of the circuit in the second
   assignment.
*/

class Trace extends AST {
    String signal;
    Boolean[] values;
    int length;

    Trace(String signal, Boolean[] values) {
        this.signal = signal;
        this.values = values;
        this.length = values.length;
    }

    Trace(String signal, int valueCount) {
        this(signal, new Boolean[valueCount]);
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        for (Boolean value : values) {
            result.append(value ? "1" : "0");
        }
        return result + " " + signal;
    }

    public void setValue(boolean value, int time) {
        values[time] = value;
    }

    public boolean getValue(int time) {
        return values[time];
    }
}

/* The main data structure of this simulator: the entire circuit with
   its inputs, outputs, latches, and updates. Additionally for each
   input signal, it has a Trace as simulation input. 
   
   There are two variables that are not part of the abstract syntax
   and thus not initialized by the constructor (so far): simoutputs
   and simlength. It is suggested to use them for assignment 2 to
   implement the interpreter:
 
   1. to have simlength as the length of the traces in siminputs. (The
   simulator should check they have all the same length and stop with
   an error otherwise.) Now simlength is the number of simulation
   cycles the interpreter should run.

   2. to store in simoutputs the value of the output signals in each
   simulation cycle, so they can be displayed at the end. These traces
   should also finally have the length simlength.
*/

class Circuit extends AST {
    String name;
    List<String> inputNames;
    List<String> outputNames;
    List<Latch> latches;
    List<String> latchOutputNames;
    List<Update> updates;
    List<String> updateNames;
    List<Trace> inputTraces;
    HashMap<String, Trace> outputNameToTrace;
    int simLength;

    List<String> legalUpdateVariables;

    Environment env;

    Circuit(String name,
            List<String> inputNames,
            List<String> outputNames,
            List<Latch> latches,
            List<Update> updates,
            List<Trace> inputTraces) {
        this.name = name;
        this.inputNames = inputNames;
        this.outputNames = outputNames;
        this.latches = latches;
        this.updates = updates;
        this.updateNames = new ArrayList<>();
        updates.forEach(update -> updateNames.add(update.name));
        this.inputTraces = inputTraces;
        this.latchOutputNames = new ArrayList<>();
        latches.forEach(latch -> latchOutputNames.add(latch.outputname));
        this.legalUpdateVariables = new ArrayList<>();
        legalUpdateVariables.addAll(inputNames);
        legalUpdateVariables.addAll(latchOutputNames);

        this.simLength = this.inputTraces.get(0).length;
        for (int i = 1; i < inputTraces.size(); i++) {
            if (inputTraces.get(i).length != simLength) {
                error("All inputs must be same length");
            }
        }

        HashSet<String> uniqueSignalNames = new HashSet<>();
        uniqueSignalNames.addAll(inputNames);
        uniqueSignalNames.addAll(latchOutputNames);
        uniqueSignalNames.addAll(updateNames);

        if (uniqueSignalNames.size() != (inputNames.size() + latches.size() + updates.size())) {
            error("A signal must be precisely 1 of the following:\n- Input signal\n- Output of a latch\n- Output of an update");
        }

        this.outputNameToTrace = new HashMap<>();
        for (String outputName : outputNames) {
            outputNameToTrace.put(outputName, new Trace(outputName, simLength));
        }
    }

    public void initialize() {
        updateInputs(0);
        for (Latch latch : latches) {
            latch.initialize(env);
        }

        performUpdates(0);
    }

    public void nextCycle(int time) {
        updateInputs(time);
        for (Latch latch : latches) {
            latch.nextCycle(env);
        }

        performUpdates(time);
    }

    private void performUpdates(int time) {
        HashSet<String> legalVariables = new HashSet<>(this.legalUpdateVariables);

        for (Update update : updates) {
            String[] rightHandNames = update.e.getDependentVariableNames();
            if (!legalVariables.containsAll(Arrays.stream(rightHandNames).toList())) {
                error("Variable " + update.name + " may be cyclical");
            }
            update.eval(env);

            String name = update.name;
            if (outputNames.contains(name)) {
                boolean newValue = env.getVariable(name);
                outputNameToTrace.get(name).setValue(newValue, time);
            }

            legalVariables.add(update.name);
        }
    }

    private void updateInputs(int time) {
        for (int i = 0; i < inputNames.size(); i++) {
            String inputName = inputNames.get(i);
            Trace inputTrace = inputTraces.get(i);

            env.setVariable(inputName, inputTrace.getValue(time));
        }
    }

    public void runSimulator() {
        env = new Environment();

        initialize();
        for (int i = 1; i < simLength; i++) {
            nextCycle(i);
        }

        inputTraces.forEach(System.out::println);
        outputNameToTrace.values().forEach(System.out::println);
    }
}
