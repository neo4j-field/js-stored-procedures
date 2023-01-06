# Neo4j JavaScript Stored Procedure Module

This plugin allows developing the stored procedures for Neo4j using Javascript. 

It allows the user to register a javascript function with a name and invoke that function. EAch function is registered against a database and it's exeuctionis limited to that database only. Each of these procedures are stored as a node in the database. So, using RBAC we can limit who can create/update these procedures.
## Installation
Run ``mvn clean package``, deploy .jar to your Neo4j plugins folder (tested with Neo4j Enterprise 4.4.6), restart dbms.

## Usage
Run the following cypher to persist your Javascript stored procedure code block (in the form of a function) in the dbms:

``CALL js.procedure.register(<JSFunctinScript>, <ExternalNameUsed>>, 
<ConfigMap>)``

| Paramter Name | Required?   | Accepted Values                                                                                                      |
|---------------|-------------|----------------------------------------------------------------------------------------------------------------------|
| JSFunctinScript|Yes| Valid Javascript function block as string                                                                            |
|ExternalNameUsed|Yes| String. This is the name that would be used to invoke the function.                                        |
|ConfigMap|No| Map. This is not used at this time. For future functionality purpose this is added. |

On successful registration, invoke the cypher below to execute your new Javascript procedure:

``CALL js.storedproc.invoke('<globalProcName>')``


| Paramter Name    | Required?   | Accepted Values                                           |
|------------------|-------------|-----------------------------------------------------------|
| globalProcName   |Yes| String with the name of a registered Javascript procedure |

On successful execution this will return a Map with the results from the Javascript function.

## Example
In cypher-shell or neo4j browser, execute:

``CALL js.procedure.register('function myTest() {log.info(\'Hello neo4j\');}', "myTest")``

Validate your DB objects by running:

``MATCH (m:JS_StoredProcedure) RETURN m;``

Next, run your new procedure:

``CALL js.procedure.invoke('myTest')``

Finally, inspect your debug.log to see the "Hello World" message.


### Some Internal Operation Notes







