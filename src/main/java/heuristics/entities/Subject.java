package heuristics.entities;

import java.util.Set;

import heuristics.UCTInstance;

public class Subject {

    public Integer id;
    public Integer theo_prac_subject_id;

    public Set<Course> Courses;
    public Subject theo_prac_subject;

    public Set<Professor> getSubjectProfessors() {
        Set<Professor> professors = new java.util.HashSet<>();
        for (Course c : Courses) {
            for (Professor prof : c.AvailableProfessors) {
                professors.add(prof);
            }
        }
        return professors;
    }


    // Integer num_hours;
    // Integer num_days;
    // Boolean theo_prac;  // true for theoretical, false for practical, null instead

    // Subject(Integer id, Integer num_hours, Integer num_days, Boolean theo_prac) {
    //     this.id = id;
    //     this.num_hours = num_hours;
    //     this.num_days = num_days;
    //     this.theo_prac = theo_prac;
    // }

    Subject(Integer id) {
        this.id = id;
    }

    public Subject() {
        // Default constructor for JSON deserialization
    }

    public void compile(UCTInstance instance) {
        
        Courses = instance.courses.stream()
                .filter(c -> c.subject_id.equals(this.id))
                .collect(java.util.stream.Collectors.toSet());
        
        if (this.theo_prac_subject_id != null) {
            theo_prac_subject = instance.subjects.stream()
                .filter(s -> s.id.equals(this.theo_prac_subject_id))
                .findFirst()
                .orElse(null);
        }
    }
    
}
