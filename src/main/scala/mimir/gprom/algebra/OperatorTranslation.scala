package mimir.gprom.algebra

import org.gprom.jdbc.jna._
import mimir.algebra._

object OperatorTranslation {
  
  def gpromStructureToMimirOperator(depth : Int, topOperator: Operator, gpromStruct: GProMStructure, gpromParentStruct: GProMStructure ) : Operator = {
    var topOp = topOperator
    gpromStruct match {
      case aggregationOperator : GProMAggregationOperator => { 
        null
        }
      case attributeDef : GProMAttributeDef => { 
        null 
        }
      case constRelOperator : GProMConstRelOperator => { 
        null 
        }
      case constant : GProMConstant => { 
        null 
        }
      case duplicateRemoval : GProMDuplicateRemoval => { 
        null 
        }
      case joinOperator : GProMJoinOperator => { 
        null 
        }
      case list:GProMList => {
        val listHead = list.head
        gpromStructureToMimirOperator(depth, topOperator, listHead, gpromParentStruct)
      }
      case listCell : GProMListCell => { 
        val listCellDataGPStructure = new GProMNode(listCell.data.ptr_value)
        val cnvNode = GProMWrapper.inst.castGProMNode(listCellDataGPStructure);
        if(topOp == null){
          topOp = gpromStructureToMimirOperator(depth, null, cnvNode, gpromParentStruct)
          if(listCell.next != null)
            gpromStructureToMimirOperator(depth, topOp, listCell.next, gpromParentStruct)
          else
            topOp
        }
        else{
          cnvNode match { 
            case projectionOperator : GProMProjectionOperator => { 
              null 
              }
            case provenanceComputation : GProMProvenanceComputation => { 
              null 
              }
            case provenanceTransactionInfo : GProMProvenanceTransactionInfo => { 
              null 
              }
            case queryOperator : GProMQueryOperator => { 
              null 
              }
            case schema : GProMSchema => { 
              null 
              }
            case selectionOperator : GProMSelectionOperator => { 
              null 
              }
          }
        }
      }
      case nestingOperator : GProMNestingOperator => { 
        null 
        }
      case node : GProMNode => { 
        null 
        }
      case orderOperator : GProMOrderOperator => { 
        null 
        }
      case projectionOperator : GProMProjectionOperator => {
        val sourceChild = gpromStructureToMimirOperator(depth+1, topOp, projectionOperator.op, projectionOperator)
        val columns = getProjectionColumnsFromGProMProjectionOperator(projectionOperator)
        val provAttrs = gpromIntPointerListToScalaList(projectionOperator.op.provAttrs)
        if(provAttrs.length > 0 && depth == 0){
          val invisibleSchema = getSchemaFromGProMQueryOperator(/*tableAccessOperator.tableName*/"", projectionOperator.op).zipWithIndex.map{ case (attr, index) => {
                if(provAttrs.contains(index))
                  Some(attr)
                else
                  None
              }
            }.flatten
          new Recover(new Project(columns, sourceChild), invisibleSchema)
        }
        else
          new Project(columns, sourceChild)
      }
      case provenanceComputation : GProMProvenanceComputation => { 
        null 
        }
      case provenanceTransactionInfo : GProMProvenanceTransactionInfo => { 
        null 
        }
      case queryOperator : GProMQueryOperator => { 
        queryOperator.`type` match {
          case GProM_JNA.GProMNodeTag.GProM_T_ProjectionOperator => gpromStructureToMimirOperator(depth+1, topOp,queryOperator.inputs, queryOperator)
          case _ => null
        }
      }
      case schema : GProMSchema => { 
        null 
        }
      case selectionOperator : GProMSelectionOperator => { 
        null 
        }
      case setOperator : GProMSetOperator => { 
        null 
        }
      case tableAccessOperator : GProMTableAccessOperator => { 
        gpromParentStruct match {
          case queryOperator : GProMQueryOperator => {
            val provAttrs = gpromIntPointerListToScalaList(queryOperator.provAttrs)
            val invisibleSchema = getSchemaFromGProMQueryOperator(/*tableAccessOperator.tableName*/"", queryOperator).zipWithIndex.map{ case (attr, index) => {
                if(provAttrs.contains(index))
                  Some(attr)
                else
                  None
              }
            }.flatten
            val tableSchema = getSchemaFromGProMQueryOperator(tableAccessOperator.tableName, tableAccessOperator.op)
            val tableMeta = Seq[(String,Expression,Type)]() //tableSchema.map(tup => (tup._1,null,tup._2))
            new Annotate(new Table(tableAccessOperator.tableName, tableSchema, tableMeta), invisibleSchema)
          }
          case _ => {
            val tableSchema = getSchemaFromGProMQueryOperator(tableAccessOperator.tableName, tableAccessOperator.op)
            val tableMeta = Seq[(String,Expression,Type)]() //tableSchema.map(tup => (tup._1,null,tup._2))
            new Table(tableAccessOperator.tableName, tableSchema, tableMeta)
          }
        }
        
      }
      case updateOperator : GProMUpdateOperator => { 
        null 
        }
      case _ => { 
        null 
        }
    }
    
  }
  
  def gpromListToScalaList(list: GProMList) : List[GProMStructure] = {
    var listCell = list.head
    (for(i <- 1 to list.length ) yield {
      val projInput = GProMWrapper.inst.castGProMNode(new GProMNode(listCell.data.ptr_value))
      listCell = listCell.next
      projInput
    }).toList
  }
  
  def gpromIntPointerListToScalaList(list: GProMList) : List[Int] = {
    var listCell = list.head
    (for(i <- 1 to list.length ) yield {
      val projInput = listCell.data.int_value
      listCell = listCell.next
      projInput
    }).toList
  }
  
  def getProjectionColumnsFromGProMProjectionOperator(gpromProjOp : GProMProjectionOperator) : Seq[ProjectArg] = {
    val projExprs = gpromProjOp.projExprs;
    val projOpInputs =  gpromProjOp.op.inputs
    var listCell = projOpInputs.head
    var projTableName = ""
    for(i <- 1 to projOpInputs.length ){
      val projInput = GProMWrapper.inst.castGProMNode(new GProMNode(listCell.data.ptr_value))
      projTableName = projInput match {
        case tableAccessOperator : GProMTableAccessOperator => {
          tableAccessOperator.tableName + "_"
        }
        case _ => {
          ""
        }
      }
      listCell = listCell.next
    }
    
    val provAttrs = gpromIntPointerListToScalaList(gpromProjOp.op.provAttrs)
    listCell = projExprs.head
    val columns : Seq[ProjectArg] =
    (for(i <- 1 to projExprs.length ) yield {
      val projExpr = GProMWrapper.inst.castGProMNode(new GProMNode(listCell.data.ptr_value))
      val projArg = projExpr match {
        case attrRef : GProMAttributeReference => {
          if(!provAttrs.contains(i-1))
            Some(new ProjectArg(attrRef.name, new Var(projTableName + attrRef.name)))
          else
            None
        }
        case _ => None
      }
      listCell = listCell.next
      projArg
    }).flatten
    columns
  }
  
  def getSchemaFromGProMQueryOperator(tableName : String, gpromQueryOp : GProMQueryOperator) : Seq[(String, Type)] = {
    val attrDefList = gpromQueryOp.schema.attrDefs;
    val tableNameStr = tableName match { 
        case "" => "" 
        case _ => tableName + "_" 
    }
    var listCell = attrDefList.head
    val columns : Seq[(String, Type)] =
    for(i <- 1 to attrDefList.length ) yield {
      val attrDef = new GProMAttributeDef(listCell.data.ptr_value)
      val attrType = attrDef.dataType match {
        case GProM_JNA.GProMDataType.GProM_DT_VARCHAR2 => new TString()
        case GProM_JNA.GProMDataType.GProM_DT_BOOL => new TBool()
        case GProM_JNA.GProMDataType.GProM_DT_FLOAT => new TFloat()
        case GProM_JNA.GProMDataType.GProM_DT_INT => new TInt()
        case GProM_JNA.GProMDataType.GProM_DT_LONG => new TInt()
        case GProM_JNA.GProMDataType.GProM_DT_STRING => new TString()
        case _ => new TAny()
      }
      val schItem = ( tableNameStr +attrDef.attrName, attrType)
      listCell = listCell.next
      schItem
    }
    columns
  }
  
  
  def mimirOperatorToGProMStructure(mimirOperator :  Operator) : GProMStructure = {
    /*mimirOperator match {
			case Project(cols, src) => {
			  null
			 }
			case Recover(psel) => {
			  null
			}
			case Select(cond, src) => {
			  null
			}
			case Aggregate(groupBy, agggregates, source) => {
			  null
			}

			case Join(lhs, rhs) => { 
			  null
			}
			case LeftOuterJoin(lhs, rhs, condition) => {
			  null
			}
			case Table(name, sch, meta) => {
			  null
			}
		}*/
    mimirOperatorToGProMList(null, mimirOperator)
  }
  
  def mimirOperatorToGProMList(inList : GProMList.ByReference, mimirOperator :  Operator) : GProMList.ByReference = {
    var list  = inList
    if(list == null){
      list = new GProMList.ByReference()
      list.`type` = GProM_JNA.GProMNodeTag.GProM_T_List
      list.length = 0;
    }
    mimirOperator match {
			case Project(cols, src) => {
			   val tableName = src match {
			     case Table(name, sch, meta) => {
			       name
			     }
			     case _ => ""
			   }
			    val toQoScm = translateMimirSchemaToGProMSchema(tableName, mimirOperator.schema)
			    val gqo = new GProMQueryOperator(GProM_JNA.GProMNodeTag.GProM_T_ProjectionOperator,  mimirOperatorToGProMList(null, src), toQoScm, null, null, null)  
			   list.head = createGProMListCell(new GProMProjectionOperator(gqo, translateMimirProjArgsToGProMList(mimirOperator.schema.map(a => a._1 -> a._2).toMap, cols))) 
			   list.length += 1;
			   list
			 }
			case ProvenanceOf(psel) => {
			  list
			}
			case Select(cond, src) => {
			  list
			}
			case Aggregate(groupBy, agggregates, source) => {
			  list
			}

			case Join(lhs, rhs) => { 
			  list
			}
			case LeftOuterJoin(lhs, rhs, condition) => {
			  list
			}
			case Table(name, sch, meta) => {
			  val toQoScm = translateMimirSchemaToGProMSchema(name, sch)
			  val gqo = new GProMQueryOperator(GProM_JNA.GProMNodeTag.GProM_T_TableAccessOperator, new GProMList.ByReference(), toQoScm, null, null, null)
			  val gpromTable = new GProMTableAccessOperator(gqo,name,null)
			  list.head = createGProMListCell(gpromTable)
			  list.length += 1
			  list
			}

		}
  }
  
  def createGProMListCell(gpromDataNode:GProMStructure) : GProMListCell.ByReference = {
    val listCell = new GProMListCell.ByReference()
    gpromDataNode.write()
    val dataUnion = new GProMListCell.data_union(gpromDataNode.getPointer())
    dataUnion.write()
    listCell.data = dataUnion
    listCell.next = null
    listCell;
  }
  
  def translateMimirSchemaToGProMSchema(tableName : String, schema : Seq[(String, Type)]) : GProMSchema.ByReference = {
    var listCell : GProMListCell.ByReference = null
    var lastListCell : GProMListCell.ByReference = null
    var listTail : GProMListCell.ByReference = null
    var i = 0;
    for(schemaTup : ((String, Type)) <- schema.reverse){
      val attrDef = new GProMAttributeDef(GProM_JNA.GProMNodeTag.GProM_T_AttributeDef, schemaTup._2 match {
        case TString() => GProM_JNA.GProMDataType.GProM_DT_VARCHAR2
        case TBool() => GProM_JNA.GProMDataType.GProM_DT_BOOL
        case TFloat() => GProM_JNA.GProMDataType.GProM_DT_FLOAT
        case TInt() => GProM_JNA.GProMDataType.GProM_DT_INT
        case _ => GProM_JNA.GProMDataType.GProM_DT_STRING
      }, schemaTup._1.replaceFirst(tableName+"_", "")) 
      attrDef.write()
      val dataUnion = new GProMListCell.data_union(attrDef.getPointer)
      dataUnion.write()
      listCell = new GProMListCell.ByReference()
      if(i==0)
        listTail
      listCell.data = dataUnion
      listCell.next = lastListCell
      lastListCell = listCell;
      i+=1
    }
    val attrDefList = new GProMList.ByReference();
    attrDefList.`type` = GProM_JNA.GProMNodeTag.GProM_T_List
    attrDefList.length = schema.length
    attrDefList.head = listCell
    attrDefList.tail = listTail
    val scmByRef = new GProMSchema.ByReference()
    scmByRef.`type` = GProM_JNA.GProMNodeTag.GProM_T_Schema
    scmByRef.name = tableName
    scmByRef.attrDefs = attrDefList
    scmByRef
  }
  
  def translateMimirProjArgsToGProMList(schema : Map[String, Type], cols : Seq[ProjectArg]) : GProMList.ByReference = {
    var listCell : GProMListCell.ByReference = null
    var lastListCell : GProMListCell.ByReference = null
    var listTail : GProMListCell.ByReference = null
    var i = 0;
    for(projArg : ProjectArg <- cols.reverse){
      val attrRef = new GProMAttributeReference(GProM_JNA.GProMNodeTag.GProM_T_AttributeReference, projArg.name, 0, cols.length - i, 0, schema(projArg.name) match {
        case TString() => GProM_JNA.GProMDataType.GProM_DT_VARCHAR2
        case TBool() => GProM_JNA.GProMDataType.GProM_DT_BOOL
        case TFloat() => GProM_JNA.GProMDataType.GProM_DT_FLOAT
        case TInt() => GProM_JNA.GProMDataType.GProM_DT_INT
        case _ => GProM_JNA.GProMDataType.GProM_DT_STRING
      }) 
      attrRef.write()
      val dataUnion = new GProMListCell.data_union(attrRef.getPointer)
      dataUnion.write()
      listCell = new GProMListCell.ByReference()
      if(i==0)
        listTail
      listCell.data = dataUnion
      listCell.next = lastListCell
      lastListCell = listCell;
      i+=1
    }
    val attrRefList = new GProMList.ByReference();
    attrRefList.`type` = GProM_JNA.GProMNodeTag.GProM_T_List
    attrRefList.length = cols.length
    attrRefList.head = listCell
    attrRefList.tail = listTail
    attrRefList
  }
}