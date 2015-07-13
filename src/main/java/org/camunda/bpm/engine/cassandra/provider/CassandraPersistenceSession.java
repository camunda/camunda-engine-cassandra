package org.camunda.bpm.engine.cassandra.provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.camunda.bpm.engine.OptimisticLockingException;
import org.camunda.bpm.engine.ProcessEngine;
import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.cassandra.cfg.CassandraProcessEngineConfiguration;
import org.camunda.bpm.engine.cassandra.provider.operation.BulkDeleteDeployment;
import org.camunda.bpm.engine.cassandra.provider.operation.BulkDeleteProcessDefinitionByDeploymentId;
import org.camunda.bpm.engine.cassandra.provider.operation.BulkDeleteResourcesByDeploymentId;
import org.camunda.bpm.engine.cassandra.provider.operation.BulkOperationHandler;
import org.camunda.bpm.engine.cassandra.provider.operation.CompositeEntityLoader;
import org.camunda.bpm.engine.cassandra.provider.operation.DeploymentOperations;
import org.camunda.bpm.engine.cassandra.provider.operation.EntityOperationHandler;
import org.camunda.bpm.engine.cassandra.provider.operation.EventSubscriptionOperations;
import org.camunda.bpm.engine.cassandra.provider.operation.ExecutionEntityOperations;
import org.camunda.bpm.engine.cassandra.provider.operation.LoadedCompositeEntity;
import org.camunda.bpm.engine.cassandra.provider.operation.ProcessDefinitionOperations;
import org.camunda.bpm.engine.cassandra.provider.operation.ProcessInstanceLoader;
import org.camunda.bpm.engine.cassandra.provider.operation.ResourceOperations;
import org.camunda.bpm.engine.cassandra.provider.operation.VariableEntityOperations;
import org.camunda.bpm.engine.cassandra.provider.query.SelectEventSubscriptionsByExecutionAndType;
import org.camunda.bpm.engine.cassandra.provider.query.SelectExecutionsByQueryCriteria;
import org.camunda.bpm.engine.cassandra.provider.query.SelectLatestProcessDefinitionByKeyQueryHandler;
import org.camunda.bpm.engine.cassandra.provider.query.SelectListQueryHandler;
import org.camunda.bpm.engine.cassandra.provider.query.SelectProcessInstanceByQueryCriteria;
import org.camunda.bpm.engine.cassandra.provider.query.SingleResultQueryHandler;
import org.camunda.bpm.engine.cassandra.provider.serializer.CassandraSerializer;
import org.camunda.bpm.engine.cassandra.provider.serializer.DeploymentEntitySerializer;
import org.camunda.bpm.engine.cassandra.provider.serializer.EventSubscriptionSerializer;
import org.camunda.bpm.engine.cassandra.provider.serializer.ExecutionEntitySerializer;
import org.camunda.bpm.engine.cassandra.provider.serializer.ProcessDefinitionSerializer;
import org.camunda.bpm.engine.cassandra.provider.serializer.ResourceEntitySerializer;
import org.camunda.bpm.engine.cassandra.provider.serializer.VariableEntitySerializer;
import org.camunda.bpm.engine.cassandra.provider.table.DeploymentTableHandler;
import org.camunda.bpm.engine.cassandra.provider.table.ProcessDefinitionTableHandler;
import org.camunda.bpm.engine.cassandra.provider.table.ProcessInstanceTableHandler;
import org.camunda.bpm.engine.cassandra.provider.table.ResourceTableHandler;
import org.camunda.bpm.engine.cassandra.provider.table.TableHandler;
import org.camunda.bpm.engine.cassandra.provider.type.EventSubscriptionTypeHandler;
import org.camunda.bpm.engine.cassandra.provider.type.ExecutionTypeHandler;
import org.camunda.bpm.engine.cassandra.provider.type.UDTypeHandler;
import org.camunda.bpm.engine.cassandra.provider.type.VariableTypeHandler;
import org.camunda.bpm.engine.impl.context.Context;
import org.camunda.bpm.engine.impl.db.AbstractPersistenceSession;
import org.camunda.bpm.engine.impl.db.DbEntity;
import org.camunda.bpm.engine.impl.db.entitymanager.operation.DbBulkOperation;
import org.camunda.bpm.engine.impl.db.entitymanager.operation.DbEntityOperation;
import org.camunda.bpm.engine.impl.persistence.entity.DeploymentEntity;
import org.camunda.bpm.engine.impl.persistence.entity.EventSubscriptionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.MessageEventSubscriptionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ResourceEntity;
import org.camunda.bpm.engine.impl.persistence.entity.VariableInstanceEntity;

import com.datastax.driver.core.BatchStatement;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.querybuilder.QueryBuilder;

public class CassandraPersistenceSession extends AbstractPersistenceSession {
  
  private final static Logger LOG = Logger.getLogger(CassandraPersistenceSession.class.getName());

  protected Session cassandraSession;
  
  protected StringBuilder batchBuilder = new StringBuilder();

  protected static List<TableHandler> tableHandlers = new ArrayList<TableHandler>();
  protected static Map<Class<?>, UDTypeHandler> udtHandlers = new HashMap<Class<?>, UDTypeHandler>();  
  protected static Map<Class<?>, CassandraSerializer<?>> serializers = new HashMap<Class<?>, CassandraSerializer<?>>();
  protected static Map<Class<?>, EntityOperationHandler<?>> operations = new HashMap<Class<?>, EntityOperationHandler<?>>();
  protected static Map<String, CompositeEntityLoader> compositeEntitiyLoader = new HashMap<String, CompositeEntityLoader>();
  protected static Map<String, SingleResultQueryHandler<?>> singleResultQueryHandlers = new HashMap<String, SingleResultQueryHandler<?>>();
  protected static Map<String, SelectListQueryHandler<?, ?>> listResultQueryHandlers = new HashMap<String, SelectListQueryHandler<?,?>>();
  protected static Map<String, BulkOperationHandler> bulkOperationHandlers = new HashMap<String, BulkOperationHandler>();
  
  protected BatchStatement varietyBatch = new BatchStatement();
  protected Map<String, LockedBatch<?>> lockedBatches = new HashMap<String, LockedBatch<?>>();
  protected Map<String, Map<String, LoadedCompositeEntity>> loadedEntityCache = new HashMap<String, Map<String, LoadedCompositeEntity>>();
  
  
  static {
    serializers.put(EventSubscriptionEntity.class, new EventSubscriptionSerializer());
    serializers.put(ExecutionEntity.class, new ExecutionEntitySerializer());
    serializers.put(ProcessDefinitionEntity.class, new ProcessDefinitionSerializer());
    serializers.put(ResourceEntity.class, new ResourceEntitySerializer());
    serializers.put(DeploymentEntity.class, new DeploymentEntitySerializer());
    serializers.put(VariableInstanceEntity.class, new VariableEntitySerializer());
        
    udtHandlers.put(ExecutionEntity.class, new ExecutionTypeHandler());
    udtHandlers.put(VariableInstanceEntity.class, new VariableTypeHandler());
    udtHandlers.put(EventSubscriptionEntity.class, new EventSubscriptionTypeHandler());
    
    tableHandlers.add(new ProcessDefinitionTableHandler());
    tableHandlers.add(new ResourceTableHandler());
    tableHandlers.add(new DeploymentTableHandler());
    tableHandlers.add(new ProcessInstanceTableHandler());
    
    operations.put(MessageEventSubscriptionEntity.class, new EventSubscriptionOperations());
    operations.put(ProcessDefinitionEntity.class, new ProcessDefinitionOperations());
    operations.put(ResourceEntity.class, new ResourceOperations());
    operations.put(DeploymentEntity.class, new DeploymentOperations());
    operations.put(ExecutionEntity.class, new ExecutionEntityOperations());
    operations.put(VariableInstanceEntity.class, new VariableEntityOperations());

    compositeEntitiyLoader.put(ProcessInstanceLoader.NAME, new ProcessInstanceLoader());
    
    singleResultQueryHandlers.put("selectLatestProcessDefinitionByKey", new SelectLatestProcessDefinitionByKeyQueryHandler());

    listResultQueryHandlers.put("selectExecutionsByQueryCriteria", new SelectExecutionsByQueryCriteria());
    listResultQueryHandlers.put("selectProcessInstanceByQueryCriteria", new SelectProcessInstanceByQueryCriteria());
    listResultQueryHandlers.put("selectEventSubscriptionsByExecutionAndType", new SelectEventSubscriptionsByExecutionAndType());
    
    bulkOperationHandlers.put("deleteDeployment", new BulkDeleteDeployment());
    bulkOperationHandlers.put("deleteResourcesByDeploymentId", new BulkDeleteResourcesByDeploymentId());
    bulkOperationHandlers.put("deleteProcessDefinitionsByDeploymentId", new BulkDeleteProcessDefinitionByDeploymentId());
    
  }
  
  protected boolean processInstanceVersionIncremented = false;
  
  public CassandraPersistenceSession(com.datastax.driver.core.Session session) {
    this.cassandraSession = session;
  }

  @SuppressWarnings({ "unchecked", "rawtypes" })
  public List<?> selectList(String statement, Object parameter) {
    SelectListQueryHandler handler = listResultQueryHandlers.get(statement);
    if(handler == null) {
      LOG.log(Level.WARNING, "unhandled select statement '"+statement+"'");
      return Collections.emptyList();
    }
    else {
      return handler.executeQuery(this, parameter);
    }
  }

  @SuppressWarnings("unchecked")
  public <T extends DbEntity> T selectById(Class<T> type, String id) {

    EntityOperationHandler<?> entityOperations = operations.get(type);
    if(entityOperations != null) {
      DbEntity loadedEntity = entityOperations.getEntityById(this, id);
      fireEntityLoaded(loadedEntity);
      return (T) loadedEntity;
    }
    
    LOG.warning("Unhandled select by id "+type +" "+id);
    return null;
  }

  public LoadedCompositeEntity selectCompositeById(String compositeName, String id) {
    CompositeEntityLoader loader = compositeEntitiyLoader.get(compositeName);
    if(loader == null) {
      throw new ProcessEngineException("There is no composite loader for the composite named "+ compositeName);
    }

    if(loadedEntityCache.get(compositeName)!=null &&
        loadedEntityCache.get(compositeName).get(id)!=null){
      return loadedEntityCache.get(compositeName).get(id);
    }

    LoadedCompositeEntity composite = loader.getEntityById(this, id);
    if(composite == null) {
      return null;
    }
    
    if(loadedEntityCache.get(compositeName)==null){
      loadedEntityCache.put(compositeName, new HashMap<String, LoadedCompositeEntity>());
    }
    loadedEntityCache.get(compositeName).put(id, composite);
    
    processLoadedComposite(composite);
    return composite;
  }
  
  protected void processLoadedComposite(LoadedCompositeEntity composite) {
    DbEntity mainEntity = composite.getPrimaryEntity();
    boolean isMainEntityEventFired = false;
    for (Map<String, ? extends DbEntity> entities : composite.getEmbeddedEntities().values()) {
      for (DbEntity entity : entities.values()) {
        fireEntityLoaded(entity);
        if(entity == mainEntity) {
          isMainEntityEventFired = true;
        }
      }      
    }
    if(!isMainEntityEventFired) {
      fireEntityLoaded(mainEntity);
    }
  }

  public Object selectOne(String statement, Object parameter) {
    
    SingleResultQueryHandler<?> queryHandler = singleResultQueryHandlers.get(statement);
    if(queryHandler != null) {
      DbEntity result = queryHandler.executeQuery(this, parameter);
      fireEntityLoaded(result);
      return result;
    }
    else if ("selectTableCount".equals(statement)) {
      @SuppressWarnings("unchecked")
      String tableName = ((Map<String, String>) parameter).get("tableName");
      return cassandraSession.execute(QueryBuilder.select().countAll().from(tableName)).one().getLong(0);
    }
    else {
      LOG.warning("unknown query "+statement);
      return null;
    }

  }

  public void lock(String statement, Object parameter) {
    
  }

  public void commit() {
    //apply all batches in the transaction with the same timestamp 
    long timestamp = ((CassandraProcessEngineConfiguration)Context.getProcessEngineConfiguration())
        .getCluster().getConfiguration().getPolicies().getTimestampGenerator().next();
    for (LockedBatch<?> batchWithLocking : lockedBatches.values()) {
      flushBatch(batchWithLocking.getBatch(), timestamp);
      flushBatch(batchWithLocking.getIndexBatch(), timestamp);
    }
    flushBatch(varietyBatch, timestamp);
  }

  private void flushBatch(BatchStatement batch, long timestamp) {
    if(batch==null){
      return;
    }
    batch.setDefaultTimestamp(timestamp);
    List<Row> rows = cassandraSession.execute(batch).all();
    for (Row row : rows) {
      if(!row.getBool("[applied]")) {
        throw new OptimisticLockingException("Process instance was updated by another transaction concurrently.");
      }
    }
  }

  public void rollback() {
    
  }

  public void dbSchemaCheckVersion() {
    
  }

  public void flush() {
    
  }

  public void close() {
    
  }
  
  @SuppressWarnings({ "rawtypes", "unchecked" })
  protected void insertEntity(DbEntityOperation operation) {
    EntityOperationHandler entityOperations = operations.get(operation.getEntityType());
    if(entityOperations == null) {
      LOG.log(Level.WARNING, "unhandled INSERT '"+operation+"'");
    }
    else {
      entityOperations.insert(this, operation.getEntity());
    }
    
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  protected void deleteEntity(DbEntityOperation operation) {
    EntityOperationHandler entityOperations = operations.get(operation.getEntityType());
    if(entityOperations == null) {
      LOG.log(Level.WARNING, "unhandled DELETE '"+operation+"'");
    }
    else {
      entityOperations.delete(this, operation.getEntity());
    }
  }

  protected void deleteBulk(DbBulkOperation operation) {
    BulkOperationHandler handler = bulkOperationHandlers.get(operation.getStatement());
    if(handler == null) {
      LOG.log(Level.WARNING, "unhandled BULK delete '"+operation+"'");
    }
    else {
      handler.perform(this, operation.getParameter(), varietyBatch);
    }
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  protected void updateEntity(DbEntityOperation operation) {
    EntityOperationHandler entityOperations = operations.get(operation.getEntityType());
    if(entityOperations == null) {
      LOG.log(Level.WARNING, "unhandled UPDATE '"+operation+"'");
    }
    else {
      entityOperations.update(this, operation.getEntity());
    }
  }

  protected void updateBulk(DbBulkOperation operation) {
    LOG.log(Level.WARNING, "unhandled BULK update '"+operation+"'");
  }
  
  
  /// Schema mngt ///////////////////////////////////7

  protected String getDbVersion() {
    return ProcessEngine.VERSION;
  }

  protected void dbSchemaCreateIdentity() {
    
  }

  protected void dbSchemaCreateHistory() {
    
  }

  
  protected void dbSchemaCreateEngine() {
    Collection<UDTypeHandler> typeHandlers_ = udtHandlers.values();
    for (UDTypeHandler typeHandler : typeHandlers_) {
      typeHandler.createType(cassandraSession);
    }
    
    for (TableHandler tableHandler : tableHandlers) {
      tableHandler.createTable(cassandraSession);
    }
  }

  protected void dbSchemaCreateCmmn() {
    
  }

  protected void dbSchemaCreateCmmnHistory() {
    
  }

  protected void dbSchemaDropIdentity() {
    
  }

  protected void dbSchemaDropHistory() {
    
  }

  protected void dbSchemaDropEngine() {
    for (TableHandler tableHandler : tableHandlers) {
      tableHandler.dropTable(cassandraSession);
    }
    
    Collection<UDTypeHandler> typeHandlers_ = udtHandlers.values();
    for (UDTypeHandler typeHandler : typeHandlers_) {
      typeHandler.dropType(cassandraSession);
    }
  }

  protected void dbSchemaDropCmmn() {
    
  }

  protected void dbSchemaDropCmmnHistory() {
    
  }

  public boolean isEngineTablePresent() {
    KeyspaceMetadata keyspaceMetaData = cassandraSession.getCluster()
      .getMetadata()
      .getKeyspace(cassandraSession.getLoggedKeyspace());
    
    return keyspaceMetaData.getTable(ProcessDefinitionTableHandler.TABLE_NAME) != null;
  }

  public boolean isHistoryTablePresent() {
    return false;
  }

  public boolean isIdentityTablePresent() {
    return false;
  }

  public boolean isCmmnTablePresent() {
    return false;
  }

  public boolean isCmmnHistoryTablePresent() {
    return false;
  }
  
  public UDTypeHandler getTypeHander(Class<?> entityType) {
    return udtHandlers.get(entityType);
  }
  
  @SuppressWarnings("unchecked")
  public <T extends DbEntity> CassandraSerializer<T> getSerializer(Class<T> type) {
    return (CassandraSerializer<T>) serializers.get(type);
  }
  
  public Session getSession() {
    return cassandraSession;
  }

  public List<String> getTableNamesPresent() {
    List<String> tableNames = new ArrayList<String>();
    for (TableHandler tableHandler : tableHandlers) {
      tableNames.addAll(tableHandler.getTableNames());
    }
    return tableNames;
  }
  
  public void addLockedBatch(String id, LockedBatch<?> batch) {
    lockedBatches.put(id, batch);
  }
  
  public void addStatement(Statement statement, String objectId) {
    if(statement==null){
      return;
    }
    LockedBatch<?> batch = lockedBatches.get(objectId);
    batch.addStatement(statement);
  }
  
  public void addIndexStatement(Statement statement, String objectId) {
    if(statement==null){
      return;
    }
    LockedBatch<?> batch = lockedBatches.get(objectId);
    batch.addIndexStatement(statement);
  }
  
  public void batchShouldNotLock(String objectId) {
    LockedBatch<?> batch = lockedBatches.get(objectId);
    batch.setShouldNotLock();
  }
  
  public void addStatement(Statement statement) {
    if(statement==null){
      return;
    }
    varietyBatch.add(statement);
  }
}
