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
            Map<String, String> scriptLoad = new HashMap<>() ;
            dbScriptStatusMap.put(dbName, scriptLoad) ;
            procNodes.stream().forEach( element ->
                    {
                        try {
                            String name = element.getProperty("script").toString() ;
                            String script = element.getProperty("script").toString() ;
                            log.debug(String.format("Loading script: %s from DB", name));

                            engine.eval(script);
                            scriptLoad.put(name, "done") ;
                        }
                        catch( ScriptException e) {
                            log.error("Could not load stored JS Script due to eval error. See message", e);
                            //throw new RuntimeException();
                        }
                    }
            ); ;
        }
    }

    public ScriptEngine getEngine(GraphDatabaseService db, String procedureName) {
        String dbName = db.databaseName() ;
        Map statusMap = dbScriptStatusMap.get(dbName) ;
        ScriptEngine engine = dbScriptEngineMap.get(dbName) ;
        if( statusMap.get(dbName) == null ) {
            try (Transaction tx = db.beginTx()) {
                Node n = tx.findNode(JS_StoredProcedure, "name", procedureName) ;
                try {
                    String name = n.getProperty("script").toString() ;
                    String script = n.getProperty("script").toString() ;
                    log.debug(String.format("Loading script: %s from DB", name));

                    engine.eval(script);
                    statusMap.put(name, "done") ;
                }
                catch( ScriptException e) {
                    log.error("Could not load stored JS Script due to eval error. See message", e);
                    //throw new RuntimeException();
                }
            }
        }

        return engine ;
    }
}
