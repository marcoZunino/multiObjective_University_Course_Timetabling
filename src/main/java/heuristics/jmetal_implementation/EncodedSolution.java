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
import heuristics.entities.Subject;
import heuristics.entities.Timeslot;
import heuristics.entities.Group;


public class EncodedSolution extends AbstractSolution<CourseAssignment> {

    private static UCTInstance instance;

    private Map<Integer, Map<Integer, Integer>> alreadyAssignedProfessors;
    private Map<Integer, Integer> courseIdToIndex;


    public static void setInstance(UCTInstance instance) {
        EncodedSolution.instance = instance;

        preSolveProfessorAssignment();
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

        return (double) preferencesObjective;
    }

    public double evaluateExceptionalTimeslots() {

        int exceptionalTimeslotsObjective = 0;

        for (CourseAssignment ca : variables()) {
            Course course = instance.getCourseById(ca.course_id);
            Set<Timeslot> assignedTimeslots = ca.getAssignedTimeslots(instance.timeslots);
            for (Timeslot ts : assignedTimeslots) {
                if (course.getExceptionalTimeslots().contains(ts)) {
                    exceptionalTimeslotsObjective ++;
                }
            }
        }

        return (double) exceptionalTimeslotsObjective;
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

    public static void preSolveProfessorAssignment() {

        for (Subject s : instance.subjects) {

            if (s.Courses.size() == 1 || s.getSubjectProfessors().size() == 1) {
                continue;
            }

            for (Course c : s.Courses) {
                // remove professor-course relations where timeslots are insufficient
                Set<Professor> toRemove = new HashSet<>();
                for (Professor p : c.AvailableProfessors) {   
                    if (getAvailableTimeslotsForCourse(c, new HashSet<>(Arrays.asList(p.id))).size() < c.num_hours) {
                        toRemove.add(p);
                        p.AvailableCourses.remove(c);
                    }
                }
                for (Professor p : toRemove) {
                    c.AvailableProfessors.remove(p);
                    c.available_professors.remove(p.id);
                    System.out.println("Pre-solve: Removed professor " + p.id + " from course " + c.id + " due to insufficient available timeslots.");
                }                
            }

            for (Professor p : s.getSubjectProfessors()) {

                Set<Course> subjectProfessorCourses = s.Courses.stream()
                        .filter(c -> p.AvailableCourses.contains(c))
                        .collect(java.util.stream.Collectors.toSet());
                if (subjectProfessorCourses.size() == 1) {
                    Course onlyCourse = subjectProfessorCourses.iterator().next();
                    if (onlyCourse.AvailableProfessors.size() == onlyCourse.num_profs) {
                        continue; // already exclusive
                    }
                    onlyCourse.AvailableProfessors.retainAll(new HashSet<>(Arrays.asList(p)));
                    onlyCourse.available_professors.retainAll(new HashSet<>(Arrays.asList(p.id)));
                    System.out.println("Pre-solve: Professor " + p.id + " can only teach course " + onlyCourse.id + " for subject " + s.id + ". Assigned exclusively.");
                }
            }
        }

    }

    private CourseAssignment getCourseAssignment(Integer course_id) {
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

        // first assign professors to courses with less available professors
        Set<Course> sortedCourses = new HashSet<>(instance.courses);
        sortedCourses = sortedCourses.stream()
            .sorted((c1, c2) -> Integer.compare(c1.AvailableProfessors.size(), c2.AvailableProfessors.size()))
            .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        for (Course course : sortedCourses) {

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

            Set<Timeslot> availableTimeslots = getAvailableTimeslotsForCourse(course, ca.professor_ids);
            Set<Timeslot> nonPenalizedTimeslots = getNonPenalizedTimeslotsForCourse(course, ca, availableTimeslots);
            // availableTimeslots.removeAll(nonPenalizedTimeslots);

            for (int i = 0; i < course.num_days; i++) {
                
                int dayHours = course.nextDayHours(i);
                
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

                if (ca.day_assignments.size() < course.num_days) {
                    updateAvailableTimeslots(course, da, availableTimeslots, nonPenalizedTimeslots);
                }
            }
        }
    }

    private static Set<Timeslot> getConsecutiveTimeslots(Set<Timeslot> timeslots, int requiredCount) {
        
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

    private static Set<Timeslot> getAvailableTimeslotsForCourse(Course course, Set<Integer> prof_ids) {   
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

        for (Integer prof_id : prof_ids) {
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

        // (1.1) penalizar horas asignadas a cursos en no_overlap
        for (Course no_overlap_course : course.NoOverlapCourses) {
            CourseAssignment no_overlap_ca = getCourseAssignment(no_overlap_course.id);
            if (no_overlap_ca != null) {
                nonPenalizedTimeslots.removeAll(no_overlap_ca.getAssignedTimeslots(instance.timeslots));
            }
        }

        // (3.3) en caso de practico penalizar horas/dias anteriores al teorico correspondiente -> asignar primero teoricos
        // if (course.isPractical()) {
        //     CourseAssignment tca = getCourseAssignment(course.getTheoreticalCourse().id);
        //     // nonPenalizedTimeslots.removeAll(all timeslots before tca.assignedTimeslots); // TODO
        // }

        // (3.1) penalizar horas si se supera la capacidad de salones?

        // (3.4) penalizar horas excepcionales si se supera el limite (si se pide como restriccion)
        for (Group group : course.Groups) {
            nonPenalizedTimeslots.removeAll(group.ExceptionalTimeslots);
        }

        // (4.2) horas puente queda por fuera, como penalizacion u objetivo

        return nonPenalizedTimeslots;
    }

    private static void updateAvailableTimeslots(Course course, DayAssignment da, Set<Timeslot> availableTimeslots, Set<Timeslot> nonPenalizedTimeslots) {
        
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