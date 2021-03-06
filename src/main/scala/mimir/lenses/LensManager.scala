package mimir.lenses;

import java.sql._

import mimir.Database
import mimir.algebra._
import mimir.ctables._
import mimir.sql._
import mimir.models._
import mimir.util.JDBCUtils

class LensManager(db: Database) {

  val lensTypes = Map[String,((Database,String,Operator,Seq[Expression]) => 
                              (Operator,TraversableOnce[Model]))](
    "MISSING_VALUE"     -> MissingValueLens.create _,
    "SCHEMA_MATCHING"   -> SchemaMatchingLens.create _,
    "TYPE_INFERENCE"    -> TypeInferenceLens.create _
  )

  def init(): Unit =
  {
    // no-op for now.
  }

  def createLens(
    t: String, 
    name: String, 
    query: Operator, 
    args: List[Expression]
  ): Unit =
  {
    val saneName = name.toUpperCase
    val constructor =
      lensTypes.get(t.toUpperCase) match {
        case Some(impl) => impl
        case None => throw new SQLException("Invalid Lens Type '"+t.toUpperCase+"'")
      }

    // Construct the appropriate lens
    val (view, models) = constructor(db, saneName, query, args)

    // Create a lens query
    db.views.createView(saneName, view)

    // Persist the associated models
    for(model <- models){
      db.models.persist(model, s"LENS:$saneName")
    }

    // Populate the best-guess cache
    db.bestGuessCache.buildCache(view)
  }

  def dropLens(name: String): Unit =
  {
    db.views.dropView(name)
    db.models.dropOwner(s"LENS:$name")
  }

}

