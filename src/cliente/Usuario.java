package cliente;

import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;
import servidor.online.ServidorRMIMethods;

import java.net.InetAddress;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Usuario extends UnicastRemoteObject implements UsuarioMethods, MessageListener{

    private final App app;
    private ServidorRMIMethods MetodosServidor;

    // Relacionados aos contatos/amigos
    private final List<String> amigos = new ArrayList<>();
    private final Map<String, List<Mensagem>> historicoMensagens = new HashMap<>();
    private final Map<String, Boolean> statusContatos = new HashMap<>();
    private final Map<String, Integer> notificacaoContatos = new HashMap<>();

    // Identificação
    private String nome;
    private String nomeComunicacao;
    private int id;
    private boolean isOnline = true;

    // Definições de rede
    private String urlServidor;
    private String urlCliente;

    // Relacionados com o consumo da fila
    private Connection connection;
    private Session session;
    private MessageConsumer consumer;

    public Usuario(String nome, String host, App app) throws Exception {
        super();
        this.app = app;

        // Obtendo o ip local
        String ipLocal = InetAddress.getLocalHost().getHostAddress();
        System.setProperty("java.rmi.server.hostname", ipLocal);

        urlServidor = "rmi://" + ipLocal + "/" + "ServidorGeral";
        MetodosServidor = (ServidorRMIMethods) Naming.lookup(urlServidor);

        if (MetodosServidor != null) {
            boolean nomeDisponivel = MetodosServidor.verificarExistenciaNome(nome);

            if (nomeDisponivel) {
                throw new Exception("Nome já está em uso no servidor!");
            }

            this.nome = nome;
            this.nomeComunicacao = obterNomeComunicacao(nome);

            // Registrando cliente no servidor de nomes
            urlCliente = "rmi://" + ipLocal + "/" + this.nomeComunicacao;
            Naming.rebind(urlCliente, this);
            System.out.println("Cliente registrado com sucesso no servidor de nomes: " + urlCliente);

            // Registrando Cliente no servidor.
            MetodosServidor.registrarCliente(this.nome);
            System.out.println("Cliente registrado com sucesso!");
        }
    }

    @Override
    public void receberMensagem(String remetente, String msg) throws RemoteException {
        javafx.application.Platform.runLater(() -> {
            app.mostrarMensagem(remetente, remetente, msg);
        });
    }

    @Override
    public void definirIdLocal(int id) throws RemoteException {
        System.out.println("Meu id: " + id);
        this.id = id;
    }

    @Override
    public void receberMudancaStatus(String contato, boolean status) throws RemoteException {
        statusContatos.put(contato, status);

        javafx.application.Platform.runLater(() -> {
            app.atualizarStatusContato(contato);
        });
    }

    public void enviarMensagem(String destinatario, String mensagem) {
        try {
            MetodosServidor.enviarMensagemRemoto(this.nome, destinatario, mensagem);
        } catch (Exception e) {
            System.out.println("Erro ao enviar mensagem.");
        }
    }

    public void registrarConexao(String contato) {
        notificacaoContatos.put(contato, 0);

        try {
            MetodosServidor.adicionarConexao(this.nome, contato);
        } catch (Exception e) {
            System.out.println("Erro ao tentar registrar conexão com contato.");
        }
    }

    public void desregistrarConexao(String contato) {
        try {
            MetodosServidor.removerConexao(this.nome, contato);
        } catch (RemoteException e) {
            System.out.println("Erro ao tentar desregistrar conexão com contato.");
        }
    }

    public void notificarMudancaStatusRemoto() {
        try {
            MetodosServidor.alterarStatusCliente(this.id, this.nome, this.isOnline);
        } catch (Exception e) {
            System.out.println("Erro ao enviar modificação de status.");
        }
    }

    public void adicionarMensagemAoHistorico (String contato, String autor, String mensagem) {
        // Se não existe histórico para esse contato, criaremos um....
        if (!historicoMensagens.containsKey(contato)) {
           historicoMensagens.put(contato, new ArrayList<>());
        }

        // Adicionando uma mensagem (autor, texto) na fila que armazena conversas com o contato...
        List<Mensagem> mensagens = historicoMensagens.get(contato);
        mensagens.add(new Mensagem(autor, mensagem));
    }

    public boolean verificarContatoNoServidor (String contato) {
        try {
            return MetodosServidor.verificarExistenciaNome(contato);
        } catch (Exception ex) {
            System.out.println("Ocorreu algum erro ao consultar servidor.");
            return false;
        }
    }

    // ============== Relacionados ao consumo da fila ===============
    public void desativarConsumoOffline() {
        try {
            if (consumer != null) {
                consumer.close();
                consumer = null;
            }

            if (session != null) {
                session.close();
                session = null;
            }

            if(connection != null) {
                connection.close();
                connection = null;
            }
        } catch (Exception e) {
            System.out.println("Aconteceu algum erro na liberação dos recursos JMS.");
        }
    }

    public void ativarConsumoOffline () {
        String url = "tcp://localhost:61616";

        try {
            if (connection != null) return; // se já existir conexão ativa, só para aqui!

            // Estabelecendo conexão com o servidorJMS.
            ConnectionFactory factory = new ActiveMQConnectionFactory(url);
            connection = factory.createConnection();
            connection.start();

            // Criando sessão
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

            // Definindo fila onde será recebido as mensagens
            String nomeFila = "FILA_OFFLINE_" + obterNomeComunicacao(nome);
            Destination fila = session.createQueue(nomeFila);

            // Definindo consumidor para a fila
            consumer = session.createConsumer(fila);

            // Definição de listener
            consumer.setMessageListener(this);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onMessage(Message message) {
        if (message instanceof TextMessage) {
            try {
                String dados = ((TextMessage) message).getText();

                String remetente = dados.split("````")[0];
                String msg = dados.split("````")[1];

                javafx.application.Platform.runLater(() -> {
                    app.mostrarMensagem(remetente, remetente, msg);
                });

            } catch (JMSException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // Auxiliar para obter nome (do usuário ou contatos) utilizado em conexões
    private String obterNomeComunicacao(String nome) {
        return nome.replaceAll(" ", "");
    }


    // Getters e Setters
    public String getNome() {
        return nome;
    }

    public boolean isOnline() {
        return isOnline;
    }

    public void setOnline(boolean online) {
        this.isOnline = online;
    }

    public void adicionarAmigo(String nomeAmigo) {
        amigos.add(nomeAmigo);
    }

    public List<String> getAmigos() {
        return amigos;
    }

    public Map<String, Boolean> getStatusContatos() {
        return statusContatos;
    }

    public Map<String, Integer> getNotificacaoContatos() {
        return notificacaoContatos;
    }

    public List<Mensagem> getHistoricoMensagens(String contato) {
        // Senão existe histórico de conversas com esse contato, retorna lista vazia.
        if (!historicoMensagens.containsKey(contato)){
            return new ArrayList<>();
        }

        // Caso existam mensagens com esse contato, é retornada apenas a lista das mensagens.
        return new ArrayList<>(historicoMensagens.get(contato));
    }
}