package heuristics.jmetal_implementation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.uma.jmetal.solution.AbstractSolution;

import heuristics.CourseAssignment;
import heuristics.DayAssignment;
import heuristics.UCTInstance;
import heuristics.entities.Course;
import heuristics.entities.Professor;


public class EncodedSolution extends AbstractSolution<CourseAssignment> {

    static private UCTInstance instance;

    private Set<Integer> alreadyAssignedProfessors;
    private Map<Integer, Integer> courseIdToIndex;

    private int preferencesObjective;


    static public void setInstance(UCTInstance instance) {
        EncodedSolution.instance = instance;
    }

    public EncodedSolution(int numberOfObjectives, int numberOfConstraints) {
        
        super(instance.courses.size(), numberOfObjectives, numberOfConstraints);

        alreadyAssignedProfessors = new java.util.HashSet<>();

        generateInitialAssignments();

    }

    public EncodedSolution(EncodedSolution solution) {

        super(instance.courses.size(), solution.objectives().length, solution.constraints().length);

        IntStream.range(0, solution.variables().size()).forEach(i -> variables().set(i, solution.variables().get(i).copy()));
        IntStream.range(0, solution.objectives().length).forEach(i -> objectives()[i] = solution.objectives()[i]);
        IntStream.range(0, solution.constraints().length).forEach(i -> constraints()[i] = solution.constraints()[i]);

        setCourseIdToIndex();
        alreadyAssignedProfessors = new java.util.HashSet<>(solution.alreadyAssignedProfessors);
    }


    @Override
    public EncodedSolution copy() {
        return new EncodedSolution(this);
    }

    public double evaluatePreferences() {
        objectives()[0] = (double) preferencesObjective;
        return objectives()[0];
    }


    private void setCourseIdToIndex() {
        courseIdToIndex = new HashMap<>();
        int index = 0;
        for (CourseAssignment ca : variables()) {
            courseIdToIndex.put(ca.course_id, index);
            index++;
        }
    }

    public CourseAssignment getCourseAssignment(Integer course_id) {
        Integer index = courseIdToIndex.get(course_id);
        if (index == null)
            return null;
        return variables().get(index);
    }

    private void generateInitialAssignments() {

        for (Course course : instance.courses) {
            variables().add(new CourseAssignment(course.id, new java.util.HashSet<>(), new java.util.HashSet<>()));
        }
        setCourseIdToIndex();

        generateInitialProfessorAssignments();
        generateInitialTimeslotAssignments();
        
    }

    private void generateInitialProfessorAssignments() {

        for (Course course : instance.courses) {

            CourseAssignment ca = getCourseAssignment(course.id);

            for (int i = 0; i < course.num_profs; i++) {
                Integer prof_id = course.available_professors.iterator().next();
                if (!alreadyAssignedProfessors.contains(prof_id)) {
                    ca.professor_ids.add(prof_id);
                }
            }

            if (ca.professor_ids.size() < course.num_profs) {
                // Not enough available professors
                throw new RuntimeException("Not enough available professors for course " + course.id);
            }

            updateProfessorAvailability(ca.professor_ids, course.subject_id);

        }
    }

    private void generateInitialTimeslotAssignments() {

        for (Course course : instance.courses) {

            CourseAssignment ca = getCourseAssignment(course.id);

            // inicializar horas disponibles segun turno de los grupos asignados

            // restar horas asignadas a cursos en no_overlap

            // profesores asignados: 
            //      - restar horas no disponibles
            //      - restar horas asignadas a otros cursos (if !prof.simult_courses)

            // int hours_left = course.num_hours;

            for (int i = 0; i < course.num_days; i++) {

                DayAssignment da = new DayAssignment();
                da.day_id = i; // Find professor available day

                // da.time_ids = new java.util.HashSet<>();
                // while (hours_left > 0) {
                //     da.time_ids.add(j); // Find professor available time
                //     // actualizar preferencesObjective // TODO
                //     hours_left--;
                // }

                ca.day_assignments.add(da);
            }
        }
    }


    private void updateProfessorAvailability(Set<Integer> prof_ids, Integer subject_id) {
        
        for (Integer prof_id : prof_ids) {

            Professor professor = instance.professors.stream()
                    .filter(p -> p.id.equals(prof_id))
                    .findFirst()
                    .orElse(null);

            Set<Integer> coursesOfSubject = instance.getCoursesBySubject(subject_id).stream()
                    .map(c -> c.id)
                    .collect(java.util.stream.Collectors.toSet());

            int numSubjectAssignedCourses = 0;
            for (CourseAssignment ca : variables()) {
                if (coursesOfSubject.contains(ca.course_id)
                    && ca.professor_ids.contains(prof_id)) {
                    numSubjectAssignedCourses++;
                }
            }

            if (numSubjectAssignedCourses < professor.getNumGroupsForSubject(subject_id)) {
                return;
            }
                    
            alreadyAssignedProfessors.add(prof_id);
        }
    }



}