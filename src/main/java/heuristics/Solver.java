package heuristics;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import heuristics.jmetal_implementation.EncodedSolution;

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
            instance = mapper.readValue(new File(inputFilePath), UCTInstance.class);
            instance.compile();

            solutions = new java.util.ArrayList<>();

            System.out.println("Number of courses: " + instance.courses.size());
            System.out.println("Number of professors: " + instance.professors.size());
            System.out.println("Number of groups: " + instance.groups.size());
            
        } catch (IOException e) {
            System.err.println("Error reading input from " + inputFilePath);
            e.printStackTrace();
        }
        
    }

    public void solve(Map<String, Object> params) {
        // Implement solving logic here using UCTInstance and parameters
        // ...

        EncodedSolution.setInstance(instance);

        // EncodedSolution encodedSolution = new EncodedSolution(3, 0);
        EncodedSolution encodedSolution = readSolution("results/solution_min_days_excep_ts.json");

        System.out.println("Preference objective value: " + encodedSolution.evaluatePreferences());
        System.out.println("Exceptional timeslots objective value: " + encodedSolution.evaluateExceptionalTimeslots() + 
            " (only practical courses: " + encodedSolution.evaluateExceptionalTimeslots(true) + 
            ", only theoretical courses: " + encodedSolution.evaluateExceptionalTimeslots(false) + ")");
        System.out.println("Professor days objective value: " + encodedSolution.evaluateProfessorDays());
        System.out.println("Elective overlap objective value: " + encodedSolution.evaluateElectiveOverlap());

        compileSolution(encodedSolution);

    }

    public void compileSolution(EncodedSolution encodedSolution) {
        solutions.add(new UCTSolution(encodedSolution.variables()));
    }

    public EncodedSolution readSolution(String inputFilePath) {
        // Implement reading input file logic here
        
        try {
            // this.instance = new UCTInstance();
            ObjectMapper mapper = new ObjectMapper();
            // set instance
            UCTSolution solution = mapper.readValue(new File(inputFilePath), UCTSolution.class);

            return new EncodedSolution(solution.course_assignments);
            
        } catch (IOException e) {
            System.err.println("Error reading input from " + inputFilePath);
            e.printStackTrace();
        }

        return null;
        
    }



    public void writeOutput(String outputDirPath) {
        File outputDir = new File(outputDirPath);

        if (!outputDir.exists()) {
            boolean created = outputDir.mkdirs();
            if (!created) {
                System.err.println("Failed to create output directory: " + outputDirPath);
                return; // stop execution if directory can't be created
            }
        }

        for (int i = 0; i < this.solutions.size(); i++) {
            try {
                String filePath = outputDirPath + "/solution_" + i + ".json";
                ObjectMapper mapper = new ObjectMapper();
                mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(new File(filePath), this.solutions.get(i));

            } catch (IOException e) {
                System.err.println("Error writing output to " + outputDirPath);
                e.printStackTrace();
            }
        }
        if (this.solutions.size() > 0) {
            System.out.println("Solutions written to " + outputDirPath);
        }
    }




    public static void main(String[] args) {
        Map<String, Object> params = new HashMap<>();

        Solver solver = new Solver();
        solver.readInput("instances/instance_2026sem1.json");
        // solver.readInput("instances/example.json");

        // for (Course course : solver.instance.courses) {
        //     System.out.println("Course ID: " + course.id + ", theo_prac: " + course.theo_prac);
        //     if (course.isPractical()) System.out.println("  Theoretical Course: " + course.theo_prac_course.id);
        //     if (course.isTheoretical()) System.out.println("  Practical Course: " + course.theo_prac_course.id);


        // }
        // for (Subject subject : solver.instance.subjects) {
        //     System.out.println("Subject ID: " + subject.id + ", TheoPrac id " + subject.theo_prac_subject_id);
        // }
        solver.solve(params);

        // solver.writeOutput("results/instance_2026sem1");

    }
    
}
