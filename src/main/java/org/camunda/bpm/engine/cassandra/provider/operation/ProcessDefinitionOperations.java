package org.camunda.bpm.engine.cassandra.provider.operation;

import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;

import org.camunda.bpm.engine.cassandra.provider.CassandraPersistenceSession;
import org.camunda.bpm.engine.cassandra.provider.serializer.CassandraSerializer;
import org.camunda.bpm.engine.cassandra.provider.table.DeploymentTableHandler;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.BoundStatement;
import com.datastax.driver.core.Session;

import static org.camunda.bpm.engine.cassandra.provider.table.ProcessDefinitionTableHandler.*; 

public class ProcessDefinitionOperations implements EntityOperations<ProcessDefinitionEntity> {
  
  protected final static String INSERT = "INSERT into "+TABLE_NAME+" (id, key, version, category, name, deployment_id, suspension_state) "
      + "values "
      + "(?, ?, ?, ?, ?, ?, ?);";

  protected final static String INSERT_IDX_VERSION = "INSERT into "+TABLE_NAME_IDX_VERSION+" (key, version, id) "
      + "values "
      + "(?, ?, ?);";
  
  public void insert(CassandraPersistenceSession session, ProcessDefinitionEntity entity, BatchStatement flush) {
    Session s = session.getSession();

    CassandraSerializer<ProcessDefinitionEntity> serializer = session.getSerializer(ProcessDefinitionEntity.class);
   
    // insert deployment
    BoundStatement statement = s.prepare(INSERT).bind();    
    serializer.write(statement, entity);     
    flush.add(statement);
    
    // write index
    flush.add(s.prepare(INSERT_IDX_VERSION).bind(
        entity.getKey(),
        entity.getVersion(),
        entity.getId()));
  }

  public void delete(CassandraPersistenceSession session, ProcessDefinitionEntity entity, BatchStatement flush) {
    
  }

  public void update(CassandraPersistenceSession session, ProcessDefinitionEntity entity, BatchStatement flush) {
    
  }

}