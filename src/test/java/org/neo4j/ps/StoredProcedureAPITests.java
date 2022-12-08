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

    @Test
    @Order(0)
    public void testRegisterProcedureGlobalStatementOnly() {
        String script = "" +
                "console.log('testing statement');" +
                "var str='cypher';";
        Map<String, Object> params = new HashMap<>();
        params.put("script", script);
        params.put("name", "test");
        try(Session session = driver.session()) {
            Result result = session.run(REGISTER_CALL, params);
            Assertions.assertEquals("There must exist exactly one JS function per register request.",
                    result.single().get("message").asString(),
                    "Only global statement must result in error message");
        }
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
        try(Session session = driver.session()) {
            Result result = session.run(REGISTER_CALL, params);
            Assertions.assertEquals("There must exist exactly one JS function per register request.",
                    result.single().get("message").asString(),
                    "Global statement + Function must result in error message");
        }
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
        try(Session session = driver.session()) {
            Result result = session.run(REGISTER_CALL, params);
            Assertions.assertEquals("There must be exactly one parameter in JS function.",
                    result.single().get("message").asString(),
                    "Function without variable must result in error message");
        }
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
        try(Session session = driver.session()) {
            Result result = session.run(REGISTER_CALL, params);
            Assertions.assertEquals("There must be exactly one parameter in JS function.",
                    result.single().get("message").asString(),
                    "Function with multiple variables must result in error message");
        }
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
        try(Session session = driver.session()) {
            Result result = session.run(REGISTER_CALL, params);
            Assertions.assertEquals("Only predefined Java.type can be used in JS function.",
                    result.single().get("message").asString(),
                    "Java.type cannot be used in the function");
        }
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
        try(Session session = driver.session()) {
            Result result = session.run(REGISTER_CALL, params);
            Assertions.assertEquals("Success",
                    result.single().get("message").asString(),
                    "Registration should be done");
        }
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
        try(Session session = driver.session()) {
            Result result = session.run(REGISTER_CALL, params);
            Assertions.assertEquals("Success",
                    result.single().get("message").asString(),
                    "Registration should be done");
        }
    }

    @Test
    @Order(2)
    public void testInvokeNonExistingProcedure() {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> procParams = new HashMap<>();
        params.put("name", "countMyNodes");
        params.put("params", procParams);
        try(Session session = driver.session()) {
            Result result = session.run(INVOKE_CALL, params);
            Assertions.assertEquals("Procedure doesn't exist or cannot be loaded into ScriptEngine.",
                    result.single().get("map").asMap().get("error"),
                    "Non existing procedure must return error message");
        }
    }

    @Test
    @Order(2)
    public void testInvokeReadProcedure() {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> procParams = new HashMap<>();
        params.put("name", "countNodes");
        params.put("params", procParams);
        try(Session session = driver.session()) {
            Result result = session.run(INVOKE_CALL, params);
            Assertions.assertEquals(2L,
                    result.single().get("map").asMap().get("result"),
                    "Count must be 2 for two procedure nodes");
        }
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
        try(Session session = driver.session()) {
            Result result = session.run(INVOKE_CALL, params);
            Assertions.assertNull(result.single().get("map").asMap().get("result"),
                    "Function createNode doesn't return anything hence null");
        }
    }

    @Test
    @Order(4)
    public void testInvokeReadProcedureAfterWrite() {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> procParams = new HashMap<>();
        params.put("name", "countNodes");
        params.put("params", procParams);
        try(Session session = driver.session()) {
            Result result = session.run(INVOKE_CALL, params);
            Assertions.assertEquals(3L,
                    result.single().get("map").asMap().get("result"),
                    "Count must be 3 for two procedure + one Test nodes");
        }
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
        try(Session session = driver.session()) {
            Result result = session.run(REGISTER_CALL, params);
            Assertions.assertEquals("Success",
                    result.single().get("message").asString(),
                    "Registration should be done");
        }
    }

    @Test
    @Order(6)
    public void testInvokeReadProcedureAfterModification() {
        Map<String, Object> params = new HashMap<>();
        Map<String, Object> procParams = new HashMap<>();
        procParams.put("label", "Test");
        params.put("name", "countNodes");
        params.put("params", procParams);
        try(Session session = driver.session()) {
            Result result = session.run(INVOKE_CALL, params);
            Assertions.assertEquals(1L,
                    result.single().get("map").asMap().get("result"),
                    "Count must be 1 for one Test nodes");
        }
    }
}
