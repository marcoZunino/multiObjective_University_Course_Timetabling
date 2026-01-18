package heuristics.jmetal_implementation;


import java.util.Map;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

import org.uma.jmetal.solution.AbstractSolution;

import heuristics.CourseAssignment;
import heuristics.DayAssignment;
import heuristics.UCTInstance;
import heuristics.entities.Course;
import heuristics.entities.Professor;
import heuristics.entities.Timeslot;
import heuristics.entities.Group;


public class EncodedSolution extends AbstractSolution<CourseAssignment> {

    static private UCTInstance instance;

    private Map<Integer, Map<Integer, Integer>> alreadyAssignedProfessors;
    private Map<Integer, Integer> courseIdToIndex;


    static public void setInstance(UCTInstance instance) {
        EncodedSolution.instance = instance;
    }

    public EncodedSolution(int numberOfObjectives, int numberOfConstraints) {
        
        super(instance.courses.size(), numberOfObjectives, numberOfConstraints);

        alreadyAssignedProfessors = new HashMap<>();

        initializeVariables();

        generateInitialAssignments();

    }

    public EncodedSolution(EncodedSolution solution) {

        super(instance.courses.size(), solution.objectives().length, solution.constraints().length);

        IntStream.range(0, solution.objectives().length).forEach(i -> objectives()[i] = solution.objectives()[i]);
        IntStream.range(0, solution.constraints().length).forEach(i -> constraints()[i] = solution.constraints()[i]);

        initializeVariables(solution.variables());
        alreadyAssignedProfessors = new HashMap<>(solution.alreadyAssignedProfessors);
    }


    @Override
    public EncodedSolution copy() {
        return new EncodedSolution(this);
    }

    public double evaluatePreferences() {
        int preferencesObjective = 0;

        for (CourseAssignment ca : variables()) {
            Course course = instance.getCourseById(ca.course_id);
            Set<Timeslot> assignedTimeslots = ca.getAssignedTimeslots(instance.timeslots);
            for (Integer prof_id : ca.professor_ids) {
                Professor professor = course.AvailableProfessors.stream()
                        .filter(p -> p.id.equals(prof_id))
                        .findFirst()
                        .orElse(null);
                if (professor != null) {
                    preferencesObjective += professor.computeTotalPreference(assignedTimeslots);
                }
            }
        }

        objectives()[0] = (double) preferencesObjective;
        return objectives()[0];
    }


    private void initializeVariables() {

        courseIdToIndex = new HashMap<>();
        int index = 0;

        for (Course course : instance.courses) {
            variables().set(index, new CourseAssignment(course.id, new HashSet<>(), new HashSet<>()));
            courseIdToIndex.put(course.id, index);
            index++;
        }
    }
    private void initializeVariables(List<CourseAssignment> coursesAssignments) {
        courseIdToIndex = new HashMap<>();
        int index = 0;
        
        for (CourseAssignment ca : coursesAssignments) {
            variables().set(index, ca.copy());
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

        generateInitialProfessorAssignments();
     
        generateInitialTimeslotAssignments(instance.courses.stream().filter(c -> !c.isPractical()).collect(java.util.stream.Collectors.toSet()));
        generateInitialTimeslotAssignments(instance.courses.stream().filter(c -> c.isPractical()).collect(java.util.stream.Collectors.toSet()));
        
    }

    private void generateInitialProfessorAssignments() {

        for (Course course : instance.courses) {

            CourseAssignment ca = getCourseAssignment(course.id);

            for (Professor available_professor : course.AvailableProfessors) {
                Integer prof_id = available_professor.id;
                if (checkProfessorAvailability(prof_id, course.subject_id)) {
                    ca.professor_ids.add(prof_id);
                    updateProfessorAvailability(prof_id, course.subject_id);
                }
                if (ca.professor_ids.size() == course.num_profs) {
                    break;
                }
            }

            if (ca.professor_ids.size() < course.num_profs) {
                // Not enough available professors
                throw new RuntimeException("Not enough available professors for course " + course.id);
            }

        }
    }

    private void generateInitialTimeslotAssignments(Set<Course> coursesSet) {

        for (Course course : coursesSet) {

            CourseAssignment ca = getCourseAssignment(course.id);

            Set<Timeslot> availableTimeslots = getAvailableTimeslotsForCourse(course, ca);
            Set<Timeslot> nonPenalizedTimeslots = getNonPenalizedTimeslotsForCourse(course, ca, availableTimeslots);
            // availableTimeslots.removeAll(nonPenalizedTimeslots);

            for (int i = 0; i < course.num_days; i++) {
                
                Integer dayHours = course.nextDayHours(i);
                
                Set<Timeslot> consecutiveTimeslots = getConsecutiveTimeslots(nonPenalizedTimeslots, dayHours);
                if (consecutiveTimeslots.isEmpty()) {
                    consecutiveTimeslots = getConsecutiveTimeslots(availableTimeslots, dayHours); // add penalized timeslots
                }
                if (consecutiveTimeslots.isEmpty()) {
                    throw new RuntimeException("Not enough available timeslots for course " + course.id);
                }

                DayAssignment da = new DayAssignment();
                da.time_ids = consecutiveTimeslots.stream().map(ts -> ts.time.id).collect(java.util.stream.Collectors.toSet());
                da.day_id = consecutiveTimeslots.iterator().next().day.id;
                
                ca.day_assignments.add(da);

                updateAvailableTimeslots(course, da, availableTimeslots, nonPenalizedTimeslots);
                
            }
        }
    }

    private Set<Timeslot> getConsecutiveTimeslots(Set<Timeslot> timeslots, int requiredCount) {
        
        Map<Integer, Set<Timeslot>> timeslotsByDay = new HashMap<>();  // group by day
        for (Timeslot ts : timeslots) {
            timeslotsByDay.putIfAbsent(ts.day.id, new HashSet<>());
            timeslotsByDay.get(ts.day.id).add(ts);
        }

        for (Map.Entry<Integer, Set<Timeslot>> entry : timeslotsByDay.entrySet()) { // for each day
            // Integer day_id = entry.getKey();
            Set<Timeslot> dayTimeslots = entry.getValue();

            if (dayTimeslots.size() < requiredCount) {
                continue;
            }

            List<Integer> sortedTimeIds = dayTimeslots.stream()
                    .map(ts -> ts.time.id)
                    .sorted()
                    .collect(java.util.stream.Collectors.toList());

            for (int i = 0; i <= sortedTimeIds.size() - requiredCount; i++) { // select first hour
                
                Set<Timeslot> result = new HashSet<>();

                for (int k = 0; k < requiredCount; k++) {
                    Integer time_id = sortedTimeIds.get(i) + k;
                    Timeslot ts = dayTimeslots.stream()
                            .filter(t -> t.time.id.equals(time_id))
                            .findFirst()
                            .orElse(null);
                    if (ts != null) {
                        result.add(ts);
                    } else {
                        // consecutive = false;
                        result.clear();
                        break;
                    }
                }
                if (!result.isEmpty()) {
                    return result;
                }
            }
        }

        return new HashSet<>();
    }

    private Set<Timeslot> getAvailableTimeslotsForCourse(Course course, CourseAssignment ca) {   
        // inicializar horas disponibles segun turno de los grupos asignados

        Set<Timeslot> availableTimeslots = new HashSet<>();

        for (Group group : course.Groups) {

            if (availableTimeslots.isEmpty()) {
                availableTimeslots.addAll(group.Timeslots);
            } else {
                availableTimeslots.retainAll(group.Timeslots);
            }
        }

        // profesores asignados: 
        //      - restar horas no disponibles
        //      - restar horas asignadas a otros cursos (if !prof.simult_courses)

        for (Integer prof_id : ca.professor_ids) {
            Professor professor = course.AvailableProfessors.stream()
                    .filter(p -> p.id.equals(prof_id))
                    .findFirst()
                    .orElse(null);
            if (professor != null) {
                availableTimeslots.retainAll(professor.AvailableTimeslots);
                // eliminar horas asignadas a otros cursos si !prof.simult_courses  //TODO
            }
        }

        return availableTimeslots;
       
    }

    private Set<Timeslot> getNonPenalizedTimeslotsForCourse(Course course, CourseAssignment ca, Set<Timeslot> availableTimeslots) {
        
        Set<Timeslot> nonPenalizedTimeslots = new HashSet<>(availableTimeslots);

        // (1.1) marcar horas asignadas a cursos en no_overlap -> seleccionar primero horas no marcadas
        for (Course no_overlap_course : course.NoOverlapCourses) {
            CourseAssignment no_overlap_ca = getCourseAssignment(no_overlap_course.id);
            if (no_overlap_ca != null) {
                nonPenalizedTimeslots.removeAll(no_overlap_ca.getAssignedTimeslots(instance.timeslots));
            }
        }

        // (3.3) en caso de practico marcar horas/dias anteriores al teorico correspondiente -> asignar primero teoricos
        // if (course.isPractical()) {
        //     CourseAssignment tca = getCourseAssignment(course.getTheoreticalCourse().id);
        //     // nonPenalizedTimeslots.removeAll(all timeslots before tca.assignedTimeslots); // TODO
        // }

        // (3.1) marcar horas si se supera la capacidad de salones?

        // (3.4) marcar horas excepcionales si se supera el limite (si se pide como restriccion)
        for (Group group : course.Groups) {
            nonPenalizedTimeslots.removeAll(group.ExceptionalTimeslots);
        }

        // (4.2) horas puente queda por fuera, como penalizacion u objetivo

        return nonPenalizedTimeslots;
    }

    private void updateAvailableTimeslots(Course course, DayAssignment da, Set<Timeslot> availableTimeslots, Set<Timeslot> nonPenalizedTimeslots) {
        
        Set<Timeslot> timeslotsToRemove = new HashSet<>();

        Set<Integer> daysToRemove = new HashSet<>(Arrays.asList(da.day_id));
        if (!course.consecutive_days) {
            daysToRemove.add(da.day_id - 1);
            daysToRemove.add(da.day_id + 1);
        }

        timeslotsToRemove.addAll(availableTimeslots.stream()
            .filter(ts -> daysToRemove.contains(ts.day.id)) // get timeslots of the days to remove
            .collect(java.util.stream.Collectors.toSet())
        );

        availableTimeslots.removeAll(timeslotsToRemove);
        nonPenalizedTimeslots.removeAll(timeslotsToRemove);

    }


    private boolean checkProfessorAvailability(Integer prof_id, Integer subject_id) {
                    
        if (!alreadyAssignedProfessors.containsKey(prof_id)) {
            return true;
        }

        Map<Integer, Integer> subjectAssignments = alreadyAssignedProfessors.get(prof_id);
        if (!subjectAssignments.containsKey(subject_id)) {
            return true;
        }
        Integer assignmentsCount = subjectAssignments.get(subject_id);

        Professor professor = instance.professors.stream()
                .filter(p -> p.id.equals(prof_id))
                .findFirst()
                .orElse(null);
        
        return assignmentsCount < professor.getNumGroupsForSubject(subject_id);
    }

    private void updateProfessorAvailability(Integer prof_id, Integer subject_id) {
        
        if (!alreadyAssignedProfessors.containsKey(prof_id)) {
            alreadyAssignedProfessors.put(prof_id, new HashMap<>());
        }
        Map<Integer, Integer> subjectAssignments = alreadyAssignedProfessors.get(prof_id);
        if (!subjectAssignments.containsKey(subject_id)) {
            subjectAssignments.put(subject_id, 1);
        } else {
            Integer assignmentsCount = subjectAssignments.get(subject_id);
            subjectAssignments.put(subject_id, assignmentsCount + 1);
        }

    }



}