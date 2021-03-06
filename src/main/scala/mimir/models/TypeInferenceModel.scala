package mimir.models

import scala.util.Random
import com.typesafe.scalalogging.slf4j.Logger

import mimir.Database
import mimir.algebra._
import mimir.util._


object TypeInferenceModel
{
  val logger = Logger(org.slf4j.LoggerFactory.getLogger("mimir.models.TypeInferenceModel"))

  def priority: Type => Int =
  {
    case TUser(_)     => 20
    case TInt()       => 10
    case TBool()      => 10
    case TDate()      => 10
    case TTimeStamp() => 10
    case TType()      => 10
    case TFloat()     => 5
    case TString()    => 0
    case TRowId()     => -5
    case TAny()       => -10
  }

  def detectType(v: String): Iterable[Type] = {
    Type.tests.flatMap({ case (t, regexp) =>
      regexp.findFirstMatchIn(v).map(_ => t)
    })++
    TypeRegistry.matchers.flatMap({ case (regexp, name) =>
      regexp.findFirstMatchIn(v).map(_ => TUser(name))
    })

  }
}

@SerialVersionUID(1000L)
class TypeInferenceModel(name: String, columns: IndexedSeq[String], defaultFrac: Double)
  extends Model(name)
  with DataIndependentFeedback
  with NoArgModel
  with FiniteDiscreteDomain
{
  var totalVotes = 
    { val v = new scala.collection.mutable.ArraySeq[Double](columns.length)
      for(col <- (0 until columns.size)){ v.update(col, 0.0) }
      v
    }
  val votes = 
    columns.map(
      _ => scala.collection.mutable.Map[Type, Double]()
    )

  def train(db: Database, query: Operator) =
  {
    TimeUtils.monitor(s"Train $name",
      () => {
        db.query(
          Project(
            columns.map( c => ProjectArg(c, Var(c)) ),
            query
          )
        ).
        foreachRow( row => learn(row.currentRow) )
      },
      TypeInferenceModel.logger.info(_)
    )
  }

  final def learn(row: Seq[PrimitiveValue]):Unit =
  {
    row.zipWithIndex.foreach({ case (v, idx) => learn(idx, v) })
  }

  final def learn(idx: Int, p: PrimitiveValue):Unit =
  {
    p match { 
      case null            => ()
      case NullPrimitive() => ()
      case _               => learn(idx, p.asString)
    }
  }

  final def learn(idx: Int, v: String):Unit =
  {
    totalVotes(idx) += 1.0
    val candidates = TypeInferenceModel.detectType(v)
    TypeInferenceModel.logger.debug(s"Guesses for '$v': $candidates")
    val votesForCurrentIdx = votes(idx)
    for(t <- candidates){
      votesForCurrentIdx(t) = votesForCurrentIdx.getOrElse(t, 0.0) + 1.0 
    }
  }

  private final def voteList(idx: Int) = 
    (TString(), defaultFrac * totalVotes(idx)) :: votes(idx).toList

  private final def rankFn(x:(Type, Double)) =
    (x._2, TypeInferenceModel.priority(x._1) )

  def varType(idx: Int, argTypes: Seq[Type]) = TType()
  def sample(idx: Int, randomness: Random, args: Seq[PrimitiveValue]): PrimitiveValue = 
    TypePrimitive(
      RandUtils.pickFromWeightedList(randomness, voteList(idx))
    )

  def bestGuess(idx: Int, args: Seq[PrimitiveValue]): PrimitiveValue = 
  {
    choices.get(idx) match {
      case None => {
        val guess = voteList(idx).maxBy( rankFn _ )._1
        TypeInferenceModel.logger.debug(s"Votes($idx): ${voteList(idx)} -> $guess")
        TypePrimitive(guess)
      }
      case Some(s) => s
    }
  }

  def validateChoice(idx: Int, v: PrimitiveValue): Boolean =
    v.isInstanceOf[TypePrimitive]


  def reason(idx: Int, args: Seq[PrimitiveValue]): String = {
    choices.get(idx) match {
      case None => {
        val (guess, guessVotes) = voteList(idx).maxBy( rankFn _ )
        val defaultPct = (defaultFrac * 100).toInt
        val guessPct = ((guessVotes / totalVotes(idx))*100).toInt
        val typeStr = Type.toString(guess).toUpperCase
        val reason =
          guess match {
            case TString() =>
              s"not more than $defaultPct% of the data fit anything else"
            case _ if (guessPct >= 100) =>
              "all of the data fit"
            case _ => 
              s"around $guessPct% of the data fit"
          }
        s"I guessed that ${columns(idx)} was of type $typeStr because $reason"
      }
      case Some(TypePrimitive(t)) =>
        val typeStr = Type.toString(t).toUpperCase
        s"You told me that ${columns(idx)} was of type $typeStr"
      case Some(c) =>
        throw new ModelException(s"Invalid choice $c for $name")
    }
  }

  def getDomain(idx: Int, args: Seq[PrimitiveValue]): Seq[(PrimitiveValue,Double)] =
    votes(idx).toList.map( x => (TypePrimitive(x._1), x._2))

}