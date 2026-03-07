import pandas as pd
import matplotlib.pyplot as plt
import os
import numpy as np

# TikZ-style fonts
plt.rcParams.update({
    "font.family": "serif",
    "mathtext.fontset": "cm",
    "mathtext.rm": "serif",
})

# TikZ/PGFPlots color palette
TIKZ_COLORS = [
    "#1f77b4", "#ff7f0e", "#2ca02c", "#d62728",
    "#9467bd", "#8c564b", "#e377c2", "#7f7f7f",
    "#bcbd22", "#17becf"
]

LINE_STYLES = ["-", "--", "-.", ":"]
MARKERS = ["o", "s", "D", "^", "v", "x", "*"]
FIGURE_SIZE = (4, 3)

# LaTeX PGF backend
plt.rcParams.update({
    "pgf.texsystem": "pdflatex",
    "font.family": "serif",
    "text.usetex": True,
    "pgf.rcfonts": False,
})




def display_stats_table(instances):
    
    rows = []

    for inst in instances:
        total_horas = len(inst.bloques_horario) * len(inst.profesores)
        rows.append({
            "instancia": inst.name,
            "#grupos": len(inst.grupos),
            # "#años": len(inst.anios),
            # "#bloques_horarios": len(inst.bloques_horario),
            "#cursos": len(inst.materias),
            "#electivas": len([m for m in inst.materias if m.electiva]),
            "#profesores": len(inst.profesores),
            "#solicitudes_min_dias": f"{round(len([p for p in inst.profesores if p.min_max_dias == "min"])/len(inst.profesores)*100)}%",
            "#carga_horaria_por_grupo": np.mean([sum([m.carga_horaria for m in inst.materias if g in m.grupos]) for g in inst.grupos]),
            # "#electivas_por_grupo": np.mean([len([m for m in inst.materias if m.electiva and g in m.grupos]) for g in inst.grupos]),
            # "#salones": inst.num_salones,
            "%disponibilidad": f"{round(len([pr for p in inst.profesores for pr in p.prioridades if pr.value > 0])/total_horas*100)}%",
            # "%pref=1": f"{round(len([pr for pr in p.prioridades for p in inst.profesores if pr.value == 1])/total_horas*100)}%",
            # "%pref=2": f"{round(len([pr for pr in p.prioridades for p in inst.profesores if pr.value == 2])/total_horas*100)}%",
            # "%pref=3": f"{round(len([pr for pr in p.prioridades for p in inst.profesores if pr.value == 3])/total_horas*100)}%"

        })

    df = pd.DataFrame(rows)
    df = df.round(2)

    return df


def display_objectives_ranges(experiments, instance, objectives):

    df = display_experiments([e for e in experiments if e.instance == instance])

    rows = []

    for obj in objectives:
        # Drop NaN and None values safely
        series = df[f"value {obj}"].dropna()

        if len(series) == 0:
            # Handle case where all values are NaN/None
            max_obj, min_obj, range_obj = np.nan, np.nan, np.nan
        else:
            max_obj = series.max()
            min_obj = series.min()
            range_obj = max_obj - min_obj

        rows.append({
            "objective": obj,
            "max": max_obj,
            "min": min_obj,
            "range": range_obj,
        })

    df2 = pd.DataFrame(rows)
    df2 = df2.round(2)

    return df2

def display_experiments(experiments):
    
    rows = []

    for exp in experiments:
        rows.append({
            "instance": exp.instance.name,
            "exec_time (s)" : exp.exec_time,
        })

        for obj in exp.objectives:
            # if obj["weight"] > 0 or obj["upper_bound"] is not None:
            for arg in ["value"]:
                    # if obj[arg] is not None:
                    rows[-1][f"{arg} {obj["name"]}"] = obj[arg]
                # rows[-1][obj["name"] + "\nupper_bound"] = obj["upper_bound"]

    df = pd.DataFrame(rows)
    df = df.round(2)

    return df

def display_exec_times(experiments):

    df = display_experiments(experiments)

    df_times = df[["instance", "exec_time (s)"]].copy()
    df_times = df_times.groupby("instance").agg(
        mean_time=("exec_time (s)", "mean"),
        max_time=("exec_time (s)", "max"),
        min_time=("exec_time (s)", "min"),
    ).reset_index()

    return df_times.round(2)

def display_payoff_matrix(experiments, instance):

    payoff_exps = [e for e in experiments if e.instance == instance and e.method == "lexicographic"
                          and e.priorities in [
                                [0, 1, 2, 3], 
                                [3, 0, 1, 2], 
                                [2, 3, 0, 1], 
                                [1, 2, 3, 0], 
                              ]]
    payoff_exps.sort(key=lambda e: e.priorities.index(0))
    
    return display_experiments(payoff_exps)[["value except_timeslots", "value elective_overlap", "value prof_days", "value preference"]]


def filtrar_no_dominados(points):
    points = np.array(points)
    n = len(points)
    no_dominados = []
    for i in range(n):
        p = points[i]
        dominado = False
        for j in range(n):
            if i == j:
                continue
            q = points[j]
            # q domina a p si es mejor o igual en todos los objetivos y estrictamente mejor en alguno
            if np.all(q <= p) and np.any(q < p):
                dominado = True
                break
        if not dominado:
            no_dominados.append(p)
    return np.array(no_dominados)

def filtrar_duplicados(points):
    points = np.array(points)
    unique_points = []
    seen = set()
    for p in points:
        p_tuple = tuple(p)
        if p_tuple not in seen:
            seen.add(p_tuple)
            unique_points.append(p)
    return np.array(unique_points)





def plot_two_objectives(objs_to_study, all_points, ax, objectives):
    # Extraer los puntos de los objetivos seleccionados
    new_points = np.array([[p[o] for o in objs_to_study] for p in all_points])
    new_points = filtrar_no_dominados(new_points)

    # Ordenar por el primer objetivo para línea continua
    new_points = new_points[np.argsort(new_points[:,0])]

    # Graficar en el eje dado
    ax.plot(
        new_points[:,0], new_points[:,1],
        color=TIKZ_COLORS[1],
        linestyle=LINE_STYLES[0],
        marker=MARKERS[0],
        label=f"{objectives[objs_to_study[0]]} vs {objectives[objs_to_study[1]]}"
    )
    ax.set_xlabel(objectives[objs_to_study[0]])
    ax.set_ylabel(objectives[objs_to_study[1]])
    ax.grid(True)
    ax.legend(fontsize=8)


def plot3d(df, steps, objectives, ideal, nadir):

    # Crear figura y eje 3D
    fig = plt.figure(figsize=(5, 5))
    ax = fig.add_subplot(111, projection='3d')

    colors = ['red', 'blue', 'green', 'black']

    ax.scatter(ideal[objectives.index("except_timeslots")], ideal[objectives.index("elective_overlap")], ideal[objectives.index("preference")], color='purple', marker='^', s=100, label="Ideal Point")
    ax.scatter(nadir[objectives.index("except_timeslots")], nadir[objectives.index("elective_overlap")], nadir[objectives.index("preference")], color='orange', marker='x', s=100, label="Nadir Point")


    for i, d in enumerate(steps["prof_days"]):
        df_filtrado = df[df['value prof_days'] == d]
        points = df_filtrado[["value except_timeslots", "value elective_overlap", "value preference"]].to_numpy()

        ax.scatter(points[:, 0], points[:, 1], points[:, 2], color=colors[i], marker='o', label=f"Day {d}")

    # Etiquetas de los ejes
    ax.set_xlabel("value except_timeslots")
    ax.set_ylabel("value elective_overlap")
    ax.set_zlabel("value preference")

    # Título
    ax.set_title("3D Scatter Plot of Filtered Values")



def normalize_minmax(points: np.ndarray) -> np.ndarray:
    """
    Normaliza los puntos objetivo al rango [0,1] por dimensión.
    """
    min_vals = np.min(points, axis=0)
    max_vals = np.max(points, axis=0)
    return (points - min_vals) / (max_vals - min_vals)


def pareto_spacing(points: np.ndarray) -> float:
    """
    Calcula el spacing de un frente de Pareto.
    
    Parámetros:
        points (np.ndarray): matriz de forma (n_puntos, n_dimensiones)
    
    Retorna:
        float: valor del spacing
    """

    points = normalize_minmax(points)

    n = points.shape[0]
    # Calcular todas las distancias entre pares de puntos
    dist_matrix = np.linalg.norm(points[:, None, :] - points[None, :, :], axis=2)
    
    # Ignorar la diagonal (distancia a sí mismo = 0)
    np.fill_diagonal(dist_matrix, np.inf)
    
    # Distancia mínima de cada punto a otro
    d_min = np.min(dist_matrix, axis=1)
    
    # Promedio de esas distancias mínimas
    d_mean = np.mean(d_min)
    
    # Spacing
    spacing = np.sum(np.abs(d_min - d_mean)) / n
    return spacing



def pareto_spread(points: np.ndarray, extreme_points: np.ndarray) -> float:
    """
    Calcula el spread de un frente de Pareto.
    
    Parámetros:
        points (np.ndarray): matriz de forma (n_puntos, n_dimensiones)
    
    Retorna:
        float: valor del spread
    """
    points = normalize_minmax(points)
    extreme_points = normalize_minmax(extreme_points)

    interior_points = np.array([p for p in points if not any(np.array_equal(p, q) for q in extreme_points)])  # Filtrar los puntos extremos del frente

    n = interior_points.shape[0]
    # Calcular todas las distancias entre pares de puntos
    dist_matrix = np.linalg.norm(interior_points[:, None, :] - interior_points[None, :, :], axis=2)
    
    # Ignorar la diagonal (distancia a sí mismo = 0)
    np.fill_diagonal(dist_matrix, np.inf)
    
    # Distancia mínima de cada punto a otro
    d_min = np.min(dist_matrix, axis=1)
    
    # Promedio de esas distancias mínimas
    d_mean = np.mean(d_min)

    d_extremos = 0
    for ext in extreme_points:
        # minima distancia entre un extremo y cualquier punto interior
        d_extremos += min(np.linalg.norm(interior_points - ext, axis=1))
    
    # Spread = (suma de distancias consecutivas) / distancia entre extremos
    spread = (d_extremos + np.sum(np.abs(d_min - d_mean))) / (d_extremos + n*d_mean)
    
    return spread
