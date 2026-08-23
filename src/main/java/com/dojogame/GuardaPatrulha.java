package com.dojogame;

import javafx.geometry.Point2D;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.util.List;

/**
 * Representação provisória de um guarda que percorre uma rota do Tiled.
 *
 * Nesta etapa ele apenas patrulha. O campo de visão e a detecção do
 * jogador serão acrescentados depois que o movimento estiver validado.
 */
public class GuardaPatrulha extends Circle {

    private static final double VELOCIDADE = 70.0;

    private final List<Point2D> rota;
    private int indiceDestino = 1;
    private int sentido = 1;

    public GuardaPatrulha(List<Point2D> rota) {
        super(11, Color.DARKORANGE);

        if (rota == null || rota.size() < 2) {
            throw new IllegalArgumentException(
                    "A rota do guarda precisa possuir pelo menos dois pontos."
            );
        }

        this.rota = List.copyOf(rota);

        Point2D pontoInicial = this.rota.get(0);
        setCenterX(pontoInicial.getX());
        setCenterY(pontoInicial.getY());

        // Contorno escuro para o guarda continuar visível sobre o caminho.
        setStroke(Color.rgb(35, 24, 15));
        setStrokeWidth(2);
    }

    /**
     * Avança o guarda em direção ao próximo ponto da rota.
     * Ao alcançar uma extremidade, ele retorna pelo mesmo caminho.
     */
    public void atualizar(double tempoDecorrido) {
        double distanciaRestante = VELOCIDADE * tempoDecorrido;

        while (distanciaRestante > 0) {
            Point2D destino = rota.get(indiceDestino);
            Point2D posicaoAtual = new Point2D(getCenterX(), getCenterY());
            Point2D deslocamento = destino.subtract(posicaoAtual);
            double distanciaAteDestino = deslocamento.magnitude();

            if (distanciaAteDestino <= distanciaRestante) {
                setCenterX(destino.getX());
                setCenterY(destino.getY());
                distanciaRestante -= distanciaAteDestino;
                escolherProximoDestino();
                continue;
            }

            Point2D movimento = deslocamento
                    .normalize()
                    .multiply(distanciaRestante);

            setCenterX(getCenterX() + movimento.getX());
            setCenterY(getCenterY() + movimento.getY());
            distanciaRestante = 0;
        }
    }

    private void escolherProximoDestino() {
        if (indiceDestino == rota.size() - 1) {
            sentido = -1;
        } else if (indiceDestino == 0) {
            sentido = 1;
        }

        indiceDestino += sentido;
    }
}
