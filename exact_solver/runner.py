
from pyexpat import model
from xml.parsers.expat import model
import gurobipy as gp
import argparse

from load_data import *
from entities import *
from variables import *


class Objective:

    def __init__(self, id, name, expr_lambda):
        self.id = id
        self.name = name
        self.expr_lambda = expr_lambda
        self.__expr = None
        self.weight = 0
        self.priority = None
        self.rng = None
        self.value = None
        self.slack = None # slack variable
        self.upper_bound = None

    def set_params(self, weight=0, priority=None, upper_bound=None, rng=None):
        self.weight = weight
        self.priority = priority
        self.upper_bound = upper_bound
        self.rng = rng
    
    def reset(self):
        # self.weight = 0
        # self.priority = None
        # self.upper_bound = None
        # self.rng = None
        self.value = None
        self.__expr = None
    
    @property
    def expr(self):
        if self.__expr is None:
            self.__expr = self.expr_lambda()
        return self.__expr

    def evaluate(self):

        if self.value is None:
            try:
                self.value = self.expr.getValue()
            except:
                return None
            
        return self.value
    

    def __str__(self):
        return f"\t{self.name}: \t{" "*(35-len(self.name))}{self.evaluate()} \t(w={self.weight}, pr={self.priority}, UB={self.upper_bound})"

    

class Instance:
    
    def __init__(self, name):

        self.name = name
        
        dias, turnos, horarios, bloques_horario, grupos, materias, profesores, superposicion, superposicion_electivas, num_salones = read_json_instance(f"instances/{name}.json")
        
        self.dias : list[Dia] = dias
        self.turnos = turnos
        self.horarios : list[Horario] = horarios
        self.bloques_horario : dict[tuple, BloqueHorario] = bloques_horario
        self.grupos : list[Grupo] = grupos
        self.materias : list[Materia] = materias
        self.profesores : list[Profesor] = profesores
        self.superposicion : dict[tuple, Superposicion] = superposicion
        self.superposicion_electivas : dict[tuple, Superposicion] = superposicion_electivas
        self.anios = list(set([g.anio for g in grupos if g.anio != None]))

        self.dias_ids = [d.id for d in dias]
        self.horarios_ids = [h.id for h in horarios]
        self.bloques_horario_ids = [b for b in bloques_horario]
        self.profesores_ids = [p.id for p in profesores]
        self.grupos_ids = [g.id for g in grupos]
        self.materias_ids = [m.id for m in materias]

        self.num_salones = num_salones

        self.model : gp.Model = None
        self.feasibility = None
        self.exec_time = None

        self.u_dict : dict[tuple, u] = {}
        self.v_dict : dict[tuple, v] = {}
        self.w_dict : dict[tuple, w] = {}
        self.x_dict : dict[tuple, x] = {}
        self.y_dict : dict[tuple, y] = {}
        self.z_dict : dict[tuple, z] = {}

        self.objectives = [
            Objective(0, "except_timeslots", self.obj_horarios_excepcionales_gral),
            Objective(1, "elective_overlap", self.obj_superposicion_electivas),
            Objective(2, "prof_days", self.obj_min_max_dias),
            Objective(3, "preference", self.obj_prioridades),
        ]


    def json_params(self):

        if self.exec_time is None and self.model:
            self.exec_time = self.model.Runtime

        return {
            "instance": self.name,
            "method": self.params.method,
            "exec_time": self.exec_time,
            "objectives": [
                {
                    "name": obj.name,
                    "weight": obj.weight,
                    "priority": obj.priority,
                    "upper_bound": obj.upper_bound,
                    "rng": obj.rng,
                    "value": obj.evaluate(),
                } for obj in self.objectives
            ]
        }


    def solve(self, params):

        # parametros: input_file, output_file, time_limit, mip_gap
            
        self.params = params

        match self.params.method:
            
            case "weighted_sum":

                for obj in self.objectives:
                    obj.set_params(weight=self.params.weights[obj.id], upper_bound=self.params.upper_bounds[obj.id])

            case "augmecon":

                for obj in self.objectives:
                    obj.set_params(priority=self.params.priorities[obj.id], rng=self.params.ranges[obj.id], upper_bound=self.params.upper_bounds[obj.id])
            
            case "lexicographic":

                for obj in self.objectives:
                    obj.set_params(priority=self.params.priorities[obj.id])
                
                self.lexicographic_solve()


        if self.params.method != "lexicographic":

            self.model: gp.Model = gp.Model(self.name)

            self.compile_variables()
            self.compile_constraints()
            self.compile_objectives()
            
            try:
                self.optimize()
            except gp.GurobiError as e:
                print(f"Gurobi Error during optimization: {e}")
                self.feasibility = False
                return
        
        self.feasibility = True

        if self.params.output_file is None or self.params.output_file.lower() != "none":
            print("Saving solution...")
            save_solution_json(self.u_dict, self.v_dict, self.w_dict, self.output_file, self.json_params())
            
        # return Experiment(self, self.output_file)


    def lexicographic_solve(self):

        
        lex_objectives = [o for o in self.objectives if o.priority is not None]
        lex_objectives.sort(key=lambda o: o.priority)

        upper_bounds = [None]*4
        exec_time = 0

        aux_instance = None

        i=0

        for obj in lex_objectives:
            i+=1

            print(f"\n{i}. Optimizing objective {obj.name} with upper bounds {upper_bounds}...\n")

            weights = [0]*4
            weights[obj.id] = 1
            
            ns = argparse.Namespace(instance = self.name, 
                                method = "weighted_sum",
                                time_limit = self.params.time_limit - exec_time,
                                mip_gap = self.params.mip_gap,
                                output_file = "None",
                                weights = weights,
                                upper_bounds = upper_bounds
                                )
            
            aux_instance = Instance(self.name)
            aux_instance.solve(ns)
            
            obj_value = aux_instance.objectives[obj.id].evaluate()

            exec_time += aux_instance.model.Runtime
            
            obj.upper_bound = aux_instance.objectives[obj.id].upper_bound
            obj.value = obj_value

            upper_bounds[obj.id] = obj_value


        self.exec_time = exec_time

        self.u_dict = aux_instance.u_dict
        self.v_dict = aux_instance.v_dict
        self.w_dict = aux_instance.w_dict

        for obj in self.objectives:
            if obj.priority is None:
                obj.value = aux_instance.objectives[obj.id].evaluate()


    def set_starting_solution(self, solution_path):

        print("Setting starting solution...")

        data = read_json_solution(solution_path)

        for mb_pair, var in self.u_dict.items():
            var.variable.Start = 1 if mb_pair in data["u_dict"] else 0
            
        for md_pair, var in self.v_dict.items():
            var.variable.Start = 1 if md_pair in data["v_dict"] else 0

        for mp_pair, var in self.w_dict.items():
            var.variable.Start = 1 if mp_pair in data["w_dict"] else 0


    def compile_variables(self):

        print("Compiling variables...")

        for obj in self.objectives:
            if obj.upper_bound != None:
                obj.slack = self.model.addVar(lb=0, vtype=GRB.INTEGER, name=f"slack_{obj.name}")

        for m in self.materias:
            for b_id in self.bloques_horario:
                new_u = u(m, self.bloques_horario[b_id])
                new_u.variable = self.model.addVar(vtype=GRB.BINARY, name=str(new_u))
                self.u_dict[(m.id, b_id)] = new_u
                
        for m in self.materias:
            for d in self.dias:
                new_v = v(m, d)
                new_v.variable = self.model.addVar(vtype=GRB.BINARY, name=str(new_v))
                self.v_dict[(m.id, d.id)] = new_v
                

        for m in self.materias:
            for p in self.profesores:
                new_w = w(m, p)
                new_w.variable = self.model.addVar(vtype=GRB.BINARY, name=str(new_w))
                self.w_dict[(m.id, p.id)] = new_w

        for g in self.grupos:
            for b_id in self.bloques_horario:
                new_x = x(g, self.bloques_horario[b_id])
                new_x.variable = self.model.addVar(vtype=GRB.BINARY, name=str(new_x))
                self.x_dict[(g.id, b_id)] = new_x

        for p in self.profesores:
            for b_id in self.bloques_horario:
                new_y = y(p, self.bloques_horario[b_id])
                new_y.variable = self.model.addVar(vtype=GRB.BINARY, name=str(new_y))
                self.y_dict[(p.id, b_id)] = new_y

        for p in self.profesores:
            for d in self.dias:
                new_z = z(p, d)
                new_z.variable = self.model.addVar(vtype=GRB.BINARY, name=str(new_z))
                self.z_dict[(p.id, d.id)] = new_z
    

    def compile_constraints(self):

        print("Compiling constraints...")
        
        # ### Seleccionar restricciones
        
        # 1) restricciones basicas para cada materia
        self.constr_carga_horaria()         # asegurar carga horaria semanal (ej: 5 horas por semana)
        self.constr_dias_materia()          # cantidad de dias por semana (ej: 2 dias por semana)
        self.constr_max_min_horas()         # maximo y minimo de horas por dia (ej: 5 horas total en 2 dias -> maximo 3 y minimo 2 por dia)
        self.constr_turnos_materia()        # fijar materia a turno de horarios (ej: solo horarios de la mañana)
        self.constr_horas_consecutivas()    # horas consecutivas dentro de un dia (ej: clase teórica de 3 horas seguidas)
        self.constr_dias_consecutivos()     # evitar dias consecutivos para una misma materia (ej: no lunes y martes)
        
        # 2) restricciones basicas para cada profesor
        self.constr_limitar_profesores_materia()  # limitar asignacion de profesores a lista que pueden dictar cada materia
        self.constr_cantidad_profesores()         # cantidad de profesores que dictan una misma materia en conjunto
        self.constr_no_disponible_profesor()      # evitar bloques horarios donde un profesor no esta disponible
        self.constr_grupos_max_profesor()         # limitar cantidad de grupos de una misma materia que tienen al mismo profesor

        # 3) definicion de variables auxiliares
        self.constr_definir_y()     # definicion de variable "y" (asignacion horario-profesor)
        self.constr_definir_z()     # definicion de variable "z" (asignacion dia-profesor)
        self.constr_definir_x()     # definicion de variable "x" (asignacion grupo-horario)

        # 4) restricciones sobre interaccion entre horarios
        self.constr_superposicion()           # evitar superposicion entre materias (no electivas) de un mismo grupo
        self.constr_unica_materia_profesor()  # evitar superposicion de materias dictadas por un mismo profesor

        # 5) restricciones particulares (casi soft-constraints)
        self.constr_cantidad_salones()      # limitar materias simultaneas a la cantidad de salones disponibles
        self.constr_teo_prac()              # al menos una hora de teorico debe preceder al practico correspondiente
        self.constr_horas_puente_grupos()   # evitar horas puente por grupo


        # self.constr_horarios_excepcionales(7, 1)   # (opcional) limitar cantidad de horas excepcionales

        for obj in self.objectives:
            if obj.upper_bound is not None:
                self.model.addConstr(obj.expr + obj.slack == obj.upper_bound, name=f"ub_{obj.name}")


    def compile_objectives(self):

        print("Compiling objectives...")

        OBJ = gp.QuadExpr()

        if self.params.method == "augmecon":

            # sort objectives by priority DESC:
            self.objectives.sort(key=lambda o: o.priority if o.priority is not None else float('inf'), reverse=True)

            OBJ += self.objectives[0].expr  # first objective without slack

            eps = -self.params.epsilon if hasattr(self.params, "epsilon") else -0.01  # for maximization of slack when sense is minimization
            for obj in self.objectives[1:]:  # from second objective onwards
                if obj.priority != None:
                    OBJ += eps * obj.slack/obj.rng
                    eps /= 10
        else:
            for obj in self.objectives:
                if obj.weight > 0:
                    OBJ += obj.weight * obj.expr
        
        self.model.setObjective(OBJ, GRB.MINIMIZE)

            

    def optimize(self):

        if hasattr(self.params, "starting_solution") and self.params.starting_solution is not None:
            self.set_starting_solution(self.params.starting_solution)

        if hasattr(self.params, "time_limit"):
            self.model.setParam("TimeLimit", self.params.time_limit)

        if hasattr(self.params, "mip_gap"):
            self.model.Params.MIPGap = self.params.mip_gap

        # print(self.json_params())

        self.model.update()
        self.model.optimize()

        # print(f"Final status: {self.model.Status}")

        if self.model.Status in [GRB.INFEASIBLE, GRB.INF_OR_UNBD, GRB.UNBOUNDED]:
            raise gp.GurobiError("Model is infeasible or unbounded")

        # for i in range(self.model.SolCount):
        #     self.model.params.SolutionNumber = i
        #     save_solution_json(self.u_dict, self.v_dict, self.w_dict, f"results/{self.name}/{self.params.method}/sol_{i}.json", self.json_params())
        
        for obj in self.objectives:
            print(obj)

    @property
    def output_file(self):

        if hasattr(self.params, "output_file") and self.params.output_file is not None:
            return self.params.output_file

        if self.params.method == "augmecon":
            f1 = self.objectives[0]
            bounds = "_".join([f"{int(obj.upper_bound)}" for obj in self.objectives[1:]])
            return f"results/{self.name}/augmecon/sol_{f1.name}_{bounds}.json"
        else:
            return f"results/{self.name}/{self.params.method}/sol.json"
    

    # #### (3.1.1) superposicion (redundante con la definicion de la variable x)
    def constr_superposicion(self):

        # self.model.addConstrs(gp.quicksum(self.u_dict[m1, b].variable * self.superposicion[(m1, m2)].value * self.u_dict[m2, b].variable
        #                         for m1 in self.materias_ids for m2 in self.materias_ids)
        #             == 0 for b in self.bloques_horario_ids)

        for b in self.bloques_horario_ids:
            for m1 in self.materias_ids:
                for m2 in self.materias_ids:
                    if m1 != m2 and self.superposicion[(m1, m2)].value == 1:
                        self.model.addConstr(self.u_dict[m1, b].variable + self.u_dict[m2, b].variable <= 1)
        
    # #### (3.1.2) cubrir carga horaria para cada materia
    def constr_carga_horaria(self):
        for m in self.materias:
            self.model.addConstr(gp.quicksum(self.u_dict[m.id, b].variable for b in self.bloques_horario_ids) == m.carga_horaria)

    # ##### (3.1.3.1) fijar cantidad de dias por materia
    def constr_dias_materia(self):
        for m in self.materias:
            self.model.addConstr(gp.quicksum(self.v_dict[m.id, d].variable for d in self.dias_ids) == m.cantidad_dias)

    # ##### (3.1.3.2) fijar maximo y minimo de horas por dia
    def constr_max_min_horas(self):

        for m in self.materias:

            self.model.addConstrs(gp.quicksum(self.u_dict[(m.id,(d,h))].variable for h in self.horarios_ids)
                            <= m.horas_max() * self.v_dict[m.id,d].variable for d in self.dias_ids)
            
            self.model.addConstrs(gp.quicksum(self.u_dict[(m.id,(d,h))].variable for h in self.horarios_ids)
                            >= m.horas_min() * self.v_dict[m.id,d].variable for d in self.dias_ids)
        
    # ##### (3.1.3.3) fijar materia a turno de horarios
    def constr_turnos_materia(self):

        for m in self.materias:
            no_bloques_materia_ids = [b for b in self.bloques_horario if b not in bloques_horario_materia(m, self.bloques_horario)]
            self.model.addConstrs(self.u_dict[m.id, b].variable == 0 for b in no_bloques_materia_ids)

    # #### (3.1.4) horas consecutivas dentro de un dia
    def constr_horas_consecutivas(self):

        self.model.addConstrs(gp.quicksum(self.u_dict[(m,(d,h))].variable for h in self.horarios_ids)
                    - gp.quicksum(self.u_dict[(m,(d,h))].variable * self.u_dict[(m,(d,h+1))].variable for h in self.horarios_ids[0:-1])
                    == self.v_dict[m, d].variable for m in self.materias_ids for d in self.dias_ids)
    
    # #### (3.1.5) evitar dias consecutivos para una misma materia  
    def constr_dias_consecutivos(self):
        mats_dias_consecutivos = [mat for mat in self.materias if mat.dias_consecutivos]
        self.model.addConstrs(gp.quicksum(self.v_dict[m,d].variable * self.v_dict[m,d+1].variable for d in self.dias_ids[0:-1]) == 0
                    for m in [mat.id for mat in self.materias if not mat in mats_dias_consecutivos])

    # #### (3.2.1) indisponibilidad
    def constr_no_disponible_profesor(self):
        for m in self.materias:
            for p in m.profesores:
                for b in p.no_disponible:
                    self.model.addConstr(self.u_dict[m.id, b.id].variable * self.w_dict[m.id, p.id].variable == 0)

    # #### (3.2.2) unica materia por profesor para un mismo bloque horario
    def constr_unica_materia_profesor(self):

        p_grupos_simultaneos = [p.nombre for p in self.profesores if p.cursos_simultaneos]

        for p in self.profesores:
            mats_p = [m.id for m in materias_profesor(p, self.materias)]
            if p.nombre not in p_grupos_simultaneos:
                self.model.addConstrs(gp.quicksum(self.u_dict[m, b].variable * self.w_dict[m, p.id].variable for m in mats_p)
                        <= 1 for b in self.bloques_horario_ids)
            else: # caso particular
                self.model.addConstrs(gp.quicksum(self.u_dict[m, b].variable * self.w_dict[m, p.id].variable for m in mats_p)
                        <= 2 for b in self.bloques_horario_ids)

    # ##### (3.2.3.1) limitar profesores a lista
    def constr_limitar_profesores_materia(self):
        
        for m in self.materias:
            for p in self.profesores:
                if p not in m.profesores:
                    self.model.addConstr(self.w_dict[m.id, p.id].variable == 0)

    # ##### (3.2.3.2) cantidad de profesores para una misma materia
    def constr_cantidad_profesores(self):
        for m in self.materias:
            self.model.addConstr(gp.quicksum(self.w_dict[m.id, p].variable for p in self.profesores_ids) == m.cantidad_profesores)

    # #### (3.2.4) carga horaria por docente: limitar la cantidad de materias por profesor
    def constr_grupos_max_profesor(self):
        for p in self.profesores:
            for l in p.lista_materias:
                mats = [m for m in self.materias if m.nombre == l["nombre_materia"]] # subject_id
                if len(mats) > 0:
                    self.model.addConstr(gp.quicksum(self.w_dict[m.id, p.id].variable for m in mats) <= l["grupos_max"])
            
    # #### (3.2.5) definicion de variable "y" (asignacion horario-profesor)
    def constr_definir_y(self):

        for p in self.profesores:
            uw_vars = {} # variable auxiliar = u_mb·w_mp
            mats_p = [m for m in self.materias if p in m.profesores]
            for m in mats_p:
                # for b_id in bloques_horario_materia(m, bloques_horario):
                for b_id in self.bloques_horario_ids:
                    uw_vars[m.id,b_id] = self.model.addVar(vtype=GRB.INTEGER, name=str(m)+str(b_id)+str(p)+"_uw")
                    self.model.addConstr(uw_vars[m.id,b_id] == self.u_dict[m.id,b_id].variable * self.w_dict[m.id,p.id].variable)
            
            mats_p_ids = [m.id for m in mats_p]
            self.model.addConstrs(self.y_dict[p.id,b_id].variable ==
                        gp.or_(uw_vars[m_id,b_id] for m_id in mats_p_ids)
                        for b_id in self.bloques_horario_ids)
        
    # #### (3.2.6) definicion de variable "z" (para dias con clase por profesor)
    def constr_definir_z(self):
        
        self.model.addConstrs(self.z_dict[p,d].variable ==
                    gp.or_(self.y_dict[p,(d,h)].variable for h in self.horarios_ids)
                    for p in self.profesores_ids for d in self.dias_ids)

    # #### (3.3.1) cantidad de salones
    def constr_cantidad_salones(self):
        self.model.addConstrs(gp.quicksum(self.u_dict[m, b].variable for m in self.materias_ids) <= self.num_salones for b in self.bloques_horario_ids)

    # #### (3.3.3) practico despues del teorico
    def constr_teo_prac(self):
        teo = []    # array con IDS de materias de teorico
        prac = []    # array con IDS de materias de practico
        vars1erHora = {}
        scaled_u_dict = {}

        for t in [m for m in self.materias if m.teo_prac == "teo"]:
            teo.append(t.id)
            vars1erHora[t.id] = self.model.addVar(vtype=GRB.INTEGER, name=str(t)+"_1er_hora")

        for p in [m for m in self.materias if m.teo_prac == "prac"]:
            prac.append(p.id)
            vars1erHora[p.id] = self.model.addVar(vtype=GRB.INTEGER, name=str(p)+"_1er_hora")

        L = len(self.bloques_horario_ids)
        for m in teo + prac:
            for b in range(0, L):
                scaled_u_dict[m, self.bloques_horario_ids[b]] = self.model.addVar(vtype=GRB.INTEGER, name=str(m)+"scaled_u")
                self.model.addConstr(scaled_u_dict[m, self.bloques_horario_ids[b]] == (L - b) * self.u_dict[m, self.bloques_horario_ids[b]].variable)

        for m_id in vars1erHora:
            self.model.addConstr(vars1erHora[m_id] ==
                            # gp.max_( (L - b) * u_dict[m_id, bloques_horario_ids[b]].variable
                            gp.max_( scaled_u_dict[m_id, self.bloques_horario_ids[b]]
                                    for b in range(0, L)) )


        if len(teo) == len(prac):
            self.model.addConstrs(vars1erHora[teo[m]] >= vars1erHora[prac[m]] for m in range(0, len(teo)))
        else:
            print("Error: no coinciden cantidad de teoricos con practicos")

    # #### (3.3.4) Limitar horas excepcionales
    def constr_horarios_excepcionales(self, E_teo, E_prac):

        # suma_horas_prac = gp.LinExpr()
        # suma_horas_teo = gp.LinExpr()
        
        # for m in self.materias:
        #     for b in self.bloques_horario.values():
        #         if b.horario.es_excepcional(m):
        #             if m.teo_prac == "prac":
        #                 suma_horas_prac += self.u_dict[m.id, b.id].variable
        #             else:
        #                 suma_horas_teo += self.u_dict[m.id, b.id].variable
        
        # self.model.addConstr(suma_horas_prac <= E_prac, "horas_excepcionales_prac")
        # self.model.addConstr(suma_horas_teo <= E_teo, "horas_excepcionales_teo")

        self.model.addConstr(self.obj_horarios_excepcionales(prac=True) <= E_prac, "horas_excepcionales_prac_obj")
        self.model.addConstr(self.obj_horarios_excepcionales(prac=False) <= E_teo, "horas_excepcionales_teo_obj")
        
    # #### (3.4.1) definicion de variable "x"
    def constr_definir_x(self):

        for g in self.grupos:
            # gr_mats_ids = [m.id for m in materias_grupo(g, materias)]
            gr_mats_ids = [m.id for m in materias_grupo(g, [m for m in self.materias if not m.electiva])] # filtrar materias electivas
            self.model.addConstrs(self.x_dict[g.id, b].variable == gp.or_(self.u_dict[m, b].variable for m in gr_mats_ids) for b in self.bloques_horario_ids)

    # #### (3.4.2) evitar horas puente por grupo
    def constr_horas_puente_grupos(self):

        for g in self.grupos:
            self.model.addConstrs(gp.quicksum(self.x_dict[(g.id,(d,h))].variable for h in self.horarios_ids)
                        - gp.quicksum(self.x_dict[(g.id,(d,h))].variable * self.x_dict[(g.id,(d,h+1))].variable for h in self.horarios_ids[0:-1])
                        <= 1 for d in self.dias_ids)

    # ## (4) Funcion Objetivo

    # ### (4.1) Prioridad horaria de los docentes
    def obj_prioridades(self):

        OBJ1 = gp.QuadExpr()

        count = 0
        for m in self.materias:
            for p in self.profesores:
                for pr in p.prioridades:
                    b = pr.bloque_horario
                    A = pr.value
                    OBJ1 += A * self.w_dict[m.id, p.id].variable * self.u_dict[m.id, b.id].variable
                    count += 1

        # print("obj_prioridades terms:", count)

        return OBJ1

    # ### (4.2) Minimizar/Maximizar dias con clase por profesor
    def obj_min_max_dias(self):

        OBJ2 = gp.LinExpr()

        count = 0
        for p in self.profesores:
            
            D_p = gp.quicksum(self.z_dict[p.id,d].variable for d in self.dias_ids)

            # filtrar si el profesor prefiere minimizar o maximizar o ninguno
            match p.min_max_dias:
                case "min":
                    OBJ2 += D_p
                    count += len(self.dias_ids)
                case "max":
                    OBJ2 -= D_p
                    count += len(self.dias_ids)
                case None:
                    pass

        # print("obj_min_max_dias terms:", count)

        return OBJ2

    # ### (4.3) Minimizar horas puente por grupo (se incluye directamente como restriccion)
    def obj_horas_puente_grupos(self):
        OBJ3 = gp.QuadExpr()

        count = 0
        for g in self.grupos:
            for d in self.dias_ids:
                OBJ3 += - gp.quicksum(self.x_dict[(g.id,(d,h))].variable * self.x_dict[(g.id,(d,h+1))].variable for h in self.horarios_ids[0:-1])
                count += 1

        # print("obj_horas_puente_grupos terms:", count)

        return OBJ3

    # ### (4.4) Minimizar superposicion de electivas
    def obj_superposicion_electivas(self):
        OBJ4 = gp.QuadExpr()

        count = 0
        for m1 in electivas(self.materias):
            for m2 in electivas(self.materias):
                for b in self.bloques_horario_ids:
                    OBJ4 += self.u_dict[m1.id, b].variable * self.superposicion_electivas[m1.id, m2.id].value * self.u_dict[m2.id, b].variable
                    count += 1

        # print("obj_superposicion_electivas terms:", count)

        return OBJ4

    # ### (4.5) Evitar horas excepcionales
    def obj_horarios_excepcionales(self, prac=False):

        objetivo = gp.QuadExpr()

        count = 0
        for m in self.materias:

            if (not prac and m.teo_prac == "prac") or (prac and m.teo_prac != "prac"):
                continue

            for b in self.bloques_horario.values():
                if b.horario.es_excepcional(m):
                    objetivo += self.u_dict[m.id, b.id].variable
                    count += 1
            
        # print("obj_horarios_excepcionales terms:", count)

        return objetivo
    
    def obj_horarios_excepcionales_gral(self):
        return self.obj_horarios_excepcionales(prac=True) + 2*self.obj_horarios_excepcionales(prac=False)




class Experiment:

    def __init__(self, instance: Instance, solution_file):
        
        with open(solution_file, 'r', encoding='utf-8') as f:
            self.solution = json.load(f)
        self.assignments = read_json_solution(solution_file)
        self.instance = instance
        
    @property
    def exec_time(self):
        return self.solution["info"]["exec_time"]
    
    @property
    def objectives(self):
        return self.solution["info"]["objectives"]

    @property
    def method(self):
        return self.solution["info"]["method"]
    
    @property
    def priorities(self):
        return [obj["priority"] for obj in self.objectives]


    



if __name__ == "__main__":
 

    #> python exact_solver/runner.py --instance instance_2026sem1 --method weighted_sum --weights 100 20 5 1 

    parser = argparse.ArgumentParser()
    parser.add_argument("--instance", type=str, required=True)

    parser.add_argument("--method", type=str, required=True)
    
    parser.add_argument("--time_limit", type=int, default=2*60*60)
    parser.add_argument("--mip_gap", type=float, default=0.0001)
    parser.add_argument("--epsilon", type=float, default=0.001, help="Epsilon value for Augmecon method")
    
    parser.add_argument("--priorities", nargs='+', type=none_or_float, default=[0,1,2,3], help="Priorities for the objectives, e.g., --priorities 1 2 0 0")
    parser.add_argument("--weights", nargs='+', type=float, help="Weights for the objectives, e.g., --weights 0 1 2 0")
    parser.add_argument("--ranges", nargs='+', type=none_or_float, help="Ranges for the objectives, e.g., --ranges 10 5 3 8")
    parser.add_argument("--upper_bounds", nargs='+', type=none_or_float, default=[None]*4, help="Upper bounds for the objectives, e.g., --upper_bounds 50 20 15 30")

    parser.add_argument("--starting_solution", type=str, default=None, help="Path to a starting solution JSON file")
    parser.add_argument("--output_file", type=str, default=None, help="Path to save the solution JSON file")

    args = parser.parse_args()


    # args = argparse.Namespace(instance = "instance_4th5th_2026sem1", 
    #                             method = "lexicographic",
    #                             time_limit = 2*60*60,
    #                             mip_gap = 0.0001,
    #                             output_file = "None",
    #                             priorities = [0,1,2,3],
    #                             )

    instance_name = args.instance
    instance = Instance(instance_name)

    instance.solve(args)
