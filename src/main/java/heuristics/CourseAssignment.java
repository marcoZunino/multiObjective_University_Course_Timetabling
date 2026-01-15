package heuristics;

import java.util.Set;

public class CourseAssignment {
    
    public Integer course_id;
    public Set<Integer> professor_ids;
    public Set<DayAssignment> day_assignments;

    public CourseAssignment() {}

    public CourseAssignment(Integer course_id, Set<Integer> professor_ids, Set<DayAssignment> day_assignments) {
        this.course_id = course_id;
        this.professor_ids = professor_ids;
        this.day_assignments = day_assignments;
    }

    public CourseAssignment copy() {
        CourseAssignment copy = new CourseAssignment();
        copy.course_id = this.course_id;
        copy.professor_ids = Set.copyOf(this.professor_ids);
        for (DayAssignment da : this.day_assignments)
            copy.day_assignments.add(da.copy());
        return copy;
    }

}