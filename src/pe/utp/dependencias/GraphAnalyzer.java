package pe.utp.dependencias;

import java.util.*;

public final class GraphAnalyzer {
    public record Result(int nodes, int edges, int modulesInCycles, int cyclicComponents,
                         int largestCyclicComponent, int backEdges, double density,
                         double medianDependents, double meanDependents, int maxDependents,
                         double medianImpactPercent, double maxImpactPercent,
                         long dfsNanos, long warshallNanos, long totalNanos,
                         long approximateMemoryBytes, List<String> cycleModules,
                         List<String> topImpactModules, boolean dfsWarshallAgreement,
                         boolean[][] closure) {}

    private static final int WARMUPS = 10;
    private static final int SAMPLES = 30;

    public Result analyze(Graph g) {
        for (int i = 0; i < WARMUPS; i++) analyzeOnce(g);
        List<Result> runs = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) runs.add(analyzeOnce(g));
        Result last = runs.get(runs.size() - 1);
        return new Result(last.nodes(), last.edges(), last.modulesInCycles(), last.cyclicComponents(),
                last.largestCyclicComponent(), last.backEdges(), last.density(),
                last.medianDependents(), last.meanDependents(), last.maxDependents(),
                last.medianImpactPercent(), last.maxImpactPercent(),
                medianLong(runs.stream().mapToLong(Result::dfsNanos).toArray()),
                medianLong(runs.stream().mapToLong(Result::warshallNanos).toArray()),
                medianLong(runs.stream().mapToLong(Result::totalNanos).toArray()),
                last.approximateMemoryBytes(), last.cycleModules(), last.topImpactModules(),
                last.dfsWarshallAgreement(), last.closure());
    }

    private Result analyzeOnce(Graph g) {
        long totalStart = System.nanoTime();
        long t = System.nanoTime();
        List<int[]> back = dfsBackEdges(g);
        SccResult scc = tarjan(g);
        long dfsTime = System.nanoTime() - t;

        t = System.nanoTime();
        boolean[][] closure = warshall(g);
        long warshallTime = System.nanoTime() - t;

        int n = g.size(), m = g.edgeCount();
        boolean[] inCycle = new boolean[n];
        List<String> cycleNames = new ArrayList<>();
        int cycleCount = 0;
        for (int i = 0; i < n; i++) {
            inCycle[i] = closure[i][i];
            if (inCycle[i]) { cycleCount++; cycleNames.add(g.name(i)); }
        }
        boolean agreement = Arrays.equals(scc.inCycle, inCycle);
        int[] dependents = new int[n];
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++)
            if (i != j && closure[j][i]) dependents[i]++;
        int sum = Arrays.stream(dependents).sum();
        int max = Arrays.stream(dependents).max().orElse(0);
        double[] sorted = Arrays.stream(dependents).asDoubleStream().sorted().toArray();
        double median = percentile(sorted, .5);
        double mean = n == 0 ? 0 : (double) sum / n;
        double medianPct = n > 1 ? 100 * median / (n - 1) : 0;
        double maxPct = n > 1 ? 100.0 * max / (n - 1) : 0;
        List<Integer> order = new ArrayList<>(); for (int i = 0; i < n; i++) order.add(i);
        order.sort((a,b) -> Integer.compare(dependents[b], dependents[a]));
        List<String> top = new ArrayList<>();
        for (int i = 0; i < Math.min(5,n); i++) top.add(g.name(order.get(i)) + " (" + dependents[order.get(i)] + ")");
        long total = System.nanoTime() - totalStart;
        long matrixBytes = (long)n*n; // payload teórico de boolean; no es heap/RSS real
        return new Result(n,m,cycleCount,scc.components,scc.largest,back.size(),
                n>1?(double)m/(n*(double)(n-1)):0,median,mean,max,medianPct,maxPct,
                dfsTime,warshallTime,total,matrixBytes,List.copyOf(cycleNames),List.copyOf(top),agreement,closure);
    }

    private static long medianLong(long[] a) {
        Arrays.sort(a); int mid = a.length / 2;
        return a.length % 2 == 1 ? a[mid] : (a[mid-1] + a[mid]) / 2;
    }
    private static double percentile(double[] a,double p) {
        if(a.length==0)return 0;
        double pos=(a.length-1)*p; int lo=(int)Math.floor(pos), hi=(int)Math.ceil(pos);
        return a[lo]+(a[hi]-a[lo])*(pos-lo);
    }

    // DFS iterativo: las aristas hacia nodos GRIS son aristas de retorno.
    public List<int[]> dfsBackEdges(Graph g) {
        int n=g.size(); byte[] color=new byte[n]; int[] next=new int[n];
        List<int[]> back=new ArrayList<>(); ArrayDeque<Integer> stack=new ArrayDeque<>();
        for(int s=0;s<n;s++) if(color[s]==0) {
            color[s]=1; stack.push(s);
            while(!stack.isEmpty()) {
                int u=stack.peek(); List<Integer> adj=g.neighbors(u);
                if(next[u]<adj.size()) {
                    int v=adj.get(next[u]++);
                    if(color[v]==0){color[v]=1;stack.push(v);}
                    else if(color[v]==1) back.add(new int[]{u,v});
                } else {color[u]=2;stack.pop();}
            }
        }
        return back;
    }

    private static final class SccResult {
        final boolean[] inCycle; final int components; final int largest;
        SccResult(boolean[] c,int n,int l){inCycle=c;components=n;largest=l;}
    }
    // Tarjan: DFS en O(V+E), identifica todos los vértices que pertenecen a ciclos.
    private SccResult tarjan(Graph g) {
        int n=g.size(); int[] index=new int[n], low=new int[n]; Arrays.fill(index,-1);
        boolean[] onStack=new boolean[n], inCycle=new boolean[n];
        ArrayDeque<Integer> stack=new ArrayDeque<>(); int[] clock={0}, components={0}, largest={0};
        for(int v=0;v<n;v++) if(index[v]<0) strongConnect(v,g,index,low,onStack,inCycle,stack,clock,components,largest);
        return new SccResult(inCycle,components[0],largest[0]);
    }
    private void strongConnect(int v,Graph g,int[] index,int[] low,boolean[] onStack,boolean[] inCycle,
                               ArrayDeque<Integer> stack,int[] clock,int[] components,int[] largest) {
        index[v]=low[v]=clock[0]++; stack.push(v); onStack[v]=true;
        for(int w:g.neighbors(v)) {
            if(index[w]<0){strongConnect(w,g,index,low,onStack,inCycle,stack,clock,components,largest);low[v]=Math.min(low[v],low[w]);}
            else if(onStack[w]) low[v]=Math.min(low[v],index[w]);
        }
        if(low[v]==index[v]) {
            List<Integer> members=new ArrayList<>(); int w;
            do {w=stack.pop();onStack[w]=false;members.add(w);} while(w!=v);
            boolean cyclic=members.size()>1 || g.neighbors(v).contains(v);
            if(cyclic){components[0]++;largest[0]=Math.max(largest[0],members.size());for(int x:members)inCycle[x]=true;}
        }
    }

    public boolean[][] warshall(Graph g) {
        int n=g.size(); boolean[][] c=new boolean[n][n];
        for(int i=0;i<n;i++)for(int j:g.neighbors(i))c[i][j]=true;
        for(int k=0;k<n;k++)for(int i=0;i<n;i++)if(c[i][k])
            for(int j=0;j<n;j++)c[i][j]=c[i][j]||c[k][j];
        return c;
    }
    public List<String> affectedBy(Graph g,boolean[][] closure,String changedModule) {
        int changed=g.indexOf(changedModule); if(changed<0)return List.of();
        List<String> out=new ArrayList<>();
        for(int i=0;i<g.size();i++)if(i!=changed&&closure[i][changed])out.add(g.name(i));
        return out;
    }
    public List<String> reachableFrom(Graph g,boolean[][] closure,String module) {
        int i=g.indexOf(module);if(i<0)return List.of();List<String> out=new ArrayList<>();
        for(int j=0;j<g.size();j++)if(closure[i][j])out.add(g.name(j));return out;
    }
}
