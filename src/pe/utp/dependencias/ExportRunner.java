package pe.utp.dependencias;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
public class ExportRunner {
 public static void main(String[] a)throws Exception{
  Path root=Path.of(a[0]), out=Path.of(a[1]); Files.createDirectories(out);
  String[][] ps={{"express","express/lib"},{"axios","axios/lib"},{"redux","redux/src"},{"zustand","zustand/src"},{"vue-runtime-core","core/packages/runtime-core/src"},{"execa","execa/lib"},{"eslint","eslint/lib"},{"webpack","webpack/lib"}};
  GraphIO io=new GraphIO(); for(String[] p:ps){Graph g=io.fromSourceDirectory(root.resolve(p[1]));io.exportEdges(g,out.resolve(p[0]+".csv"));Files.write(out.resolve(p[0]+"_nodes.txt"),g.names(),StandardCharsets.UTF_8);}
 }
}
