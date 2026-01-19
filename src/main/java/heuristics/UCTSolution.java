package heuristics;

import java.util.List;

public class UCTSolution {

    public List<CourseAssignment> course_assignments;

    public UCTSolution(List<CourseAssignment> course_assignments) {
        this.course_assignments = course_assignments;
    }
    public UCTSolution() {
    }
    public static UCTSolution emptySolution() {
        return new UCTSolution(
            new java.util.ArrayList<>()
        );
    }

}