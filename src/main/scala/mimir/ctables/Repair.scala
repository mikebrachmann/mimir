package mimir.ctables

import mimir.algebra._
import mimir.util._

sealed trait Repair
{
  def toJSON: String
}

case class RepairFromList(choices: Seq[(PrimitiveValue, Double)])
  extends Repair
{
  def toJSON =
    JSONBuilder.dict(Map(
      "selector" -> JSONBuilder.string("list"),
      "values"   -> JSONBuilder.list( choices.map({ case (choice, weight) =>
          JSONBuilder.dict(Map(
            "choice" -> JSONBuilder.string(choice.toString),
            "weight" -> JSONBuilder.double(weight)
          ))
        }))
    ))
}

case class RepairByType(t: Type)
  extends Repair
{
  def toJSON =
    JSONBuilder.dict(Map(
      "selector" -> JSONBuilder.string("by_type"),
      "type"     -> JSONBuilder.prim(TypePrimitive(t))
    ))
}