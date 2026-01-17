package heuristics.entities;

// import java.util.HashSet;
import java.util.Set;

import heuristics.UCTInstance;

public class Course {
    
    public Integer id;
    public Integer subject_id;
    public Integer num_hours;
    public Integer num_days;
    public Integer num_profs;
    public boolean elective;
    public boolean consecutive_days;
    public Boolean theo_prac;

    public Set<Group> Groups;
    public Set<Professor> AvailableProfessors;
    public Set<Course> NoOverlapCourses;
    public Set<Course> ElectiveNoOverlapCourses;

    // ids
    private Set<Integer> groups;
    private Set<Integer> available_professors;
    private Set<Integer> no_overlap;
    private Set<Integer> elective_no_overlap;

    
    private Course theoreticalCourse;
    private Course practicalCourse;
    

    public int maxHoursPerDay() {
        return (int) Math.ceil((double) num_hours / (double) num_days);
    }
    public int minHoursPerDay() {
        return (int) Math.floor((double) num_hours / (double) num_days);
    }
    public int nextDayHours(int dayIndex) {
        if (dayIndex < num_hours % minHoursPerDay()) {
            return maxHoursPerDay();
        } else {
            return minHoursPerDay();
        }
    }

    public boolean isTheoretical() {
        return Boolean.TRUE.equals(theo_prac);
    }
    public boolean isPractical() {
        return Boolean.FALSE.equals(theo_prac);
    }
    public Course getTheoreticalCourse() {
        if (isPractical()) {
            return theoreticalCourse;
        }
        return null;
    }
    public Course getPracticalCourse() {
        if (isTheoretical()) {
            return practicalCourse;
        }
        return null;
    }
    // variables
    // public Set<Timeslot> assigned_timeslots;
    // public Set<Integer> assigned_professors;
    

    public Course(Integer id, Integer subject_id, Set<Integer> available_professors, Integer num_profs, Set<Integer> groups, Set<Integer> no_overlap, Set<Integer> elective_no_overlap, boolean elective, boolean consecutive_days, Integer num_days, Integer num_hours, Boolean theo_prac) {
        
        this.id = id;
        this.subject_id = subject_id;
        this.num_hours = num_hours;
        this.num_days = num_days;
        this.num_profs = num_profs;

        this.available_professors = available_professors;
        this.groups = groups;
        this.no_overlap = no_overlap;
        this.elective_no_overlap = elective_no_overlap;
        
        this.elective = elective;
        this.consecutive_days = consecutive_days;
        this.theo_prac = theo_prac;

        // this.assigned_timeslots = new HashSet<>();
        // this.assigned_professors = new HashSet<>();
    }

    public Course() {
        // this.assigned_timeslots = new HashSet<>();
        // this.assigned_professors = new HashSet<>();

        // Default constructor for JSON deserialization
    }

    public void compile(UCTInstance instance) {
        
        // set Groups
        this.Groups = new java.util.HashSet<>();
        for (Group g : instance.groups) {
            if (this.groups.contains(g.id)) {
                this.Groups.add(g);
                break;
            }
        }
        
        // set AvailableProfessors
        this.AvailableProfessors = new java.util.HashSet<>();
        for (Professor p : instance.professors) {
            if (this.available_professors.contains(p.id)) {
                this.AvailableProfessors.add(p);
            }
        }

        // set NoOverlapCourses
        this.NoOverlapCourses = new java.util.HashSet<>();
        for (Course c : instance.courses) {
            if (this.no_overlap.contains(c.id)) {
                this.NoOverlapCourses.add(c);
            }
        }

        // set ElectiveNoOverlapCourses
        this.ElectiveNoOverlapCourses = new java.util.HashSet<>();
        for (Course c : instance.courses) {
            if (this.elective_no_overlap.contains(c.id)) {
                this.ElectiveNoOverlapCourses.add(c);
            }
        }

        // set theoretical/practical courses // TODO

    }


    
}
