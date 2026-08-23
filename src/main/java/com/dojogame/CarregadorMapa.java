package com.dojogame;

import java.net.URL;

/**
 * Responsável por localizar e, futuramente,
 * interpretar os mapas criados no Tiled.
 */
public class CarregadorMapa {

    /**
     * Procura um mapa dentro da pasta de recursos "maps".
     *
     * @param nomeArquivo nome do arquivo, incluindo a extensão .tmx.
     * @return endereço do mapa encontrado.
     */
    public URL localizarMapa(String nomeArquivo) {

        // Monta o caminho utilizado para procurar o mapa.
        String caminhoMapa = "/maps/" + nomeArquivo;

        /*
         * Procura o arquivo nos recursos que foram
         * configurados no pom.xml.
         */
        URL enderecoMapa = getClass().getResource(caminhoMapa);

        /*
         * Se o resultado for nulo, significa que o Java
         * não encontrou o arquivo no caminho informado.
         */
        if (enderecoMapa == null) {
            throw new IllegalStateException(
                    "O mapa não foi encontrado: " + caminhoMapa
            );
        }

        // Retorna o endereço do arquivo encontrado.
        return enderecoMapa;
    }
}