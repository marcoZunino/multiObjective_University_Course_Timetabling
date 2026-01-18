package heuristics.entities;

import java.util.Set;

import heuristics.UCTInstance;

public class Time {
    
    public Integer id;
    public Set<Shift> Shifts;
    public Set<Shift> ExceptionalShifts;

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

    public void compile(UCTInstance instance) {
        // set Shifts
        this.Shifts = new java.util.HashSet<>();
        this.ExceptionalShifts = new java.util.HashSet<>();
        for (Shift s : instance.shifts) {
            if (this.shifts.contains(s.id)) {
                this.Shifts.add(s);
            }
            if (this.exceptional_shifts.contains(s.id)) {
                this.ExceptionalShifts.add(s);
            }
        }
    }


}
