package varys.examples.tracedriven

import java.io.{ObjectInputStream, ObjectOutputStream}
import java.net.{Socket, ServerSocket}
import java.util.concurrent.atomic.AtomicInteger
import _root_.log2coflow.YarnMapReduceLogParser
import varys.{Logging, Utils}
import varys.framework.{CoflowType}
import _root_.log2coflow.CoflowDescription
import varys.framework.client.{VarysClient, ClientListener}

/**
 * Created by wakira on 15-7-17.
 */

// TODO start DNBD

class PutDescription(val id: String, val size: Int)
class GetDescription(val id: String)

case class JobMission(coflowId: String, putList: List[PutDescription], getList: List[GetDescription])
case class StartGetting()

object Master extends Logging {

  class TestListener extends ClientListener with Logging {
    def connected(id: String) {
      logInfo("Connected to master, got client ID " + id)
    }

    def disconnected() {
      logInfo("Disconnected from master")
      System.exit(0)
    }
  }

  class MasterThread (val coflowId: String, val coflowDescription: CoflowDescription,
                       val listenPort: Int)
    extends Thread("TraceMasterThread") with Logging {

    val nodesInCoflow = coflowDescription.nodes.toArray

    val HEARTBEAT_SEC = System.getProperty("varys.framework.heartbeat", "1").toInt
    var serverSocket: ServerSocket = new ServerSocket(listenPort)

    var assignedWorkers = new AtomicInteger()
    var finishedWorkers = new AtomicInteger()
    var putCompletedWorkers = new AtomicInteger()
    var connectedWorkers = new AtomicInteger()
    var stopServer = false
    this.setDaemon(true)

    override def run(): Unit = {
      var threadPool = varys.Utils.newDaemonCachedThreadPool()
      try {
        while (!stopServer && !finished) {
          var clientSocket: Socket = null
          try {
            serverSocket.setSoTimeout(HEARTBEAT_SEC * 1000)
            clientSocket = serverSocket.accept
          } catch {
            case e: Exception => {
              if (stopServer) {
                logInfo("Stopping TraceMaster" )
              }
            }
          }

          if  (clientSocket != null) {
            try {
              threadPool.execute(new Thread {
                override def run(): Unit = {
                  val oos = new ObjectOutputStream(clientSocket.getOutputStream)
                  oos.flush()
                  val ois = new ObjectInputStream(clientSocket.getInputStream)

                  try {
                    // Mark start of worker connection
                    ois.readObject.asInstanceOf[WorkerOnline]
                    connectedWorkers.getAndIncrement()

                    // assign a node for this worker
                    val nodeForWorker: String = getUnassignedNode
                    // filter flows with the node as source to construct putList
                    val putList = coflowDescription.flows.filter(_.source == nodeForWorker).map(flow =>
                      new PutDescription("flow-"+flow.source+"-"+flow.dest, flow.size)
                    )
                    // filter flows with the node as dest to construct getList
                    val getList = coflowDescription.flows.filter(_.source == nodeForWorker).map(flow =>
                      new GetDescription("flow-"+flow.source+"-"+flow.dest)
                    )

                    // send coflowId and JobMission
                    oos.writeObject(JobMission(coflowId, putList, getList))

                    // wait for ALL workers to complete put
                    ois.readObject().asInstanceOf[PutComplete]
                    var completedWorkers = putCompletedWorkers.incrementAndGet()
                    while (completedWorkers < coflowDescription.width) {
                      Thread.sleep(500) // FIXME choose appropriate value
                      completedWorkers = putCompletedWorkers.get()
                    }

                    // send StartGetting
                    oos.writeObject(StartGetting)

                    // wait for PutComplete
                    ois.readObject().asInstanceOf[PutComplete]
                  } catch {
                    case e: Exception => {
                      logWarning ("TraceMaster had a " + e)
                    }
                  } finally {
                    clientSocket.close()
                  }
                }
              })
            } catch {
              // In failure, close socket here; else, client thread will close
              case e: Exception =>
                logError("TraceMaster had a " + e)
                clientSocket.close()
            }
          }

        }
      } finally {
        serverSocket.close()
      }
      // Shutdown the thread pool
      threadPool.shutdown()
    }
    def finished = finishedWorkers.get() == coflowDescription.width

    def getUnassignedNode : String= {
      val index = assignedWorkers.getAndIncrement()
      nodesInCoflow.apply(index)
    }
  }

  def main(args: Array[String]) {
    if (args.length < 4) {
      println("USAGE: TraceMaster <varysMasterUrl> <traceLogFile> <numWorkers> <listenPort>")
      System.exit(1)
    }

    val url = args(0)
    val pathToFile = args(1)
    val numWorkers = args(2).toInt
    val listenPort = args(3).toInt

    var fileName: String = null

    // run log2coflow on file
    val input = scala.io.Source.fromFile(args(0)).getLines()
    val desc = new YarnMapReduceLogParser(input).run()

    val listener = new TestListener
    val client = new VarysClient("TraceMaster", url, listener)
    client.start()

    val varysDesc = new varys.framework.CoflowDescription(
      "Trace-" + fileName,
      CoflowType.SHUFFLE, desc.width, desc.size)

    val coflowId = client.registerCoflow(varysDesc)
    logInfo("Registered coflow " + coflowId)

    // Start server after registering the coflow and relevant
    val masterThread = new MasterThread(coflowId, desc, listenPort)
    masterThread.start()
    logInfo("Started MasterThread. Now waiting for it to die.")
    logInfo("Broadcast Master Url: %s:%d".format(
      Utils.localHostName, listenPort))

    // Wait for all slaves to receive
    masterThread.join()
    logInfo("Unregistered coflow " + coflowId)
    client.unregisterCoflow(coflowId)
  }

}