package servidor.online;

import java.net.InetAddress;
import java.rmi.*;
import java.rmi.registry.LocateRegistry;

public class Registrador {

    public static void main(String[] args) {
        try {
            // Criando servidor
            ServidorRMI servidor = new ServidorRMI();

            // Informações básicas do servidor
            String ipLocal = InetAddress.getLocalHost().getHostAddress();
            String serverName = "ServidorGeral";
            int porta = 1099;

            String url = "rmi://" + ipLocal + "/" + serverName;
            System.out.println(url);

            // Configuração para identificação, pelo RMI, do ip local da máquina onde está o servidor.
            System.setProperty("java.rmi.server.hostname", ipLocal);

            // Registrando servidor com url e porta específica.
            LocateRegistry.createRegistry(porta);
            Naming.rebind(url, servidor);

            System.out.println("Servidor registrado com sucesso!");

        } catch (Exception e) {
            System.out.println("Erro!");
        }
    }
}

