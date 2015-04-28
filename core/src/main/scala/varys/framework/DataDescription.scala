package varys.framework

private[varys] object DataType extends Enumeration("FAKE", "INMEMORY", "ONDISK") {
  type DataType = Value

  val FAKE, INMEMORY, ONDISK = Value
}

private[varys] case class DataIdentifier(
    dataId: String, 
    coflowId: String)


//DNBD: add source client id to get information of link
private[varys] class FlowDescription(
    val id: String,  // Expected to be unique within the coflow
    val coflowId: String,  // Must be a valid coflow
    val dataType: DataType.DataType,  // http://www.scala-lang.org/node/7661
    val sizeInBytes: Long,
    val maxReceivers: Int,  // Upper-bound on the number of receivers (how long to keep it around?)
    val originHost: String,
    var originCommPort: Int,
    val originHostId: String)
  extends Serializable {

  val dataId = DataIdentifier(id, coflowId)
  val user = System.getProperty("user.name", "<unknown>")

  override def toString: String = "FlowDescription(" + id + ":" + dataType + ":" + coflowId + 
    ":" + originHostId + " # " + sizeInBytes + " Bytes)"
  
  def updateCommPort(commPort: Int) {
    originCommPort = commPort
  }
}

private[varys] class FileDescription(
    val id_ : String,  // Expected to be unique within the coflow
    val pathToFile: String,
    val cId_ : String,  // Must be a valid coflow
    val dataType_ : DataType.DataType,
    val offset : Long,
    val size_ : Long,
    val maxR_ : Int,
    val originHost_ : String,
    val originCommPort_ : Int,
    val originHostId_ : String)
  extends FlowDescription(id_, cId_, dataType_, size_, maxR_, originHost_, originCommPort_, originHostId_) {

  override def toString: String = "FileDescription(" + id + "["+ pathToFile + "]:" + dataType + 
    ":" + coflowId + ":" + originHostId_ + " # " + sizeInBytes + " Bytes)"
}

private[varys] class ObjectDescription(
    val id_ : String,  // Expected to be unique within the coflow
    val className: String, 
    val cId_ : String,  // Must be a valid coflow
    val dataType_ : DataType.DataType,
    val serializedSize : Long,
    val maxR_ : Int,
    val originHost_ : String,
    val origCommPort_ : Int,
    val originHostId_ : String)
  extends FlowDescription(id_, cId_, dataType_, serializedSize, maxR_, originHost_, origCommPort_, originHostId_) {

  override def toString: String = "ObjectDescription(" + id + "["+ className + "]:" + dataType + 
    ":" + coflowId + ":" + originHostId_ + " # " + sizeInBytes + " Bytes)"
}
