package pe.utp.dependencias;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Main extends JFrame {
    private final GraphIO io=new GraphIO();
    private final GraphAnalyzer analyzer=new GraphAnalyzer();
    private Graph graph;
    private GraphAnalyzer.Result result;
    private final JTextArea output=new JTextArea();
    private final JLabel status=new JLabel("Importe un grafo o seleccione una carpeta de proyecto.");
    private final JComboBox<String> moduleBox=new JComboBox<>();

    public Main(){
        super("Detección de dependencias críticas — DFS y Warshall");
        setDefaultCloseOperation(EXIT_ON_CLOSE); setSize(1050,720); setLocationRelativeTo(null);
        JPanel root=new JPanel(new BorderLayout(10,10)); root.setBorder(new EmptyBorder(12,12,12,12)); setContentPane(root);
        JLabel title=new JLabel("ANÁLISIS DE DEPENDENCIAS DE SOFTWARE");
        title.setFont(title.getFont().deriveFont(Font.BOLD,20f));
        JLabel subtitle=new JLabel("Grafo dirigido · DFS · Clausura transitiva de Warshall · Impacto de cambios");
        JPanel heading=new JPanel(new GridLayout(2,1,2,2)); heading.add(title); heading.add(subtitle);

        JPanel actions=new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton folder=new JButton("Analizar carpeta JS/TS");
        JButton edgeFile=new JButton("Importar grafo CSV/TXT");
        JButton analyze=new JButton("Ejecutar análisis");
        JButton impact=new JButton("Consultar impacto");
        JButton export=new JButton("Exportar resultados");
        actions.add(folder);actions.add(edgeFile);actions.add(analyze);actions.add(new JLabel("Módulo:"));actions.add(moduleBox);actions.add(impact);actions.add(export);
        JPanel top=new JPanel(new BorderLayout(4,8)); top.add(heading,BorderLayout.NORTH); top.add(actions,BorderLayout.SOUTH);
        root.add(top,BorderLayout.NORTH);

        output.setEditable(false); output.setFont(new Font(Font.MONOSPACED,Font.PLAIN,13)); output.setText(
            "INSTRUCCIONES\n\n1. Analice una carpeta de código JavaScript/TypeScript o importe un archivo de aristas.\n"+
            "2. Pulse «Ejecutar análisis» para aplicar DFS y Warshall.\n"+
            "3. Seleccione un módulo y consulte los módulos que podrían verse afectados por su cambio.\n\n"+
            "Convención: A → B significa que A depende de B. Por tanto, si B cambia, A puede verse afectado.\n");
        root.add(new JScrollPane(output),BorderLayout.CENTER);
        root.add(status,BorderLayout.SOUTH);

        folder.addActionListener(e->chooseFolder());
        edgeFile.addActionListener(e->chooseEdgeFile());
        analyze.addActionListener(e->runAnalysis());
        impact.addActionListener(e->showImpact());
        export.addActionListener(e->export());
    }
    private void chooseFolder(){
        JFileChooser c=new JFileChooser();c.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if(c.showOpenDialog(this)==JFileChooser.APPROVE_OPTION) try{
            graph=io.fromSourceDirectory(c.getSelectedFile().toPath()); afterLoad("Carpeta analizada: "+c.getSelectedFile());
        }catch(Exception ex){error(ex);}
    }
    private void chooseEdgeFile(){
        JFileChooser c=new JFileChooser();
        if(c.showOpenDialog(this)==JFileChooser.APPROVE_OPTION) try{
            graph=io.fromEdgeFile(c.getSelectedFile().toPath()); afterLoad("Grafo importado: "+c.getSelectedFile());
        }catch(Exception ex){error(ex);}
    }
    private void afterLoad(String message){
        moduleBox.removeAllItems();for(String n:graph.names())moduleBox.addItem(n);
        result=null;output.setText(message+"\n\nNodos: "+graph.size()+"\nAristas: "+graph.edgeCount()+
            "\n\nPulse «Ejecutar análisis».");status.setText("Grafo cargado correctamente.");
    }
    private void runAnalysis(){
        if(graph==null){warn("Primero importe un grafo o analice una carpeta.");return;}
        try{
            result=analyzer.analyze(graph);
            StringBuilder s=new StringBuilder();
            s.append("RESULTADOS DEL ANÁLISIS\n=======================\n");
            s.append("Nodos (módulos): ").append(result.nodes()).append("\nAristas (dependencias): ").append(result.edges()).append("\n");
            s.append(String.format(Locale.US,"Densidad: %.6f%n",result.density()));
            s.append("Módulos pertenecientes a ciclos: ").append(result.modulesInCycles()).append("\n");
            s.append("Componentes cíclicos: ").append(result.cyclicComponents()).append("\nMayor componente cíclico: ").append(result.largestCyclicComponent()).append("\n");
            s.append("Aristas de retorno encontradas por DFS: ").append(result.backEdges()).append("\n");
            s.append(String.format(Locale.US,"Dependientes por módulo — mediana: %.2f; media: %.2f; máximo: %d%n",result.medianDependents(),result.meanDependents(),result.maxDependents()));
            s.append(String.format(Locale.US,"Impacto — mediana: %.2f%%; máximo: %.2f%%%n",result.medianImpactPercent(),result.maxImpactPercent()));
            s.append("Tiempos: mediana de 15 ejecuciones, después de 3 calentamientos\n");
            s.append(String.format(Locale.US,"Tiempo DFS (DFS + Tarjan): %.3f ms%nTiempo Warshall: %.3f ms%nTiempo total: %.3f ms%n",result.dfsNanos()/1e6,result.warshallNanos()/1e6,result.totalNanos()/1e6));
            s.append("Memoria teórica: payload booleano de la matriz (no es memoria total JVM): ").append(result.approximateMemoryBytes()).append(" bytes\n");
            s.append("Coincidencia de módulos cíclicos DFS/Warshall: ").append(result.dfsWarshallAgreement()?"SÍ":"NO").append("\n\n");
            s.append("MÓDULOS EN CICLOS\n");result.cycleModules().forEach(x->s.append(" • ").append(x).append("\n"));
            s.append("\nCINCO MÓDULOS CON MAYOR IMPACTO POTENCIAL\n");result.topImpactModules().forEach(x->s.append(" • ").append(x).append("\n"));
            output.setText(s.toString());status.setText("Análisis terminado. DFS y Warshall "+(result.dfsWarshallAgreement()?"coinciden.":"NO coinciden."));
        }catch(Exception ex){error(ex);}
    }
    private void showImpact(){
        if(graph==null||result==null){warn("Ejecute primero el análisis.");return;}
        String selected=(String)moduleBox.getSelectedItem();if(selected==null)return;
        java.util.List<String> affected=analyzer.affectedBy(graph,result.closure(),selected);
        java.util.List<String> deps=analyzer.reachableFrom(graph,result.closure(),selected);
        StringBuilder s=new StringBuilder("ANÁLISIS DE IMPACTO: "+selected+"\n"+"=".repeat(55)+"\n");
        s.append("Módulos de los que depende (directa o indirectamente): ").append(deps.size()).append("\n");
        deps.forEach(x->s.append("  → ").append(x).append("\n"));
        s.append("\nMódulos que dependen de este módulo y podrían verse afectados: ").append(affected.size()).append("\n");
        affected.forEach(x->s.append("  ← ").append(x).append("\n"));
        output.setText(s.toString());
    }
    private void export(){
        if(graph==null||result==null){warn("Ejecute primero el análisis.");return;}
        JFileChooser c=new JFileChooser();c.setSelectedFile(new File("informe_dependencias.json"));
        if(c.showSaveDialog(this)==JFileChooser.APPROVE_OPTION)try{
            Path p=c.getSelectedFile().toPath();io.exportReport(graph,result,p);
            Path csv=p.resolveSibling("grafo_dependencias.csv");io.exportEdges(graph,csv);
            status.setText("Exportados informe JSON y grafo CSV.");
            JOptionPane.showMessageDialog(this,"Archivos guardados:\n"+p+"\n"+csv);
        }catch(Exception ex){error(ex);}
    }
    private void error(Exception ex){JOptionPane.showMessageDialog(this,ex.getMessage(),"Error",JOptionPane.ERROR_MESSAGE);status.setText("Ocurrió un error.");}
    private void warn(String s){JOptionPane.showMessageDialog(this,s,"Aviso",JOptionPane.WARNING_MESSAGE);}
    public static void main(String[] args){SwingUtilities.invokeLater(()->new Main().setVisible(true));}
}
