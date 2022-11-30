package org.neo4j.ps;

import org.neo4j.graphdb.Path;

import java.util.Map;

public class MapResult {
  public Map map;

  public MapResult(Map path) {
    this.map = path;
  }

  public Map getMap() {
    return map;
  }

  public void setMap(Map map) {
    this.map = map;
  }
}
