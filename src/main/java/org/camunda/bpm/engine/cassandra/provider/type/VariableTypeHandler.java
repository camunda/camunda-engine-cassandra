/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camunda.bpm.engine.cassandra.provider.type;


public class VariableTypeHandler extends AbstractTypeHandler {

  public final static String TYPE_NAME = "variable";

  public final static String CREATE_TYPE_STATEMENT = "CREATE TYPE IF NOT EXISTS " + TYPE_NAME + " ("
    + "id text,"
    + "type text,"
    + "name text,"
    + "execution_id text,"
    + "proc_inst_id text,"
    + "case_execution_id text,"
    + "case_inst_id text,"
    + "task_id text,"
    + "bytearray_id text,"
    + "double double,"
    + "long bigint,"
    + "text text,"
    + "text2 text,"
    + "sequence_counter bigint,"
    + "is_concurrent_local boolean"
    + ");";

  public final static String DROP_TYPE_STATEMENT = "DROP TYPE IF EXISTS " + TYPE_NAME + ";";

  public String getTypeName() {
    return TYPE_NAME;
  }

  protected String getCreateStatement() {
    return CREATE_TYPE_STATEMENT;
  }

  protected String getDropStatement() {
    return DROP_TYPE_STATEMENT;
  }


}
