# Dependencias críticas — Java

Aplicación Java Swing para analizar grafos dirigidos de dependencias JS/TS mediante DFS, componentes fuertemente conexas con Tarjan y clausura transitiva de Warshall.

## Requisitos
- JDK 17 o superior.
- Compatible con Eclipse y NetBeans como proyecto Java estándar.

## Ejecución
Abra el proyecto en el IDE y ejecute `pe.utp.dependencias.Main`.

## Metodología de medición
La clase `GraphAnalyzer` realiza 10 ejecuciones de calentamiento y reporta la mediana de 30 ejecuciones medidas. El tiempo DFS incluye el recorrido DFS para aristas de retorno y Tarjan para identificar todos los vértices que pertenecen a componentes cíclicas. Warshall se mide por separado. El tiempo total comprende el análisis y el cálculo de métricas.

El valor `approximateMemoryBytes` es únicamente el payload teórico de la matriz booleana (n × n bytes); no representa el heap total ni el pico de memoria del proceso JVM. Para medir bytes asignados durante Warshall se incluye `BenchmarkRunner`, que usa `ThreadMXBean`; esa cifra incluye la matriz y las asignaciones temporales realizadas por el método, y tampoco equivale a memoria máxima residente.

## Benchmark de los ocho proyectos
Compile el proyecto y ejecute:

```bash
java -Xms512m -Xmx4g -cp bin pe.utp.dependencias.BenchmarkRunner RUTA_A_REPOS
```

La ruta debe contener las carpetas `express`, `axios`, `redux`, `zustand`, `core`, `execa`, `eslint` y `webpack` con los commits fijados en el estudio. El benchmark imprime una línea CSV por proyecto.

## Alcance
El extractor reconoce dependencias estáticas JS/TS según su implementación léxica. No es un parser completo del lenguaje; alias complejos, imports calculados y construcciones sintácticas no reconocidas pueden quedar fuera. Los resultados deben interpretarse según esta limitación.
