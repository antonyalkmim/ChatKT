package chat

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.PrintStream
import java.net.Socket
import java.util.*



fun main(args : Array<String>){

    //criar novo cliente
    val client = Client("127.0.0.1", 9999)

    //perguntar qual o username que deseja utilizar
    println("Qual seu nickname: ")
    val `in` = BufferedReader(InputStreamReader(System.`in`))
    val username = `in`.readLine()

    //conectar baseado no username informado
    client.connect(username)

    //esperar cliente estabelecer a conexao
    while(client.connected == null){ }

    //enquanto o usuario estiver conectado fica lendo o teclado
    while (client.connected!!) {
        val reader = BufferedReader(InputStreamReader(System.`in`))
        val message = reader.readLine()

        //enviar mensagem capturada do teclado
        client.sendMessage(message)
    }
}

class Client (val host:String, val port : Int){

    var readClient : Socket? = null //socket para ler as mensagens
    var writeClient : Socket? = null //socket para enviar as mensagens

    var username : String? = null
        private set

    var connected : Boolean? = null
        private set

    fun connect(username : String){
        this.username = username

        readClient = Socket(host, port)
        val out = PrintStream(readClient?.outputStream)

        //envia o nome de usuario que deseja conectar
        out.println(username)

        //ler resposta da tentativa de conexao
        val s = Scanner(readClient?.inputStream)

        //esperar resposta do server
        while(!s.hasNextLine()){}

        val response = s.nextLine()

        /*
            Se a resposta conter no max 5 caracteres entao :
                Resposta é o numero da porta do socket de envio de mensagens
            caso contrário a conexao foi estabelecida com sucesso
         */
        if (response.length > 6) { //usuario rejeitado
            println("Usuario duplicado!")
            readClient?.close()
            connected = false
        }else{ //usuario conectado com sucesso

            //cria nova thread para escutar as mensagens enviadas pelo servidor
            Thread(MessageReceiver(readClient!!.inputStream)).start()

            writeClient = Socket(host, response.toInt())
            connected = true
        }
    }

    /**
     * Enviar uma mensagem para o servidor atraves do socket responsavel
     */
    fun sendMessage(message: String) {
        PrintStream(writeClient?.outputStream)
                .println(Message(username!!, message))
    }

    /**
     * Responsavel por ficar escutando as mensagens enviadas pelo server
     */
    class MessageReceiver (val server : InputStream): Runnable {
        override fun run() {
            val s = Scanner(server)
            while(s.hasNextLine()){
                val message = Message.Companion.parseMessage(s.nextLine())
                println("${message.usernameFrom} diz: ${message.text}")
            }
        }
    }

}