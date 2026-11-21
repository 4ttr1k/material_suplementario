package pe.utp.dependencias;

import java.util.*;

public final class Graph {
    private final List<String> names;
    private final List<Set<Integer>> adj;
    private final Map<String,Integer> index;

    public Graph(List<String> names, List<? extends Collection<Integer>> edges) {
        this.names = List.copyOf(names);
        this.index = new LinkedHashMap<>();
        for (int i=0;i<names.size();i++) index.put(names.get(i), i);
        this.adj = new ArrayList<>();
        for (Collection<Integer> row: edges) this.adj.add(new TreeSet<>(row));
    }
    public int size(){ return names.size(); }
    public int edgeCount(){ return adj.stream().mapToInt(Set::size).sum(); }
    public String name(int i){ return names.get(i); }
    public List<String> names(){ return names; }
    public List<Integer> neighbors(int i){ return List.copyOf(adj.get(i)); }
    public int indexOf(String name){ return index.getOrDefault(name,-1); }
    public List<Set<Integer>> adjacency(){ return Collections.unmodifiableList(adj); }

    public static final class Builder {
        private final LinkedHashMap<String,LinkedHashSet<String>> rows = new LinkedHashMap<>();
        public void addNode(String n){ rows.computeIfAbsent(n.trim(), k->new LinkedHashSet<>()); }
        public void addEdge(String from,String to){
            String a=from.trim(), b=to.trim();
            if(a.isEmpty()||b.isEmpty()) return;
            addNode(a); addNode(b); rows.get(a).add(b);
        }
        public Graph build(){
            List<String> names=new ArrayList<>(rows.keySet());
            Map<String,Integer> ix=new HashMap<>();
            for(int i=0;i<names.size();i++) ix.put(names.get(i),i);
            List<Collection<Integer>> edges=new ArrayList<>();
            for(String n:names){
                List<Integer> out=new ArrayList<>();
                for(String v:rows.get(n)) out.add(ix.get(v));
                edges.add(out);
            }
            return new Graph(names,edges);
        }
    }
}
