package model

import com.corundumstudio.socketio.listener.{ConnectListener, DataListener}
import com.corundumstudio.socketio.{AckRequest, Configuration, SocketIOClient, SocketIOServer}
import model.database.{Database, DatabaseAPI, TestingDatabase}
import play.api.libs.json.{JsValue, Json}
import java.util.Calendar


class TodoServer() {

  val database: DatabaseAPI = if (Configuration.DEV_MODE) {
    new TestingDatabase
  } else {
    new Database
  }

  setNextId()

  var usernameToSocket: Map[String, SocketIOClient] = Map()
  var socketToUsername: Map[SocketIOClient, String] = Map()

  val config: Configuration = new Configuration {
    setHostname("0.0.0.0")
    setPort(8090)
  }

  val server: SocketIOServer = new SocketIOServer(config)

  server.addConnectListener(new ConnectionListener(this))
  server.addEventListener("add_task", classOf[String], new AddTaskListener(this))
  server.addEventListener("complete_task", classOf[String], new CompleteTaskListener(this))

  server.start()

  def tasksJSON(): String = {
    val tasks: List[Task] = database.getTasks
    val tasksJSON: List[JsValue] = tasks.map((entry: Task) => entry.asJsValue())
    Json.stringify(Json.toJson(tasksJSON))
  }

  def setNextId(): Unit = {
    val tasks = database.getTasks
    if (tasks.nonEmpty) {
      Task.nextId = tasks.map(_.id.toInt).max + 1
    }
  }

}

object TodoServer {
  def main(args: Array[String]): Unit = {
    new TodoServer()
  }
}


class ConnectionListener(server: TodoServer) extends ConnectListener {

  override def onConnect(socket: SocketIOClient): Unit = {
    socket.sendEvent("all_tasks", server.tasksJSON())
  }

}


class AddTaskListener(server: TodoServer) extends DataListener[String] {

  override def onData(socket: SocketIOClient, taskJSON: String, ackRequest: AckRequest): Unit = {
    val task: JsValue = Json.parse(taskJSON)
    val title: String = (task \ "title").as[String]
    val description: String = (task \ "description").as[String]
    val deadline: String = (task \ "deadline").as[String]
    val now = Calendar.getInstance
    val day = now.get(Calendar.DATE)
    val month = now.get(Calendar.MONTH) + 1
    val year = now.get(Calendar.YEAR)
    if(!deadline(0).isDigit){
      server.server.getBroadcastOperations.sendEvent("error")
    }
    if (deadline.slice(6,10).toInt > year){
      server.database.addTask(Task(title, description,deadline))
      server.server.getBroadcastOperations.sendEvent("all_tasks", server.tasksJSON())
    }
    else if (deadline.slice(6,10).toInt == year && deadline.slice(0,2).toInt > month && deadline.slice(0,2).toInt < 13){
      server.database.addTask(Task(title, description,deadline))
      server.server.getBroadcastOperations.sendEvent("all_tasks", server.tasksJSON())
    }
    else if (deadline.slice(6,10).toInt == year && deadline.slice(0,2).toInt == month && deadline.slice(3,5).toInt >= day){
      server.database.addTask(Task(title, description,deadline))
      server.server.getBroadcastOperations.sendEvent("all_tasks", server.tasksJSON())
    }
    else{
      server.server.getBroadcastOperations.sendEvent("error")
    }
  }
}


class CompleteTaskListener(server: TodoServer) extends DataListener[String] {

  override def onData(socket: SocketIOClient, taskId: String, ackRequest: AckRequest): Unit = {
    server.database.completeTask(taskId)
    server.server.getBroadcastOperations.sendEvent("all_tasks", server.tasksJSON())
  }

}


