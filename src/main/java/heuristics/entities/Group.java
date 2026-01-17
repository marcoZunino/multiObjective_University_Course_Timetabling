package heuristics.entities;

import java.util.Set;

import heuristics.UCTInstance;

// import java.util.Set;

public class Group {
    // attributes: id, year, shift, timeslots

    public Integer id;
    public Integer year;
    public Set<Timeslot> Timeslots;
    public Set<Timeslot> ExceptionalTimeslots;

    private Integer shift;
    public Shift GroupShift;

    
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

    public void compile(UCTInstance instance) {
        // No additional attributes to compile for Group

        // set Shift
        for (Shift s : instance.shifts) {
            if (s.id.equals(this.shift)) {
                this.GroupShift = s;
                break;
            }
        }

        Timeslots = new java.util.HashSet<>();
        for (Timeslot ts : instance.timeslots) {
            if (ts.time.Shifts.contains(this.GroupShift)) {
                Timeslots.add(ts);
            }
            if (ts.time.ExceptionalShifts.contains(this.GroupShift)) {
                ExceptionalTimeslots.add(ts);
            }
        }


    }

}
