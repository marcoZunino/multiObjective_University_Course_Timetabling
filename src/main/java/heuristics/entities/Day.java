package heuristics.entities;

import java.util.Set;

import heuristics.UCTInstance;

public class Day {

    public Integer id;
    public Set<Timeslot> Timeslots;

    public Day(Integer id) {
        this.id = id;
    }

    public Day() {
        // Default constructor for JSON deserialization
    }

    public void compile(UCTInstance instance) {
        // No additional attributes to compile for Day

        Timeslots = new java.util.HashSet<>();
        for (Timeslot ts : instance.timeslots) {
            if (ts.day.id.equals(this.id)) {
                Timeslots.add(ts);
            }
        }

    }
}
