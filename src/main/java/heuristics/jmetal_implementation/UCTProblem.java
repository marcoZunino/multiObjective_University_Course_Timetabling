package heuristics.jmetal_implementation;

import org.uma.jmetal.problem.Problem;

import heuristics.UCTInstance;


public class UCTProblem implements Problem<EncodedSolution> {

    public UCTInstance instance;


    public UCTProblem(UCTInstance instance) { // add params
        this.instance = instance;
        EncodedSolution.setInstance(instance);
        // this.numberOfObjectives = ...
        // this.numberOfConstraints = ...
    }

    public int numberOfVariables() {
        return instance.courses.size();
    }

    public int numberOfObjectives() {
        return 4;
    }

    public int numberOfConstraints() {
        return 0;
    }

    public String name() {
        return "UniversityCourseTimetablingProblem";
    }

    @Override
    public EncodedSolution evaluate(EncodedSolution solution) {

        solution.objectives()[0] = solution.evaluatePreferences();
        solution.objectives()[1] = solution.evaluateExceptionalTimeslots();
        solution.objectives()[2] = solution.evaluateProfessorDays();
        solution.objectives()[3] = solution.evaluateElectiveOverlap();

        return solution;
    }

    @Override
    public EncodedSolution createSolution() {
        return new EncodedSolution(numberOfObjectives(), numberOfConstraints());
    }
    
}
