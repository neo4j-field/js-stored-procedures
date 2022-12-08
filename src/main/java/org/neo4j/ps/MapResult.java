package org.neo4j.ps;

import java.util.HashMap;
import java.util.Map;

public class MapResult {

  public final static MapResult EMPTY = new MapResult(new HashMap());

  public final Map<String, Object> map;

  public MapResult(Map map) {
    this.map = map;
  }

  public Map getMap() {
    return map;
  }

}
