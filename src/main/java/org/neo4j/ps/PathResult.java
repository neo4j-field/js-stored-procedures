package org.neo4j.ps;

import org.neo4j.graphdb.Path;

public class PathResult {
  public Path path;

  public PathResult(Path path) {
    this.path = path;
  }

  public Path getPath() {
    return path;
  }

  public void setPath(Path path) {
    this.path = path;
  }
}
