package heuristics.entities;

// import java.util.HashSet;
import java.util.Set;

public class Course {
    
    public Integer id;
    public Integer subject_id;
    public Integer num_hours;
    public Integer num_days;
    public Integer num_profs;
    public Set<Integer> groups;
    public Set<Integer> available_professors;
    public Set<Integer> no_overlap;
    public Set<Integer> elective_no_overlap;
    public boolean elective;
    public boolean consecutive_days;
    public Boolean theo_prac;

    public int maxHoursPerDay() {
        return (int) Math.ceil((double) num_hours / (double) num_days);

    }
    public int minHoursPerDay() {
        return (int) Math.floor((double) num_hours / (double) num_days);

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


    
}
