package servidor.online;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ServidorRMIMethods extends Remote {

    // Relacionado a existência de usuários de acordo com nomes.
    boolean verificarExistenciaNome(String nome) throws RemoteException;

    void registrarCliente(String nome) throws RemoteException;
    void enviarMensagemRemoto(String remetente, String destinatario, String mensagem) throws RemoteException;

    // Relacionando a mudança de status.
    void adicionarConexao(String usuario, String contato) throws RemoteException;
    void removerConexao(String usuario, String contato) throws RemoteException;
    void alterarStatusCliente(int id, String usuario, boolean status) throws RemoteException;
}
