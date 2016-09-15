package chat

import java.io.PrintStream
import java.net.ServerSocket
import java.net.Socket
import java.util.*
import kotlin.concurrent.thread


fun main(args : Array<String>){
    Server(9999).start()
}


class Server(val port : Int){

    private var connectedClients : List<ChatClient> = emptyList()
    private var listener : Thread? = null

    fun start(){
        listener = thread {
            listenConnections()
        }


        while (true){
            var messages = emptyList<Message>()

            //verificar se algum usuario desconectou
            val clientsClosed = connectedClients.filter { it.isClosed }
            if (clientsClosed.size > 0) {
                connectedClients = connectedClients.filter { !it.isClosed }

                clientsClosed.forEach { client ->
                    messages += Message("Server", "${client.username} saiu!")
                }
            }



            connectedClients.forEach { client ->
                if (client.hasMessage){
                    messages += client.readMessage()
                }
            }

            //send messages
            messages.forEach { message ->
                connectedClients
                        .filter { it.username != message.usernameFrom && !it.isClosed } //nao mandar a mensagem para quem esta enviando
                        .forEach { client -> client.writeMessage(message) }
            }
        }

    }

    private fun listenConnections(){

        ServerSocket(port).use { server ->
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
                val readClient = privateListener.accept()
                privateListener.close()

                val chatClient = ChatClient(connectMessage.username, readClient, client)
                connectedClients += chatClient

                //informar todos os usuarios que existe um novo usuario
                connectedClients.forEach { client ->
                    client.writeMessage(Message("Server", "${chatClient.username} entrou!"))
                }
            }
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

    var isClosed : Boolean = false
        private set

    var hasMessage = false
        private set

    var readLine : String? = null
        private set

    init {
        thread {
            while (!readClient.isClosed){
                val s = Scanner(readClient.inputStream)
                while (s.hasNextLine()){
                    synchronized(hasMessage){
                        readLine = s.nextLine()
                        hasMessage = true
                    }
                }
                s.close()
            }
            readClient.close()
            writeClient.close()
            isClosed = true
        }
    }


    fun readMessage() : Message {
        synchronized(hasMessage) {

            //readLine = usernameFrom::mensagem

            val messageParts = readLine!!.split("::")
            val message = Message(messageParts[0], messageParts[1])

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
                   val text : String) {

    override fun toString(): String {
        return "$usernameFrom::$text"
    }

    companion object {
        fun parseMessage(string : String) : Message {
            val stringParts = string.split("::")
            return Message(stringParts[0], stringParts[1])
        }
    }

}
