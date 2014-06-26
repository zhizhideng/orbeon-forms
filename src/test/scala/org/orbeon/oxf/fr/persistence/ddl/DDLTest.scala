/**
 * Copyright (C) 2013 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.fr.persistence.ddl

import java.sql.Connection
import org.junit.Test
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.scalatest.junit.AssertionsForJUnit
import org.orbeon.oxf.fr.persistence.db._
import org.orbeon.oxf.util.ScalaUtils._
import org.orbeon.oxf.util.{Logging, LoggerFactory, IndentedLogger}
import org.apache.log4j.Level

/**
 * Test the DDL we provide to create and update databases.
 */
class DDLTest extends ResourceManagerTestBase with AssertionsForJUnit with Logging {

    private implicit val Logger = new IndentedLogger(LoggerFactory.createLogger(classOf[DDLTest]), true, "")

    private def withNewDatabase[T](provider: Provider)(block: Connection ⇒ T): T = {
        val schema = s"orbeon_${System.getenv("TRAVIS_BUILD_NUMBER")}_ddl"
        val createUserAndDatabase = provider match {
            case Oracle    ⇒ Seq(s"CREATE USER $schema IDENTIFIED BY ${System.getenv("RDS_PASSWORD")}",
                                 s"ALTER  USER $schema QUOTA UNLIMITED ON users",
                                 s"GRANT  CREATE SESSION TO $schema",
                                 s"GRANT  CREATE TABLE   TO $schema",
                                 s"GRANT  CREATE TRIGGER TO $schema")
            case MySQL     ⇒ Seq(s"CREATE DATABASE $schema")
            case SQLServer ⇒ Seq(s"CREATE DATABASE $schema")
            case _         ⇒ ???
        }
        val dropUserAndDatabase = provider match {
            case Oracle    ⇒ Seq(s"DROP USER $schema CASCADE")
            case MySQL     ⇒ Seq(s"DROP DATABASE $schema")
            case SQLServer ⇒ Seq(s"DROP DATABASE $schema")
            case _         ⇒ ???
        }
        try {
            Connect.asRoot(provider)(createUserAndDatabase foreach _.createStatement.executeUpdate)
            Connect.asDDL(provider)(block)
        } finally {
            Connect.asRoot(provider)(dropUserAndDatabase foreach _.createStatement.executeUpdate)
        }
    }

    case class TableMeta(tableName: String, colsMeta: Seq[ColMeta])
    case class ColMeta(colName: String, meta: Set[ColKeyVal])
    case class ColKeyVal(key: String, value: AnyRef)

    /**
     * Runs the SQL, and returns the information about the tables as defined in the database. The form in which this
     * information is returned varies depending on the database, hence the Any return type.
     */
    private def sqlToTableInfo(provider: Provider, sql: Seq[String]): Set[TableMeta] = {
        withNewDatabase(provider) { connection ⇒
            val statement = connection.createStatement
            SQL.executeStatements(provider, statement, sql)
            val query = provider match {
                // On Oracle, column order is "non-relevant", so we order by column name instead of position
                case Oracle ⇒ """  SELECT *
                                 |    FROM all_tab_cols
                                 |   WHERE table_name = ?
                                 |         AND NOT column_name LIKE 'SYS%'
                                 |ORDER BY column_name"""
                case _      ⇒ """   SELECT *
                                 |    FROM information_schema.columns
                                 |   WHERE table_name = ?
                                 |ORDER BY ordinal_position"""
            }
            Connect.getTableNames(provider, connection).map { tableName ⇒
                val tableInfoResultSet = {
                    val ps = connection.prepareStatement(query.stripMargin)
                    ps.setString(1, tableName)
                    ps.executeQuery()
                }
                def tableInfo(): ColMeta = {
                    val colName = tableInfoResultSet.getString("column_name")
                    val interestingKeys = Set(if (provider == Oracle) "nullable" else "is_nullable", "data_type")
                    val colKeyVals = for (metaKey ← interestingKeys) yield
                        ColKeyVal(metaKey, tableInfoResultSet.getObject(metaKey))
                    ColMeta(colName, colKeyVals)
                }
                val colsMeta = Iterator.iterateWhile(tableInfoResultSet.next(), tableInfo()).toList
                assert(colsMeta.length > 0)
                TableMeta(tableName, colsMeta)
            }.toSet
        }
    }

    private def assertSameTable(provider: Provider, from: String, to: String): Unit = {
        val name = provider.name
        withDebug("comparing upgrade to straight", List("provider" → name, "from" → from, "to" → to)) {
            val upgrade  = sqlToTableInfo(provider, SQL.read(s"$name-$from.sql") ++ SQL.read(s"$name-$from-to-$to.sql"))
            val straight = sqlToTableInfo(provider, SQL.read(s"$name-$to.sql"))
            assert(upgrade === straight, s"$name from $from to $to")
        }
    }

    @Test def createAndUpgradeTest(): Unit = {
        Provider.all.foreach {
            case Oracle ⇒
                assertSameTable(Oracle, "4_3", "4_4")
                assertSameTable(Oracle, "4_4", "4_5")
                assertSameTable(Oracle, "4_5", "4_6")
            case MySQL ⇒
                assertSameTable(MySQL,  "4_3", "4_4")
                assertSameTable(MySQL,  "4_4", "4_5")
                assertSameTable(MySQL,  "4_5", "4_6")
            case SQLServer ⇒
                // No assertions for now (we don't have upgrades yet), but at least test that DDL runs
                sqlToTableInfo(SQLServer, SQL.read("sqlserver-4_6.sql"))
            case DB2 ⇒
                assertSameTable(MySQL,  "4_3", "4_4")
                assertSameTable(MySQL,  "4_4", "4_6")
        }
    }
}
