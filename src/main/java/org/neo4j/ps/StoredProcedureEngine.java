package org.neo4j.ps;

import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.graphdb.*;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;


import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class StoredProcedureEngine {
    public static Label JS_StoredProcedure = Label.label("JS_StoredProcedure") ;

    private static  final ScriptEngineManager scriptFactory = new ScriptEngineManager() ;
    private static Map<String, ScriptEngine> dbScriptEngineMap = new HashMap<>();
    private static Map<String, Map> dbScriptStatusMap = new HashMap<>() ;

    public static final String PublicName = "publicName" ;
    public static final String FunctionName = "name" ;
    public static final String Script = "script" ;

    private Log log ;

    public void setLog(Log log) {
        this.log = log;
    }

    public void loadStoredProcedures(GraphDatabaseAPI db) {
        String dbName = db.databaseName() ;
        ScriptEngine engine = scriptFactory.getEngineByName("nashhorn") ;

        dbScriptEngineMap.put(dbName, engine) ;

        try (Transaction tx = db.beginTx()) {
            log.debug("Cleared to load Stored Proc Nodes from DB");
            ResourceIterator<Node> procNodes = tx.findNodes(JS_StoredProcedure);
            Map<String, Map<String, String>> scriptLoad = new HashMap<>() ;
            dbScriptStatusMap.put(dbName, scriptLoad) ;
            procNodes.stream().forEach( element ->
                    {
                        try {
                            String name = element.getProperty(PublicName).toString() ;
                            String script = element.getProperty(Script).toString() ;
                            log.debug(String.format("Loading script: %s from DB", name));

                            engine.eval(script);
                            Map<String, String> data = new HashMap<>() ;
                            data.put(FunctionName, element.getProperty(FunctionName).toString()) ;
                            scriptLoad.put(name, data) ;
                        }
                        catch( ScriptException e) {
                            log.error("Could not load stored JS Script due to eval error. See message", e);
                            //throw new RuntimeException();
                        }
                    }
            ); ;
        }
    }

    public ScriptDetails getEngine(GraphDatabaseService db, String procedureName) {
        String dbName = db.databaseName() ;
        ScriptDetails details = new ScriptDetails() ;
        Map statusMap = dbScriptStatusMap.get(dbName) ;
        ScriptEngine engine = dbScriptEngineMap.get(dbName) ;
        details.setEngine(engine);
        if( statusMap.get(dbName) == null ) {
            try (Transaction tx = db.beginTx()) {
                Node n = tx.findNode(JS_StoredProcedure, "name", procedureName) ;
                try {
                    String publicName = n.getProperty(PublicName).toString() ;
                    String script = n.getProperty(Script).toString() ;
                    String name = n.getProperty(FunctionName).toString() ;
                    log.debug(String.format("Loading script: %s from DB", name));

                    engine.eval(script);
                    Map<String, String> data = new HashMap<>() ;
                    data.put(FunctionName, n.getProperty(FunctionName).toString()) ;
                    statusMap.put(publicName, data) ;
                }
                catch( ScriptException e) {
                    log.error("Could not load stored JS Script due to eval error. See message", e);
                    //throw new RuntimeException();
                }
            }
        }

        Map data = (Map) statusMap.get(dbName) ;
        details.setName(data.get(FunctionName).toString());
        details.setPublicName(procedureName);
        return details ;
    }
}
