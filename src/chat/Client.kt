package chat

import chat.Message
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.net.Socket
import java.util.*



fun main(args : Array<String>){

    val client = Client("127.0.0.1", 9999)

    println("Qual seu nickname: ")
    val `in` = BufferedReader(InputStreamReader(System.`in`))
    val username = `in`.readLine()
    client.connect(username)

    while(client.connected == null){ }

    while (client.connected!!) {
        val `in` = BufferedReader(InputStreamReader(System.`in`))
        val message = `in`.readLine()

        client.sendMessage(message)
    }
}

class Client (val host:String, val port : Int){

    var readClient : Socket? = null
    var writeClient : Socket? = null

    var username : String? = null
        private set

    var connected : Boolean? = null
        private set

    fun connect(username : String){
        this.username = username

        readClient = Socket(host, port)
        val out = PrintStream(readClient?.outputStream)

        //connect
        out.println(username)

        //ler resposta da tentatica de conexao
        val s = Scanner(readClient?.inputStream)
        while(!s.hasNextLine()){}

        var response = s.nextLine()

        if (response.length > 6) { //usuario rejeitado
            println("Usuario duplicado!")
            readClient?.close()
            connected = false
        }else{
            Thread(MessageReceiver(readClient!!.inputStream)).start()

            writeClient = Socket(host, response.toInt())
            connected = true
        }

    }

    class MessageReceiver (val server : InputStream): Runnable{
        override fun run() {
            val s = Scanner(server)
            while(s.hasNextLine()){
                val message = Message.Companion.parseMessage(s.nextLine())
                println("${message.usernameFrom} diz: ${message.text}")
            }
        }
    }

    fun sendMessage(message: String) {
        PrintStream(writeClient?.outputStream)
                .println(Message(username!!, message))
    }

}