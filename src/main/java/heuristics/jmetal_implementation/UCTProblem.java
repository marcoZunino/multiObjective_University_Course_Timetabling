package heuristics.jmetal_implementation;

import org.uma.jmetal.problem.Problem;

import heuristics.UCTInstance;


public class UCTProblem implements Problem<EncodedSolution> {

    public UCTInstance instance;


    public UCTProblem(UCTInstance instance) {
        this.instance = instance;
        EncodedSolution.setInstance(instance);
        // this.numberOfObjectives = ...
        // this.numberOfConstraints = ...
    }

    public int numberOfVariables() {
        return instance.courses.size();
    }

    public int numberOfObjectives() {
        return 2; // Example: two objectives
    }

    public int numberOfConstraints() {
        return 0; // Example: two objectives
    }

    public String name() {
        return "UniversityCourseTimetablingProblem";
    }

    @Override
    public EncodedSolution evaluate(EncodedSolution solution) {
        // // Implement the evaluation logic here
        // // Placeholder implementation
        // int var0 = solution.getVariableValue(0);

        solution.evaluatePreferences();
        // double objective2 = solution.evaluateMinMaxDays();
        // ...

        // solution.setObjective(0, objective1);
        // solution.setObjective(1, objective2);
        return solution;
    }

    @Override
    public EncodedSolution createSolution() {
        return new EncodedSolution(numberOfObjectives(), numberOfConstraints());
    }
    
}
