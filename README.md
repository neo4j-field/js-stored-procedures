# Neo4j JavaScript Stored Procedure Module

This plugin allows developing the stored procedures for Neo4j with Javascript. 

It uses these nodes to persist the stored procedures and the related details.

1. JS_StoredProcedure
   * This node stores the JS script, public name, function name and last updated time stamp.
2. JS_RequiredClasses
   * This node stores all the Neo4j DB related classes and other external java classes required for the stored procedures. 
   This list is important to limit what Java classes can be leveraged by stored procedures. It contains 2 separate lists named basicDBClasses and otherClasses.
3. JS_ProcedureLock
   * This node is is required for making sure we can keep the script engine read the updates for the scripts.
   
The procedure node also has a property called **loadOnStartup** that defines if this script should be loaded into the script engine on startup.

These nodes can be protected using RBAC so that only authorized users can create/update/delete the stored procedures.

## Installation
Run ``mvn clean package``, deploy .jar to your Neo4j plugins folder restart dbms.

## Usage
Run the following cypher to persist your Javascript stored procedure code block (in the form of a function) in the dbms:

``CALL js.procedure.register(<JSFunctinScript>, <ExternalNameUsed>>, 
<ConfigMap>)``

| Paramter Name | Required?   | Accepted Values                                                                                                      |
|---------------|-------------|----------------------------------------------------------------------------------------------------------------------|
| script|Yes| Valid Javascript function block as string                                                                            |
|publicName|Yes| String. This is the name that would be used to invoke the function.                                        |
|config|No| Map. This is not used at this time. For future functionality purpose this is added. |

This will register the function and on the leader it will also gets added to the script engine immediately. On followers it gets loaded into script engine when it is invoked first time.

To invoke JS stored procedure:


``CALL js.storedproc.invoke('<globalProcName>')``


| Paramter Name    | Required?   | Accepted Values                                           |
|------------------|-------------|-----------------------------------------------------------|
| globalProcName   |Yes| Public name of the registered Javascript procedure |
| parameters   |No| Map All the input parameters if any should be passed as a map  |

On successful execution this will return a Map with the results from the Javascript function.

## Example
In cypher-shell or neo4j browser, execute:

``CALL js.procedure.register("function myTest(params) {var log = params['log']; log.info('Hello neo4j');}", "myTest", {})``

Validate your DB objects by running:

``MATCH (m:JS_StoredProcedure) RETURN m;``

Next, run your new procedure:

``CALL js.procedure.invoke('myTest')``

Finally, inspect your debug.log to see the "Hello World" message.

Another example: 

``CALL js.procedure.register("function nodeCount(params) { var log = params['log'] ; var txn = params['txn'] ; log.info('Testing log') ;  return txn.getAllNodes().stream().count() }", "nodecount", {})``

Run this procedure next

``call js.procedure.invoke("nodecount", {})``

This should return the node count.

### Some Internal Operation Notes

The updates to the existing stored procedures are controlled by the intevral value on the 
**JS_ProcedureLock** node. By default it is one hour. when a procedure is invoked it checks 
when it was last read from database. If it is smaller than the last update time of the update lock, 
it will try to read the script changes if any and update the engine.






