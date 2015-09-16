/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camunda.bpm.engine.cassandra.provider.query;

import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.engine.cassandra.provider.CassandraPersistenceSession;
import org.camunda.bpm.engine.cassandra.provider.indexes.IndexHandler;
import org.camunda.bpm.engine.cassandra.provider.indexes.JobsByExecutionIdIndex;
import org.camunda.bpm.engine.cassandra.provider.operation.JobOperations;
import org.camunda.bpm.engine.impl.db.ListQueryParameterObject;
import org.camunda.bpm.engine.impl.persistence.entity.JobEntity;

/**
 * @author Natalia Levine
 *
 * @created 15/09/2015
 */
public class SelectJobsByExecutionId implements SelectListQueryHandler<JobEntity, ListQueryParameterObject> {
  public List<JobEntity> executeQuery(CassandraPersistenceSession session, ListQueryParameterObject query) {
    String executionId = (String) query.getParameter(); 
    
    IndexHandler<JobEntity> index = JobOperations.getIndexHandler(JobsByExecutionIdIndex.class);
    List<String> keys = index.getValues(null,session, executionId);
    
    List<JobEntity> result = new ArrayList<JobEntity>();
    for(String key : keys){
      JobEntity job = session.selectById(JobEntity.class, key);
      if(job!=null){
        result.add(job);
      }
    }
    return result;
  }
}
