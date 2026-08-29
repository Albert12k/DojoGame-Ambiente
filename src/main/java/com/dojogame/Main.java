package com.dojogame;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Point2D;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Classe principal do jogo.
 *
 * Responsável por:
 * - Criar a janela;
 * - Criar o jogador provisório;
 * - Ler as teclas WASD;
 * - Atualizar a movimentação do jogador.
 */
public class Main extends Application {

    // Largura da área visível do jogo em pixels.
    private static final double LARGURA_JANELA = 960.0;

    // Altura da área visível do jogo em pixels.
    private static final double ALTURA_JANELA = 640.0;

    /*
     * Guarda todas as teclas que estão pressionadas.
     *
     * Usamos um conjunto porque o jogador pode pressionar
     * mais de uma tecla ao mesmo tempo, como W + D.
     */
    private final Set<KeyCode> teclasPressionadas = new HashSet<>();

    /*
     * Velocidade do jogador em pixels por segundo.
     *
     * Depois poderemos alterar esse valor ou transferi-lo
     * para uma classe própria chamada Jogador.
     */
    private static final double VELOCIDADE_JOGADOR = 220.0;

    // Tempo escondido necessário para os guardas abandonarem a perseguição.
    private static final double TEMPO_PARA_PERDER_JOGADOR = 3.0;

    /**
     * Método executado quando o JavaFX é iniciado.
     *
     * @param janela janela principal do jogo.
     */
    @Override
    public void start(Stage janela) {
        iniciarFase(
                janela,
                "entrada_labirinto.tmx",
                "saida_escadaria",
                false,
                100
        );
    }

    /**
     * Monta uma fase completa usando o mapa e a saída informados.
     */
    private void iniciarFase(
            Stage janela,
            String nomeMapa,
            String nomePontoSaida,
            boolean armazem,
            int vidaInicial
    ) {

        // Cria o objeto responsável por interpretar arquivos do Tiled.
        CarregadorMapa carregadorMapa = new CarregadorMapa();

        /*
         * Carrega todas as camadas de tiles do labirinto
         * e as desenha em um Canvas do JavaFX.
         */
        Canvas visualMapa =
                carregadorMapa.carregarVisualMapa(nomeMapa);

        /*
         * Também busca a posição da saída criada no Tiled.
         * Esse ponto será utilizado para detectar a chegada do jogador.
         */
        Point2D pontoSaida =
                carregadorMapa.obterPontoImportante(nomePontoSaida);

        /*
         * Painel que representa o mundo completo do jogo.
         *
         * O mapa e o jogador ficarão dentro dele. A câmera será simulada
         * movimentando este painel na direção contrária ao jogador.
         */
        Pane mundo = new Pane();

        // Faz o tamanho do mundo acompanhar o tamanho completo do mapa.
        mundo.setPrefSize(visualMapa.getWidth(), visualMapa.getHeight());

        /*
         * O mapa precisa ser adicionado antes do jogador.
         * Assim, o jogador será desenhado por cima dos tiles.
         */
        mundo.getChildren().add(visualMapa);

        /*
         * Cria um guarda provisório para cada rota desenhada no Tiled.
         * Eles são adicionados antes do jogador para ficarem atrás dele.
         */
        List<GuardaPatrulha> guardas = new ArrayList<>();

        carregadorMapa.obterRotasGuardas().forEach((nome, rota) -> {
            GuardaPatrulha guarda = new GuardaPatrulha(rota);
            guarda.setId(nome);
            guarda.atualizarCampoVisao(carregadorMapa);
            guardas.add(guarda);
            mundo.getChildren().add(guarda);
        });

        // Disparos existentes no mundo durante o estado de alerta.
        List<Projetil> projeteis = new ArrayList<>();

        // Shurikens que o jogador lançou e ainda estão voando.
        List<ShurikenJogador> shurikensLancadas = new ArrayList<>();

        // Baús desenhados como retângulos na camada ItensInterativos.
        List<CarregadorMapa.ObjetoInterativo> baus =
                carregadorMapa.obterItensInterativos().stream()
                        .filter(item -> item.nome().toLowerCase()
                                .startsWith("bau_"))
                        .toList();
        Set<String> bausAbertos = new HashSet<>();

        // Arrays permitem alterar esses valores dentro do AnimationTimer.
        boolean[] alertaAtivo = {false};
        boolean[] jogadorDerrotado = {false};
        boolean[] faseConcluida = {false};
        int[] vidaJogador = {vidaInicial};
        int[] shurikens = {0};
        int[] bombasFumaca = {0};
        int[] kitsMedicos = {0};
        boolean[] teclaEConsumida = {false};
        boolean[] teclaHConsumida = {false};
        boolean[] teclaQConsumida = {false};
        boolean[] teclaEspacoConsumida = {false};
        Point2D[] ultimaDirecaoJogador = {new Point2D(0, -1)};
        Circle[] nuvemFumaca = {null};
        double[] tempoFumaca = {0};
        double[] tempoMensagemColeta = {0};
        double[] tempoSemVerJogador = {0};
        Point2D[] ultimaPosicaoVista = {new Point2D(0, 0)};

        /*
         * Cria um jogador provisório.
         *
         * Ele possui 32 pixels de largura e 32 de altura,
         * o mesmo tamanho dos blocos utilizados no Tiled.
         */
        Rectangle jogador = new Rectangle(32, 32);

        // Define a cor provisória do jogador.
        jogador.setFill(Color.DODGERBLUE);

        // Busca no arquivo TMX o ponto criado com esse nome no Tiled.
        Point2D pontoSpawn =
                carregadorMapa.obterPontoImportante("spawn_jogador");

        /*
         * No Tiled, o ponto marca o centro inferior do personagem.
         * Por isso descontamos metade da largura e toda a altura.
         */
        double posicaoInicialX =
                pontoSpawn.getX() - jogador.getWidth() / 2.0;
        double posicaoInicialY =
                pontoSpawn.getY() - jogador.getHeight();

        /*
         * Também limitamos a posição ao tamanho do mapa, pois o marcador
         * pode estar exatamente sobre a borda inferior do cenário.
         */
        jogador.setX(Math.max(
                0,
                Math.min(
                        posicaoInicialX,
                        visualMapa.getWidth() - jogador.getWidth()
                )
        ));

        jogador.setY(Math.max(
                0,
                Math.min(
                        posicaoInicialY,
                        visualMapa.getHeight() - jogador.getHeight()
                )
        ));

        // Adiciona o jogador ao painel principal.
        mundo.getChildren().add(jogador);

        /*
         * Este painel representa somente a parte do mundo que aparece
         * dentro da janela. Ele funciona como a área visível da câmera.
         */
        Pane areaVisivel = new Pane();
        areaVisivel.getChildren().add(mundo);

        /*
         * Aviso exibido quando o jogador alcança a saída do labirinto.
         * Como ele pertence à área visível, e não ao mundo, permanece
         * parado na tela mesmo enquanto a câmera se movimenta.
         */
        Label avisoSaida = new Label(
                armazem
                        ? "Saída encontrada — pressione E para continuar"
                        : "Escadaria encontrada — pressione E para entrar"
        );

        // Define uma aparência provisória de interface para o aviso.
        avisoSaida.setStyle(
                "-fx-background-color: rgba(15, 18, 22, 0.90);"
                        + "-fx-text-fill: white;"
                        + "-fx-font-size: 18px;"
                        + "-fx-padding: 12px 18px;"
                        + "-fx-background-radius: 8px;"
        );

        // Posiciona a mensagem no canto superior esquerdo da janela.
        avisoSaida.setLayoutX(20);
        avisoSaida.setLayoutY(20);

        // O aviso começa escondido e não interfere com o mouse.
        avisoSaida.setVisible(false);
        avisoSaida.setMouseTransparent(true);

        // Adiciona depois do mundo para desenhá-lo por cima do mapa.
        areaVisivel.getChildren().add(avisoSaida);

        Label avisoAlerta = new Label("ALERTA: os guardas viram você!");
        avisoAlerta.setStyle(
                "-fx-background-color: rgba(120, 0, 0, 0.90);"
                        + "-fx-text-fill: white;"
                        + "-fx-font-size: 18px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-padding: 10px 16px;"
                        + "-fx-background-radius: 8px;"
        );
        avisoAlerta.setLayoutX(20);
        avisoAlerta.setLayoutY(76);
        avisoAlerta.setVisible(false);
        avisoAlerta.setMouseTransparent(true);

        Label indicadorVida =
                new Label("Vida: " + vidaJogador[0]);
        indicadorVida.setStyle(
                "-fx-background-color: rgba(15, 18, 22, 0.90);"
                        + "-fx-text-fill: white;"
                        + "-fx-font-size: 18px;"
                        + "-fx-padding: 10px 16px;"
                        + "-fx-background-radius: 8px;"
        );
        indicadorVida.setLayoutX(LARGURA_JANELA - 130);
        indicadorVida.setLayoutY(20);
        indicadorVida.setMouseTransparent(true);


        Label avisoBau = new Label("Pressione E para abrir o baú");
        avisoBau.setStyle(
                "-fx-background-color: rgba(92, 61, 16, 0.94);"
                        + "-fx-text-fill: #ffe4a3;"
                        + "-fx-font-size: 18px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-padding: 10px 16px;"
                        + "-fx-background-radius: 8px;"
        );
        avisoBau.setLayoutX(20);
        avisoBau.setLayoutY(132);
        avisoBau.setVisible(false);
        avisoBau.setMouseTransparent(true);

        Label indicadorInventario =
                new Label("Shurikens: 0  |  Fumaça: 0  |  Kits: 0");
        indicadorInventario.setStyle(
                "-fx-background-color: rgba(15, 18, 22, 0.90);"
                        + "-fx-text-fill: white;"
                        + "-fx-font-size: 15px;"
                        + "-fx-padding: 9px 13px;"
                        + "-fx-background-radius: 8px;"
        );
        indicadorInventario.setLayoutX(LARGURA_JANELA - 350);
        indicadorInventario.setLayoutY(76);
        indicadorInventario.setVisible(armazem);
        indicadorInventario.setMouseTransparent(true);


        Label controlesItens =
                new Label(
                        "Espaço: shuriken  |  H: kit  |  Q: fumaça"
                );
        controlesItens.setStyle(
                "-fx-background-color: rgba(15, 18, 22, 0.82);"
                        + "-fx-text-fill: #d8dde6;"
                        + "-fx-font-size: 13px;"
                        + "-fx-padding: 7px 11px;"
                        + "-fx-background-radius: 7px;"
        );
        controlesItens.setLayoutX(LARGURA_JANELA - 350);
        controlesItens.setLayoutY(121);
        controlesItens.setVisible(armazem);
        controlesItens.setMouseTransparent(true);

        Label avisoColeta = new Label();
        avisoColeta.setStyle(
                "-fx-background-color: rgba(20, 82, 48, 0.94);"
                        + "-fx-text-fill: white;"
                        + "-fx-font-size: 18px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-padding: 10px 16px;"
                        + "-fx-background-radius: 8px;"
        );
        avisoColeta.setLayoutX(20);
        avisoColeta.setLayoutY(188);
        avisoColeta.setVisible(false);
        avisoColeta.setMouseTransparent(true);

        /*
         * Painel central usado tanto ao concluir o labirinto quanto ao perder.
         * Ele fica fora do mundo para não se mover junto com a câmera.
         */
        Label painelResultado = new Label();
        painelResultado.setStyle(
                "-fx-background-color: rgba(15, 18, 22, 0.95);"
                        + "-fx-text-fill: white;"
                        + "-fx-font-size: 22px;"
                        + "-fx-font-weight: bold;"
                        + "-fx-alignment: center;"
                        + "-fx-text-alignment: center;"
                        + "-fx-padding: 24px;"
                        + "-fx-background-radius: 12px;"
                        + "-fx-border-color: rgba(255, 255, 255, 0.25);"
                        + "-fx-border-radius: 12px;"
        );
        painelResultado.setLayoutX((LARGURA_JANELA - 460) / 2.0);
        painelResultado.setLayoutY((ALTURA_JANELA - 190) / 2.0);
        painelResultado.setPrefSize(460, 190);
        painelResultado.setVisible(false);
        painelResultado.setMouseTransparent(true);

        areaVisivel.getChildren().addAll(
                avisoAlerta,
                indicadorVida,
                indicadorInventario,
                controlesItens,
                avisoBau,
                avisoColeta,
                painelResultado
        );

        // Cria uma janela menor que o mapa para permitir o uso da câmera.
        Scene cena = new Scene(areaVisivel, LARGURA_JANELA, ALTURA_JANELA);

        // Define uma cor escura temporária para o fundo.
        areaVisivel.setStyle("-fx-background-color: #20252b;");

        /*
         * Quando uma tecla for pressionada,
         * ela será adicionada ao conjunto.
         */
        cena.setOnKeyPressed(evento ->
                teclasPressionadas.add(evento.getCode())
        );

        /*
         * Quando a tecla for solta,
         * ela será removida do conjunto.
         */
        cena.setOnKeyReleased(evento ->
                teclasPressionadas.remove(evento.getCode())
        );

        /*
         * Controla a atualização contínua do jogo.
         *
         * O método handle() é chamado várias vezes por segundo,
         * permitindo uma movimentação suave.
         */
        AnimationTimer loopDoJogo = new AnimationTimer() {

            // Armazena o instante em que o quadro anterior foi executado.
            private long tempoAnterior = 0;

            // Evita recalcular os caminhos em todos os quadros.
            private double tempoParaRecalcularPerseguicao = 0;

            @Override
            public void handle(long tempoAtual) {

                /*
                 * Na primeira execução ainda não existe um quadro anterior.
                 * Por isso apenas guardamos o tempo atual.
                 */
                if (tempoAnterior == 0) {
                    tempoAnterior = tempoAtual;
                    return;
                }

                /*
                 * Calcula quantos segundos passaram desde o quadro anterior.
                 *
                 * O JavaFX fornece o tempo em nanossegundos,
                 * então dividimos por 1 bilhão.
                 */
                double tempoDecorrido =
                        (tempoAtual - tempoAnterior) / 1_000_000_000.0;

                // Evita saltos grandes quando a janela fica momentaneamente parada.
                tempoDecorrido = Math.min(tempoDecorrido, 0.05);

                // Atualiza o tempo usado no próximo quadro.
                tempoAnterior = tempoAtual;

                /*
                 * Depois da vitória ou derrota, R recria a fase inteira.
                 * O loop antigo é encerrado antes de montar a nova cena.
                 */
                if ((faseConcluida[0] || jogadorDerrotado[0])
                        && teclasPressionadas.contains(KeyCode.R)) {
                    stop();
                    teclasPressionadas.clear();
                    iniciarFase(
                            janela,
                            nomeMapa,
                            nomePontoSaida,
                            armazem,
                            100
                    );
                    return;
                }

                // Controla o tempo da mensagem exibida depois de abrir um baú.
                if (tempoMensagemColeta[0] > 0) {
                    tempoMensagemColeta[0] -= tempoDecorrido;
                    avisoColeta.setVisible(true);
                } else {
                    avisoColeta.setVisible(false);
                }

                boolean teclaEPressionada =
                        teclasPressionadas.contains(KeyCode.E);

                if (!teclaEPressionada) {
                    teclaEConsumida[0] = false;
                }
                if (!teclasPressionadas.contains(KeyCode.H)) {
                    teclaHConsumida[0] = false;
                }
                if (!teclasPressionadas.contains(KeyCode.Q)) {
                    teclaQConsumida[0] = false;
                }
                if (!teclasPressionadas.contains(KeyCode.SPACE)) {
                    teclaEspacoConsumida[0] = false;
                }

                // Congela guardas, disparos e jogador enquanto o resultado aparece.
                if (faseConcluida[0] || jogadorDerrotado[0]) {
                    return;
                }


                // A nuvem permanece no lugar em que a bomba foi lançada.
                if (nuvemFumaca[0] != null) {
                    tempoFumaca[0] -= tempoDecorrido;

                    if (tempoFumaca[0] <= 0) {
                        mundo.getChildren().remove(nuvemFumaca[0]);
                        nuvemFumaca[0] = null;
                    }
                }

                if (armazem
                        && teclasPressionadas.contains(KeyCode.H)
                        && !teclaHConsumida[0]
                        && kitsMedicos[0] > 0
                        && vidaJogador[0] < 100) {
                    teclaHConsumida[0] = true;
                    kitsMedicos[0]--;
                    vidaJogador[0] = Math.min(100, vidaJogador[0] + 30);
                    indicadorVida.setText("Vida: " + vidaJogador[0]);
                    indicadorInventario.setText(
                            "Shurikens: " + shurikens[0]
                                    + "  |  Fumaça: " + bombasFumaca[0]
                                    + "  |  Kits: " + kitsMedicos[0]
                    );
                    avisoColeta.setText("Kit médico usado: +30 de vida");
                    tempoMensagemColeta[0] = 2.5;
                }

                if (armazem
                        && teclasPressionadas.contains(KeyCode.Q)
                        && !teclaQConsumida[0]
                        && bombasFumaca[0] > 0
                        && nuvemFumaca[0] == null) {
                    teclaQConsumida[0] = true;
                    bombasFumaca[0]--;

                    Point2D centroFumaca = centroDoJogador(jogador);
                    Circle novaNuvem = new Circle(
                            centroFumaca.getX(),
                            centroFumaca.getY(),
                            72,
                            Color.rgb(125, 132, 140, 0.58)
                    );
                    novaNuvem.setStroke(Color.rgb(205, 210, 215, 0.68));
                    novaNuvem.setStrokeWidth(3);
                    novaNuvem.setMouseTransparent(true);
                    nuvemFumaca[0] = novaNuvem;
                    tempoFumaca[0] = 5.0;
                    mundo.getChildren().add(novaNuvem);

                    indicadorInventario.setText(
                            "Shurikens: " + shurikens[0]
                                    + "  |  Fumaça: " + bombasFumaca[0]
                                    + "  |  Kits: " + kitsMedicos[0]
                    );
                    avisoColeta.setText(
                            "Bomba de fumaça usada: visão bloqueada por 5s"
                    );
                    tempoMensagemColeta[0] = 2.5;
                }


                if (armazem
                        && teclasPressionadas.contains(KeyCode.SPACE)
                        && !teclaEspacoConsumida[0]
                        && shurikens[0] > 0) {
                    teclaEspacoConsumida[0] = true;
                    shurikens[0]--;

                    ShurikenJogador shuriken = new ShurikenJogador(
                            centroDoJogador(jogador),
                            ultimaDirecaoJogador[0]
                    );
                    shurikensLancadas.add(shuriken);
                    mundo.getChildren().add(shuriken);

                    indicadorInventario.setText(
                            "Shurikens: " + shurikens[0]
                                    + "  |  Fumaça: " + bombasFumaca[0]
                                    + "  |  Kits: " + kitsMedicos[0]
                    );
                }

                boolean jogadorProtegidoPelaFumaca =
                        nuvemFumaca[0] != null
                                && centroDoJogador(jogador).distance(
                                        nuvemFumaca[0].getCenterX(),
                                        nuvemFumaca[0].getCenterY()
                                ) <= nuvemFumaca[0].getRadius();

                if (!alertaAtivo[0]) {
                    // Alguns podem ainda estar voltando para suas rotas.
                    for (GuardaPatrulha guarda : guardas) {
                        if (guarda.estaRetornandoPatrulha()) {
                            guarda.atualizarRetornoPatrulha(tempoDecorrido);
                        } else {
                            guarda.atualizarPatrulha(tempoDecorrido);
                        }
                    }

                    boolean jogadorFoiVisto =
                            !jogadorProtegidoPelaFumaca
                                    && guardas.stream().anyMatch(
                            guarda -> guarda.consegueVer(
                                    jogador,
                                    carregadorMapa
                            )
                    );

                    if (jogadorFoiVisto) {
                        alertaAtivo[0] = true;
                        tempoSemVerJogador[0] = 0;
                        tempoParaRecalcularPerseguicao = 0;
                        ultimaPosicaoVista[0] = centroDoJogador(jogador);
                        avisoAlerta.setText(
                                "ALERTA: os guardas viram você!"
                        );
                        avisoAlerta.setVisible(true);

                        for (GuardaPatrulha guarda : guardas) {
                            guarda.ativarAlerta();
                        }
                    }
                } else {
                    boolean jogadorVisivelAgora =
                            !jogadorProtegidoPelaFumaca
                                    && guardas.stream().anyMatch(
                            guarda -> guarda.consegueVer(
                                    jogador,
                                    carregadorMapa
                            )
                    );

                    if (jogadorVisivelAgora) {
                        tempoSemVerJogador[0] = 0;
                        ultimaPosicaoVista[0] = centroDoJogador(jogador);
                        avisoAlerta.setText(
                                "ALERTA: os guardas viram você!"
                        );
                    } else {
                        tempoSemVerJogador[0] += tempoDecorrido;
                        avisoAlerta.setText(
                                "Os guardas estão procurando você..."
                        );
                    }

                    if (tempoSemVerJogador[0]
                            >= TEMPO_PARA_PERDER_JOGADOR) {
                        alertaAtivo[0] = false;
                        avisoAlerta.setVisible(false);

                        for (GuardaPatrulha guarda : guardas) {
                            Point2D pontoDeRetorno =
                                    guarda.obterPontoPatrulhaMaisProximo();
                            guarda.iniciarRetornoPatrulha(
                                    carregadorMapa.encontrarCaminho(
                                            guarda.obterPosicao(),
                                            pontoDeRetorno
                                    ),
                                    pontoDeRetorno
                            );
                        }
                    } else {
                        tempoParaRecalcularPerseguicao -= tempoDecorrido;

                        if (tempoParaRecalcularPerseguicao <= 0) {
                            List<Point2D> posicoesAoRedor =
                                    carregadorMapa.encontrarPosicoesAoRedor(
                                            ultimaPosicaoVista[0],
                                            guardas.size()
                                    );

                            for (int indice = 0;
                                 indice < guardas.size();
                                 indice++) {
                                GuardaPatrulha guarda = guardas.get(indice);
                                Point2D destino = posicoesAoRedor.get(
                                        indice % posicoesAoRedor.size()
                                );
                                guarda.definirCaminhoPerseguicao(
                                        carregadorMapa.encontrarCaminho(
                                                guarda.obterPosicao(),
                                                destino
                                        )
                                );
                            }

                            tempoParaRecalcularPerseguicao = 0.35;
                        }

                        for (GuardaPatrulha guarda : guardas) {
                            guarda.atualizarPerseguicao(
                                    tempoDecorrido,
                                    jogadorVisivelAgora
                            );

                            if (!jogadorDerrotado[0]
                                    && !jogadorProtegidoPelaFumaca
                                    && guarda.podeDisparar(
                                            jogador,
                                            carregadorMapa,
                                            tempoDecorrido
                                    )) {
                                Projetil projetil = new Projetil(
                                        guarda.obterPosicao(),
                                        centroDoJogador(jogador)
                                );
                                projeteis.add(projetil);
                                mundo.getChildren().add(projetil);
                            }
                        }
                    }
                }

                /*
                 * Atualiza o desenho somente depois que todos os guardas se
                 * moveram ou viraram, evitando um cone visual atrasado.
                 */
                for (GuardaPatrulha guarda : guardas) {
                    guarda.atualizarCampoVisao(carregadorMapa);
                }

                // Atualiza disparos, colisões com paredes e dano no jogador.
                Iterator<Projetil> iteradorProjeteis = projeteis.iterator();

                while (iteradorProjeteis.hasNext()) {
                    Projetil projetil = iteradorProjeteis.next();
                    projetil.atualizar(tempoDecorrido);
                    boolean atingiuJogador = !jogadorDerrotado[0]
                            && projetil.atingiu(jogador);

                    if (atingiuJogador) {
                        vidaJogador[0] = Math.max(0, vidaJogador[0] - 10);
                        indicadorVida.setText("Vida: " + vidaJogador[0]);

                        if (vidaJogador[0] == 0) {
                            jogadorDerrotado[0] = true;
                            avisoAlerta.setVisible(false);
                            avisoSaida.setVisible(false);
                            painelResultado.setText(
                                    "Você foi derrotado\n\n"
                                            + "Pressione R para tentar novamente"
                            );
                            painelResultado.setVisible(true);
                            teclasPressionadas.clear();
                        }
                    }

                    if (atingiuJogador
                            || projetil.deveSerRemovido(carregadorMapa)) {
                        mundo.getChildren().remove(projetil);
                        iteradorProjeteis.remove();
                    }
                }


                // Atualiza as shurikens e remove o guarda atingido.
                Iterator<ShurikenJogador> iteradorShurikens =
                        shurikensLancadas.iterator();

                while (iteradorShurikens.hasNext()) {
                    ShurikenJogador shuriken = iteradorShurikens.next();
                    shuriken.atualizar(tempoDecorrido);
                    boolean atingiuGuarda = false;

                    Iterator<GuardaPatrulha> iteradorGuardas =
                            guardas.iterator();

                    while (iteradorGuardas.hasNext()) {
                        GuardaPatrulha guarda = iteradorGuardas.next();

                        if (shuriken.atingiu(guarda)) {
                            mundo.getChildren().remove(guarda);
                            iteradorGuardas.remove();
                            atingiuGuarda = true;
                            avisoColeta.setText("Guarda nocauteado!");
                            tempoMensagemColeta[0] = 2.5;
                            break;
                        }
                    }

                    if (atingiuGuarda
                            || shuriken.deveSerRemovida(carregadorMapa)) {
                        mundo.getChildren().remove(shuriken);
                        iteradorShurikens.remove();
                    }
                }

                // Direção horizontal do jogador.
                double direcaoX = 0;

                // Direção vertical do jogador.
                double direcaoY = 0;

                // W movimenta o jogador para cima.
                if (teclasPressionadas.contains(KeyCode.W)) {
                    direcaoY -= 1;
                }

                // S movimenta o jogador para baixo.
                if (teclasPressionadas.contains(KeyCode.S)) {
                    direcaoY += 1;
                }

                // A movimenta o jogador para a esquerda.
                if (teclasPressionadas.contains(KeyCode.A)) {
                    direcaoX -= 1;
                }

                // D movimenta o jogador para a direita.
                if (teclasPressionadas.contains(KeyCode.D)) {
                    direcaoX += 1;
                }

                // Depois de perder toda a vida, o jogador não se movimenta.
                if (jogadorDerrotado[0]) {
                    direcaoX = 0;
                    direcaoY = 0;
                }

                /*
                 * Impede que a movimentação diagonal seja mais rápida.
                 *
                 * Por exemplo, W + D movimenta simultaneamente
                 * para cima e para a direita.
                 */
                if (direcaoX != 0 && direcaoY != 0) {
                    double ajusteDiagonal = Math.sqrt(2);

                    direcaoX /= ajusteDiagonal;
                    direcaoY /= ajusteDiagonal;
                }

                if (direcaoX != 0 || direcaoY != 0) {
                    ultimaDirecaoJogador[0] =
                            new Point2D(direcaoX, direcaoY).normalize();
                }

                /*
                 * Calcula a nova posição usando:
                 *
                 * direção × velocidade × tempo decorrido.
                 */
                double novaPosicaoX = jogador.getX()
                        + direcaoX * VELOCIDADE_JOGADOR * tempoDecorrido;

                double novaPosicaoY = jogador.getY()
                        + direcaoY * VELOCIDADE_JOGADOR * tempoDecorrido;

                /*
                 * Limita a posição horizontal para impedir
                 * que o jogador saia da janela.
                 */
                novaPosicaoX = Math.max(
                        0,
                        Math.min(novaPosicaoX,
                                visualMapa.getWidth() - jogador.getWidth())
                );

                /*
                 * Limita a posição vertical para impedir
                 * que o jogador saia da janela.
                 */
                novaPosicaoY = Math.max(
                        0,
                        Math.min(novaPosicaoY,
                                visualMapa.getHeight() - jogador.getHeight())
                );

                /*
                 * Testa primeiro o movimento horizontal.
                 * Separar os dois eixos permite que o jogador deslize
                 * ao longo de uma parede em vez de ficar completamente preso.
                 */
                if (!carregadorMapa.possuiColisao(
                        novaPosicaoX,
                        jogador.getY(),
                        jogador.getWidth(),
                        jogador.getHeight()
                )) {
                    jogador.setX(novaPosicaoX);
                }

                // Depois testa o movimento vertical usando o novo X aprovado.
                if (!carregadorMapa.possuiColisao(
                        jogador.getX(),
                        novaPosicaoY,
                        jogador.getWidth(),
                        jogador.getHeight()
                )) {
                    jogador.setY(novaPosicaoY);
                }

                /*
                 * Reposiciona o mundo depois de atualizar o jogador.
                 * Isso produz o efeito de uma câmera acompanhando-o.
                 */
                atualizarCamera(mundo, jogador, cena, visualMapa);


                CarregadorMapa.ObjetoInterativo bauProximo =
                        encontrarBauProximo(jogador, baus, bausAbertos);
                avisoBau.setVisible(bauProximo != null);

                if (bauProximo != null
                        && teclaEPressionada
                        && !teclaEConsumida[0]) {
                    teclaEConsumida[0] = true;
                    bausAbertos.add(bauProximo.nome());

                    int sorteio = ThreadLocalRandom.current().nextInt(100);
                    String mensagemColeta;

                    if (sorteio < 50) {
                        int quantidade =
                                ThreadLocalRandom.current().nextInt(3, 8);
                        shurikens[0] += quantidade;
                        mensagemColeta =
                                "Baú aberto: +" + quantidade + " shurikens";
                    } else if (sorteio < 80) {
                        int quantidade =
                                ThreadLocalRandom.current().nextInt(1, 3);
                        bombasFumaca[0] += quantidade;
                        mensagemColeta =
                                "Baú aberto: +" + quantidade
                                        + " bomba(s) de fumaça";
                    } else {
                        kitsMedicos[0]++;
                        mensagemColeta =
                                "Baú aberto: +1 kit médico";
                    }

                    indicadorInventario.setText(
                            "Shurikens: " + shurikens[0]
                                    + "  |  Fumaça: " + bombasFumaca[0]
                                    + "  |  Kits: " + kitsMedicos[0]
                    );
                    avisoColeta.setText(mensagemColeta);
                    tempoMensagemColeta[0] = 2.5;
                    avisoBau.setVisible(false);
                }

                /*
                 * Verifica continuamente se o jogador chegou perto
                 * do ponto de saída definido no Tiled.
                 */
                boolean jogadorNaSaida = atualizarAvisoSaida(
                        jogador,
                        pontoSaida,
                        avisoSaida
                );

                if (jogadorNaSaida
                        && bauProximo == null
                        && teclaEPressionada
                        && !teclaEConsumida[0]) {
                    teclaEConsumida[0] = true;
                    avisoSaida.setVisible(false);
                    avisoAlerta.setVisible(false);
                    avisoBau.setVisible(false);

                    if (!armazem) {
                        stop();
                        teclasPressionadas.clear();
                        iniciarFase(
                                janela,
                                "armazem_dojo.tmx",
                                "saida_armazem",
                                true,
                                vidaJogador[0]
                        );
                        return;
                    }

                    faseConcluida[0] = true;
                    painelResultado.setText(
                            "Armazém concluído!\n\n"
                                    + "Todos os itens coletados ficam "
                                    + "no inventário.\n"
                                    + "Pressione R para explorar novamente"
                    );
                    painelResultado.setVisible(true);
                    teclasPressionadas.clear();
                }
            }
        };

        // Inicia o ciclo de atualização do jogo.
        loopDoJogo.start();

        // Configura e exibe a janela.
        janela.setTitle("Hangetsu: Shadow Dojo");
        janela.setScene(cena);

        // Mantém a janela no tamanho exato do mapa durante este primeiro teste.
        janela.setResizable(false);

        janela.show();

        // Posiciona a câmera corretamente antes do primeiro movimento.
        atualizarCamera(mundo, jogador, cena, visualMapa);

        // Confere também a saída antes do primeiro quadro do jogo.
        atualizarAvisoSaida(jogador, pontoSaida, avisoSaida);
    }


    /**
     * Encontra o baú fechado mais próximo do jogador.
     */
    private CarregadorMapa.ObjetoInterativo encontrarBauProximo(
            Rectangle jogador,
            List<CarregadorMapa.ObjetoInterativo> baus,
            Set<String> bausAbertos
    ) {
        Point2D centroJogador = centroDoJogador(jogador);
        CarregadorMapa.ObjetoInterativo maisProximo = null;
        double menorDistancia = Double.MAX_VALUE;

        for (CarregadorMapa.ObjetoInterativo bau : baus) {
            if (bausAbertos.contains(bau.nome())) {
                continue;
            }

            double distancia = centroJogador.distance(bau.centro());

            if (distancia <= 56.0 && distancia < menorDistancia) {
                menorDistancia = distancia;
                maisProximo = bau;
            }
        }

        return maisProximo;
    }

    private Point2D centroDoJogador(Rectangle jogador) {
        return new Point2D(
                jogador.getX() + jogador.getWidth() / 2.0,
                jogador.getY() + jogador.getHeight() / 2.0
        );
    }

    /**
     * Exibe ou esconde o aviso da saída conforme a distância do jogador.
     *
     * @param jogador representação provisória do jogador.
     * @param pontoSaida posição da saída carregada do Tiled.
     * @param avisoSaida mensagem mostrada na interface.
     */
    private boolean atualizarAvisoSaida(
            Rectangle jogador,
            Point2D pontoSaida,
            Label avisoSaida
    ) {
        // Usa o centro do jogador para medir a distância até a saída.
        Point2D centroJogador = new Point2D(
                jogador.getX() + jogador.getWidth() / 2.0,
                jogador.getY() + jogador.getHeight() / 2.0
        );

        /*
         * A saída é considerada alcançada dentro de um raio de 48 pixels,
         * equivalente a um bloco e meio do nosso mapa.
         */
        double distanciaAteSaida = centroJogador.distance(pontoSaida);
        boolean jogadorChegouNaSaida = distanciaAteSaida <= 48.0;

        avisoSaida.setVisible(jogadorChegouNaSaida);
        return jogadorChegouNaSaida;
    }

    /**
     * Move o painel do mundo para manter o jogador visível.
     *
     * A câmera tenta centralizar o jogador, mas é limitada pelas bordas
     * do mapa para nunca mostrar uma região vazia fora do cenário.
     *
     * @param mundo painel que contém o mapa e os personagens.
     * @param jogador representação provisória do jogador.
     * @param cena área visível da janela.
     * @param visualMapa Canvas que informa o tamanho completo do mapa.
     */
    private void atualizarCamera(
            Pane mundo,
            Rectangle jogador,
            Scene cena,
            Canvas visualMapa
    ) {
        // Centro atual do jogador nas coordenadas do mapa.
        double centroJogadorX = jogador.getX() + jogador.getWidth() / 2.0;
        double centroJogadorY = jogador.getY() + jogador.getHeight() / 2.0;

        /*
         * Calcula quanto o mundo precisa ser deslocado para que o centro
         * do jogador coincida com o centro da área visível.
         */
        double deslocamentoX = cena.getWidth() / 2.0 - centroJogadorX;
        double deslocamentoY = cena.getHeight() / 2.0 - centroJogadorY;

        /*
         * Estes são os menores deslocamentos permitidos. Por exemplo,
         * quando a câmera chega ao lado direito, ela não pode continuar
         * movendo o mapa para a esquerda e revelar uma área vazia.
         */
        double limiteMinimoX = cena.getWidth() - visualMapa.getWidth();
        double limiteMinimoY = cena.getHeight() - visualMapa.getHeight();

        // Limita a câmera horizontalmente entre as duas bordas do mapa.
        deslocamentoX = Math.max(
                Math.min(0, limiteMinimoX),
                Math.min(deslocamentoX, 0)
        );

        // Limita a câmera verticalmente entre as duas bordas do mapa.
        deslocamentoY = Math.max(
                Math.min(0, limiteMinimoY),
                Math.min(deslocamentoY, 0)
        );

        // Aplica o deslocamento calculado a todo o mundo do jogo.
        mundo.setTranslateX(deslocamentoX);
        mundo.setTranslateY(deslocamentoY);
    }

    /**
     * Ponto de entrada do programa.
     *
     * @param args argumentos da execução.
     */
    public static void main(String[] args) {

        // Inicia o JavaFX e chama o método start().
        launch(args);
    }
}
