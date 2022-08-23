if (!$OPTIONS._fx) {
    print("Usage: jjs -scripting -fx astviewer.js -- <.js file>");
    exit(1);
}

// do we have a script file passed? if not, use current script
var sourceName = arguments.length == 0? __FILE__ : arguments[0];

// load parser.js from nashorn resources
load("nashorn:parser.js");

var ast = parse(readFully(sourceName));