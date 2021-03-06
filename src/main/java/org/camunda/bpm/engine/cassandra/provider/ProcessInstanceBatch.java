package org.camunda.bpm.engine.cassandra.provider;

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;

import org.camunda.bpm.engine.cassandra.provider.table.ProcessInstanceTableHandler;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.Statement;

public class ProcessInstanceBatch extends LockedBatch<ExecutionEntity> {
  
  public ProcessInstanceBatch(ExecutionEntity entity) {
    super(entity);
  }

  protected void addLockStatement(BatchStatement batch) {
    Statement st=update(ProcessInstanceTableHandler.TABLE_NAME)
        .with(set("version", entity.getRevisionNext()))
        .where(eq("id", entity.getId()))
        .onlyIf(eq("version", entity.getRevision()));
    batch.add(st);
    setVersion( Integer.toString(entity.getRevision()));
  }
  
}
