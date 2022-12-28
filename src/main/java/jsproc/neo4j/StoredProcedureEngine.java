package jsproc.neo4j;

import org.neo4j.graphdb.*;
import org.neo4j.kernel.internal.GraphDatabaseAPI;
import org.neo4j.logging.Log;


import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class StoredProcedureEngine {
    public static Label JS_StoredProcedure = Label.label("JS_StoredProcedure");
    public static Label JS_RequiredClasses = Label.label("JS_RequiredClasses");
    public static Label JS_ProcedureLock = Label.label("JS_ProcedureLock");
    public static Label JS_FUNCTION = Label.label("JS_FUNCTION");

    private static  final ScriptEngineManager scriptFactory = new ScriptEngineManager();
    private static Map<String, ScriptEngine> dbScriptEngineMap = new HashMap<>();
    private static Map<String, Map<String, ScriptStatus>> dbScriptPublicNameMap = new HashMap<>();
    private static Map<String, Map<String, ScriptStatus>> dbScriptNameMap = new HashMap<>();
    private static Map<String, Date> dbScriptLastUpdate = new HashMap<>();

    public static final String PublicName = "publicName";
    public static final String FunctionName = "name";
    public static final String Script = "script";
    public static final String CheckSum = "checkSum";
    public static final String LastReadTime = "lastReadTime";
    public static final String LastUpdatedime = "lastUpdatedTime";
    public static final String LoadOnStartup = "loadOnStartup";
    public static final String LoadInterval = "loadInterval";

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
            Map<String, ScriptStatus> publicNameMap = new HashMap<>();
            Map<String, ScriptStatus> nameMap = new HashMap<>();
            dbScriptPublicNameMap.put(dbName, publicNameMap);
            dbScriptNameMap.put(dbName, nameMap);
            procNodes.stream().forEach(n -> {
                try {
                    loadScriptIntoEngine(engine, publicNameMap, nameMap, n, false);
                } catch(ScriptException e) {
                    log.error("Could not load stored JS Script due to eval error. See message", e);
                }
            });
        }
    }

    public ScriptDetails getEngine(GraphDatabaseService db, Transaction tx, String procedureName) {
        String dbName = db.databaseName();
        ScriptDetails details = new ScriptDetails();
        Map<String, ScriptStatus> publicNameMap = dbScriptPublicNameMap.get(dbName);
        ScriptStatus data = publicNameMap.get(procedureName);

        UpdatedStatus updatedStatus = UpdatedStatus.getInstance(db) ;

        ScriptEngine engine = dbScriptEngineMap.get(dbName);
        details.setEngine(engine);
        if( ( data == null || updatedStatus.getLastUpdated().getTime() > data.lastReadTime.getTime() ) && (data != null && !data.disabled) && !loadProcedure(db, tx, procedureName, updatedStatus)) {
            //Procedure doesn't exist and cannot be loaded into ScriptEngine
            return null;
        }

        details.setName(data.functionName);
        details.setPublicName(procedureName);
        return details;
    }

    public ValidationStatusCode validateFunction(String dbName, String publicName, String name) {
        Map<String, ScriptStatus> publicNameMap = dbScriptPublicNameMap.get(dbName);
        Map<String, ScriptStatus> nameMap = dbScriptNameMap.get(dbName);

        if(publicNameMap == null || nameMap == null) {
            return ValidationStatusCode.NO_DATABASE_MATCH;
        }

        ScriptStatus savedPublicNameMap = nameMap.get(name);
        ScriptStatus savedNameMap = publicNameMap.get(publicName);

        if(savedPublicNameMap == null) {
            return ValidationStatusCode.PUBLIC_NAME_MISSING;
        }

        if(savedNameMap == null) {
            return ValidationStatusCode.NAME_MISSING;
        }

        String savedName = savedPublicNameMap.functionName;
        String savedPublicName = savedNameMap.publicName;

        if(!(savedName.equals(name) && savedPublicName.equals(publicName))) {
            return ValidationStatusCode.NAMES_MISMATCH;
        }

        return ValidationStatusCode.SUCCESS;
    }

    public boolean loadProcedure(GraphDatabaseService db, Transaction tx, String publicName, UpdatedStatus updatedStatus) {
        log.info("This class : " + this );
        String dbName = db.databaseName();
        ScriptEngine engine = dbScriptEngineMap.get(dbName);
        Map<String, ScriptStatus> publicNameMap = dbScriptPublicNameMap.get(dbName);
        Map<String, ScriptStatus> nameMap = dbScriptNameMap.get(dbName);
        ScriptStatus data = publicNameMap.get(publicName);

        Node n = tx.findNode(JS_StoredProcedure, PublicName, publicName);
        try {
            if (n != null) {
                Date lastUpdated = (Date) n.getProperty(LastUpdatedime);
                if( lastUpdated.getTime() > updatedStatus.getLastUpdated().getTime()) {
                    loadScriptIntoEngine(engine, publicNameMap, nameMap, n, true);
                }
                data.lastReadTime = new Date() ;
                return true;
            }
        } catch(ScriptException e) {
            log.error("Could not load stored JS Script due to eval error. See message", e);
        }
        return false;
    }

    private void loadScriptIntoEngine(
            ScriptEngine engine,
            Map<String, ScriptStatus> publicNameMap,
            Map<String, ScriptStatus> nameMap,
            Node n,
            boolean forceLoad) throws ScriptException {
        String pubName = n.getProperty(PublicName).toString();
        String script = n.getProperty(Script).toString();
        String name = n.getProperty(FunctionName).toString();
        boolean loaded = true ;

        if( !forceLoad ) {
            Object temp = n.getProperty(LoadOnStartup, null);
            try {
                if (temp != null) {
                    loaded = ((Boolean) temp).booleanValue();
                }
            } catch (Exception e) {
            }
        }

        log.debug(String.format("Loading script: %s from DB", name));

        ScriptStatus data = publicNameMap.get(pubName);
        if( data == null ) {
            data = new ScriptStatus() ;
            data.functionName = name;
            data.publicName = pubName;

            publicNameMap.put(pubName, data);
            nameMap.put(name, data);
        }

        data.loaded = loaded ;
        data.lastReadTime = new Date() ;

        if( loaded ) {
            engine.eval(script);
        }
    }

}
