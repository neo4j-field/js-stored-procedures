package org.neo4j.ps;

import org.neo4j.graphdb.*;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;
//import org.openjdk.nashorn.api.tree.CompilationUnitTree;
//import org.openjdk.nashorn.api.tree.Parser;

import javax.script.Invocable;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.*;
import java.util.stream.Stream;

public class StoredProcedureAPI {

    @Context
    public Transaction txn;
    @Context
    public Log log;
    @Context
    public GraphDatabaseService db;

    private static final ScriptEngineManager scriptFactory = new ScriptEngineManager() ;

    /**
     *
     * @param jsFunc
     * @param publicName
     * @param reqClasses
     * @return
     * @throws RuntimeException
     */
    @Procedure(name = "js.storedproc.register", mode = Mode.WRITE)
    @Description("js.storedproc.register(<Valid Javascript Function Code>, <Public Name>, <Required Java Classes>) - Save a Javascript Stored Procedure")
    public Stream<Output> addNewJsFunction(@Name(value = "script") String script,
                                     @Name(value = "publicName" , defaultValue = "") String publicName,
                                     @Name(value = "reqClasses", defaultValue = "") String reqClasses) throws RuntimeException {

        if (validate(publicName, script)) {
            // Only after successful script + Java Class List validation we can proceed to record nodes
//            try (Transaction tx = db.beginTx()) {
//                StartupActions.scriptEngine.eval(jsFunc);
//                Node newProcNode = tx.createNode(EnumJsProcLabels.JS_StoredProc);
//                newProcNode.setProperty("script", jsFunc);
//                newProcNode.setProperty("publicName", publicName);
//                newProcNode.setProperty("name", publicName);
//                tx.commit();
//                log.info("Saved Node with JS Function info in DB");
//            }
//            catch (ScriptException e) {
//                // TODO - Do we want to throw a Runtime exception or return a non-standard error message?
//                log.error("Failed to register script", e);
//                throw new RuntimeException();
//            }
            return Stream.of(new Output("Done"));
        }
        else {
            throw new RuntimeException("Proc Name not unique");
        }
    }



    /**
     *
     * @param procedureName
     * @param parameters
     * @return
     */
    @Procedure(name = "js.procedure.invoke", mode = Mode.READ)
    @Description("js.procedure.invoke(<String procPublicName)>, {Proc Params}")
    public Stream<MapResult> invokeStoredProcedure(@Name("procedureName") String procedureName,
                                               @Name(value = "parameters") Map parameters) {
        var details = StartupActions.engine.getEngine(db, procedureName);
        if( parameters == null ) {
            parameters = new HashMap() ;
        }
        parameters.put("txn", txn);
        parameters.put("log", log);

        try {

            Invocable invocable = (Invocable) details.getEngine();
            Object response = invocable.invokeFunction(details.getName(), parameters) ;

        } catch (Exception e) {
            log.error("Something went wrong", e);
            throw new RuntimeException(e);
        }
        return null;
    }

    public static class Output {
        public String out;
        Output(String out) {
            this.out = out;
        }
    }

    private boolean validate(String publicName, String script) {
//        Parser parser = Parser.create();
//        CompilationUnitTree cut = parser.parse(publicName, script, (d) -> { System.out.println(d); }) ;

        try (Transaction tx= db.beginTx()) {
            Node n = tx.findNode(StoredProcedureEngine.JS_StoredProcedure, StoredProcedureEngine.PublicName, publicName) ;
            if( n != null ) {
                // We already have a function with this public name.
                // We need to make sure the script function name is the same as we
                // have received.
            }
            tx.commit();
        }
        return true ;
    }
}
