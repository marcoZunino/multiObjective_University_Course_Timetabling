package heuristics.entities;

import java.util.Set;

public class Time {
    
    public Integer id;
    public Set<Integer> shifts;
    public Set<Integer> exceptional_shifts;


    public Time(Integer id, Set<Integer> shifts, Set<Integer> exceptional_shifts) {
        this.id = id;
        this.shifts = shifts;
        this.exceptional_shifts = exceptional_shifts;
    }

    public Time() {
        // Default constructor for JSON deserialization
    }


}
