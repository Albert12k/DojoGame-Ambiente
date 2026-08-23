package com.dojogame;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URL;

/**
 * Responsável por localizar, interpretar e desenhar
 * os mapas criados no programa Tiled.
 */
public class CarregadorMapa {

    // Identificador utilizado pelas pequenas árvores no tileset atual.
    private static final int GID_ARVORE = 116;

    // Grade que informa quais posições do mapa bloqueiam o jogador.
    private boolean[][] blocosComColisao;

    // Medidas utilizadas para converter pixels em linhas e colunas.
    private int larguraTileMapa;
    private int alturaTileMapa;

    /**
     * Procura um mapa dentro da pasta de recursos "maps".
     *
     * @param nomeArquivo nome do arquivo, incluindo a extensão .tmx.
     * @return endereço do mapa encontrado.
     */
    public URL localizarMapa(String nomeArquivo) {
        String caminhoMapa = "/maps/" + nomeArquivo;
        URL enderecoMapa = getClass().getResource(caminhoMapa);

        if (enderecoMapa == null) {
            throw new IllegalStateException(
                    "O mapa não foi encontrado: " + caminhoMapa
            );
        }

        return enderecoMapa;
    }

    /**
     * Carrega um arquivo TMX e transforma suas camadas de tiles
     * em uma imagem desenhada sobre um Canvas do JavaFX.
     *
     * @param nomeArquivo nome do mapa que será carregado.
     * @return Canvas contendo o mapa desenhado.
     */
    public Canvas carregarVisualMapa(String nomeArquivo) {
        URL enderecoMapa = localizarMapa(nomeArquivo);
        Document documentoMapa = lerDocumentoXml(enderecoMapa);
        Element mapa = documentoMapa.getDocumentElement();

        // Lê as medidas gerais definidas no Tiled.
        int larguraEmTiles = lerInteiro(mapa, "width");
        int alturaEmTiles = lerInteiro(mapa, "height");
        larguraTileMapa = lerInteiro(mapa, "tilewidth");
        alturaTileMapa = lerInteiro(mapa, "tileheight");

        // Inicialmente nenhuma posição do mapa possui colisão.
        blocosComColisao = new boolean[alturaEmTiles][larguraEmTiles];

        // Calcula o tamanho total do mapa em pixels.
        int larguraEmPixels = larguraEmTiles * larguraTileMapa;
        int alturaEmPixels = alturaEmTiles * alturaTileMapa;

        // O Canvas funciona como a tela sobre a qual os tiles são desenhados.
        Canvas canvasMapa = new Canvas(larguraEmPixels, alturaEmPixels);
        GraphicsContext pincel = canvasMapa.getGraphicsContext2D();

        // Carrega a imagem e as informações do tileset externo (.tsx).
        DadosTileset tileset = carregarTileset(enderecoMapa, mapa);

        /*
         * Procura somente camadas de tiles. Camadas de objetos,
         * como PontosImportantes e RotasGuardas, ficam para outra etapa.
         */
        NodeList camadas = mapa.getElementsByTagName("layer");

        // Desenha as camadas na mesma ordem em que aparecem no Tiled.
        for (int indice = 0; indice < camadas.getLength(); indice++) {
            Element camada = (Element) camadas.item(indice);

            // Uma camada com visible="0" está oculta e não deve aparecer.
            if ("0".equals(camada.getAttribute("visible"))) {
                continue;
            }

            // Verifica a propriedade personalizada colisao=true do Tiled.
            boolean camadaTemColisao = camadaPossuiColisao(camada);

            desenharCamada(
                    pincel,
                    camada,
                    tileset,
                    larguraTileMapa,
                    alturaTileMapa,
                    camadaTemColisao
            );
        }

        return canvasMapa;
    }

    /**
     * Carrega o arquivo TSX indicado pelo mapa e a imagem PNG
     * que contém todos os tiles.
     */
    private DadosTileset carregarTileset(URL enderecoMapa, Element mapa) {
        NodeList elementosTileset = mapa.getElementsByTagName("tileset");

        if (elementosTileset.getLength() == 0) {
            throw new IllegalStateException("O mapa não possui um tileset.");
        }

        Element referenciaTileset = (Element) elementosTileset.item(0);

        // Primeiro identificador utilizado por esse tileset dentro do mapa.
        int primeiroGid = lerInteiro(referenciaTileset, "firstgid");

        // Caminho relativo do arquivo Exterior.tsx.
        String caminhoTileset = referenciaTileset.getAttribute("source");
        URL enderecoTileset = resolverEndereco(enderecoMapa, caminhoTileset);

        // Abre o XML do tileset externo.
        Document documentoTileset = lerDocumentoXml(enderecoTileset);
        Element tileset = documentoTileset.getDocumentElement();

        int larguraTile = lerInteiro(tileset, "tilewidth");
        int alturaTile = lerInteiro(tileset, "tileheight");
        int quantidadeColunas = lerInteiro(tileset, "columns");

        NodeList elementosImagem = tileset.getElementsByTagName("image");

        if (elementosImagem.getLength() == 0) {
            throw new IllegalStateException("O tileset não possui uma imagem.");
        }

        Element elementoImagem = (Element) elementosImagem.item(0);

        // Resolve o caminho do PNG a partir da localização do arquivo TSX.
        String caminhoImagem = elementoImagem.getAttribute("source");
        URL enderecoImagem = resolverEndereco(enderecoTileset, caminhoImagem);

        // Carrega a imagem completa do tileset para o JavaFX.
        Image imagem = new Image(enderecoImagem.toExternalForm(), false);

        if (imagem.isError()) {
            throw new IllegalStateException(
                    "Não foi possível carregar a imagem do tileset: "
                            + enderecoImagem,
                    imagem.getException()
            );
        }

        return new DadosTileset(
                imagem,
                primeiroGid,
                quantidadeColunas,
                larguraTile,
                alturaTile
        );
    }

    /**
     * Desenha uma camada que utiliza dados no formato CSV.
     */
    private void desenharCamada(
            GraphicsContext pincel,
            Element camada,
            DadosTileset tileset,
            int larguraTileMapa,
            int alturaTileMapa,
            boolean camadaTemColisao
    ) {
        int larguraCamada = lerInteiro(camada, "width");
        NodeList elementosData = camada.getElementsByTagName("data");

        if (elementosData.getLength() == 0) {
            return;
        }

        Element data = (Element) elementosData.item(0);

        // Nosso mapa foi salvo no Tiled utilizando codificação CSV.
        if (!"csv".equals(data.getAttribute("encoding"))) {
            throw new IllegalStateException(
                    "A camada " + camada.getAttribute("name")
                            + " não utiliza codificação CSV."
            );
        }

        // Separa a sequência CSV em identificadores individuais de tiles.
        String[] identificadores = data.getTextContent().trim().split(",");

        for (int indice = 0; indice < identificadores.length; indice++) {
            String textoGid = identificadores[indice].trim();

            // Ignora espaços vazios que possam existir no final do CSV.
            if (textoGid.isEmpty()) {
                continue;
            }

            /*
             * O Tiled reserva alguns bits do GID para indicar rotações.
             * A máscara mantém somente o número real do tile.
             */
            long gidCompleto = Long.parseLong(textoGid);
            int gid = (int) (gidCompleto & 0x0FFFFFFFL);

            // GID zero representa uma posição vazia na camada.
            if (gid == 0) {
                continue;
            }

            // Descobre a posição ocupada pelo tile dentro do mapa.
            int colunaMapa = indice % larguraCamada;
            int linhaMapa = indice / larguraCamada;

            /*
             * Registra a colisão antes de desenhar o tile.
             * Na camada de vegetação, somente o GID 116 representa
             * árvore; os outros GIDs são partes do chão e do caminho.
             */
            if (camadaTemColisao && tileBloqueiaMovimento(camada, gid)) {
                blocosComColisao[linhaMapa][colunaMapa] = true;
            }

            // Converte o GID do mapa para o índice interno do tileset.
            int indiceNoTileset = gid - tileset.primeiroGid;

            // Descobre onde o tile está localizado dentro da imagem PNG.
            int origemX = (indiceNoTileset % tileset.quantidadeColunas)
                    * tileset.larguraTile;
            int origemY = (indiceNoTileset / tileset.quantidadeColunas)
                    * tileset.alturaTile;

            // Descobre onde o tile deve aparecer dentro do mapa.
            int destinoX = colunaMapa * larguraTileMapa;
            int destinoY = linhaMapa * alturaTileMapa;

            // Recorta o tile correto da imagem e o desenha no Canvas.
            pincel.drawImage(
                    tileset.imagem,
                    origemX,
                    origemY,
                    tileset.larguraTile,
                    tileset.alturaTile,
                    destinoX,
                    destinoY,
                    larguraTileMapa,
                    alturaTileMapa
            );
        }
    }

    /**
     * Verifica se uma camada recebeu a propriedade personalizada
     * colisao=true dentro do Tiled.
     */
    private boolean camadaPossuiColisao(Element camada) {
        NodeList propriedades = camada.getElementsByTagName("property");

        for (int indice = 0; indice < propriedades.getLength(); indice++) {
            Element propriedade = (Element) propriedades.item(indice);

            if (!"colisao".equalsIgnoreCase(
                    propriedade.getAttribute("name")
            )) {
                continue;
            }

            String valor = propriedade.getAttribute("value");

            // O Tiled também pode armazenar o valor dentro do elemento.
            if (valor.isBlank()) {
                valor = propriedade.getTextContent();
            }

            return Boolean.parseBoolean(valor.trim());
        }

        return false;
    }

    /**
     * Decide quais tiles de uma camada bloqueiam o movimento.
     */
    private boolean tileBloqueiaMovimento(Element camada, int gid) {
        String nomeCamada = camada.getAttribute("name");

        /*
         * A camada ParedesVegetacao contém alguns tiles de acabamento
         * do chão. Por isso, apenas o tile da árvore deve bloquear.
         */
        if ("ParedesVegetacao".equalsIgnoreCase(nomeCamada)) {
            return gid == GID_ARVORE;
        }

        // Nas outras camadas de colisão, qualquer tile ocupado bloqueia.
        return true;
    }

    /**
     * Verifica se o retângulo do jogador encosta em algum tile bloqueado.
     *
     * @param x posição horizontal desejada.
     * @param y posição vertical desejada.
     * @param largura largura do jogador.
     * @param altura altura do jogador.
     * @return true quando a posição está bloqueada.
     */
    public boolean possuiColisao(
            double x,
            double y,
            double largura,
            double altura
    ) {
        if (blocosComColisao == null) {
            return false;
        }

        /*
         * A margem deixa a caixa de colisão um pouco menor que o desenho
         * do jogador, tornando a passagem pelos corredores mais natural.
         */
        double margem = 5.0;

        int colunaEsquerda = (int) ((x + margem) / larguraTileMapa);
        int colunaDireita = (int) (
                (x + largura - margem - 0.001) / larguraTileMapa
        );
        int linhaSuperior = (int) ((y + margem) / alturaTileMapa);
        int linhaInferior = (int) (
                (y + altura - margem - 0.001) / alturaTileMapa
        );

        // Sair da grade também é considerado uma colisão.
        if (linhaSuperior < 0
                || colunaEsquerda < 0
                || linhaInferior >= blocosComColisao.length
                || colunaDireita >= blocosComColisao[0].length) {
            return true;
        }

        // Examina todos os blocos tocados pela caixa do jogador.
        for (int linha = linhaSuperior; linha <= linhaInferior; linha++) {
            for (int coluna = colunaEsquerda;
                 coluna <= colunaDireita;
                 coluna++) {

                if (blocosComColisao[linha][coluna]) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Abre e interpreta um arquivo XML localizado por uma URL.
     */
    private Document lerDocumentoXml(URL enderecoArquivo) {
        try (InputStream fluxo = enderecoArquivo.openStream()) {
            DocumentBuilderFactory fabrica =
                    DocumentBuilderFactory.newInstance();

            // Evita que o XML tente carregar entidades externas.
            fabrica.setFeature(
                    "http://apache.org/xml/features/disallow-doctype-decl",
                    true
            );
            fabrica.setExpandEntityReferences(false);

            return fabrica.newDocumentBuilder().parse(fluxo);

        } catch (Exception erro) {
            throw new IllegalStateException(
                    "Não foi possível interpretar o XML: " + enderecoArquivo,
                    erro
            );
        }
    }

    /**
     * Converte um atributo numérico de um elemento XML para inteiro.
     */
    private int lerInteiro(Element elemento, String atributo) {
        String valor = elemento.getAttribute(atributo);

        if (valor.isBlank()) {
            throw new IllegalStateException(
                    "O atributo obrigatório '" + atributo + "' não foi encontrado."
            );
        }

        return Integer.parseInt(valor);
    }

    /**
     * Resolve caminhos relativos utilizados pelos arquivos TMX e TSX.
     */
    private URL resolverEndereco(URL enderecoBase, String caminhoRelativo) {
        try {
            return new URL(enderecoBase, caminhoRelativo);
        } catch (Exception erro) {
            throw new IllegalStateException(
                    "Não foi possível resolver o caminho: " + caminhoRelativo,
                    erro
            );
        }
    }

    /**
     * Agrupa as informações necessárias para recortar os tiles da imagem.
     */
    private static class DadosTileset {
        private final Image imagem;
        private final int primeiroGid;
        private final int quantidadeColunas;
        private final int larguraTile;
        private final int alturaTile;

        private DadosTileset(
                Image imagem,
                int primeiroGid,
                int quantidadeColunas,
                int larguraTile,
                int alturaTile
        ) {
            this.imagem = imagem;
            this.primeiroGid = primeiroGid;
            this.quantidadeColunas = quantidadeColunas;
            this.larguraTile = larguraTile;
            this.alturaTile = alturaTile;
        }
    }
}
