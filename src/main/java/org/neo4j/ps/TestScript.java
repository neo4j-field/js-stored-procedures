package org.neo4j.ps;


import jdk.nashorn.api.tree.CompilationUnitTree;
import jdk.nashorn.api.tree.Parser;

public class TestScript {
    public static void main(String[] args) {
        String publicName = "test" ;
        String script = "function sTest() { return 1 } function anotherTest(in1) { return 10 }" ;
        Parser parser = Parser.create();
        CompilationUnitTree cut = parser.parse(publicName, script, (d) -> { System.out.println(d); }) ;

        System.out.println("Done");
    }
}
