package heuristics.entities;

import java.util.Set;

public class Timeslot {
    
    // Integer id;
    public Day day;
    public Time time;

    public Timeslot(Day day, Time time) {
        this.day = day;
        this.time = time;
    }

    public static Set<Timeslot> generateTimeslots(Set<Day> days, Set<Time> times) {
        Set<Timeslot> timeslots = new java.util.HashSet<>();
        for (Day day : days) {
            for (Time time : times) {
                timeslots.add(new Timeslot(day, time));
            }
        }
        return timeslots;
    }

    public Timeslot() {
        // Default constructor for JSON deserialization
    }

}
