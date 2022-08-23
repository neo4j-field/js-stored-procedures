# Neo4j JavaScript Stored Procedure Compatibility Module

Plugin that allows the development of Neo4j Stored Procedures leveraging Javascript as a primary language, 
and Java imports as needed.

## Installation
Run ``mvn clean package``, deploy .jar to your Neo4j plugins folder (tested with Neo4j Enterprise 4.4.6), restart dbms.

## Usage
Run the following cypher to persist your Javascript stored procedure code block (in the form of a function) in the dbms:

``CALL js.storedproc.register('<jsFunctionString>', '<globalProcName>', '<internalProcName>' 
"<requiredClasses>")``

| Paramter Name | Required?   | Accepted Values                                                                                                      |
|---------------|-------------|----------------------------------------------------------------------------------------------------------------------|
| jsFunctionString|Yes| Valid Javascript function block as string                                                                            |
|globalProcName|No| String. Omition specifies a private function available to other functions only                                       |
|internalProcName|No| String. Required if globalProcName is blank or not provided                                                          |
|requiredClasses|No| List of String. List of required Java Classes to support Stored Proc functionality. Subject to internal constraints. |

On successful registration, invoke the cypher below to execute your new Javascript procedure:

``CALL js.storedproc.invoke('<globalProcName>')``


| Paramter Name    | Required?   | Accepted Values                                           |
|------------------|-------------|-----------------------------------------------------------|
| globalProcName   |Yes| String with the name of a registered Javascript procedure |

On successful execution this will return a Map with the results from the Javascript function.

## Example
In cypher-shell or neo4j browser, execute:

``CALL js.storedproc.register('function myTest() {log.info(\'Hello neo4j\');}', "myTest")``

Validate your DB objects by running:

``MATCH (m:JS_StoredProc) RETURN m;``

Next, run your new procedure:

``CALL js.storedproc.invoke('myTest')``

Finally, inspect your debug.log to see the "Hello World" message.


### Some Internal Operation Notes

When a Stored Procedure is successfully registered, some maintenance objects are created in the dbms. Namely:
- One Node with the following properties:
  - Label: JS_StoredProc
  - name: JS Function Name as obtained from original SCript
  - publicName: Public Name for the Procedure
  - script: Actual JavaScript code
- One Node for each of the required Java classes:
  - Label: JS_Required_Class
  - name: name
  - class: class
- Relationships to connect all objects as needed

For more information, see EnumJSProcLabels and EnumJSProcRelationshipTypes






