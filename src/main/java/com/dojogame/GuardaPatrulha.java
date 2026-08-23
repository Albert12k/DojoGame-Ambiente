package com.dojogame;

import javafx.geometry.Point2D;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Rectangle;

import java.util.List;

/** Guarda provisório com patrulha, cone de visão e estado de alerta. */
public class GuardaPatrulha extends Pane {

    private static final double VELOCIDADE_PATRULHA = 70.0;
    private static final double VELOCIDADE_ALERTA = 115.0;
    private static final double ALCANCE_VISAO = 190.0;
    private static final double METADE_ANGULO_VISAO = 32.0;
    private static final double INTERVALO_DISPARO = 1.1;

    private final List<Point2D> rotaPatrulha;
    private final Polygon coneVisao;
    private final Label exclamacao;

    private List<Point2D> caminhoAlerta = List.of();
    private int indicePatrulha = 1;
    private int sentidoPatrulha = 1;
    private int indiceCaminhoAlerta = 1;
    private boolean emAlerta;
    private boolean chegouAoEncontro;
    private double tempoAteDisparo;
    private Point2D direcaoOlhar = new Point2D(1, 0);

    public GuardaPatrulha(List<Point2D> rota) {
        if (rota == null || rota.size() < 2) {
            throw new IllegalArgumentException(
                    "A rota do guarda precisa possuir pelo menos dois pontos."
            );
        }

        rotaPatrulha = List.copyOf(rota);

        double metadeLarguraCone =
                Math.tan(Math.toRadians(METADE_ANGULO_VISAO)) * ALCANCE_VISAO;

        coneVisao = new Polygon(
                0.0, 0.0,
                ALCANCE_VISAO, -metadeLarguraCone,
                ALCANCE_VISAO, metadeLarguraCone
        );
        coneVisao.setFill(Color.rgb(255, 220, 70, 0.16));
        coneVisao.setStroke(Color.rgb(255, 220, 70, 0.38));
        coneVisao.setMouseTransparent(true);

        Circle corpo = new Circle(0, 0, 11, Color.DARKORANGE);
        corpo.setStroke(Color.rgb(35, 24, 15));
        corpo.setStrokeWidth(2);

        Line indicadorDirecao = new Line(0, 0, 16, 0);
        indicadorDirecao.setStroke(Color.rgb(70, 35, 10));
        indicadorDirecao.setStrokeWidth(3);
        indicadorDirecao.setMouseTransparent(true);

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

        getChildren().addAll(coneVisao, indicadorDirecao, corpo, exclamacao);
        setManaged(false);
        setMouseTransparent(true);

        Point2D inicio = rotaPatrulha.get(0);
        setLayoutX(inicio.getX());
        setLayoutY(inicio.getY());

        atualizarDirecao(rotaPatrulha.get(1).subtract(inicio));
        indicadorDirecao.rotateProperty().bind(coneVisao.rotateProperty());
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

    public void entrarEmAlerta(List<Point2D> caminhoAteEncontro) {
        emAlerta = true;
        exclamacao.setVisible(true);
        coneVisao.setFill(Color.rgb(255, 40, 40, 0.13));
        coneVisao.setStroke(Color.rgb(255, 40, 40, 0.34));
        caminhoAlerta = List.copyOf(caminhoAteEncontro);
        indiceCaminhoAlerta = Math.min(1, caminhoAlerta.size());
        chegouAoEncontro = caminhoAlerta.size() <= 1;
        tempoAteDisparo = 0;
    }

    public void atualizarAlerta(double tempoDecorrido) {
        if (!emAlerta || chegouAoEncontro) {
            return;
        }

        double distanciaRestante = VELOCIDADE_ALERTA * tempoDecorrido;

        while (distanciaRestante > 0 && !chegouAoEncontro) {
            Point2D destino = caminhoAlerta.get(indiceCaminhoAlerta);
            double sobra = moverEmDirecao(destino, distanciaRestante);

            if (sobra < 0) {
                return;
            }

            distanciaRestante = sobra;
            indiceCaminhoAlerta++;
            chegouAoEncontro = indiceCaminhoAlerta >= caminhoAlerta.size();
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

    public boolean podeDisparar(
            Rectangle jogador,
            CarregadorMapa carregadorMapa,
            double tempoDecorrido
    ) {
        if (!chegouAoEncontro) {
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
        double angulo = Math.toDegrees(
                Math.atan2(direcaoOlhar.getY(), direcaoOlhar.getX())
        );
        coneVisao.setRotate(angulo);
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
