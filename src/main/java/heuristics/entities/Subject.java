package heuristics.entities;

public class Subject {

    public Integer id;
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
    
}
