package heuristics;

import java.util.Set;

public class DayAssignment {    

    public Integer day_id;
    public Set<Integer> time_ids;

    public DayAssignment() {}

    public DayAssignment copy() {
        DayAssignment copy = new DayAssignment();
        copy.day_id = this.day_id;
        copy.time_ids = Set.copyOf(this.time_ids);
        return copy;
    }
}
