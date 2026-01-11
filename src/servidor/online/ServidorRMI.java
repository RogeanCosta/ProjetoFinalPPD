package servidor.online;

import cliente.UsuarioMethods;
import servidor.offline.ServidorOffline;

import java.net.InetAddress;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ServidorRMI extends UnicastRemoteObject implements ServidorRMIMethods {

    // Informações essenciais dos usuários
    private final Map<Integer, String> nomesUsuarios;
    private final Map<String, Integer> idsUsuarios;
    private final Map<Integer, Boolean> statusUsuarios;
    private final Map<String, ArrayList<String>> conexoes;

    private ServidorOffline servidorOffline;
    private final String ipLocal;
    private int idUnique = 0;

    public ServidorRMI() throws RemoteException {
        super();

        servidorOffline = new ServidorOffline();

        nomesUsuarios = new HashMap<>();
        idsUsuarios = new HashMap<>();
        statusUsuarios = new HashMap<>();
        conexoes = new HashMap<>();

        try {
            ipLocal = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            System.out.println("Erro ao obter IP Local do Servidor.");
            throw new RemoteException();
        }

        System.out.println("Servidor criado com sucesso!");
    }


    @Override
    public boolean verificarExistenciaNome(String nome) throws RemoteException {
        return idsUsuarios.containsKey(nome);
    }

    // ================ MÉTODOS DISPONÍVEIS REMOTAMENTE AO USUÁRIO ================
    @Override
    public void registrarCliente(String nome) throws RemoteException {
        String nomeComunicacao = obterNomeComunicacao(nome);

        try {
            UsuarioMethods cliente = (UsuarioMethods) Naming.lookup("rmi://" + ipLocal + "/" + nomeComunicacao);

            // Somente se for encontrado o cliente no registrador de nomes é que seu nome vai ser incluso no map.
            if (cliente != null) {
                statusUsuarios.put(idUnique, true);
                idsUsuarios.put(nome, idUnique);
                nomesUsuarios.put(idUnique++, nome);

                cliente.definirIdLocal(idUnique - 1);

                // Criando fila para o cliente no servidor Offline
                servidorOffline.registrarFilaUsuario(nomeComunicacao);

                System.out.println("Novo cliente registrado com ID " + (idUnique - 1) + ". Total: " + nomesUsuarios.size());
            }

        } catch (Exception e) {
            System.out.println("Erro ao registrar o cliente!");
        }
    }

    @Override
    public void enviarMensagemRemoto(String remetente, String destinatario, String mensagem) throws RemoteException {
        String nomeComunicacaoDestinatario = obterNomeComunicacao(destinatario);

        try {
            // Se o destinatario está online, mensagem é enviada diretamente a ele.
            if (verificarStatusDestinatario(destinatario)) {
                UsuarioMethods usuario = (UsuarioMethods) Naming.lookup("rmi://" + ipLocal + "/" + nomeComunicacaoDestinatario);
                usuario.receberMensagem(remetente, mensagem);

            } else { // Se o destinatário está offline, mensagem é enviada para a sua fila.
                servidorOffline.armazenarMensagem(remetente, nomeComunicacaoDestinatario, mensagem);
            }

        } catch (Exception e) {
            System.out.println("NÃO FOI POSSÍVEL ENVIAR MENSAGEM REMOTA!");
        }
    }

    // Registrar que um usuário tem interesse em saber o status de outro.
    @Override
    public void adicionarConexao(String nomeUsuario, String contato) throws RemoteException {
        // Se não existe interessado para esse contato, criaremos um....
        if (!conexoes.containsKey(contato)) {
            conexoes.put(contato, new ArrayList<>());
        }

        // Adicionando um interessado na fila de interssados do contato
        ArrayList<String> interessados = conexoes.get(contato);
        interessados.add(nomeUsuario);

        // Obtendo id do contato para enviar seu status atual para o novo interessado nele
        int id = idsUsuarios.get(contato);
        enviarMudancaStatusRemoto(contato, nomeUsuario, statusUsuarios.get(id));
    }

    // Desregistrar que usuário tinha interesse em saber o status do outro.
    @Override
    public void removerConexao(String usuario, String contato) throws RemoteException {
        // Removendo o usuário da fila de interessados do contato
        ArrayList<String> interesados = conexoes.get(contato);
        interesados.remove(usuario);
    }


    @Override
    public void alterarStatusCliente(int id, String nomeUsuario, boolean status) throws RemoteException {
        // Primeiro, altera a informação de status para o servidor....
        statusUsuarios.put(id, status);

        // Depois, notifica cada uma das conexões interessadas (se existirem)  nas informações de status sobre a mudança
        if (conexoes.containsKey(nomeUsuario)) {
            for (String contato : conexoes.get(nomeUsuario)) {
                enviarMudancaStatusRemoto(nomeUsuario, contato, statusUsuarios.get(id));
            }
        }
    }

    // ============================= MÉTODOS LOCAIS AUXILIARES ============================
    private void enviarMudancaStatusRemoto(String remetente, String destinatario, boolean isOnline) {
        String nomeComunicacaoDestinatario = obterNomeComunicacao(destinatario);

        try {
            UsuarioMethods usuario = (UsuarioMethods) Naming.lookup("rmi://" + ipLocal + "/" + nomeComunicacaoDestinatario);
            usuario.receberMudancaStatus(remetente, isOnline);

        } catch (Exception e) {
            System.out.println("NÃO FOI POSSÍVEL ENVIAR MUDANÇA DE STATUS!");
        }
    }

    private boolean verificarStatusDestinatario(String destinatario) {
        int idDestinatario = idsUsuarios.get(destinatario);
        boolean isDestinatarioOnline = statusUsuarios.get(idDestinatario);

        return isDestinatarioOnline;
    }

    // Auxiliar para obter nome (do usuário ou contatos) utilizado em conexões
    private String obterNomeComunicacao(String nome) {
        return nome.replaceAll(" ", "");
    }
}
