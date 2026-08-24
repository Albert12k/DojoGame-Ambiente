package com.dojogame;

import javafx.geometry.Point2D;
import javafx.scene.Group;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.transform.Rotate;

import java.util.List;

/** Guarda provisório com patrulha, visão e perseguição. */
public class GuardaPatrulha extends Pane {

    private static final double VELOCIDADE_PATRULHA = 70.0;
    private static final double VELOCIDADE_PERSEGUICAO = 135.0;
    private static final double ALCANCE_VISAO = 190.0;
    private static final double METADE_ANGULO_VISAO = 32.0;
    private static final double INTERVALO_DISPARO = 1.0;

    private final List<Point2D> rotaPatrulha;
    private final Arc coneVisao;
    private final Rotate rotacaoVisao = new Rotate(0, 0, 0);
    private final Label exclamacao;

    private List<Point2D> caminhoPerseguicao = List.of();
    private int indicePatrulha = 1;
    private int sentidoPatrulha = 1;
    private int indicePerseguicao = 1;
    private boolean emAlerta;
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
         * ArcType.ROUND cria um setor com duas laterais em V e uma borda
         * arredondada. A rotação usa pivô 0,0 para permanecer presa ao guarda.
         */
        coneVisao = new Arc(
                0,
                0,
                ALCANCE_VISAO,
                ALCANCE_VISAO,
                -METADE_ANGULO_VISAO,
                METADE_ANGULO_VISAO * 2
        );
        coneVisao.setType(ArcType.ROUND);
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
        if (emAlerta) {
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

    public void atualizarPerseguicao(double tempoDecorrido) {
        if (!emAlerta
                || caminhoPerseguicao.size() <= 1
                || indicePerseguicao >= caminhoPerseguicao.size()) {
            return;
        }

        double distanciaRestante = VELOCIDADE_PERSEGUICAO * tempoDecorrido;

        while (distanciaRestante > 0
                && indicePerseguicao < caminhoPerseguicao.size()) {
            Point2D destino = caminhoPerseguicao.get(indicePerseguicao);
            double sobra = moverEmDirecao(destino, distanciaRestante);

            if (sobra < 0) {
                return;
            }

            distanciaRestante = sobra;
            indicePerseguicao++;
        }
    }

    public boolean consegueVer(
            Rectangle jogador,
            CarregadorMapa carregadorMapa
    ) {
        Point2D centroJogador = centroDoJogador(jogador);
        Point2D ateJogador = centroJogador.subtract(obterPosicao());

        if (ateJogador.magnitude() > ALCANCE_VISAO
                || ateJogador.magnitude() == 0) {
            return false;
        }

        double produtoEscalar = direcaoOlhar.dotProduct(ateJogador.normalize());
        double limiteAngulo = Math.cos(Math.toRadians(METADE_ANGULO_VISAO));

        return produtoEscalar >= limiteAngulo
                && carregadorMapa.possuiLinhaDeVisao(
                        obterPosicao(), centroJogador
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

        if (obterPosicao().distance(centroJogador) > 430.0
                || !carregadorMapa.possuiLinhaDeVisao(
                        obterPosicao(), centroJogador
                )) {
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
    }
}
