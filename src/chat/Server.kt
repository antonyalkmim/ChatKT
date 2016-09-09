package chat

import java.io.PrintStream
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import kotlin.concurrent.thread


fun main(args : Array<String>){

    val server = Server(9999)

    var clients : List<ChatClient> = emptyList()

    while (true){
        if(server.hasNewClient){
            clients += server.getNewClient()
        }

        var messages = emptyList<Message>()

        clients.forEach { client ->
            if (client.hasMessage){
                messages += client.readMessage()
            }
        }

        //send messages
        messages.forEach { message ->
            clients
                    .filter { it.username == message.usernameTo }
                    .forEach { client ->
                        client.writeMessage(message)
                    }
        }

    }

}


class Server(val port : Int){

    private var connectedClients : List<ChatClient> = emptyList()
    private var clientQueue : Queue<ChatClient> = LinkedList()
    private val listener : Thread

    init{
        listener = thread {
            listenConnections()
        }
    }


    var hasNewClient : Boolean = false
        get() {
            synchronized(clientQueue){
                return clientQueue.size > 0
            }
        }

    private fun listenConnections(){

        ServerSocket(9999).use { server ->
            println("Server running on port ${server.localPort}!")

            while (true) {
                val client = server.accept()
                println("Client connected ${client.inetAddress.hostAddress}")

                val connectMessage = receiveConnectMessage(client)

                //verificar se usuario ja possui conexao
                val hasDuplicatedUsername = connectedClients.any { it.username == connectMessage.username }
                if(hasDuplicatedUsername){
                    reject(client)
                }

                //criar conexao privada para chat
                val privateListener = ServerSocket(0)
                sendSuccess(client, privateListener.localPort)

                //confirmar conexao em servidor privado
                val writeClient = privateListener.accept()
                privateListener.close()

                val chatClient = ChatClient(connectMessage.username, writeClient, client)
                synchronized(clientQueue){
                    clientQueue.add(chatClient)
                }
            }
        }

    }

    fun dispose(){
        listener.stop()
    }

    fun getNewClient() : ChatClient {
        synchronized(clientQueue){
            return clientQueue.remove()
        }
    }

    private fun sendSuccess(client: Socket, localPort: Int) {
        //escrever a porta que foi aberta para a conexao prval text : Stringivada
        PrintStream(client.outputStream).println(localPort)
    }

    private fun reject(client : Socket){
        PrintStream(client.outputStream).println("Usuario ja possui conexao")
        client.close()
    }


    private fun receiveConnectMessage(client : Socket) : ConnectMessage {
        val scanner = Scanner(client.inputStream)
        val username = scanner.nextLine()
        return ConnectMessage(username)
    }


}

class ChatClient(val username: String,
                 val readClient: Socket,
                 val writeClient : Socket){

    var hasMessage = false
        private set

    var readLine : String? = null
        private set

    init {
        thread {
            while (true){
                val s = Scanner(readClient.inputStream)
                while (s.hasNextLine()){
                    synchronized(hasMessage){
                        readLine = s.nextLine()
                        hasMessage = true
                    }
                }
                s.close()
            }
        }
    }


    fun readMessage() : Message {
        synchronized(hasMessage) {
            val messageParts = readLine!!.split("::")
            val message = Message(messageParts[0], messageParts[1], messageParts[2])

            readLine = null
            hasMessage = false

            return message
        }
    }

    fun writeMessage(message : Message) {
        val ps = PrintStream(writeClient.outputStream)
        ps.println(message)
    }

}


data class ConnectMessage(val username:String)
data class Message(val usernameFrom:String,
                   val usernameTo:String,
                   val text : String) {
    override fun toString(): String {
        return "$usernameFrom::$usernameTo::$text"
    }

    companion object {
        fun parseMessage(string: String) : Message? {
            val messageParts = string.split("::")

            if (messageParts.size != 3)
                return null

            return Message(messageParts[0],messageParts[1], messageParts[2])
        }
    }

}
