package com.dojogame;

import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/** Disparo provisório utilizado pelos guardas durante o alerta. */
public class Projetil extends Circle {

    private static final double VELOCIDADE = 360.0;
    private final Point2D velocidade;
    private double tempoRestante = 2.5;

    public Projetil(Point2D origem, Point2D destino) {
        super(origem.getX(), origem.getY(), 4, Color.CRIMSON);

        Point2D direcao = destino.subtract(origem);

        if (direcao.magnitude() == 0) {
            direcao = new Point2D(1, 0);
        }

        velocidade = direcao.normalize().multiply(VELOCIDADE);
        setStroke(Color.DARKRED);
        setManaged(false);
        setMouseTransparent(true);
    }

    public void atualizar(double tempoDecorrido) {
        setCenterX(getCenterX() + velocidade.getX() * tempoDecorrido);
        setCenterY(getCenterY() + velocidade.getY() * tempoDecorrido);
        tempoRestante -= tempoDecorrido;
    }

    public boolean atingiu(Rectangle jogador) {
        return getBoundsInParent().intersects(jogador.getBoundsInParent());
    }

    public boolean deveSerRemovido(CarregadorMapa carregadorMapa) {
        return tempoRestante <= 0
                || carregadorMapa.possuiColisao(
                        getCenterX() - getRadius(),
                        getCenterY() - getRadius(),
                        getRadius() * 2,
                        getRadius() * 2
                );
    }
}
