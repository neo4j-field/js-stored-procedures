package org.neo4j.ps;

import org.junit.jupiter.api.*;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import java.util.HashMap;
import java.util.Map;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class StoredProcedureAPITests {

    private static final String REGISTER_CALL = "call js.procedure.register($script,$name, null)";
    private static final String INVOKE_CALL = "call js.procedure.invoke($name, $params)";

    private Driver driver;
    private Neo4j embeddedDatabaseServer;

    @BeforeAll
    void initializeNeo4j() throws InterruptedException {
        this.embeddedDatabaseServer = Neo4jBuilders.newInProcessBuilder()
                .withProcedure(StoredProcedureAPI.class)
                .withDisabledServer()
                .build();
        this.driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI());
        Thread.sleep(5000);
    }

    @AfterAll
    void closeAll(){
        this.driver.close();
        this.embeddedDatabaseServer.close();
    }

    private void test(String cypher, Map<String, Object> params, String returnKey, Object expected, String message) {
        try(Session session = driver.session()) {
            Result result = session.run(cypher, params);
            Object actual = (expected instanceof String) ? result.single().get(returnKey).asString()
                    : (expected instanceof Map) ? result.single().get(returnKey).asMap()
                    : result.single().get(returnKey);
            Assertions.assertEquals(expected, actual, message);
        }
    }

    @Test
    @Order(0)
    public void testRegisterProcedureGlobalStatementOnly() {
        String script = "" +
                "console.log('testing statement');" +
                "var str='cypher';";
        Map<String, Object> params = new HashMap<>();
        params.put("script", script);
        params.put("name", "test");
        test(REGISTER_CALL, params, "message", "There must exist exactly one JS function per register request.", "Only global statement must result in error message");
    }

    @Test
    @Order(0)
    public void testRegisterProcedureGlobalStatementWithFunction() {
        String script = "" +
                "console.log('testing statement');" +
                "var str='cypher';" +
                "function test(params){" +
                "   var log=params['log'];" +
                "   var txn=params['txn'];" +
                "   return txn.getAllNodes().stream().count();" +
                "}";
        Map<String, Object> params = new HashMap<>();
        params.put("script", script);
        params.put("name", "test");
        test(REGISTER_CALL, params, "message", "There must exist exactly one JS function per register request.", "Global statement + Function must result in error message");
    }

    @Test
    @Order(0)
    public void testRegisterProcedureFunctionWithoutVariable() {
        String script = "" +
                "function test(){" +
                "   return 1;" +
                "}";
        Map<String, Object> params = new HashMap<>();
        params.put("script", script);
        params.put("name", "test");
        test(REGISTER_CALL, params, "message", "There must be exactly one parameter in JS function.", "Function without variable must result in error message");
    }

    @Test
    @Order(0)
    public void testRegisterProcedureFunctionWithMultipleVariable() {
        String script = "" +
                "function test(params,text){" +
                "   var log=params['log'];" +
                "   var txn=params['txn'];" +
                "   return txn.getAllNodes().stream().count();" +
                "}";
        Map<String, Object> params = new HashMap<>();
        params.put("script", script);
        params.put("name", "test");
        test(REGISTER_CALL, params, "message", "There must be exactly one parameter in JS function.", "Function with multiple variables must result in error message");
    }

    @Test
    @Order(0)
    public void testRegisterProcedureWithJavaTypeUsage() {
        String script = "" +
                "function test(params){" +
                "   var log=params['log'];" +
                "   var txn=params['txn'];" +
                "   var Driver = Java.type('org.neo4j.driver.Driver');" +
                "   return txn.getAllNodes().stream().count();" +
                "}";
        Map<String, Object> params = new HashMap<>();
        params.put("script", script);
        params.put("name", "test");
        test(REGISTER_CALL, params, "message", "Only predefined Java.type can be used in JS function.",  "Java.type cannot be used in the function");
    }

    @Test
    @Order(1)
    public void testRegisterValidReadProcedure() {
        String script = "" +
                "function nodeCount(params){" +
                "   var log=params['log'];" +
                "   var txn=params['txn'];" +
                "   return txn.getAllNodes().stream().count();" +
                "}";
        Map<String, Object> params = new HashMap<>();
        params.put("script", script);
        params.put("name", "countNodes");
        test(REGISTER_CALL, params, "message", "Success",  "Registration should be done");
    }

    @Test
    @Order(1)
    public void testRegisterValidWriteProcedure() {
        String script = "" +
                "function createNode(params){" +
                "   var log=params['log'];" +
                "   var txn=params['txn'];" +
                "   var n = txn.createNode(Label.label(params['label']));" +
                "   n.setProperty('id',params['id']);" +
                "}";
        Map<String, Object> params = new HashMap<>();
        params.put("script", script);
        params.put("name", "createNode");
        test(REGISTER_CALL, params, "message", "Success",  "Registration should be done");
    }

    @Test
    @Order(2)
    public void testInvokeNonExistingProcedure() {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> procParams = new HashMap<>();
        params.put("name", "countMyNodes");
        params.put("params", procParams);
        Map<String, Object> expected = new HashMap<>();
        expected.put("error","Procedure doesn't exist or cannot be loaded into ScriptEngine.");
        test(INVOKE_CALL, params, "map", expected, "Non existing procedure must return error message");
    }

    @Test
    @Order(2)
    public void testInvokeReadProcedure() {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> procParams = new HashMap<>();
        params.put("name", "countNodes");
        params.put("params", procParams);
        Map<String, Object> expected = new HashMap<>();
        expected.put("result",2L);
        test(INVOKE_CALL, params, "map", expected, "Count must be 2 for two procedure nodes");
    }

    @Test
    @Order(3)
    public void testInvokeWriteProcedure() {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> procParams = new HashMap<>();
        procParams.put("label","Test");
        procParams.put("id",111);
        params.put("name", "createNode");
        params.put("params", procParams);
        Map<String, Object> expected = new HashMap<>();
        expected.put("result",null);
        test(INVOKE_CALL, params, "map", expected, "Function createNode doesn't return anything hence null");
    }

    @Test
    @Order(4)
    public void testInvokeReadProcedureAfterWrite() {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> procParams = new HashMap<>();
        params.put("name", "countNodes");
        params.put("params", procParams);
        Map<String, Object> expected = new HashMap<>();
        expected.put("result",3L);
        test(INVOKE_CALL, params, "map", expected, "Count must be 3 for two procedure + one Test nodes");
    }

    @Test
    @Order(5)
    public void testReRegisterReadProcedure() {
        String script = "" +
                "function nodeCount(params){" +
                "   var log=params['log'];" +
                "   var txn=params['txn'];" +
                "   return txn.findNodes(Label.label(params['label'])).stream().count();" +
                "}";
        Map<String, Object> params = new HashMap<>();
        params.put("script", script);
        params.put("name", "countNodes");
        test(REGISTER_CALL, params, "message", "Success",  "Registration should be done");
    }

    @Test
    @Order(6)
    public void testInvokeReadProcedureAfterModification() {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> procParams = new HashMap<>();
        procParams.put("label", "Test");
        params.put("name", "countNodes");
        params.put("params", procParams);
        Map<String, Object> expected = new HashMap<>();
        expected.put("result",1L);
        test(INVOKE_CALL, params, "map", expected, "Count must be 1 for one Test nodes");
    }
}