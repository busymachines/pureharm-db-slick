/*
 * Copyright 2019 BusyMachines
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package busymachines.pureharm.dbslick.internals

import busymachines.pureharm.json._
import busymachines.pureharm.json.implicits._

/** @author Lorand Szakacs, https://github.com/lorandszakacs
  * @since 16 Jun 2019
  */
trait SlickPostgresCirceSupportAPI {

  import scala.reflect.ClassTag

  protected val enclosingPostgresProfile: slick.jdbc.PostgresProfile

  import enclosingPostgresProfile._

  /** Override if by any chance you need to use legacy "json" type
    * instead of new and improved "jsonb".
    */
  protected def jsonPostgresType: PostgresqlJSON = PostgresqlJSON.jsonb

  final private object CirceJsonJDBCDriverType extends DriverJdbcType[Json] {
    import java.sql.{PreparedStatement, ResultSet}

    import slick.ast.FieldSymbol

    override val sqlType: Int = java.sql.Types.OTHER

    override def sqlTypeName(sym: Option[FieldSymbol]): String = jsonPostgresType.typeName

    override def setValue(v: Json, p: PreparedStatement, idx: Int): Unit =
      p.setObject(idx, v.noSpaces, sqlType)

    override def getValue(r: ResultSet, idx: Int): Json = {
      val strValue = r.getString(idx)
      if (r.wasNull) Json.Null else busymachines.pureharm.internals.json.JsonParsing.parseString(strValue).toTry.get
    }

    override def updateValue(v: Json, r: ResultSet, idx: Int): Unit =
      r.updateObject(idx, v.noSpaces, sqlType)
  }

  implicit lazy val circeJsonbType: DriverJdbcType[Json] = CirceJsonJDBCDriverType

  def createJsonbColumnType[T: ClassTag](implicit e: Encoder[T], d: Decoder[T]): ColumnType[T] =
    MappedColumnType.base[T, Json](
      tmap   = t => e(t),
      tcomap = json => json.decodeAs[T].toTry.get,
    )

}

sealed trait PostgresqlJSON extends Product with Serializable {
  val typeName: String
}

object PostgresqlJSON {
  case object jsonb extends PostgresqlJSON { override val typeName: String = "jsonb" }
  case object json  extends PostgresqlJSON { override val typeName: String = "json" }
}
