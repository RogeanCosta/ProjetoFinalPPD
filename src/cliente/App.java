package cliente;

import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.rmi.RemoteException;
import java.util.List;

public class App extends Application {
    private static Stage primaryStage;

    // Interface - Contatos
    private ListView<String> listaContatos;
    private Label nomeContatoLabel;
    private Label statusContatoLabel;
    private Button btnAdicionar;

    // Interface - Chat
    private BorderPane chatPane;
    private VBox areaMensagens;
    private TextField campoMensagem;
    private Button btnEnviar;

    private Usuario usuario;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        abrirTelaCadastro();
    }

    private void abrirTelaCadastro() {
        Label titulo = new Label("Bem vindo ao Chat.");
        titulo.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");

        Label labelNome = new Label("Digite seu nome de usuário:");
        labelNome.setStyle("-fx-text-fill: #BBB; -fx-font-size: 14px;");

        TextField campoNome = new TextField();
        campoNome.setPromptText("Fulado de Tal");
        campoNome.setStyle("""
                -fx-background-color: #1e1e1e;
                -fx-text-fill: white;
                -fx-prompt-text-fill: #777;
                -fx-border-color: #333;
                -fx-border-radius: 6;
                -fx-background-radius: 6;
                """);

        Button btnCadastrar = new Button("Cadastrar");
        btnCadastrar.setStyle("""
            -fx-background-color: #274B7A;
            -fx-text-fill: #e0e0e0;
            -fx-font-size: 14px;
            -fx-font-weight: bold;
            -fx-background-radius: 6;
            -fx-cursor: hand;
            -fx-padding: 8 16 8 16;
        """);

        VBox layout = new VBox(12, titulo, labelNome, campoNome, btnCadastrar);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(25));
        layout.setStyle("-fx-background-color: #121212;");

        Scene CadastroScene = new Scene(layout, 400, 250);
        primaryStage.setScene(CadastroScene);
        primaryStage.setTitle("Chat - Cadastro");
        primaryStage.centerOnScreen();
        primaryStage.show();

        btnCadastrar.setOnAction(e -> {
            String nome = campoNome.getText().trim();

            if (!nome.isBlank()) {
                try {
                    Usuario u = new Usuario(nome, "localhost", this);
                    setUsuario(u);

                    abrirInterfacePrincipal();

                } catch (RemoteException ex) {
                    mostrarAlerta("Erro na comunicação", "Não foi possível conectar ao servidor.");
                } catch (Exception ex) {
                    mostrarAlerta("Nome não permitido!", ex.getMessage());
                }
            } else {
                mostrarAlerta("Nome inválido!", "Não é permitido deixar o campo em branco.");
            }
        });

        // Configurando fechamento da aplicação ao clicar no X.
        primaryStage.setOnCloseRequest(e -> {
            Platform.exit();
            System.exit(0);
        });
    }

    private void abrirInterfacePrincipal() {
        BorderPane root = new BorderPane();
        root.setPrefSize(1000, 600);
        root.setStyle("-fx-background-color: #121212;");

        // ================= SIDEBAR =================
        VBox sidebar = new VBox(15);
        sidebar.setPadding(new Insets(12));
        sidebar.setPrefWidth(260);
        sidebar.setStyle("""
            -fx-background-color: #1e1e1e;
            -fx-border-color: #2a2a2a;
            -fx-border-width: 0 1 0 0;
        """);

        // ===== SWITCH ONLINE / OFFLINE =====
        StackPane switchPane = new StackPane();
        switchPane.setPrefSize(50, 25);
        switchPane.setStyle("""
            -fx-background-color: #4CAF50;
            -fx-background-radius: 15;
        """);

        Circle knob = new Circle(10);
        knob.setFill(Color.WHITE);
        knob.setTranslateX(12);
        switchPane.getChildren().add(knob);

        Label statusLabel = new Label("ONLINE");
        statusLabel.setStyle("""
            -fx-text-fill: #4CAF50;
            -fx-font-size: 14px;
            -fx-font-weight: bold;
        """);

        // Para evitar travamento posterior, como padrão é ONLINE, definimos consumo de fila já aqui...
        usuario.ativarConsumoOffline();

        HBox statusBox = new HBox(10, switchPane, statusLabel);
        statusBox.setAlignment(Pos.CENTER);

        switchPane.setOnMouseClicked(e -> {
            usuario.setOnline(!usuario.isOnline());

            // Baseado no status atual, iremos verificar fila para consumir ou liberar recursos JMS.
            if(usuario.isOnline()) {
                usuario.ativarConsumoOffline();
            } else {
                usuario.desativarConsumoOffline();
            }

            TranslateTransition anim = new TranslateTransition(Duration.millis(180), knob);
            if (usuario.isOnline()) {
                anim.setToX(12);
                switchPane.setStyle("""
                    -fx-background-color: #4CAF50;
                    -fx-background-radius: 15;
                """);
                statusLabel.setText("ONLINE");
                statusLabel.setTextFill(Color.web("#4CAF50"));
            } else {
                anim.setToX(-12);
                switchPane.setStyle("""
                    -fx-background-color: #555;
                    -fx-background-radius: 15;
                """);
                statusLabel.setText("OFFLINE");
                statusLabel.setTextFill(Color.web("#aaa"));
            }
            anim.play();

            // Notifica remotamente (servidorRMI e amigos) da mudança
            usuario.notificarMudancaStatusRemoto();

            // Atualiza o botão de adicionar contato
            atualizarAdicionarContato();

            // Atualiza o campo de digitar mensagem e o botão de envio baseado no status do usuário
            atualizarElementosDeEnvio();
        });

        // ===== LISTA DE CONTATOS =====
        ObservableList<String> contatos = FXCollections.observableArrayList();
        listaContatos = new ListView<>(contatos);

        VBox.setVgrow(listaContatos, Priority.ALWAYS);
        listaContatos.setStyle("""
            -fx-background-color: #1e1e1e;
            -fx-control-inner-background: #1e1e1e;
        """);

        listaContatos.setCellFactory(lv -> new ListCell<>() {

            private final Label nome = new Label();
            private final Circle statusCircle = new Circle(6);
            private final Region spacer = new Region();
            private final HBox conteudo = new HBox(10, nome, spacer, statusCircle);

            private final ContextMenu menuContexto = new ContextMenu();

            {
                nome.setTextFill(Color.web("#e0e0e0"));
                nome.setStyle("""
            -fx-font-size: 15px;
            -fx-font-weight: 600;
        """);

                conteudo.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(spacer, Priority.ALWAYS);

                setPadding(new Insets(12, 10, 12, 10));
                setStyle("""
            -fx-background-color: transparent;
            -fx-border-color: #2f2f2f;
            -fx-border-width: 0 0 1 0;
        """);

                // ===== MENU DE CONTEXTO (CLIQUE DIREITO) =====
                MenuItem excluir = new MenuItem("Excluir contato");
                excluir.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");

                excluir.setOnAction(e -> {
                    String contato = getItem();

                    if (contato != null) {
                        // Se o chat com o contato estiver aberto, então a visualização do chat com ele é fechada.
                        if (nomeContatoLabel != null && nomeContatoLabel.getText().equals(contato)) {
                            chatPane.setTop(null);
                            chatPane.setBottom(null);
                            chatPane.setCenter(construirAreaChatSemChat());
                        }

                        // Remoção Local
                        listaContatos.getItems().remove(contato);
                        usuario.getAmigos().remove(contato);
                        usuario.getStatusContatos().remove(contato);
                        usuario.getNotificacaoContatos().remove(contato);

                        // Remoção de interesse no servidor
                        usuario.desregistrarConexao(contato);
                    }
                });

                menuContexto.getItems().add(excluir);

                // MOSTRAR MENU APENAS COM BOTÃO DIREITO
                setOnContextMenuRequested(e -> {
                    if (!isEmpty()) {
                        menuContexto.show(this, e.getScreenX(), e.getScreenY());
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                    setContextMenu(null);

                } else {
                    // Checar notificações para mudar estilo
                    int notificacoes = usuario.getNotificacaoContatos().getOrDefault(item, 0);

                    if (notificacoes > 0) {
                        nome.setText(item + " (" + notificacoes + ")");
                        nome.setTextFill(Color.ORANGE);
                        nome.setStyle("""
                            -fx-font-size: 15px;
                            -fx-font-weight: 600;
                            """);
                    } else {
                        nome.setText(item);
                        nome.setTextFill(Color.web("#e0e0e0"));
                        nome.setStyle("""
                            -fx-font-size: 15px;
                            -fx-font-weight: 600;
                            """);
                    }

                    boolean online = usuario.getStatusContatos().getOrDefault(item, true);
                    statusCircle.setFill(online ? Color.web("#4CAF50") : Color.web("#777"));

                    setGraphic(conteudo);
                }
            }
        });

        // Clique no contato
        listaContatos.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                String selecionado = listaContatos.getSelectionModel().getSelectedItem();
                if (selecionado != null) {
                    abrirChat(selecionado);
                }
            }
        });

        btnAdicionar = new Button("Adicionar contato");
        btnAdicionar.setMaxWidth(Double.MAX_VALUE);
        btnAdicionar.setStyle("""
            -fx-background-color: #274B7A;
            -fx-text-fill: #e0e0e0;
            -fx-font-size: 14px;
            -fx-font-weight: bold;
            -fx-background-radius: 6;
            -fx-cursor: hand;
        """);

        btnAdicionar.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Adicionar contato");
            dialog.setHeaderText("Digite o nome do amigo");
            dialog.setContentText("Nome:");

            // Para que apareça junto a janela correta
            dialog.initOwner(primaryStage);
            dialog.initModality(Modality.WINDOW_MODAL);

            dialog.showAndWait().ifPresent(nomeAmigo -> {
                if (!nomeAmigo.isBlank()) {
                    if (nomeAmigo.equals(usuario.getNome())) {
                      mostrarAlerta("Contato Inválido", "Você não pode se adicionar como contato.");

                    } else if (usuario.getAmigos().contains(nomeAmigo)) {
                        mostrarAlerta("Contato Inválido", "O contato já está presente em sua lista de contatos.");

                    } else if (usuario.verificarContatoNoServidor(nomeAmigo)) {
                        usuario.adicionarAmigo(nomeAmigo);
                        usuario.getNotificacaoContatos().put(nomeAmigo, 0);
                        usuario.registrarConexao(nomeAmigo);
                        listaContatos.getItems().add(nomeAmigo);

                    } else {
                        mostrarAlerta("Contato Inválido", "O contato não está registrado no servidor.");
                    }
                }
            });
        });

        sidebar.getChildren().addAll(statusBox, listaContatos, btnAdicionar);

        // ================= CHAT AREA =================
        chatPane = new BorderPane();
        chatPane.setStyle("-fx-background-color: #181818;");

        // Colocando a Vbox com informações na parte central, mostrada quando nenhum chat está aberto.
        chatPane.setCenter(construirAreaChatSemChat());

        root.setLeft(sidebar);
        root.setCenter(chatPane);

        Scene scene = new Scene(root);
        primaryStage.setTitle("Chat - Tela Principal");
        primaryStage.setScene(scene);
        primaryStage.centerOnScreen();
        primaryStage.show();

        // Configuração encerramento da aplicação ao clique no X
        primaryStage.setOnCloseRequest(e -> {
            try {
                if (usuario != null) {
                    usuario.setOnline(false);
                    usuario.desativarConsumoOffline();
                    usuario.notificarMudancaStatusRemoto();

                    for (String amigo : usuario.getAmigos()) {
                        usuario.desregistrarConexao(amigo);
                    }
                }
            } catch (Exception ex) {
                System.out.println("Ocorreu um erro no fechamento correto da aplicação principal.");
            }

            Platform.exit();
            System.exit(0);
        });
    }

    private void abrirChat(String nomeContato) {
        // Número de notificações com o contato zera ao abrir chat com ele.
        usuario.getNotificacaoContatos().put(nomeContato, 0);
        listaContatos.refresh();

        nomeContatoLabel = new Label(nomeContato);
        nomeContatoLabel.setStyle("""
            -fx-font-size: 20px;
            -fx-font-weight: bold;
            -fx-text-fill: #e0e0e0;
        """);

        // Online por padrão.
        boolean online = usuario.getStatusContatos().getOrDefault(nomeContato, true);

        statusContatoLabel = new Label(online ? "Online" : "Offline");
        statusContatoLabel.setTextFill(online ? Color.web("#4CAF50") : Color.web("#aaa"));
        statusContatoLabel.setStyle("""
            -fx-font-size: 14px;
            -fx-font-weight: bold;
        """);

        VBox info = new VBox(3, nomeContatoLabel, statusContatoLabel);

        Button btnFechar = new Button("X");
        btnFechar.setStyle("""
            -fx-background-color: transparent;
            -fx-text-fill: #ff4d4d;
            -fx-font-size: 18px;
            -fx-font-weight: bold;
        """);

        btnFechar.setOnAction(e -> {
            chatPane.setTop(null);
            chatPane.setBottom(null);
            chatPane.setCenter(construirAreaChatSemChat());

            // Se não há chat ativo, então, o campoMensagem e o btnEnviar não devem "existir".
            this.campoMensagem = null;
            this.btnEnviar = null;
            this.nomeContatoLabel = null;
        });

        HBox topo = new HBox(info, btnFechar);
        topo.setPadding(new Insets(10));
        topo.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, Priority.ALWAYS);
        topo.setStyle("""
            -fx-background-color: #202020;
            -fx-border-color: #2a2a2a;
            -fx-border-width: 0 0 1 0;
        """);

        areaMensagens = new VBox(10);
        areaMensagens.setPadding(new Insets(10));

        ScrollPane scroll = new ScrollPane(areaMensagens);
        scroll.setFitToWidth(true);
        scroll.setStyle("""
            -fx-background: #181818;
            -fx-background-color: #181818;
        """);

        // Reconstrução do chat a partir do históricoMensagens sempre que o contato for aberto.
        areaMensagens.getChildren().clear();
        List<Mensagem> historico = usuario.getHistoricoMensagens(nomeContato);
        for (Mensagem m : historico) {
            adicionarMensagemNoChat(m.getAutor(), m.getConteudo());
        }

        campoMensagem = new TextField();
        campoMensagem.setPromptText("Digite uma mensagem...");
        campoMensagem.setStyle("""
            -fx-background-color: #2a2a2a;
            -fx-text-fill: #e0e0e0;
            -fx-prompt-text-fill: #888;
            -fx-font-size: 14px;
        """);

        btnEnviar = new Button("Enviar");
        btnEnviar.setStyle("""
            -fx-background-color: #4CAF50;
            -fx-text-fill: white;
            -fx-font-size: 14px;
            -fx-font-weight: bold;
            -fx-background-radius: 6;
        """);

        btnEnviar.setOnAction(e -> {
            String msg = campoMensagem.getText().trim();

            if (!msg.isEmpty()) {
                usuario.enviarMensagem(nomeContato, msg);
                mostrarMensagem(nomeContato, usuario.getNome(), msg);
                campoMensagem.clear();
            }
        });

        HBox envio = new HBox(10, campoMensagem, btnEnviar);
        envio.setPadding(new Insets(10));
        envio.setStyle("-fx-background-color: #202020;");
        HBox.setHgrow(campoMensagem, Priority.ALWAYS);

        // Sempre que abrir um chat, deve atualizar, caso necessário o campo de mensagem e o botão de envio
        atualizarElementosDeEnvio();

        chatPane.setTop(topo);
        chatPane.setCenter(scroll);
        chatPane.setBottom(envio);
    }

    private void adicionarMensagemNoChat (String autor, String messagem) {
        Label label = new Label(messagem);
        label.setWrapText(true);
        label.setMaxWidth(350);

        HBox container = new HBox(label);
        container.setPadding(new Insets(2, 5, 2, 5));

        // Se o autor da mensagem a ser mostrada foi o próprio usuário, terá um estilo. Senão, terá outro.
        if(autor.equals(usuario.getNome())) {
            container.setAlignment(Pos.CENTER_RIGHT);
            label.setStyle("""
                    -fx-background-color: #4CAF50;
                    -fx-text-fill: white;
                    -fx-padding: 8 12;
                    -fx-background-radius: 10;
                    """);
        } else {
            container.setAlignment(Pos.CENTER_LEFT);
            label.setStyle("""
                    -fx-background-color: #2a2a2a;
                    -fx-text-fill: #e0e0e0;
                    -fx-padding: 8 12;
                    -fx-background-radius: 10;
                    """);
        }

        areaMensagens.getChildren().add(container);
    }

    public void mostrarMensagem(String contato, String remetente, String msg) {
        usuario.adicionarMensagemAoHistorico(contato, remetente, msg);

        // Se o chat com o contato estiver aberto, a mensagem já é adicionada a visualização do chat.
        if (nomeContatoLabel != null && nomeContatoLabel.getText().equals(contato)) {
            adicionarMensagemNoChat(remetente, msg);
        } else {
            int qntdNotificacoes = usuario.getNotificacaoContatos().get(contato);
            usuario.getNotificacaoContatos().put(contato, qntdNotificacoes + 1);
            listaContatos.refresh();
        }
    }

    public void atualizarStatusContato(String contato) {
        listaContatos.refresh();

        // Somente quando estiver aberto é que atualiza o status!
        if (nomeContatoLabel != null && nomeContatoLabel.getText().equals(contato)) {
            boolean online = usuario.getStatusContatos().get(contato);

            statusContatoLabel.setText(online ? "Online" : "Offline");
            statusContatoLabel.setTextFill(online ? Color.web("#4CAF50") : Color.web("#aaa"));
            statusContatoLabel.setStyle("""
            -fx-font-size: 14px;
            -fx-font-weight: bold;
        """);
        }

    }


    // Construção das informações a serem mostradas na area do chat quando nenhum chat estiver aberto.
    private Node construirAreaChatSemChat() {
        // Container para as três labels a serem exibidas caso nenhum chat esteja aberto
        VBox placeholderBox = new VBox(10);
        placeholderBox.setAlignment(Pos.CENTER);

        // Label 1 : Boas vindas ao usuário
        Label helloLabel = new Label("Olá, " + usuario.getNome() + "!");
        helloLabel.setStyle("-fx-font-size: 22px; -fx-font-weight: bold;");
        helloLabel.setTextFill(Color.WHITE);

        // Label 2: Sugestão para abrir chat
        Label sugestLabel = new Label("Clique em um contato para iniciar a conversa.");
        sugestLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold;");
        sugestLabel.setTextFill(Color.web("#888"));

        // Label 3: Instrução para excluir contato
        Label instructLabel = new Label("Para excluir um contato, clique com botão direito nele e escolha a opção excluir.");
        instructLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        instructLabel.setTextFill(Color.web("#ff4d4d"));

        // Adicionando as três labels na Vbox
        placeholderBox.getChildren().addAll(helloLabel, sugestLabel, instructLabel);

        return placeholderBox;
    }

    // Quando usuário está offline, campo de mensagem e botão enviar estão desabilitados.
    // Quando usuário está online, campo de mensagem e botão enviar estão habilitados.
    private void atualizarElementosDeEnvio() {
        if (btnEnviar == null || campoMensagem == null) return;

        if (usuario.isOnline()) {
            // Campo de mensagem habilitado com texto padrão.
            campoMensagem.setDisable(false);
            campoMensagem.setPromptText("Digite uma mensagem...");

            // Botão enviar habilitado com texto e estilos padrão.
            btnEnviar.setDisable(false);
            btnEnviar.setText("Enviar");
            btnEnviar.setStyle("""
                        -fx-background-color: #4CAF50;
                        -fx-text-fill: white;
                        -fx-font-size: 14px;
                        -fx-font-weight: bold;
                        -fx-background-radius: 6;
                    """);

        } else {
            // Campo de mensagem desabilitado com texto de desabilitado.
            campoMensagem.setDisable(true);
            campoMensagem.setPromptText("Não é possível digitar mensagem enquanto está servidor.offline...");

            // Botão enviar desabilitado com texto e estilos de desabilitado.
            btnEnviar.setDisable(true);
            btnEnviar.setText("Offline 🔒");
            btnEnviar.setStyle("""
                        -fx-background-color: #555;
                        -fx-text-fill: #CCC;
                        -fx-font-size: 14px;
                        -fx-font-weight: bold;
                        -fx-background-radius: 6;
                    """);
        }
    }

    // Quando usuário está offline, botão de adicionar contato é desabilitado.
    // Quando usuário está online, botão de adicionar contato é habilitado.
    private void atualizarAdicionarContato() {
        if (usuario.isOnline()) {
            // Botão adicionar contato com estilos padrão.
            btnAdicionar.setDisable(false);
            btnAdicionar.setStyle("""
            -fx-background-color: #274B7A;
            -fx-text-fill: #e0e0e0;
            -fx-font-size: 14px;
            -fx-font-weight: bold;
            -fx-background-radius: 6;
            -fx-cursor: hand;
        """);

        } else {
            // Botão adicionar contato desabilitado com estilos de desabilitado.
            btnAdicionar.setDisable(true);
            btnAdicionar.setStyle("""
            -fx-background-color: #555;
            -fx-text-fill: #AAA;
            -fx-font-size: 14px;
            -fx-font-weight: bold;
            -fx-background-radius: 6;
        """);
        }
    }

    private void mostrarAlerta (String titulo, String mensagem) {
        Alert alerta = new Alert(Alert.AlertType.ERROR);
        alerta.setTitle(titulo);
        alerta.setHeaderText(null);
        alerta.setContentText(mensagem);
        alerta.initOwner(primaryStage);
        alerta.show();
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public static void main(String[] args) {
        launch();
    }
}