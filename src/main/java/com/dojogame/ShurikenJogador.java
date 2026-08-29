package com.dojogame;

import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

/**
 * Shuriken lançada pelo jogador na última direção de movimento.
 */
public class ShurikenJogador extends Circle {

    private static final double VELOCIDADE = 420.0;
    private static final double TEMPO_MAXIMO = 2.0;
    private static final double RAIO_ACERTO_GUARDA = 17.0;

    private final Point2D velocidade;
    private double tempoRestante = TEMPO_MAXIMO;

    public ShurikenJogador(Point2D origem, Point2D direcao) {
        super(origem.getX(), origem.getY(), 5.0);

        Point2D direcaoValida = direcao == null || direcao.magnitude() == 0
                ? new Point2D(0, -1)
                : direcao.normalize();
        velocidade = direcaoValida.multiply(VELOCIDADE);

        setFill(Color.SILVER);
        setStroke(Color.rgb(35, 40, 48));
        setStrokeWidth(2);
        setManaged(false);
        setMouseTransparent(true);
    }

    public void atualizar(double tempoDecorrido) {
        setCenterX(getCenterX() + velocidade.getX() * tempoDecorrido);
        setCenterY(getCenterY() + velocidade.getY() * tempoDecorrido);
        setRotate(getRotate() + 900.0 * tempoDecorrido);
        tempoRestante -= tempoDecorrido;
    }

    public boolean atingiu(GuardaPatrulha guarda) {
        Point2D posicaoShuriken = new Point2D(getCenterX(), getCenterY());
        return posicaoShuriken.distance(guarda.obterPosicao())
                <= RAIO_ACERTO_GUARDA;
    }

    public boolean deveSerRemovida(CarregadorMapa carregadorMapa) {
        return tempoRestante <= 0
                || carregadorMapa.possuiColisao(
                        getCenterX() - getRadius(),
                        getCenterY() - getRadius(),
                        getRadius() * 2,
                        getRadius() * 2
                );
    }
}
