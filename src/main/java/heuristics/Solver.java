package heuristics;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;

public class Solver {

    UCTInstance instance;
    List<UCTSolution> solutions;

    public void readInput(String inputFilePath) {
        // Implement reading input file logic here
        
        try {
            // this.instance = new UCTInstance();
            ObjectMapper mapper = new ObjectMapper();
            // set instance
            this.instance = mapper.readValue(new File(inputFilePath), UCTInstance.class);
            this.instance.compile();

            this.solutions = new java.util.ArrayList<>();

            System.out.println("Number of courses: " + this.instance.courses.size());
            System.out.println("Number of professors: " + this.instance.professors.size());
            System.out.println("Number of groups: " + this.instance.groups.size());
            
        } catch (IOException e) {
            System.err.println("Error reading input from " + inputFilePath);
            e.printStackTrace();
        }
        
    }

    public void solve(Map<String, Object> params) {
        // Implement solving logic here using UCTInstance and parameters
        // ...

        // pre-solve with pre-assigned professors and timeslots? //TODO

        // Course c = this.instance.courses.iterator().next();
        // Professor p = this.instance.professors.iterator().next();
        // c.assigned_professors.add(p.id);
        // Iterator<Timeslot> timeslotIterator = this.instance.timeslots.iterator();
        // for (int i=0; i < c.num_hours; i++) {
        //     c.assigned_timeslots.add(timeslotIterator.next());
        // }

        // // compileSolution();

    }

    // public void compileSolution() {

    //     // set solution

    //     Set<UCTSolution.ProfessorCourse> professor_course_assignment = new java.util.HashSet<>();
    //     Set<UCTSolution.CourseTimeslot> course_timeslot_assignment = new java.util.HashSet<>();
        
    //     for (Course c : this.instance.courses) {
    //         // dummy assignments for illustration
    //         for (Timeslot ts : c.assigned_timeslots) {
    //             course_timeslot_assignment.add(
    //                 new UCTSolution.CourseTimeslot(c.id, ts.day.id, ts.time.id)
    //             );
    //         }
    //         for (Integer prof_id : c.assigned_professors) {
    //             professor_course_assignment.add(
    //                 new UCTSolution.ProfessorCourse(c.id, prof_id)
    //             );
    //         }
    //     }
    // }



    public void writeOutput(String outputDirPath) {
        // Implement writing output file logic here
        // write solution
        for (int i = 0; i < this.solutions.size(); i++) {
            try {
                String filePath = outputDirPath + "/solution_" + i + ".json";
                ObjectMapper mapper = new ObjectMapper();
                mapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), this.solutions.get(i));
            } catch (IOException e) {
                System.err.println("Error writing output to " + outputDirPath);
                e.printStackTrace();
            }
        }

        System.out.println("Solutions written to " + outputDirPath);
    }



    public static void main(String[] args) {
        Map<String, Object> params = new HashMap<>();

        Solver solver = new Solver();
        solver.readInput("instances/instance_2026sem1.json");
        // solver.readInput("instances/example.json");

        solver.solve(params);

        solver.writeOutput("results/instance_2026sem1");

        // Further processing
    }
    
}
