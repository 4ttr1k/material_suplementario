package pe.utp.dependencias;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public final class GraphIO {
    private static final Set<String> EXT=Set.of(".ts",".tsx",".js",".mjs",".cjs",".jsx");
    private static final Pattern IMPORT=Pattern.compile(
        "(?m)(?:\\bimport\\s+(?:[^;\\n]*?\\s+from\\s+)?|\\bexport\\s+(?:[^;\\n]*?\\s+from\\s+))['\\\"]([^'\\\"]+)['\\\"]|\\b(?:require|import)\\s*\\(\\s*['\\\"]([^'\\\"]+)['\\\"]\\s*\\)");
    private static final Pattern COMMENT=Pattern.compile("(?s)/\\*.*?\\*/|//[^\\r\\n]*");
    private static final Pattern STATIC_REQUIRE=Pattern.compile("\\brequire\\s*\\(\\s*(['\\\"])([^'\\\"]+)\\1\\s*\\)");
    private static final List<String> RESOLVE_EXT=List.of(".ts",".tsx",".js",".mjs",".cjs",".jsx");

    public Graph fromEdgeFile(Path file) throws IOException {
        Graph.Builder b=new Graph.Builder();
        int lineNo=0;
        for(String raw:Files.readAllLines(file,StandardCharsets.UTF_8)){
            lineNo++; String line=raw.trim();
            if(line.isEmpty()||line.startsWith("#")||line.startsWith("//"))continue;
            if(line.toLowerCase(Locale.ROOT).startsWith("origen,")||line.toLowerCase(Locale.ROOT).startsWith("source,"))continue;
            String[] p=parseEdge(line);
            if(p.length<2) throw new IOException("Formato inválido en línea "+lineNo+": "+raw);
            b.addEdge(p[0],p[1]);
        }
        return b.build();
    }

    public Graph fromSourceDirectory(Path directory) throws IOException {
        Path root=directory.toRealPath();
        List<Path> files=new ArrayList<>();
        try(var walk=Files.walk(root)){
            walk.filter(Files::isRegularFile)
                .filter(f->EXT.contains(extension(f)))
                .filter(f->!excluded(root.relativize(f).toString().replace('\\','/')))
                .filter(f->!containsNodeModules(root.relativize(f)))
                .forEach(files::add);
        }
        files.sort(Comparator.comparing(f->root.relativize(f).toString().replace('\\','/')));
        Set<Path> fileSet=new HashSet<>(files);
        Graph.Builder b=new Graph.Builder();
        for(Path f:files) b.addNode(root.relativize(f).toString().replace('\\','/'));

        for(Path f:files){
            String source=Files.readString(f,StandardCharsets.UTF_8);
            for(String spec:extractModuleSpecifiers(source)){
                if(!spec.startsWith(".")) continue; // mismo criterio del experimento: dependencias internas relativas
                Path resolved;
                try {
                    resolved=resolve(f.getParent().resolve(spec).normalize(),fileSet);
                } catch (InvalidPathException ex) {
                    continue;
                }
                if(resolved!=null) b.addEdge(
                    root.relativize(f).toString().replace('\\','/'),
                    root.relativize(resolved).toString().replace('\\','/'));
            }
        }
        return b.build();
    }

    /*
     * Extractor léxico ligero para imports/exports de JS y TypeScript.
     * A diferencia de la primera versión basada en una expresión regular,
     * admite declaraciones multilínea, import type, export type, imports
     * laterales, import(), require() e importaciones TypeScript = require().
     * No interpreta comentarios ni cadenas como código.
     */
    private static List<String> extractModuleSpecifiers(String source){
        List<Token> tokens=tokenize(source);
        LinkedHashSet<String> specs=new LinkedHashSet<>();
        for(int i=0;i<tokens.size();i++){
            Token t=tokens.get(i);
            if(!t.identifier) continue;
            if(t.text.equals("import")){
                int j=i+1;
                if(j<tokens.size() && tokens.get(j).text.equals(".")) continue;
                if(j<tokens.size() && tokens.get(j).text.equals("(")){
                    if(j+1<tokens.size() && tokens.get(j+1).string) specs.add(tokens.get(j+1).text);
                    continue;
                }
                if(j<tokens.size() && tokens.get(j).string){
                    specs.add(tokens.get(j).text); // import './module'
                    continue;
                }
                for(int k=j;k<tokens.size();k++){
                    Token x=tokens.get(k);
                    if(x.text.equals(";")) break;
                    if(x.identifier && (x.text.equals("import") || x.text.equals("export")) && k>j) break;
                    if(x.identifier && x.text.equals("from") && k+1<tokens.size() && tokens.get(k+1).string){
                        specs.add(tokens.get(k+1).text); break;
                    }
                    if(x.lineBreakBefore && k>j && (x.identifier && (x.text.equals("const")||x.text.equals("let")||x.text.equals("var")||x.text.equals("function")||x.text.equals("class")))) break;
                }
            } else if(t.text.equals("export")){
                for(int k=i+1;k<tokens.size();k++){
                    Token x=tokens.get(k);
                    if(x.text.equals(";")) break;
                    if(x.identifier && (x.text.equals("import") || x.text.equals("export")) && k>i+1) break;
                    if(x.identifier && x.text.equals("from") && k+1<tokens.size() && tokens.get(k+1).string){
                        specs.add(tokens.get(k+1).text); break;
                    }
                    if(x.lineBreakBefore && k>i+1 && x.identifier &&
                       (x.text.equals("const")||x.text.equals("let")||x.text.equals("var")||x.text.equals("function")||x.text.equals("class"))) break;
                }
            } else if(t.text.equals("require") && i+2<tokens.size() &&
                      tokens.get(i+1).text.equals("(") && tokens.get(i+2).string){
                specs.add(tokens.get(i+2).text);
            }
        }
        // Respaldo para require() literales que aparecen después de template
        // literals complejos. Primero se retiran comentarios para no contar
        // ejemplos documentados como dependencias reales.
        String codeWithoutComments=COMMENT.matcher(source).replaceAll(" ");
        Matcher requireMatcher=STATIC_REQUIRE.matcher(codeWithoutComments);
        while(requireMatcher.find()) specs.add(requireMatcher.group(2));

        return new ArrayList<>(specs);
    }

    private record Token(String text, boolean identifier, boolean string, boolean lineBreakBefore) {}

    private static List<Token> tokenize(String s){
        List<Token> out=new ArrayList<>();
        int i=0; boolean lineBreak=false;
        while(i<s.length()){
            char c=s.charAt(i);
            if(Character.isWhitespace(c)){ if(c=='\n'||c=='\r') lineBreak=true; i++; continue; }
            if(c=='/' && i+1<s.length() && s.charAt(i+1)=='/'){
                i+=2; while(i<s.length() && s.charAt(i)!='\n' && s.charAt(i)!='\r') i++; continue;
            }
            if(c=='/' && i+1<s.length() && s.charAt(i+1)=='*'){
                i+=2; while(i+1<s.length() && !(s.charAt(i)=='*'&&s.charAt(i+1)=='/')){
                    if(s.charAt(i)=='\n'||s.charAt(i)=='\r') lineBreak=true; i++;
                }
                i=Math.min(s.length(),i+2); continue;
            }
            boolean lb=lineBreak; lineBreak=false;
            if(c=='\''||c=='"'||c=='`'){
                char quote=c; i++; StringBuilder value=new StringBuilder();
                while(i<s.length()){
                    char q=s.charAt(i++);
                    if(q=='\\' && i<s.length()){ value.append(s.charAt(i++)); continue; }
                    if(q==quote) break;
                    value.append(q);
                }
                out.add(new Token(value.toString(),false,true,lb)); continue;
            }
            if(Character.isJavaIdentifierStart(c)||c=='$'){
                int a=i++; while(i<s.length()&&(Character.isJavaIdentifierPart(s.charAt(i))||s.charAt(i)=='$'))i++;
                out.add(new Token(s.substring(a,i),true,false,lb)); continue;
            }
            out.add(new Token(String.valueOf(c),false,false,lb)); i++;
        }
        return out;
    }
    private static Path resolve(Path base,Set<Path> files){
        List<Path> c=new ArrayList<>(); c.add(base);
        String name=base.getFileName()==null?"":base.getFileName().toString();
        int dot=name.lastIndexOf('.');
        if(dot>0 && Set.of(".js",".mjs",".cjs",".jsx").contains(name.substring(dot))){
            Path stem=base.resolveSibling(name.substring(0,dot));
            c.add(stem.resolveSibling(stem.getFileName()+".ts"));
            c.add(stem.resolveSibling(stem.getFileName()+".tsx"));
        }
        for(String e:RESOLVE_EXT)c.add(Path.of(base.toString()+e));
        for(String e:RESOLVE_EXT)c.add(base.resolve("index"+e));
        for(Path p:c) if(files.contains(p)) return p;
        return null;
    }
    private static String extension(Path p){
        String n=p.getFileName().toString(); int i=n.lastIndexOf('.');
        return i<0?"":n.substring(i).toLowerCase(Locale.ROOT);
    }
    private static boolean excluded(String p){
        String s="/"+p.toLowerCase(Locale.ROOT);
        return s.contains("/__tests__/")||s.contains("/__mocks__/")||s.contains("/test/")||
            s.contains("/tests/")||s.endsWith(".d.ts")||s.contains(".test.")||s.contains(".spec.");
    }
    private static String[] parseEdge(String line) throws IOException {
        if (line.indexOf('"') >= 0) {
            List<String> fields=new ArrayList<>(); StringBuilder value=new StringBuilder(); boolean quoted=false;
            for(int i=0;i<line.length();i++){
                char c=line.charAt(i);
                if(quoted){
                    if(c=='"'){
                        if(i+1<line.length()&&line.charAt(i+1)=='"'){value.append('"');i++;}
                        else quoted=false;
                    } else value.append(c);
                } else if(c=='"') quoted=true;
                else if(c==','){fields.add(value.toString());value.setLength(0);}
                else value.append(c);
            }
            if(quoted) throw new IOException("Comillas sin cerrar en arista: "+line);
            fields.add(value.toString());
            if(fields.size()<2) throw new IOException("Arista CSV incompleta: "+line);
            return new String[]{fields.get(0),fields.get(1)};
        }
        return line.split("[,;\\t ]+",2);
    }
    private static boolean containsNodeModules(Path p){
        for(Path part:p) if(part.toString().equals("node_modules")||part.toString().startsWith("."))return true;
        return false;
    }
    public void exportEdges(Graph g,Path file)throws IOException{
        try(BufferedWriter w=Files.newBufferedWriter(file,StandardCharsets.UTF_8)){
            w.write("origen,destino\n");
            for(int i=0;i<g.size();i++)for(int j:g.neighbors(i))
                w.write(csv(g.name(i))+","+csv(g.name(j))+"\n");
        }
    }
    private static String csv(String s){return "\""+s.replace("\"","\"\"")+"\"";}
    public void exportReport(Graph g,GraphAnalyzer.Result r,Path file)throws IOException{
        StringBuilder b=new StringBuilder();
        b.append("{\n  \"nodes\": ").append(r.nodes()).append(",\n  \"edges\": ").append(r.edges())
         .append(",\n  \"density\": ").append(r.density()).append(",\n  \"modulesInCycles\": ").append(r.modulesInCycles())
         .append(",\n  \"cyclicComponents\": ").append(r.cyclicComponents()).append(",\n  \"largestCyclicComponent\": ").append(r.largestCyclicComponent())
         .append(",\n  \"backEdges\": ").append(r.backEdges()).append(",\n  \"medianDependents\": ").append(r.medianDependents())
         .append(",\n  \"meanDependents\": ").append(r.meanDependents()).append(",\n  \"maxDependents\": ").append(r.maxDependents())
         .append(",\n  \"medianImpactPercent\": ").append(r.medianImpactPercent()).append(",\n  \"maxImpactPercent\": ").append(r.maxImpactPercent())
         .append(",\n  \"dfsMillis\": ").append(r.dfsNanos()/1_000_000.0).append(",\n  \"warshallMillis\": ").append(r.warshallNanos()/1_000_000.0)
         .append(",\n  \"totalMillis\": ").append(r.totalNanos()/1_000_000.0).append(",\n  \"approximateMatrixBytes\": ").append(r.approximateMemoryBytes())
         .append(",\n  \"dfsWarshallAgreement\": ").append(r.dfsWarshallAgreement()).append(",\n  \"cycleModules\": ");
        appendArray(b,r.cycleModules()); b.append(",\n  \"topImpactModules\": "); appendArray(b,r.topImpactModules());
        b.append("\n}\n"); Files.writeString(file,b.toString(),StandardCharsets.UTF_8);
    }
    private static void appendArray(StringBuilder b,List<String> values){
        b.append("["); for(int i=0;i<values.size();i++){if(i>0)b.append(", ");b.append("\"").append(values.get(i).replace("\\","\\\\").replace("\"","\\\"")).append("\"");} b.append("]");
    }
}
