package heuristics;

import java.util.Set;

import heuristics.entities.Subject;
import heuristics.entities.Group;
import heuristics.entities.Professor;
import heuristics.entities.Shift;
import heuristics.entities.Course;
import heuristics.entities.Day;
import heuristics.entities.Timeslot;
import heuristics.entities.Time;

public class UCTInstance {

    public Set<Subject> subjects;
    public Set<Course> courses;
    public Set<Group> groups;
    public Set<Professor> professors;
    public Set<Day> days;
    public Set<Shift> shifts;
    public Set<Time> times;
    public Set<Timeslot> timeslots;
    public Integer num_rooms;

    public UCTInstance(Set<Subject> subjects, Set<Course> courses, Set<Group> groups,
            Set<Professor> professors, Set<Day> days, Set<Shift> shifts, Set<Time> times) {
        this.subjects = subjects;
        this.courses = courses;
        this.groups = groups;
        this.professors = professors;
        this.days = days;
        this.shifts = shifts;
        this.times = times;
        this.timeslots = Timeslot.generateTimeslots(days, times);
    }

    public UCTInstance() {
        // Default constructor for JSON deserialization
    }

    public void compile() {

        this.timeslots = Timeslot.generateTimeslots(this.days, this.times);

        subjects.forEach(v -> v.compile(this));
        courses.forEach(v -> v.compile(this));
        groups.forEach(v -> v.compile(this));
        professors.forEach(v -> v.compile(this));
        days.forEach(v -> v.compile(this));
        // shifts.forEach(v -> v.compile(this));
        times.forEach(v -> v.compile(this));

    }

    public Course getCourseById(Integer course_id) {
        for (Course c : courses) {
            if (c.id.equals(course_id)) {
                return c;
            }
        }
        return null;
    }

    public Set<Course> getCoursesBySubject(Integer subject_id) {
        Set<Course> result = new java.util.HashSet<>();
        for (Course c : courses) {
            if (c.subject_id.equals(subject_id)) {
                result.add(c);
            }
        }
        return result;
    }


    // set group time slots
    
}
