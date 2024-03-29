package jsproc.neo4j;


import org.openjdk.nashorn.api.tree.*;

public class TestScript {
    public static void main(String[] args) {
        String publicName = "test" ;
//        String script = "function sTest() { return 1 } function anotherTest(in1) { return 10 }" ;
        String script = "function sTest() { return 1 } var test=1 ; " ;

        Parser parser = Parser.create();

        JSParserDiagnosticListener listener = new JSParserDiagnosticListener() ;

        CompilationUnitTree cut = parser.parse(publicName, script, listener) ;

        System.out.println("Parser Messange : " + listener.message) ;
        System.out.println("Count : " + cut.getSourceElements().size()) ;
        System.out.println(((FunctionDeclarationTree)(cut.getSourceElements().get(0))).getName().getName()) ;

        System.out.println("Done");
    }
}
