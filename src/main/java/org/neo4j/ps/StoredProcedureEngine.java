package org.neo4j.ps;

import org.neo4j.graphdb.*;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;


import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.HashMap;
import java.util.Map;

public class StoredProcedureEngine {
    public static Label JS_StoredProcedure = Label.label("JS_StoredProcedure");
    public static Label JS_RequiredClasses = Label.label("JS_RequiredClasses");
    public static Label JS_FUNCTION = Label.label("JS_FUNCTION");

    private static  final ScriptEngineManager scriptFactory = new ScriptEngineManager();
    private static Map<String, ScriptEngine> dbScriptEngineMap = new HashMap<>();
    private static Map<String, Map<String, Map<String, String>>> dbScriptPublicNameMap = new HashMap<>();
    private static Map<String, Map<String, Map<String, String>>> dbScriptNameMap = new HashMap<>();

    public static final String PublicName = "publicName";
    public static final String FunctionName = "name";
    public static final String Script = "script";

    public static final String BasicDBClasses = "basicDBClasses";
    public static final String OtherClasses = "otherClasses";

    private static final String DBBasicClasses = "" +
            "var Direction = Java.type(\"org.neo4j.graphdb.Direction\")\n" +
            "var Label = Java.type(\"org.neo4j.graphdb.Label\")\n" +
            "var PathBuilder = Java.type(\"org.neo4j.graphalgo.impl.util.PathImpl.Builder\")\n" +
            "var RelationshipType = Java.type(\"org.neo4j.graphdb.RelationshipType\")\n" +
            "var Node  = Java.type(\"org.neo4j.graphdb.Node\")\n" +
            "var ResourceIterator  = Java.type(\"org.neo4j.graphdb.ResourceIterator\")\n" +
            "var Relationship  = Java.type(\"org.neo4j.graphdb.Relationship\")\n" +
            "var Path  = Java.type(\"org.neo4j.graphdb.Path\")\n" +
            "var GraphDatabaseService  = Java.type(\"org.neo4j.graphdb.GraphDatabaseService\")\n" +
            "var Transaction  = Java.type(\"org.neo4j.graphdb.Transaction\")";

    private static final String OtherRequiredClasses = "" +
            "var ArrayList = Java.type(\"java.util.ArrayList\")\n" +
            "var HashMap = Java.type(\"java.util.HashMap\")";

    private Log log;

    private static StoredProcedureEngine thisEngine;

    public static synchronized StoredProcedureEngine getStoredProcedureEngine(Log log) {
        if(thisEngine == null) {
            thisEngine = new StoredProcedureEngine(log);
        }
        return thisEngine;
    }

    private StoredProcedureEngine(Log log) {
        if(log != null) {
            this.log = log;
            log.info("Stored Procedure Engine Constructor called");
        }
    }

    public void loadStoredProcedures(GraphDatabaseAPI db) {
        String dbName = db.databaseName();
        ScriptEngine engine = scriptFactory.getEngineByName("nashorn");

        log.info("Loading Stored procedures for Database : " + dbName);
        log.info("This class : " + this);
        dbScriptEngineMap.put(dbName, engine);

        try (Transaction tx = db.beginTx()) {
            log.debug("Cleared to load Stored Proc Nodes from DB");

            try {
                ResourceIterator<Node> requiredClasses  = tx.findNodes(JS_RequiredClasses);
                if(requiredClasses.hasNext()) {
                        Node n = requiredClasses.next();
                        log.debug("Loading Basic Database classes");
                        String classText;
                        if (n.hasProperty(BasicDBClasses)) {
                            classText = n.getProperty(BasicDBClasses).toString();
                        } else {
                            classText = DBBasicClasses;
                        }
                        engine.eval(classText);
                        log.debug("Loading Other Required classes");
                        if (n.hasProperty(OtherClasses)) {
                            classText = n.getProperty(OtherClasses).toString();
                        } else {
                            classText = OtherRequiredClasses;
                        }
                        engine.eval(classText);
                    log.debug("Loaded required class definitions.");
                } else {
                    engine.eval(DBBasicClasses);
                    engine.eval(OtherRequiredClasses);
                    log.debug("Loaded default class definitions.");
                }
            } catch(ScriptException e) {
                log.error("Could not load stored JS Script due to eval error. See message", e);
            }

            ResourceIterator<Node> procNodes = tx.findNodes(JS_StoredProcedure);
            Map<String, Map<String, String>> publicNameMap = new HashMap<>();
            Map<String, Map<String, String>> nameMap = new HashMap<>();
            dbScriptPublicNameMap.put(dbName, publicNameMap);
            dbScriptNameMap.put(dbName, nameMap);
            procNodes.stream().forEach(n -> {
                try {
                    loadScriptIntoEngine(engine, publicNameMap, nameMap, n);
                } catch(ScriptException e) {
                    log.error("Could not load stored JS Script due to eval error. See message", e);
                }
            });
        }
    }

    public ScriptDetails getEngine(GraphDatabaseService db, Transaction tx, String procedureName) {
        String dbName = db.databaseName();
        ScriptDetails details = new ScriptDetails();
        Map<String, Map<String, String>> publicNameMap = dbScriptPublicNameMap.get(dbName);

        ScriptEngine engine = dbScriptEngineMap.get(dbName);
        details.setEngine(engine);
        if( publicNameMap.get(procedureName) == null && !loadProcedure(db, tx, procedureName)) {
            //Procedure doesn't exist and cannot be loaded into ScriptEngine
            return null;
        }

        Map<String, String> data = publicNameMap.get(procedureName);
        details.setName(data.get(FunctionName));
        details.setPublicName(procedureName);
        return details;
    }

    public ValidationStatusCode validateFunction(String dbName, String publicName, String name) {
        Map<String, Map<String, String>> publicNameMap = dbScriptPublicNameMap.get(dbName) ;
        Map<String, Map<String, String>> nameMap = dbScriptNameMap.get(dbName) ;

        if(publicNameMap == null || nameMap == null) {
            return ValidationStatusCode.NO_DATABASE_MATCH;
        }

        Map<String, String> savedPublicNameMap = nameMap.get(name);
        Map<String, String> savedNameMap = publicNameMap.get(publicName);

        if(savedPublicNameMap == null) {
            return ValidationStatusCode.PUBLIC_NAME_MISSING;
        }

        if(savedNameMap == null) {
            return ValidationStatusCode.NAME_MISSING;
        }

        String savedName = savedPublicNameMap.get(FunctionName);
        String savedPublicName = savedNameMap.get(PublicName);

        if(!(savedName.equals(name) && savedPublicName.equals(publicName))) {
            return ValidationStatusCode.NAMES_MISMATCH;
        }

        return ValidationStatusCode.SUCCESS;
    }

    public boolean loadProcedure(GraphDatabaseService db, Transaction tx, String publicName) {
        log.info("This class : " + this );
        String dbName = db.databaseName() ;
        ScriptEngine engine = dbScriptEngineMap.get(dbName);
        Map<String, Map<String, String>> publicNameMap = dbScriptPublicNameMap.get(dbName);
        Map<String, Map<String, String>> nameMap = dbScriptNameMap.get(dbName);

        Node n = tx.findNode(JS_StoredProcedure, PublicName, publicName);
        try {
            if (n != null) {
                loadScriptIntoEngine(engine, publicNameMap, nameMap, n);
                return true;
            }
        } catch(ScriptException e) {
            log.error("Could not load stored JS Script due to eval error. See message", e);
        }
        return false;
    }

    private void loadScriptIntoEngine(ScriptEngine engine, Map<String, Map<String, String>> publicNameMap, Map<String, Map<String, String>> nameMap, Node n) throws ScriptException {
        String pubName = n.getProperty(PublicName).toString();
        String script = n.getProperty(Script).toString();
        String name = n.getProperty(FunctionName).toString();
        log.debug(String.format("Loading script: %s from DB", name));

        engine.eval(script);
        Map<String, String> data = new HashMap<>();
        data.put(FunctionName, name);
        data.put(PublicName, pubName);
        publicNameMap.put(pubName, data);
        nameMap.put(name, data);
    }

}
