package com.dojogame;

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;

import java.util.List;

/** Guarda provisório com patrulha, visão e perseguição. */
public class GuardaPatrulha extends Pane {

    // Fora do alerta, os guardas deixam mais espaço para infiltração.
    private static final double VELOCIDADE_PATRULHA = 48.0;
    private static final double VELOCIDADE_BUSCA = 72.0;
    private static final double VELOCIDADE_RETORNO = 55.0;

    // Ao enxergar o jogador, eles continuam perigosos sem serem instantâneos.
    private static final double VELOCIDADE_PERSEGUICAO = 125.0;
    private static final double ALCANCE_VISAO = 190.0;
    private static final double METADE_ANGULO_VISAO = 32.0;
    private static final int QUANTIDADE_RAIOS_VISAO = 32;
    private static final double INTERVALO_DISPARO = 1.0;

    private final List<Point2D> rotaPatrulha;
    private final Polygon coneVisao;
    private final Rotate rotacaoVisao = new Rotate(0, 0, 0);
    private final Label exclamacao;

    private List<Point2D> caminhoPerseguicao = List.of();
    private int indicePatrulha = 1;
    private int sentidoPatrulha = 1;
    private int indicePerseguicao = 1;
    private boolean emAlerta;
    private boolean retornandoPatrulha;
    private int indiceRotaRetorno;
    private double tempoAteDisparo;
    private Point2D direcaoOlhar = new Point2D(1, 0);

    public GuardaPatrulha(List<Point2D> rota) {
        if (rota == null || rota.size() < 2) {
            throw new IllegalArgumentException(
                    "A rota do guarda precisa possuir pelo menos dois pontos."
            );
        }

        rotaPatrulha = List.copyOf(rota);

        /*
         * O cone é um polígono reconstruído por pequenos raios. Isso permite
         * que cada trecho pare exatamente ao encontrar uma árvore ou parede.
         */
        coneVisao = new Polygon();
        coneVisao.setFill(Color.rgb(255, 220, 70, 0.14));
        coneVisao.setStroke(Color.rgb(255, 220, 70, 0.55));
        coneVisao.setMouseTransparent(true);

        Line indicadorDirecao = new Line(0, 0, 17, 0);
        indicadorDirecao.setStroke(Color.rgb(70, 35, 10));
        indicadorDirecao.setStrokeWidth(3);

        Group visualDaDirecao = new Group(coneVisao, indicadorDirecao);
        visualDaDirecao.getTransforms().add(rotacaoVisao);
        visualDaDirecao.setMouseTransparent(true);

        Circle corpo = new Circle(0, 0, 11, Color.DARKORANGE);
        corpo.setStroke(Color.rgb(35, 24, 15));
        corpo.setStrokeWidth(2);

        exclamacao = new Label("!");
        exclamacao.setStyle(
                "-fx-text-fill: #ff2020;"
                        + "-fx-font-size: 25px;"
                        + "-fx-font-weight: bold;"
        );
        exclamacao.setLayoutX(-5);
        exclamacao.setLayoutY(-39);
        exclamacao.setVisible(false);
        exclamacao.setMouseTransparent(true);

        getChildren().addAll(visualDaDirecao, corpo, exclamacao);
        setManaged(false);
        setMouseTransparent(true);

        Point2D inicio = rotaPatrulha.get(0);
        setLayoutX(inicio.getX());
        setLayoutY(inicio.getY());
        atualizarDirecao(rotaPatrulha.get(1).subtract(inicio));
    }

    public void atualizarPatrulha(double tempoDecorrido) {
        if (emAlerta || retornandoPatrulha) {
            return;
        }

        double distanciaRestante = VELOCIDADE_PATRULHA * tempoDecorrido;

        while (distanciaRestante > 0) {
            Point2D destino = rotaPatrulha.get(indicePatrulha);
            double sobra = moverEmDirecao(destino, distanciaRestante);

            if (sobra < 0) {
                return;
            }

            distanciaRestante = sobra;
            escolherProximoDestinoPatrulha();
        }
    }

    public void ativarAlerta() {
        emAlerta = true;
        retornandoPatrulha = false;
        exclamacao.setVisible(true);
        coneVisao.setFill(Color.rgb(255, 40, 40, 0.12));
        coneVisao.setStroke(Color.rgb(255, 40, 40, 0.50));
        tempoAteDisparo = 0;
    }

    /** Recebe um caminho atualizado em direção ao redor do jogador. */
    public void definirCaminhoPerseguicao(List<Point2D> caminho) {
        caminhoPerseguicao = List.copyOf(caminho);
        indicePerseguicao = Math.min(1, caminhoPerseguicao.size());
    }

    public void atualizarPerseguicao(
            double tempoDecorrido,
            boolean jogadorVisivel
    ) {
        if (!emAlerta) {
            return;
        }

        double velocidade = jogadorVisivel
                ? VELOCIDADE_PERSEGUICAO
                : VELOCIDADE_BUSCA;
        atualizarCaminhoAtual(tempoDecorrido, velocidade);
    }

    /**
     * Faz o guarda voltar pelo mapa até o ponto mais próximo de sua rota.
     */
    public void iniciarRetornoPatrulha(
            List<Point2D> caminho,
            Point2D pontoDaRota
    ) {
        emAlerta = false;
        retornandoPatrulha = true;
        exclamacao.setVisible(false);
        coneVisao.setFill(Color.rgb(255, 220, 70, 0.14));
        coneVisao.setStroke(Color.rgb(255, 220, 70, 0.55));
        caminhoPerseguicao = List.copyOf(caminho);
        indicePerseguicao = Math.min(1, caminhoPerseguicao.size());
        indiceRotaRetorno = Math.max(0, rotaPatrulha.indexOf(pontoDaRota));
    }

    public void atualizarRetornoPatrulha(double tempoDecorrido) {
        if (!retornandoPatrulha) {
            return;
        }

        boolean terminou = atualizarCaminhoAtual(
                tempoDecorrido,
                VELOCIDADE_RETORNO
        );

        if (terminou) {
            retornandoPatrulha = false;
            indicePatrulha = indiceRotaRetorno;
        }
    }

    public boolean estaRetornandoPatrulha() {
        return retornandoPatrulha;
    }

    public Point2D obterPontoPatrulhaMaisProximo() {
        Point2D maisProximo = rotaPatrulha.get(0);
        double menorDistancia = obterPosicao().distance(maisProximo);

        for (Point2D ponto : rotaPatrulha) {
            double distancia = obterPosicao().distance(ponto);

            if (distancia < menorDistancia) {
                menorDistancia = distancia;
                maisProximo = ponto;
            }
        }

        return maisProximo;
    }

    private boolean atualizarCaminhoAtual(
            double tempoDecorrido,
            double velocidade
    ) {
        if (caminhoPerseguicao.size() <= 1
                || indicePerseguicao >= caminhoPerseguicao.size()) {
            return true;
        }

        double distanciaRestante = velocidade * tempoDecorrido;

        while (distanciaRestante > 0
                && indicePerseguicao < caminhoPerseguicao.size()) {
            Point2D destino = caminhoPerseguicao.get(indicePerseguicao);
            double sobra = moverEmDirecao(destino, distanciaRestante);

            if (sobra < 0) {
                return false;
            }

            distanciaRestante = sobra;
            indicePerseguicao++;
        }

        return indicePerseguicao >= caminhoPerseguicao.size();
    }

    public boolean consegueVer(
            Rectangle jogador,
            CarregadorMapa carregadorMapa
    ) {
        /*
         * Testa uma grade de 25 pontos sobre todo o corpo do jogador.
         * Assim, encostar uma lateral ou um canto no cone também conta.
         */
        for (int linha = 0; linha < 5; linha++) {
            for (int coluna = 0; coluna < 5; coluna++) {
                Point2D pontoDoCorpo = new Point2D(
                        jogador.getX()
                                + jogador.getWidth() * coluna / 4.0,
                        jogador.getY()
                                + jogador.getHeight() * linha / 4.0
                );

                if (pontoEstaVisivel(pontoDoCorpo, carregadorMapa)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Reconstrói o cone visível, limitando cada raio pelo primeiro obstáculo.
     */
    public void atualizarCampoVisao(CarregadorMapa carregadorMapa) {
        coneVisao.getPoints().clear();
        coneVisao.getPoints().addAll(0.0, 0.0);

        double anguloDirecao = Math.atan2(
                direcaoOlhar.getY(),
                direcaoOlhar.getX()
        );

        for (int raio = 0; raio <= QUANTIDADE_RAIOS_VISAO; raio++) {
            double proporcao = raio / (double) QUANTIDADE_RAIOS_VISAO;
            double anguloLocalGraus = -METADE_ANGULO_VISAO
                    + proporcao * METADE_ANGULO_VISAO * 2.0;
            double anguloLocal = Math.toRadians(anguloLocalGraus);
            double anguloMundo = anguloDirecao + anguloLocal;

            Point2D direcaoRaio = new Point2D(
                    Math.cos(anguloMundo),
                    Math.sin(anguloMundo)
            );
            double alcanceLivre = carregadorMapa.obterAlcanceLivre(
                    obterPosicao(),
                    direcaoRaio,
                    ALCANCE_VISAO
            );

            /*
             * Os pontos ficam no sistema local do guarda. O grupo aplica a
             * rotação geral, mantendo desenho e cálculo na mesma direção.
             */
            coneVisao.getPoints().addAll(
                    Math.cos(anguloLocal) * alcanceLivre,
                    Math.sin(anguloLocal) * alcanceLivre
            );
        }
    }

    private boolean pontoEstaVisivel(
            Point2D ponto,
            CarregadorMapa carregadorMapa
    ) {
        Point2D atePonto = ponto.subtract(obterPosicao());
        double distancia = atePonto.magnitude();

        if (distancia > ALCANCE_VISAO) {
            return false;
        }

        if (distancia == 0) {
            return true;
        }

        double produtoEscalar = direcaoOlhar.dotProduct(atePonto.normalize());
        double limiteAngulo = Math.cos(Math.toRadians(METADE_ANGULO_VISAO));

        return produtoEscalar >= limiteAngulo
                && carregadorMapa.possuiLinhaDeVisao(
                        obterPosicao(), ponto
                );
    }

    /** O guarda pode disparar enquanto corre, desde que veja o jogador. */
    public boolean podeDisparar(
            Rectangle jogador,
            CarregadorMapa carregadorMapa,
            double tempoDecorrido
    ) {
        if (!emAlerta) {
            return false;
        }

        tempoAteDisparo -= tempoDecorrido;
        Point2D centroJogador = centroDoJogador(jogador);

        /*
         * Linha reta não é suficiente: o jogador precisa estar dentro do
         * mesmo cone usado pela detecção e exibido na tela. Isso impede que
         * o guarda mire por um campo de visão antigo ou lateral.
         */
        if (obterPosicao().distance(centroJogador) > 430.0
                || !consegueVer(jogador, carregadorMapa)) {
            return false;
        }

        atualizarDirecao(centroJogador.subtract(obterPosicao()));

        if (tempoAteDisparo > 0) {
            return false;
        }

        tempoAteDisparo = INTERVALO_DISPARO;
        return true;
    }

    public Point2D obterPosicao() {
        return new Point2D(getLayoutX(), getLayoutY());
    }

    private Point2D centroDoJogador(Rectangle jogador) {
        return new Point2D(
                jogador.getX() + jogador.getWidth() / 2.0,
                jogador.getY() + jogador.getHeight() / 2.0
        );
    }

    private double moverEmDirecao(Point2D destino, double distanciaPermitida) {
        Point2D deslocamento = destino.subtract(obterPosicao());
        double distancia = deslocamento.magnitude();

        if (distancia == 0) {
            return distanciaPermitida;
        }

        atualizarDirecao(deslocamento);

        if (distancia <= distanciaPermitida) {
            setLayoutX(destino.getX());
            setLayoutY(destino.getY());
            return distanciaPermitida - distancia;
        }

        Point2D movimento = deslocamento
                .normalize()
                .multiply(distanciaPermitida);
        setLayoutX(getLayoutX() + movimento.getX());
        setLayoutY(getLayoutY() + movimento.getY());
        return -1;
    }

    private void atualizarDirecao(Point2D deslocamento) {
        if (deslocamento.magnitude() == 0) {
            return;
        }

        direcaoOlhar = deslocamento.normalize();
        rotacaoVisao.setAngle(Math.toDegrees(
                Math.atan2(direcaoOlhar.getY(), direcaoOlhar.getX())
        ));
    }

    private void escolherProximoDestinoPatrulha() {
        if (indicePatrulha == rotaPatrulha.size() - 1) {
            sentidoPatrulha = -1;
        } else if (indicePatrulha == 0) {
            sentidoPatrulha = 1;
        }

        indicePatrulha += sentidoPatrulha;

        /*
         * Vira o campo de visão no mesmo quadro em que troca de destino.
         * Sem isso, a direção antiga permanecia ativa por um quadro.
         */
        atualizarDirecao(
                rotaPatrulha.get(indicePatrulha).subtract(obterPosicao())
        );
    }
}
