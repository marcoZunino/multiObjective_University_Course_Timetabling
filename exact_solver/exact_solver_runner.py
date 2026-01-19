
from gurobipy import *

from load_data import *
from entities import *
from variables import *


# parametros: input_file, output_file, time_limit, mip_gap

class Instance:
    
    def __init__(self, input_file):
        
        dias, turnos, horarios, bloques_horario, grupos, materias, profesores, superposicion, superposicion_electivas = read_json_instance(input_file)
        
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

        self.num_salones = 13  # parametro fijo por ahora  #TODO: pasarlo como input

        self.model : gp.Model = None

        self.u_dict : dict[tuple, u] = {}
        self.v_dict : dict[tuple, v] = {}
        self.w_dict : dict[tuple, w] = {}
        self.x_dict : dict[tuple, x] = {}
        self.y_dict : dict[tuple, y] = {}
        self.z_dict : dict[tuple, z] = {}

        self.objectives : dict[str, tuple] = {}



    def solve(self):
            
        self.model: gp.Model = gp.Model("timetable")

        self.compile_variables()
        self.compile_constraints()
        self.compile_objectives()

        self.optimize()




    def compile_variables(self):

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
        
        print("Variables:", len(self.u_dict) + len(self.v_dict) + len(self.w_dict) + len(self.x_dict) + len(self.y_dict) + len(self.z_dict))


    def compile_constraints(self):
        
        # ### Seleccionar restricciones
        self.constr_superposicion()
        self.constr_carga_horaria()
        self.constr_dias_materia()
        self.constr_max_min_horas()

        self.constr_turnos_materia()
        self.constr_horas_consecutivas()
        self.constr_dias_consecutivos()
        
        self.constr_no_disponible_profesor()
        self.constr_unica_materia_profesor()
        self.constr_limitar_profesores_materia()
        self.constr_cantidad_profesores()
        self.constr_grupos_max_profesor()

        self.constr_definir_y()
        self.constr_definir_z()

        self.constr_cantidad_salones()
        self.constr_teo_prac()

        self.constr_definir_x()
        self.constr_horas_puente_grupos()


        # if ...  #TODO
        self.constr_horarios_excepcionales(7, 1)   # (opcional) limitar cantidad de horas excepcionales

        print("Restricciones:", self.model.NumConstrs)


    def compile_objectives(self):
        
        self.objectives = { # name : (expr, weight)
            
            "prioridades" : (self.obj_prioridades(), 0), # weight = 1
            "min_max_dias" : (self.obj_min_max_dias()[0], 1), # weight = 5
            "superposicion_electivas" : (self.obj_superposicion_electivas(), 0), # weight = 10
            "horarios_excepcionales (practico)" : (self.obj_horarios_excepcionales(prac=True), 0), # weight = 50
            "horarios_excepcionales" : (self.obj_horarios_excepcionales(prac=False), 0), # weight = 100
        }

        OBJ = gp.QuadExpr()
        for name in self.objectives:
            expr, weight = self.objectives[name]
            OBJ += weight * expr

        self.model.setObjective(OBJ, GRB.MINIMIZE)

        print("Objectives:", sum([len(str(self.objectives[name][0]).split('+')) for name in self.objectives]))
            

    def optimize(self):

        self.model.setParam("TimeLimit", 40*60)
        self.model.Params.MIPGap = 0.01/100

        self.model.optimize()

        if not self.model.Status == GRB.INFEASIBLE:
            print('Obj: %g' % self.model.ObjVal)
            for name in self.objectives:
                expr, weight = self.objectives[name]
                print(f"\t{name}: \t{" "*(35-len(name))}{round(expr.getValue())} \t(peso={weight})")

            save_solution_json(self.u_dict, self.v_dict, self.w_dict, "../results/solution.json")   


    # #### (3.1.1) superposicion (redundante con la definicion de la variable x)
    def constr_superposicion(self):

        self.model.addConstrs(gp.quicksum(self.u_dict[m1, b].variable * self.superposicion[(m1, m2)].value * self.u_dict[m2, b].variable
                                for m1 in self.materias_ids for m2 in self.materias_ids)
                    == 0 for b in self.bloques_horario_ids)
        
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

        for p in [p for p in self.profesores if p.nombre not in p_grupos_simultaneos]:
            self.model.addConstrs(gp.quicksum(self.u_dict[m, b].variable * self.w_dict[m, p.id].variable for m in self.materias_ids)
                        <= 1 for b in self.bloques_horario_ids)
            
        # caso particular
        for p in [p for p in self.profesores if p.nombre in p_grupos_simultaneos]:
            self.model.addConstrs(gp.quicksum(self.u_dict[m, b].variable * self.w_dict[m, p.id].variable for m in self.materias_ids)
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

        suma_horas_prac = gp.LinExpr()
        suma_horas_teo = gp.LinExpr()
        
        for m in self.materias:
            for b in self.bloques_horario.values():
                if b.horario.es_excepcional(m):
                    if m.teo_prac == "prac":
                        suma_horas_prac += self.u_dict[m.id, b.id].variable
                    else:
                        suma_horas_teo += self.u_dict[m.id, b.id].variable
        
        self.model.addConstr(suma_horas_prac <= E_prac, "horas_excepcionales_prac")
        self.model.addConstr(suma_horas_teo <= E_teo, "horas_excepcionales_teo")
        
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

        print("obj_prioridades terms:", count)

        return OBJ1

    # ### (4.2) Minimizar/Maximizar dias con clase por profesor
    def obj_min_max_dias(self):

        OBJ2 = gp.LinExpr()
        DP = {}

        count = 0
        for p in self.profesores:
            
            D_p = gp.quicksum(self.z_dict[p.id,d].variable for d in self.dias_ids)
            DP[p.id] = D_p

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

        print("obj_min_max_dias terms:", count)

        return OBJ2, DP

    # ### (4.3) Minimizar horas puente por grupo (se incluye directamente como restriccion)
    def obj_horas_puente_grupos(self):
        OBJ3 = gp.QuadExpr()

        count = 0
        for g in self.grupos:
            for d in self.dias_ids:
                OBJ3 += - gp.quicksum(self.x_dict[(g.id,(d,h))].variable * self.x_dict[(g.id,(d,h+1))].variable for h in self.horarios_ids[0:-1])
                count += 1

        print("obj_horas_puente_grupos terms:", count)

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

        print("obj_superposicion_electivas terms:", count)

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
            
        print("obj_horarios_excepcionales terms:", count)

        return objetivo


if __name__ == "__main__":
    instance = Instance("instances/instance_2026sem1.json")
    instance.solve()