package heuristics.entities;

import java.util.Set;

public class Professor {
    
    public Integer id;
    public Boolean min_max_days; // true if min days, false if max days, null if none
    public boolean simult_courses; // default false

    public Set<SubjectGroupsNumber> num_groups_per_subject; // subject_id -> num_groups
    public Set<Preference> availability; // pair<day, time> -> availability (1,2,3), non available if not present

    // public Set<Integer> assigned_courses;
    // public Set<Timeslot> assigned_timeslots;

    public Professor(Integer id, Boolean min_max_days, boolean simult_courses) {
        this.id = id;
        this.min_max_days = min_max_days;
        this.simult_courses = simult_courses;
        // this.assigned_timeslots = new java.util.HashSet<>();
        // this.assigned_courses = new java.util.HashSet<>();
    }

    public Professor() {
        // this.assigned_timeslots = new java.util.HashSet<>();
        // this.assigned_courses = new java.util.HashSet<>();
        // Default constructor for JSON deserialization
    }

    // public Set<Integer> assigned_days() {
    //     Set<Integer> days = new java.util.HashSet<>();
    //     for (Integer ts : assigned_timeslots) {
    //         days.add(ts.day.id); // assuming timeslot encoding where day is the tens digit
    //     }
    //     return days;
    // }

    static public class Preference {

        public Integer day;
        public Integer time;
        public Integer preference; // 1 (low), 2 (medium), 3 (high)

        public Preference() {
            // Default constructor for JSON deserialization
        }
    }

    static public class SubjectGroupsNumber {

        public Integer subject_id;
        public Integer num_groups;

        public SubjectGroupsNumber() {
            // Default constructor for JSON deserialization
        }
    }

    public Integer getNumGroupsForSubject(Integer subject_id) {
        for (SubjectGroupsNumber sgn : num_groups_per_subject) {
            if (sgn.subject_id.equals(subject_id)) {
                return sgn.num_groups;
            }
        }
        return 0; // or throw an exception if subject not found
    }

}