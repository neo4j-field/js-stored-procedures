var CUSTOMER_LABEL_NAME = "Customer"
var SIGNUP_LABEL_NAME = "Signup"
var VIEWING_LABEL_NAME = "Viewing"

var ATTRIBUTION_REL_NAME = "FROM_ATTRIBUTION"
var FIRST_ACTIVITY_REL_NAME = "FIRST_ACTIVITY"
var FIRST_VIEWING_REL_NAME = "FIRST_VIEWING"
var INCLUDES_SIGNUP_REL_NAME = "INCLUDES_SIGNUP"
var NEXT_REL_NAME = "NEXT"
var VIEWED_REL_NAME = "VIEWED"

var ArrayList = Java.type("java.util.ArrayList")
var Direction = Java.type("org.neo4j.graphdb.Direction")
var Label = Java.type("org.neo4j.graphdb.Label")
var PathBuilder = Java.type("org.neo4j.graphalgo.impl.util.PathImpl.Builder")
var RelationshipType = Java.type("org.neo4j.graphdb.RelationshipType")

// Start
log.info('Hello from Nashorn')
var customerNode = txn.findNode(Label.label(CUSTOMER_LABEL_NAME), 'id', 'A00003041N5YLXJFM330N')
var pathBuilders = new ArrayList
var pathBuilder = new PathBuilder(customerNode)

var firstActivityRel = customerNode.getSingleRelationship(
    RelationshipType.withName(FIRST_ACTIVITY_REL_NAME), Direction.OUTGOING)
if (firstActivityRel != null) {
    log.info('Found FIRST_ACTIVITY')
    pathBuilder = pathBuilder.push(firstActivityRel)

    var caNode = firstActivityRel.getEndNode()
    var firstViewingRel = caNode.getSingleRelationship(
        RelationshipType.withName(FIRST_VIEWING_REL_NAME), Direction.OUTGOING)
    if (firstViewingRel != null) {
        log.info('Found FIRST_VIEWING')
        pathBuilder = pathBuilder.push(firstViewingRel)
        var vNode = firstViewingRel.getEndNode()
        pathBuilders = followNext(vNode, pathBuilder)
    } else {
        var includesSignupRel = caNode.getSingleRelationship(
            RelationshipType.withName(INCLUDES_SIGNUP_REL_NAME), Direction.OUTGOING)
        pathBuilder = pathBuilder.push(includesSignupRel)
        var sNode = includesSignupRel.getEndNode()
        pathBuilders = followNext(sNode, pathBuilder)
    }
}
pathBuilders.add(pathBuilder)
log.info("There are " + pathBuilders.size() + " total PathBuilders")
var pathResults = new ArrayList
for (var i = 0; i < pathBuilders.length; i++) {
    var pb = pathBuilders.get(i)
    var path = pb.build()
    pathResults.add(path)
}
log.info("There are " + pathResults.size() + " total PathResults")

function followNext(n, pathBuilder) {
    log.info("FollowNext on " + n.getId())
    var output = new ArrayList
    getMetadataPathsFromEventNode(n, output)
    log.info("Added metadata paths: " + output.size() + " paths")

    var nextRel = n.getSingleRelationship(RelationshipType.withName(NEXT_REL_NAME), Direction.OUTGOING)
    while (nextRel != null) {
        log.info("Followed NEXT")
        pathBuilder = pathBuilder.push(nextRel)
        n = nextRel.getEndNode()
        getMetadataPathsFromEventNode(n, output)
        log.info("Added metadata paths: " + output.size() + " paths")
        nextRel = n.getSingleRelationship(RelationshipType.withName(NEXT_REL_NAME), Direction.OUTGOING)
    }
    output.add(pathBuilder)
    return output
}

function getMetadataPathsFromEventNode(n, output) {
    if (n.hasLabel(Label.label(VIEWING_LABEL_NAME))) {
        var viewedRel = n.getSingleRelationship(RelationshipType.withName(VIEWED_REL_NAME), Direction.OUTGOING)
        if (viewedRel != null) {
            log.info("Following VIEWED rel")
            var pb = new PathBuilder(n)
            pb = pb.push(viewedRel)
            output.add(pb)
        }
    } else if (n.hasLabel(Label.label(SIGNUP_LABEL_NAME))) {
        var fromAttribRel = n.getSingleRelationship(RelationshipType.withName(ATTRIBUTION_REL_NAME), Direction.OUTGOING)
        if (fromAttribRel != null) {
            log.info("Following FROM_ATTRIBUTION rel")
            var pb = new PathBuilder(n)
            pb = pb.push(fromAttribRel)
            output.add(pb)
        }
    }
}