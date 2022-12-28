package jsproc.neo4j;

import jdk.nashorn.api.tree.CompilationUnitTree;
import jdk.nashorn.api.tree.FunctionDeclarationTree;
import jdk.nashorn.api.tree.Parser;
import org.neo4j.graphdb.*;
import org.neo4j.logging.Log;
import org.neo4j.procedure.*;

import javax.script.Invocable;
import javax.xml.bind.DatatypeConverter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class StoredProcedureAPI {

    @Context
    public Transaction txn;
    @Context
    public Log log;
    @Context
    public GraphDatabaseService db;

    private static final Pattern JAVA_TYPE_REGEX = Pattern.compile("Java.type\\s*\\(\\s*['\"]([a-zA-Z_$][a-zA-Z\\d_$]*\\.?)+['\"]\\s*\\)");
    private static final MessageDigest MESSAGE_DIGEST;
    private static final String UNREGISTER_SCRIPT_TEMPLATE = "function %s(params){throw new ReferenceError('Cannot invoke unregistered procedure');}";

    static {
        try {
            MESSAGE_DIGEST = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     *
     * @param script
     * @param publicName
     * @param config
     * @return
     * @throws RuntimeException
     */
    @Procedure(name = "js.procedure.register", mode = Mode.WRITE)
    @Description("js.procedure.register(<Valid Javascript Function Code>, <Public Name>, <Config>) - Save a Javascript Stored Procedure")
    public Stream<RegisterResult> registerStoredProcedure(@Name(value = "script") String script,
                                                    @Name(value = "publicName" , defaultValue = "") String publicName,
                                                    @Name(value = "config", defaultValue = "{}") Map<String,Object> config) {

        Parser parser = Parser.create();
        JSParserDiagnosticListener listener = new JSParserDiagnosticListener();
        CompilationUnitTree cut = parser.parse(publicName, script, listener);

        if(listener.hasError()) {
            // There is some parse error.
            return Stream.of(new RegisterResult(listener.getMessage()));
        }

        if(cut.getSourceElements().size() != 1 || !(cut.getSourceElements().get(0) instanceof FunctionDeclarationTree)) {
            // Error
            return Stream.of(new RegisterResult("There must exist exactly one JS function per register request."));
        } else if (((FunctionDeclarationTree)(cut.getSourceElements().get(0))).getParameters().size() != 1) {
            // Error
            return Stream.of(new RegisterResult("There must be exactly one parameter in JS function."));
        } else if(JAVA_TYPE_REGEX.matcher(script).find()) {
            // Error
            return Stream.of(new RegisterResult("Only predefined Java.type can be used in JS function."));
        }
        String name = ((FunctionDeclarationTree)(cut.getSourceElements().get(0))).getName().getName();
        String checkSum = DatatypeConverter.printHexBinary(MESSAGE_DIGEST.digest(script.getBytes(StandardCharsets.UTF_8))).toUpperCase();
        if (validate(publicName, name)) {
            UpdatedStatus updatedStatus = UpdatedStatus.getInstance(db) ;
            updatedStatus.updateTimestamp(txn, new Date());
            Node n = txn.findNode(StoredProcedureEngine.JS_StoredProcedure, StoredProcedureEngine.PublicName, publicName);
            if(n == null) {
                n = txn.createNode(StoredProcedureEngine.JS_StoredProcedure);
                n.setProperty(StoredProcedureEngine.PublicName, publicName);
                n.setProperty(StoredProcedureEngine.FunctionName, name);
                n.setProperty(StoredProcedureEngine.LastUpdatedime, updatedStatus.getLastUpdated());
                n.setProperty(StoredProcedureEngine.CheckSum, checkSum);
            } else if(String.valueOf(n.getProperty(StoredProcedureEngine.CheckSum)).equals(checkSum)) {
                return Stream.of(RegisterResult.NO_CHANGE);
            }
            n.setProperty(StoredProcedureEngine.CheckSum, checkSum);
            n.setProperty(StoredProcedureEngine.Script, script);
            n.setProperty(StoredProcedureEngine.LastUpdatedime, updatedStatus.getLastUpdated());
            StoredProcedureEngine.getStoredProcedureEngine(null).loadProcedure(db, txn, publicName, updatedStatus);
            return Stream.of(RegisterResult.SUCCESS);
        } else {
            throw new RuntimeException("Proc Name not unique");
        }
    }

    @Procedure(name = "js.procedure.unregister", mode = Mode.WRITE)
    @Description("js.procedure.unregister(<Public Name>) - Unregister a Javascript Stored Procedure")
    public Stream<RegisterResult> unregisterStoredProcedure(@Name(value = "publicName") String publicName) {

        Node n = txn.findNode(StoredProcedureEngine.JS_StoredProcedure, StoredProcedureEngine.PublicName, publicName);
        if (n == null) {
            return Stream.of(new RegisterResult("Procedure '"+publicName+"' not found"));
        } else {
            return registerStoredProcedure(String.format(UNREGISTER_SCRIPT_TEMPLATE, n.getProperty(StoredProcedureEngine.FunctionName)), publicName, null);
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
    public Stream<MapResult> invokeStoredProcedure(@Name("procedureName") String procedureName, @Name(value = "parameters") Map<String, Object> parameters) {
        Map<String, Object> result = new HashMap<>();
        ScriptDetails details = StoredProcedureEngine.getStoredProcedureEngine(null).getEngine(db, txn, procedureName);

        if (details == null) {
            result.put("error", "Procedure doesn't exist or cannot be loaded into ScriptEngine.");
        } else {
            if(parameters == null) {
                parameters = new HashMap();
            }
            parameters.put("txn", txn);
            parameters.put("log", log);
            try {
                Invocable invocable = (Invocable) details.getEngine();
                Object response = invocable.invokeFunction(details.getName(), parameters);
                result.put("result", response);
            } catch (Exception e) {
                log.error("Something went wrong", e);
                result.put("error", e.getMessage());
            }
        }
        return Stream.of(new MapResult(result));
    }

    private boolean validate(String publicName, String name) {
        ValidationStatusCode status = StoredProcedureEngine.getStoredProcedureEngine(null).validateFunction(db.databaseName(), publicName, name);
        if(status == ValidationStatusCode.NAME_MISSING || status == ValidationStatusCode.PUBLIC_NAME_MISSING) {
            Node n = txn.findNode(StoredProcedureEngine.JS_StoredProcedure, StoredProcedureEngine.PublicName, publicName);
            if (n != null) {
                // We already have a function with this public name.
                if(status == ValidationStatusCode.NAME_MISSING) {
                    // Since we couldn't find the name, but found the public name
                    // There is a mismatch.
                    return false;
                }
            }
        }
        return true;
    }
}
