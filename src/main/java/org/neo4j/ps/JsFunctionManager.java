package org.neo4j.ps;

import org.neo4j.graphdb.*;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;

import javax.script.ScriptException;
import java.util.*;
import java.util.stream.Stream;

public class JsFunctionManager {

    @Context
    public Transaction txn;
    @Context
    public Log log;
    @Context
    public GraphDatabaseService db;

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
    public Stream<Output> addNewJsFunction(@Name(value = "funcCode") String jsFunc,
                                     @Name(value = "pubName" , defaultValue = "") String publicName,
                                     @Name(value = "reqClasses", defaultValue = "") String reqClasses) throws RuntimeException {

        if (validateUniqueJsFunctionName(publicName, publicName)) {
            // Only after successful script + Java Class List validation we can proceed to record nodes
            try (Transaction tx = db.beginTx()) {
                StartupActions.scriptEngine.eval(jsFunc);
                Node newProcNode = tx.createNode(EnumJsProcLabels.JS_StoredProc);
                newProcNode.setProperty("script", jsFunc);
                newProcNode.setProperty("publicName", publicName);
                newProcNode.setProperty("name", publicName);
                tx.commit();
                log.info("Saved Node with JS Function info in DB");
            }
            catch (ScriptException e) {
                // TODO - Do we want to throw a Runtime exception or return a non-standard error message?
                log.error("Failed to register script", e);
                throw new RuntimeException();
            }
            return Stream.of(new Output("Done"));
        }
        else {
            throw new RuntimeException("Proc Name not unique");
        }
    }



    /**
     *
     * @param jsFunctionName
     * @param procParams
     * @return
     */
    @Procedure(name = "js.storedproc.invoke", mode = Mode.READ)
    @Description("js.storedproc.invoke(<String procPublicName)>, {Proc Params}")
    public Stream<PathResult> executeJsFunction(@Name("procPublicName") String jsFunctionName,
                                                @Name(value = "procParams", defaultValue = "") String procParams) {
        var singletonScriptEngine = StartupActions.scriptEngine;
        singletonScriptEngine.put("txn", txn);
        singletonScriptEngine.put("log", log);

        var paths = new ArrayList();

        try {
            jsFunctionName = jsFunctionName.concat("();");
            singletonScriptEngine.eval(jsFunctionName);
            // TODO TBD until we define how we will control and treat outputs
            //paths = (ArrayList) singletonScriptEngine.get("pathResults");

            return paths.stream().map(p -> new PathResult((Path) p));

        } catch (ScriptException e) {
            log.error("Something went wrong", e);
            throw new RuntimeException(e);
        }
    }

    public static class Output {
        public String out;
        Output(String out) {
            this.out = out;
        }
    }

    private boolean validateUniqueJsFunctionName(String proposedName, String autoName) {
        // TODO - Implement Unique JS Function Name Validation
        return true;
//        long found;
//        Map<String, Object> namesMap = new HashMap<>();
//
//        if (!proposedName.equals("")) {
//            namesMap.put("publicName", proposedName);
//        }
//        else {
//            namesMap.put("name", autoName);
//        }
//        try (Transaction tx = db.beginTx()) {
//            found = tx.findNodes(EnumJsProcLabels.JS_StoredProc, namesMap)
//                    .stream()
//                    .findFirst()
//                    .stream().count();
//        }
//        return found <= 0;
    }
}
