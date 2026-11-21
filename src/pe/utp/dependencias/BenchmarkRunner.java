package pe.utp.dependencias;
import java.nio.file.*;import java.util.*;import java.lang.management.*;
public class BenchmarkRunner {
 static long allocation(GraphAnalyzer a,Graph g,com.sun.management.ThreadMXBean bean){long id=Thread.currentThread().getId();long before=bean.getThreadAllocatedBytes(id);a.warshall(g);return bean.getThreadAllocatedBytes(id)-before;}
 static long median(long[] x){Arrays.sort(x);return x[x.length/2];}
 public static void main(String[] a)throws Exception{
  Path root=Path.of(a[0]);
  String[][] ps={{"Express","express/lib"},{"Axios","axios/lib"},{"Redux","redux/src"},{"Zustand","zustand/src"},{"Vue runtime-core","core/packages/runtime-core/src"},{"Execa","execa/lib"},{"ESLint","eslint/lib"},{"Webpack","webpack/lib"}};
  GraphIO io=new GraphIO();GraphAnalyzer ga=new GraphAnalyzer();
  var base=ManagementFactory.getThreadMXBean();var bean=(com.sun.management.ThreadMXBean)base;
  if(bean.isThreadAllocatedMemorySupported()&&!bean.isThreadAllocatedMemoryEnabled())bean.setThreadAllocatedMemoryEnabled(true);
  for(String[] p:ps){Graph g=io.fromSourceDirectory(root.resolve(p[1]));GraphAnalyzer.Result r=ga.analyze(g);
   for(int i=0;i<10;i++)allocation(ga,g,bean);long[] mem=new long[30];for(int i=0;i<30;i++)mem[i]=allocation(ga,g,bean);
   System.out.printf(Locale.US,"%s,%d,%d,%d,%d,%d,%d,%.3f,%.3f,%.3f,%d,%d,%s%n",p[0],r.nodes(),r.edges(),r.modulesInCycles(),r.cyclicComponents(),r.largestCyclicComponent(),r.backEdges(),r.dfsNanos()/1e6,r.warshallNanos()/1e6,r.totalNanos()/1e6,r.approximateMemoryBytes(),median(mem),r.dfsWarshallAgreement());
  }
 }
}
