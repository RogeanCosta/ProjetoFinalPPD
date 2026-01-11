package cliente;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface UsuarioMethods extends Remote {
    // Servidor envia mensagem ao chat do cliente.
    void receberMensagem(String remetente, String msg) throws RemoteException;

    // Servidor informa ao usuario local qual o seu id (posição de registro na lista)
    void definirIdLocal(int id) throws RemoteException;

    // Servidor informa ao usuario que um de seus contatos alterou de servidor.online para servidor.offline, ou inverso.
    void receberMudancaStatus(String contato, boolean status) throws RemoteException;
}
