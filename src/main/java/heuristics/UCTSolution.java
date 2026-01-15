package heuristics;

import java.util.Set;

// public record UCTSolution (
//     Set<ProfessorCourse> professor_course_assignment, Set<CourseTimeslot> course_timeslot_assignment
// ) {


//     static public UCTSolution emptySolution() {
//         return new UCTSolution(
//             new java.util.HashSet<>(),
//             new java.util.HashSet<>()
//         );
//     }

//     static public record ProfessorCourse (Integer course_id, Integer professor_id) {}

//     static public record CourseTimeslot (Integer course_id, Integer day_id, Integer time_id) {}

// }


public record UCTSolution (Set<CourseAssignment> course_assignments) {

    static public UCTSolution emptySolution() {
        return new UCTSolution(
            new java.util.HashSet<>()
        );
    }

}