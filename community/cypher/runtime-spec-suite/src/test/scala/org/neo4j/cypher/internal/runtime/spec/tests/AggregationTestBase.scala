/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.internal.runtime.spec.tests

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.Collections

import org.neo4j.cypher.internal.runtime.spec._
import org.neo4j.cypher.internal.v4_0.expressions.CountStar
import org.neo4j.cypher.internal.{CypherRuntime, RuntimeContext}
import org.neo4j.values.AnyValue
import org.neo4j.values.storable.{DoubleValue, DurationValue, StringValue, Values}
import org.neo4j.values.virtual.ListValue
import org.scalactic.{Equality, TolerantNumerics}

abstract class AggregationTestBase[CONTEXT <: RuntimeContext](
                                                               edition: Edition[CONTEXT],
                                                               runtime: CypherRuntime[CONTEXT]
                                                             ) extends RuntimeTestSuite[CONTEXT](edition, runtime) {

  test("should count(*)") {
    // given
    nodeGraph(10000, "Honey")

    // when
    val logicalQuery = new LogicalQueryBuilder()
      .produceResults("c")
      .aggregation(Map.empty, Map("c" -> CountStar()(pos)))
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("c").withRow(10000)
  }

  test("should count(*) on single grouping column") {
    // given
    nodePropertyGraph(10000, {
      case i: Int => Map("num" -> i, "name" -> s"bob${i % 10}")
    }, "Honey")

    // when
    val logicalQuery = new LogicalQueryBuilder()
      .produceResults("name", "c")
      .aggregation(Map("name" -> prop("x", "name")), Map("c" -> CountStar()(pos)))
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("name", "c").withRows(for (i <- 0 until 10) yield {
      Array(s"bob$i", 1000)
    })
  }

  test("should count(*) on single grouping column with nulls") {
    // given
    nodePropertyGraph(10000, {
      case i: Int if i % 2 == 0 => Map("num" -> i, "name" -> s"bob${i % 10}")
      case i: Int if i % 2 == 1 => Map("num" -> i)
    }, "Honey")

    // when
    val logicalQuery = new LogicalQueryBuilder()
      .produceResults("name", "c")
      .aggregation(Map("name" -> prop("x", "name")), Map("c" -> CountStar()(pos)))
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("name", "c").withRows((for (i <- 0 until 10 by 2) yield {
      Array(s"bob$i", 1000)
    }) :+ Array(null, 5000))
  }

  test("should count(*) on two grouping columns") {
    // given
    nodePropertyGraph(10000, {
      case i: Int => Map("num" -> i, "name" -> s"bob${i % 10}", "surname" -> s"bobbins${i / 100}")
    }, "Honey")

    // when
    val logicalQuery = new LogicalQueryBuilder()
      .produceResults("name", "surname", "c")
      .aggregation(Map("name" -> prop("x", "name"), "surname" -> prop("x", "surname")), Map("c" -> CountStar()(pos)))
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("name", "surname", "c").withRows(for (i <- 0 until 10; j <- 0 until 100) yield {
      Array(s"bob$i", s"bobbins$j", 10)
    })
  }

  test("should count(*) on three grouping columns") {
    // given
    nodePropertyGraph(10000, {
      case i: Int => Map("num" -> i, "name" -> s"bob${i % 10}", "surname" -> s"bobbins${i / 100}", "dead" -> i % 2)
    }, "Honey")

    // when
    val logicalQuery = new LogicalQueryBuilder()
      .produceResults("name", "surname", "dead", "c")
      .aggregation(Map("name" -> prop("x", "name"), "surname" -> prop("x", "surname"), "dead" -> prop("x", "dead")), Map("c" -> CountStar()(pos)))
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("name", "surname", "dead", "c").withRows(for (i <- 0 until 10; j <- 0 until 100) yield {
      Array(s"bob$i", s"bobbins$j", i % 2, 10)
    })
  }

  test("should count(n.prop)") {
    // given
    nodePropertyGraph(10000, {
      case i: Int if i % 2 == 0 => Map("num" -> i)
    }, "Honey")

    // when
    val logicalQuery = new LogicalQueryBuilder()
      .produceResults("c")
      .aggregation(Map.empty, Map("c" -> count(prop("x", "num"))))
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("c").withRow(5000)
  }

  test("should collect(n.prop)") {
    // given
    nodePropertyGraph(10000, {
      case i: Int if i % 2 == 0 => Map("num" -> i)
    }, "Honey")

    // when
    val logicalQuery = new LogicalQueryBuilder()
      .produceResults("c")
      .aggregation(Map.empty, Map("c" -> collect(prop("x", "num"))))
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("c").withResultMatching {
      // The order of the collected elements in the list can differ
      case Seq(Array(d:ListValue)) if d.asArray().toSeq.sorted(ANY_VALUE_ORDERING) == (0 until 10000 by 2).map(Values.intValue) =>
    }
  }

  test("should sum(n.prop)") {
    // given
    nodePropertyGraph(10000, {
      case i: Int if i % 2 == 0 => Map("num" -> i)
    }, "Honey")

    // when
    val logicalQuery = new LogicalQueryBuilder()
      .produceResults("c")
      .aggregation(Map.empty, Map("c" -> sum(prop("x", "num"))))
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("c").withRow((0 until 10000 by 2).sum)
  }

  test("should min(n.prop)") {
    // given
    nodePropertyGraph(10000, {
      case i: Int if i % 2 == 0 => Map("num" -> i)
    }, "Honey")

    // when
    val logicalQuery = new LogicalQueryBuilder()
      .produceResults("c")
      .aggregation(Map.empty, Map("c" -> min(prop("x", "num"))))
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("c").withRow((0 until 10000 by 2).min)
  }

  test("should max(n.prop)") {
    // given
    nodePropertyGraph(10000, {
      case i: Int if i % 2 == 0 => Map("num" -> i)
    }, "Honey")

    // when
    val logicalQuery = new LogicalQueryBuilder()
      .produceResults("c")
      .aggregation(Map.empty, Map("c" -> max(prop("x", "num"))))
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("c").withRow((0 until 10000 by 2).max)
  }

  test("should avg(n.prop)") {
    // given
    nodePropertyGraph(10000, {
      case i: Int if i % 2 == 0 => Map("num" -> (i + 1))
    }, "Honey")

    // when
    val logicalQuery = new LogicalQueryBuilder()
      .produceResults("c")
      .aggregation(Map.empty, Map("c" -> avg(prop("x", "num"))))
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    implicit val doubleEquality: Equality[Double] = TolerantNumerics.tolerantDoubleEquality(0.0001)
    runtimeResult should beColumns("c").withResultMatching {
      case Seq(Array(d:DoubleValue)) if d.value() === 5000.0 =>
    }
  }

  test("should avg(n.prop) with grouping") {
    // given
    nodePropertyGraph(10000, {
      case i: Int => Map("num" -> (i + 1), "name" -> s"bob${i % 10}")
    }, "Honey")

    // when
    val logicalQuery = new LogicalQueryBuilder()
      .produceResults("name", "c")
      .aggregation(Map("name" -> prop("x", "name")), Map("c" -> avg(prop("x", "num"))))
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    implicit val doubleEquality: Equality[Double] = TolerantNumerics.tolerantDoubleEquality(0.0001)
    val expectedBobCounts = Map(
      "bob0" -> 4996.0,
      "bob1" -> 4997.0,
      "bob2" -> 4998.0,
      "bob3" -> 4999.0,
      "bob4" -> 5000.0,
      "bob5" -> 5001.0,
      "bob6" -> 5002.0,
      "bob7" -> 5003.0,
      "bob8" -> 5004.0,
      "bob9" -> 5005.0
    )
    runtimeResult should beColumns("name", "c").withResultMatching {
      case rows:Seq[Array[AnyValue]] if rows.size == expectedBobCounts.size && rows.forall {
        case Array(s:StringValue, d:DoubleValue) => expectedBobCounts(s.stringValue()) === d.value()
      } =>
    }
  }

  test("should avg(n.prop) with durations") {
    // given
    nodePropertyGraph(10000, {
      case i: Int if i % 2 == 0 => Map("num" -> Duration.of(i + 1, ChronoUnit.NANOS))
    }, "Honey")

    // when
    val logicalQuery = new LogicalQueryBuilder()
      .produceResults("c")
      .aggregation(Map.empty, Map("c" -> avg(prop("x", "num"))))
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    implicit val doubleEquality: Equality[Double] = TolerantNumerics.tolerantDoubleEquality(0.0001)
    runtimeResult should beColumns("c").withResultMatching {
      case Seq(Array(d:DurationValue)) if d.get(ChronoUnit.NANOS) === 5000.0 =>
    }
  }

  test("should not get a numerical overflow in avg(n.prop)") {
    // given
    nodePropertyGraph(10000, {
      case i: Int if i % 1000 == 0 => Map("num" -> (Double.MaxValue - 2.0))
      case i: Int if i % 1000 == 1 => Map("num" -> (Double.MaxValue - 1.0))
    }, "Honey")

    // when
    val logicalQuery = new LogicalQueryBuilder()
      .produceResults("c")
      .aggregation(Map.empty, Map("c" -> avg(prop("x", "num"))))
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    implicit val doubleEquality: Equality[Double] = TolerantNumerics.tolerantDoubleEquality(0.0001)
    runtimeResult should beColumns("c").withResultMatching {
      case Seq(Array(d:DoubleValue)) if d.value() === Double.MaxValue - 1.5 =>
    }
  }

  test("should return zero for empty input") {
    // given nothing

    // when
    val logicalQuery = new LogicalQueryBuilder()
      .produceResults("countStar", "count", "avg", "collect", "max", "min", "sum")
      .aggregation(Map.empty, Map(
        "countStar" -> CountStar()(pos),
        "count" -> count(prop("x", "num")),
        "avg" -> avg(prop("x", "num")),
        "collect" -> collect(prop("x", "num")),
        "max" -> max(prop("x", "num")),
        "min" -> min(prop("x", "num")),
        "sum" -> sum(prop("x", "num"))
      ))
      .allNodeScan("x")
      .build()

    val runtimeResult = execute(logicalQuery, runtime)

    // then
    runtimeResult should beColumns("countStar", "count", "avg", "collect", "max", "min", "sum").withRow(0, 0, null, Collections.emptyList(),  null, null, 0)
  }
}