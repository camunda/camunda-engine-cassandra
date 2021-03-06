package org.camunda.bpm.engine.cassandra.provider.table;


import java.util.Collections;
import java.util.List;

public class DeploymentTableHandler extends AbstractTableHandler {
  
  public final static String TABLE_NAME = "cam_deployment";

  protected final static String CREATE_TABLE_STMNT = "CREATE TABLE IF NOT EXISTS "+TABLE_NAME +" "
      + "(id text, "
      + "name text, "
      + "deploy_time bigint, "
      + "PRIMARY KEY (id));";
  
  protected final static String DROP_TABLE = "DROP TABLE IF EXISTS "+TABLE_NAME;
  
  public static String getTypeName() {
    return TABLE_NAME;
  }
  
  protected String getCreateStatement() {
    return CREATE_TABLE_STMNT;
  }
  
  protected String getDropStatement() {
    return DROP_TABLE;
  }

  public List<String> getTableNames() {
    return Collections.singletonList(TABLE_NAME);
  }
}
