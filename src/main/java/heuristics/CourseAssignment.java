package heuristics;

import java.util.Set;

import heuristics.entities.Timeslot;

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

    public Set<Timeslot> getAssignedTimeslots(Set<Timeslot> allTimeslots) {
        Set<Timeslot> assignedTimeslots = new java.util.HashSet<>();
        for (Timeslot ts : allTimeslots) {
            if (day_assignments.stream().anyMatch(da -> da.day_id.equals(ts.day.id) && da.time_ids.contains(ts.time.id))) {
                assignedTimeslots.add(ts);
            }
        }
        return assignedTimeslots;
    }

}