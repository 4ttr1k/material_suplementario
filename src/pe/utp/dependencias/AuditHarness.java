package pe.utp.dependencias;
import java.nio.file.*;
import java.util.*;
public class AuditHarness {
 public static void main(String[] a) throws Exception {
  GraphIO io=new GraphIO(); Graph g=io.fromSourceDirectory(Path.of(a[0]));
  GraphAnalyzer.Result r=new GraphAnalyzer().analyze(g);
  io.exportEdges(g,Path.of(a[1]));
  Files.write(Path.of(a[1]+".nodes"),g.names(),java.nio.charset.StandardCharsets.UTF_8);
  System.out.printf(Locale.US,"nodes=%d edges=%d cycles=%d scc=%d largest=%d back=%d dfs_ms=%.3f warshall_ms=%.3f total_ms=%.3f matrix_bytes=%d agreement=%s%n",r.nodes(),r.edges(),r.modulesInCycles(),r.cyclicComponents(),r.largestCyclicComponent(),r.backEdges(),r.dfsNanos()/1e6,r.warshallNanos()/1e6,r.totalNanos()/1e6,r.approximateMemoryBytes(),r.dfsWarshallAgreement());
 }
}
