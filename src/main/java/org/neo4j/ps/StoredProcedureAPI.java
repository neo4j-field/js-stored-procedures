package org.neo4j.ps;

import jdk.nashorn.api.tree.CompilationUnitTree;
import jdk.nashorn.api.tree.FunctionDeclarationTree;
import jdk.nashorn.api.tree.Parser;
import org.neo4j.graphdb.*;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;


import javax.script.Invocable;
import javax.script.ScriptEngineManager;
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
     * @param script
     * @param publicName
     * @param reqClasses
     * @return
     * @throws RuntimeException
     */
    @Procedure(name = "js.procedure.register", mode = Mode.WRITE)
    @Description("js.procedure.register(<Valid Javascript Function Code>, <Public Name>, <Required Java Classes>) - Save a Javascript Stored Procedure")
    public Stream<RegisterResult> addNewJsProcedure(@Name(value = "script") String script,
                                     @Name(value = "publicName" , defaultValue = "") String publicName,
                                     @Name(value = "reqClasses", defaultValue = "") String reqClasses) throws RuntimeException {

        Parser parser = Parser.create();
        String compilationMessage = "" ;
        JSParserDiagnosticListener listener = new JSParserDiagnosticListener() ;
        CompilationUnitTree cut = parser.parse(publicName, script, listener) ;

        if( listener.hasError() ) {
            // There is some parse error.
            return Stream.of(new RegisterResult(listener.getMessage()));
        }

        if( cut.getSourceElements().size() > 1 ) {
            // Error
            return Stream.of(new RegisterResult("There can be only on JS function per register request."));
        }

        String name = ((FunctionDeclarationTree)(cut.getSourceElements().get(0))).getName().getName() ;

        if (validate(publicName, name)) {
            Node n = txn.findNode(StoredProcedureEngine.JS_StoredProcedure, StoredProcedureEngine.PublicName, publicName);
            if( n == null ) {
                n = txn.createNode(StoredProcedureEngine.JS_StoredProcedure) ;
                n.setProperty(StoredProcedureEngine.PublicName, publicName);
                n.setProperty(StoredProcedureEngine.FunctionName, name);
                n.setProperty(StoredProcedureEngine.Script, script);
                StoredProcedureEngine.getStoredProcedureEngine(null).loadProcedure(db, txn, publicName);
            } else {
                n.setProperty(StoredProcedureEngine.Script, script);
            }
            return Stream.of(RegisterResult.SUCCESS);
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
    @Procedure(name = "js.procedure.invoke", mode = Mode.WRITE)
    @Description("js.procedure.invoke(<String procPublicName)>, {Proc Params}")
    public Stream<MapResult> invokeStoredProcedure(@Name("procedureName") String procedureName,
                                               @Name(value = "parameters") Map parameters) {
        Map<String, Object> result = new HashMap<>() ;

        var details = StoredProcedureEngine.getStoredProcedureEngine(null).getEngine(db, txn, procedureName);
        if( parameters == null ) {
            parameters = new HashMap() ;
        }

        parameters.put("txn", txn);
        parameters.put("log", log);

        try {

            Invocable invocable = (Invocable) details.getEngine();
            Object response = invocable.invokeFunction(details.getName(), parameters) ;

            result.put("result", response) ;

        } catch (Exception e) {
            log.error("Something went wrong", e);
            result.put("error", e.getMessage()) ;
            //throw new RuntimeException(e);
        }
        return Stream.of(new MapResult(result));
    }

    public static class Output {
        public String out;
        Output(String out) {
            this.out = out;
        }
    }

    private boolean validate(String publicName, String name) {
        ValidationStatusCode status = StoredProcedureEngine.getStoredProcedureEngine(null).validateFunction(db.databaseName(), publicName, name) ;
        if( status == ValidationStatusCode.NAME_MISSING || status == ValidationStatusCode.PUBLIC_NAME_MISSING ) {
            Node n = txn.findNode(StoredProcedureEngine.JS_StoredProcedure, StoredProcedureEngine.PublicName, publicName);
            if (n != null) {
                // We already have a function with this public name.
                if( status == ValidationStatusCode.NAME_MISSING ) {
                    // Since we couldn't find the name, but found the public name
                    // There is a mismatch.
                    return false;
                }
            }
        }
        return true ;
    }
}
