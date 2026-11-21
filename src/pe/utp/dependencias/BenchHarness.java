package pe.utp.dependencias;
import java.nio.file.*;import java.util.*;
public class BenchHarness {
 static double med(long[] x){Arrays.sort(x);return x[x.length/2]/1e6;}
 public static void main(String[] a)throws Exception{
  Graph g=new GraphIO().fromSourceDirectory(Path.of(a[0])); GraphAnalyzer z=new GraphAnalyzer();
  for(int i=0;i<5;i++)z.analyze(g);
  long[] d=new long[30],w=new long[30],t=new long[30]; GraphAnalyzer.Result r=null;
  for(int i=0;i<30;i++){r=z.analyze(g);d[i]=r.dfsNanos();w[i]=r.warshallNanos();t[i]=r.totalNanos();}
  System.out.printf(Locale.US,"%d,%d,%d,%.3f,%.3f,%.3f,%d%n",r.nodes(),r.edges(),r.modulesInCycles(),med(d),med(w),med(t),r.approximateMemoryBytes());
 }
}
