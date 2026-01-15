package heuristics.entities;

// import java.util.Set;

public class Group {
    // attributes: id, year, shift, timeslots

    public Integer id;
    public Integer year;
    public Integer shift;
    
    // public Set<Timeslot> assigned_timeslots;

    public Group(Integer id, Integer year, Integer shift) {
        this.id = id;
        this.year = year;
        this.shift = shift;
        // this.assigned_timeslots = new java.util.HashSet<>();
    }

    public Group() {
        // this.assigned_timeslots = new java.util.HashSet<>();
        // Default constructor for JSON deserialization
    }


}
