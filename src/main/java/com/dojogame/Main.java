package com.dojogame;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

import java.util.HashSet;
import java.util.Set;

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

    /**
     * Método executado quando o JavaFX é iniciado.
     *
     * @param janela janela principal do jogo.
     */
    @Override
    public void start(Stage janela) {

        // Cria o objeto responsável por interpretar arquivos do Tiled.
        CarregadorMapa carregadorMapa = new CarregadorMapa();

        /*
         * Carrega todas as camadas de tiles do labirinto
         * e as desenha em um Canvas do JavaFX.
         */
        Canvas visualMapa =
                carregadorMapa.carregarVisualMapa("entrada_labirinto.tmx");

        // Painel que receberá todos os elementos visuais do jogo.
        Pane raiz = new Pane();

        // Faz o tamanho do painel acompanhar o tamanho completo do mapa.
        raiz.setPrefSize(visualMapa.getWidth(), visualMapa.getHeight());

        /*
         * O mapa precisa ser adicionado antes do jogador.
         * Assim, o jogador será desenhado por cima dos tiles.
         */
        raiz.getChildren().add(visualMapa);

        /*
         * Cria um jogador provisório.
         *
         * Ele possui 32 pixels de largura e 32 de altura,
         * o mesmo tamanho dos blocos utilizados no Tiled.
         */
        Rectangle jogador = new Rectangle(32, 32);

        // Define a cor provisória do jogador.
        jogador.setFill(Color.DODGERBLUE);

        /*
         * Coloca o jogador próximo ao ponto spawn_jogador
         * definido na entrada inferior do mapa do Tiled.
         */
        jogador.setX(609);
        jogador.setY(928);

        // Adiciona o jogador ao painel principal.
        raiz.getChildren().add(jogador);

        // Cria a cena com o mesmo tamanho em pixels do tilemap.
        Scene cena = new Scene(
                raiz,
                visualMapa.getWidth(),
                visualMapa.getHeight()
        );

        // Define uma cor escura temporária para o fundo.
        raiz.setStyle("-fx-background-color: #20252b;");

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

                // Atualiza o tempo usado no próximo quadro.
                tempoAnterior = tempoAtual;

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
