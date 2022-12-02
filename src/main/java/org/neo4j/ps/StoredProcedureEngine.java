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
    private static Map<String, Map> dbScriptPublicNameMap = new HashMap<>() ;
    private static Map<String, Map> dbScriptNameMap = new HashMap<>() ;

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
            Map<String, Map<String, String>> publicNameMap = new HashMap<>() ;
            Map<String, Map<String, String>> nameMap = new HashMap<>() ;
            dbScriptPublicNameMap.put(dbName, publicNameMap) ;
            dbScriptNameMap.put(dbName, nameMap) ;
            procNodes.stream().forEach( element ->
                    {
                        try {
                            String publicName = element.getProperty(PublicName).toString() ;
                            String name = element.getProperty(FunctionName).toString() ;
                            String script = element.getProperty(Script).toString() ;

                            log.debug(String.format("Loading script: %s from DB", publicName));

                            engine.eval(script);
                            Map<String, String> data = new HashMap<>() ;
                            data.put(FunctionName, name ) ;
                            data.put(PublicName, publicName) ;
                            publicNameMap.put(publicName, data) ;
                            nameMap.put(name, data) ;
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
        Map publicNameMap = dbScriptPublicNameMap.get(dbName) ;
        Map nameMap = dbScriptNameMap.get(dbName) ;

        ScriptEngine engine = dbScriptEngineMap.get(dbName) ;
        details.setEngine(engine);
        if( publicNameMap.get(dbName) == null ) {
            loadProcedure(db, procedureName);
        }

        Map data = (Map) publicNameMap.get(dbName) ;
        details.setName(data.get(FunctionName).toString());
        details.setPublicName(procedureName);
        return details ;
    }

    public ValidationStatusCode validateFunction(String dbName, String publicName, String name) {
        Map publicNameMap = dbScriptPublicNameMap.get(dbName) ;
        Map nameMap = dbScriptNameMap.get(dbName) ;

        if( publicNameMap == null || nameMap == null ) {
            return ValidationStatusCode.NO_DATABASE_MATCH ;
        }

        String savedPublicName = nameMap.get(name).toString() ;
        String savedName = publicNameMap.get(publicName).toString() ;

        if( savedPublicName == null ) {
            return ValidationStatusCode.PUBLIC_NAME_MISSING ;
        }

        if( savedName == null ) {
            return ValidationStatusCode.NAME_MISSING ;
        }

        if(!( savedName.equals(name) && savedPublicName.equals(publicName) ) ) {
            return ValidationStatusCode.NAMES_MISMATCH ;
        }

        return ValidationStatusCode.SUCCESS ;
    }

    public void loadProcedure(GraphDatabaseService db, String publicName) {
        String dbName = db.databaseName() ;
        ScriptEngine engine = dbScriptEngineMap.get(dbName) ;
        Map publicNameMap = dbScriptPublicNameMap.get(dbName) ;
        Map nameMap = dbScriptNameMap.get(dbName) ;

        try (Transaction tx = db.beginTx()) {
            Node n = tx.findNode(JS_StoredProcedure, PublicName, publicName) ;
            try {
                String pubName = n.getProperty(PublicName).toString() ;
                String script = n.getProperty(Script).toString() ;
                String name = n.getProperty(FunctionName).toString() ;
                log.debug(String.format("Loading script: %s from DB", name));

                engine.eval(script);
                Map<String, String> data = new HashMap<>() ;
                data.put(FunctionName, name) ;
                data.put(PublicName, pubName );
                publicNameMap.put(pubName, data) ;
                nameMap.put(name, data) ;
                tx.commit();
            }
            catch( ScriptException e) {
                log.error("Could not load stored JS Script due to eval error. See message", e);
                //throw new RuntimeException();
            }
        }
    }
}
