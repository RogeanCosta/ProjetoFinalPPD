package servidor.offline;

import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;

public class ServidorOffline {

    private static final String url = "tcp://localhost:61616";
    private ConnectionFactory connectionFactory;

    public ServidorOffline()  {
        connectionFactory = new ActiveMQConnectionFactory(url);
    }

    public void registrarFilaUsuario(String nomeUsuario) {
        Connection connection = null;
        Session session = null;
        MessageConsumer consumer = null;
        MessageProducer producer = null;

        try {
            // Estabelecimento de conexão com o servidor JMS
            connection = connectionFactory.createConnection();
            connection.start();

            // Criando sessão com confirmação automática estabelecida.
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Criando fila par o usuário
            String nomeFila = "FILA_OFFLINE_" + nomeUsuario;
            Destination fila = session.createQueue(nomeFila);

            // Pelo que pude perceber, a fila somente é criada quando um consumidor ou algo é enviado para ela...
            // Então, definirei um consumidor para a fila e depois liberarei o recurso.
            consumer = session.createConsumer(fila);

            System.out.println("Fila criada: " + nomeFila);

        } catch (Exception e) {
            System.out.println("Erro ao criar fila do usuário.");

        } finally {
            // Liberação de recursos
            fecharRecursosJMS(consumer, session, connection);
        }


    }

    public void armazenarMensagem(String remetente, String destinatario, String mensagem) {
        Connection connection = null;
        Session session = null;
        MessageProducer producer = null;

        try {
            // Estabelecimento de conexão com o servidor JMS
            connection = connectionFactory.createConnection();
            connection.start();

            // Criando sessão com confirmação automática estabelecida.
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Definindo para qual fila será enviada a mensagem
            String nomeFila = "FILA_OFFLINE_" + destinatario;
            Destination fila = session.createQueue(nomeFila);

            // Criando produtor para a fila
            producer = session.createProducer(fila);

            // Estilizando mensagem a ser enviada
            String dados = remetente + "````" + mensagem;

            // Enviando mensagem para a fila do destinatário
            TextMessage message = session.createTextMessage(dados);
            producer.send(message);

        } catch (Exception e) {
            System.out.println("Erro ao enviar mensagem para a fila");

        } finally {
            // Liberando recursos
            fecharRecursosJMS(producer, session, connection);
        }
    }

    // Metodo auxiliar para garantir liberação de recursos da conexão.
    private void fecharRecursosJMS(AutoCloseable... recursos) {
        for (AutoCloseable recurso : recursos) {
            if (recurso != null) {
                try {
                    recurso.close();
                } catch (Exception ex) {
                    System.out.println("Algum recurso não foi liberado com sucesso.");
                }
            }
        }
    }
}
