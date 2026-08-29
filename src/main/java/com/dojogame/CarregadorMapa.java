package com.dojogame;

import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    /*
     * Guarda os pontos criados na camada PontosImportantes do Tiled.
     * A chave é o nome do objeto, como "spawn_jogador".
     */
    private final Map<String, Point2D> pontosImportantes = new HashMap<>();

    // Rotas desenhadas como polylines na camada RotasGuardas do Tiled.
    private final Map<String, List<Point2D>> rotasGuardas =
            new LinkedHashMap<>();

    // Baús e outros objetos posicionados na camada ItensInterativos.
    private final List<ObjetoInterativo> itensInterativos = new ArrayList<>();

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

        // Lê os pontos especiais antes de criar os elementos do jogo.
        carregarPontosImportantes(mapa);
        carregarItensInterativos(mapa);

        // Lê também os caminhos que serão percorridos pelos guardas.
        // Mapas sem guardas, como o armazém, podem omitir essa camada.
        carregarRotasGuardas(mapa);

        // Inicialmente nenhuma posição do mapa possui colisão.
        blocosComColisao = new boolean[alturaEmTiles][larguraEmTiles];

        // Calcula o tamanho total do mapa em pixels.
        int larguraEmPixels = larguraEmTiles * larguraTileMapa;
        int alturaEmPixels = alturaEmTiles * alturaTileMapa;

        // O Canvas funciona como a tela sobre a qual os tiles são desenhados.
        Canvas canvasMapa = new Canvas(larguraEmPixels, alturaEmPixels);
        GraphicsContext pincel = canvasMapa.getGraphicsContext2D();

        // Carrega as imagens e informações de todos os tilesets externos.
        List<DadosTileset> tilesets = carregarTilesets(enderecoMapa, mapa);

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
                    tilesets,
                    larguraTileMapa,
                    alturaTileMapa,
                    camadaTemColisao
            );
        }

        return canvasMapa;
    }

    /**
     * Lê os objetos da camada PontosImportantes criada no Tiled.
     *
     * Cada objeto encontrado é armazenado pelo seu nome e pelas
     * coordenadas X e Y registradas dentro do arquivo TMX.
     */
    private void carregarPontosImportantes(Element mapa) {
        // Limpa os pontos anteriores caso outro mapa seja carregado.
        pontosImportantes.clear();

        NodeList gruposDeObjetos = mapa.getElementsByTagName("objectgroup");

        for (int indiceGrupo = 0;
             indiceGrupo < gruposDeObjetos.getLength();
             indiceGrupo++) {

            Element grupo = (Element) gruposDeObjetos.item(indiceGrupo);

            // Ignora as outras camadas, como RotasGuardas.
            if (!"PontosImportantes".equalsIgnoreCase(
                    grupo.getAttribute("name")
            )) {
                continue;
            }

            NodeList objetos = grupo.getElementsByTagName("object");

            for (int indiceObjeto = 0;
                 indiceObjeto < objetos.getLength();
                 indiceObjeto++) {

                Element objeto = (Element) objetos.item(indiceObjeto);
                String nome = objeto.getAttribute("name");

                // Um objeto sem nome não pode ser solicitado pelo jogo.
                if (nome.isBlank()) {
                    continue;
                }

                double x = Double.parseDouble(objeto.getAttribute("x"));
                double y = Double.parseDouble(objeto.getAttribute("y"));

                pontosImportantes.put(nome, new Point2D(x, y));
            }

            // Existe apenas uma camada PontosImportantes neste mapa.
            return;
        }

        throw new IllegalStateException(
                "A camada PontosImportantes não foi encontrada no mapa."
        );
    }

    /**
     * Obtém a posição de um ponto criado no Tiled.
     *
     * Este método deve ser chamado depois de carregarVisualMapa().
     *
     * @param nome nome exato do objeto dentro do Tiled.
     * @return coordenadas do ponto dentro do mapa.
     */
    public Point2D obterPontoImportante(String nome) {
        Point2D ponto = pontosImportantes.get(nome);

        if (ponto == null) {
            throw new IllegalStateException(
                    "O ponto importante não foi encontrado: " + nome
            );
        }

        return ponto;
    }


    /**
     * Lê os retângulos criados na camada ItensInterativos.
     */
    private void carregarItensInterativos(Element mapa) {
        itensInterativos.clear();
        NodeList gruposDeObjetos = mapa.getElementsByTagName("objectgroup");

        for (int indiceGrupo = 0;
             indiceGrupo < gruposDeObjetos.getLength();
             indiceGrupo++) {
            Element grupo = (Element) gruposDeObjetos.item(indiceGrupo);

            if (!"ItensInterativos".equalsIgnoreCase(
                    grupo.getAttribute("name")
            )) {
                continue;
            }

            NodeList objetos = grupo.getElementsByTagName("object");

            for (int indiceObjeto = 0;
                 indiceObjeto < objetos.getLength();
                 indiceObjeto++) {
                Element objeto = (Element) objetos.item(indiceObjeto);
                String nome = objeto.getAttribute("name");

                if (nome.isBlank()) {
                    continue;
                }

                itensInterativos.add(new ObjetoInterativo(
                        nome,
                        Double.parseDouble(objeto.getAttribute("x")),
                        Double.parseDouble(objeto.getAttribute("y")),
                        lerDoubleOpcional(objeto, "width"),
                        lerDoubleOpcional(objeto, "height")
                ));
            }

            return;
        }
    }

    /**
     * Retorna os objetos interativos encontrados no mapa atual.
     */
    public List<ObjetoInterativo> obterItensInterativos() {
        return List.copyOf(itensInterativos);
    }

    private double lerDoubleOpcional(Element elemento, String atributo) {
        String valor = elemento.getAttribute(atributo);
        return valor.isBlank() ? 0.0 : Double.parseDouble(valor);
    }

    /**
     * Lê as polylines da camada RotasGuardas.
     *
     * Os pontos de uma polyline são relativos à posição X/Y do objeto.
     * Por isso, cada ponto é convertido para uma coordenada absoluta do mapa.
     */
    private void carregarRotasGuardas(Element mapa) {
        rotasGuardas.clear();

        NodeList gruposDeObjetos = mapa.getElementsByTagName("objectgroup");

        for (int indiceGrupo = 0;
             indiceGrupo < gruposDeObjetos.getLength();
             indiceGrupo++) {

            Element grupo = (Element) gruposDeObjetos.item(indiceGrupo);

            if (!"RotasGuardas".equalsIgnoreCase(
                    grupo.getAttribute("name")
            )) {
                continue;
            }

            NodeList objetos = grupo.getElementsByTagName("object");

            for (int indiceObjeto = 0;
                 indiceObjeto < objetos.getLength();
                 indiceObjeto++) {

                Element objeto = (Element) objetos.item(indiceObjeto);
                String nome = objeto.getAttribute("name");
                NodeList polylines = objeto.getElementsByTagName("polyline");

                if (nome.isBlank() || polylines.getLength() == 0) {
                    continue;
                }

                double origemX = Double.parseDouble(objeto.getAttribute("x"));
                double origemY = Double.parseDouble(objeto.getAttribute("y"));

                Element polyline = (Element) polylines.item(0);
                String[] pontosTexto = polyline
                        .getAttribute("points")
                        .trim()
                        .split("\\s+");

                List<Point2D> pontosDaRota = new ArrayList<>();

                for (String pontoTexto : pontosTexto) {
                    String[] coordenadas = pontoTexto.split(",");

                    if (coordenadas.length != 2) {
                        continue;
                    }

                    double x = origemX + Double.parseDouble(coordenadas[0]);
                    double y = origemY + Double.parseDouble(coordenadas[1]);
                    pontosDaRota.add(new Point2D(x, y));
                }

                // Uma patrulha precisa de pelo menos origem e destino.
                if (pontosDaRota.size() >= 2) {
                    rotasGuardas.put(nome, List.copyOf(pontosDaRota));
                }
            }

            return;
        }

        // A ausência dessa camada significa apenas que o mapa não tem guardas.
    }

    /**
     * Retorna as rotas encontradas sem permitir alterações externas.
     */
    public Map<String, List<Point2D>> obterRotasGuardas() {
        return Collections.unmodifiableMap(rotasGuardas);
    }

    /**
     * Verifica se duas posições conseguem se enxergar sem uma parede no meio.
     */
    public boolean possuiLinhaDeVisao(Point2D origem, Point2D destino) {
        if (blocosComColisao == null) {
            return false;
        }

        Point2D deslocamento = destino.subtract(origem);
        double distancia = deslocamento.magnitude();

        if (distancia == 0) {
            return true;
        }

        int quantidadePassos = Math.max(1, (int) Math.ceil(distancia / 8.0));

        for (int passo = 1; passo < quantidadePassos; passo++) {
            double proporcao = passo / (double) quantidadePassos;
            double x = origem.getX() + deslocamento.getX() * proporcao;
            double y = origem.getY() + deslocamento.getY() * proporcao;
            int coluna = (int) (x / larguraTileMapa);
            int linha = (int) (y / alturaTileMapa);

            if (posicaoDaGradeBloqueada(linha, coluna)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Mede quanto um raio pode avançar antes de encontrar uma colisão.
     * É usado para o desenho do campo de visão não atravessar obstáculos.
     */
    public double obterAlcanceLivre(
            Point2D origem,
            Point2D direcao,
            double alcanceMaximo
    ) {
        if (blocosComColisao == null || direcao.magnitude() == 0) {
            return 0;
        }

        Point2D direcaoNormalizada = direcao.normalize();
        double tamanhoPasso = 4.0;

        for (double distancia = tamanhoPasso;
             distancia <= alcanceMaximo;
             distancia += tamanhoPasso) {
            Point2D ponto = origem.add(
                    direcaoNormalizada.multiply(distancia)
            );
            int coluna = (int) (ponto.getX() / larguraTileMapa);
            int linha = (int) (ponto.getY() / alturaTileMapa);

            if (posicaoDaGradeBloqueada(linha, coluna)) {
                return Math.max(0, distancia - tamanhoPasso);
            }
        }

        return alcanceMaximo;
    }

    /**
     * Encontra um caminho na grade para o guarda não atravessar paredes.
     */
    public List<Point2D> encontrarCaminho(
            Point2D origem,
            Point2D destino
    ) {
        if (blocosComColisao == null) {
            return List.of(origem);
        }

        int linhaInicial = (int) (origem.getY() / alturaTileMapa);
        int colunaInicial = (int) (origem.getX() / larguraTileMapa);
        int linhaFinal = (int) (destino.getY() / alturaTileMapa);
        int colunaFinal = (int) (destino.getX() / larguraTileMapa);

        if (posicaoDaGradeBloqueada(linhaInicial, colunaInicial)
                || posicaoDaGradeBloqueada(linhaFinal, colunaFinal)) {
            return List.of(origem);
        }

        int quantidadeLinhas = blocosComColisao.length;
        int quantidadeColunas = blocosComColisao[0].length;
        boolean[][] visitado =
                new boolean[quantidadeLinhas][quantidadeColunas];
        int[][] linhaAnterior =
                new int[quantidadeLinhas][quantidadeColunas];
        int[][] colunaAnterior =
                new int[quantidadeLinhas][quantidadeColunas];

        for (int linha = 0; linha < quantidadeLinhas; linha++) {
            java.util.Arrays.fill(linhaAnterior[linha], -1);
            java.util.Arrays.fill(colunaAnterior[linha], -1);
        }

        ArrayDeque<int[]> fila = new ArrayDeque<>();
        fila.add(new int[]{linhaInicial, colunaInicial});
        visitado[linhaInicial][colunaInicial] = true;

        int[][] direcoes = {
                {-1, 0}, {1, 0}, {0, -1}, {0, 1}
        };

        while (!fila.isEmpty()) {
            int[] atual = fila.removeFirst();

            if (atual[0] == linhaFinal && atual[1] == colunaFinal) {
                break;
            }

            for (int[] direcao : direcoes) {
                int proximaLinha = atual[0] + direcao[0];
                int proximaColuna = atual[1] + direcao[1];

                if (posicaoDaGradeBloqueada(proximaLinha, proximaColuna)
                        || visitado[proximaLinha][proximaColuna]) {
                    continue;
                }

                visitado[proximaLinha][proximaColuna] = true;
                linhaAnterior[proximaLinha][proximaColuna] = atual[0];
                colunaAnterior[proximaLinha][proximaColuna] = atual[1];
                fila.addLast(new int[]{proximaLinha, proximaColuna});
            }
        }

        if (!visitado[linhaFinal][colunaFinal]) {
            return List.of(origem);
        }

        List<Point2D> caminhoInvertido = new ArrayList<>();
        int linhaAtual = linhaFinal;
        int colunaAtual = colunaFinal;

        while (linhaAtual != linhaInicial || colunaAtual != colunaInicial) {
            caminhoInvertido.add(centroDoTile(linhaAtual, colunaAtual));
            int proximaLinha = linhaAnterior[linhaAtual][colunaAtual];
            int proximaColuna = colunaAnterior[linhaAtual][colunaAtual];
            linhaAtual = proximaLinha;
            colunaAtual = proximaColuna;
        }

        Collections.reverse(caminhoInvertido);

        List<Point2D> caminho = new ArrayList<>();
        caminho.add(origem);
        caminho.addAll(caminhoInvertido);

        if (!caminho.get(caminho.size() - 1).equals(destino)) {
            caminho.add(destino);
        }

        return caminho;
    }

    /**
     * Escolhe posições livres e diferentes ao redor do jogador.
     * Assim os guardas cercam o alvo em vez de ocuparem o mesmo ponto.
     */
    public List<Point2D> encontrarPosicoesAoRedor(
            Point2D centro,
            int quantidade
    ) {
        int linhaCentral = (int) (centro.getY() / alturaTileMapa);
        int colunaCentral = (int) (centro.getX() / larguraTileMapa);

        if (posicaoDaGradeBloqueada(linhaCentral, colunaCentral)) {
            return List.of(centro);
        }

        int quantidadeLinhas = blocosComColisao.length;
        int quantidadeColunas = blocosComColisao[0].length;
        int[][] distancia = new int[quantidadeLinhas][quantidadeColunas];

        for (int linha = 0; linha < quantidadeLinhas; linha++) {
            java.util.Arrays.fill(distancia[linha], -1);
        }

        ArrayDeque<int[]> fila = new ArrayDeque<>();
        List<Point2D> candidatas = new ArrayList<>();
        fila.add(new int[]{linhaCentral, colunaCentral});
        distancia[linhaCentral][colunaCentral] = 0;

        int[][] direcoes = {
                {-1, 0}, {0, 1}, {1, 0}, {0, -1}
        };

        while (!fila.isEmpty()) {
            int[] atual = fila.removeFirst();
            int distanciaAtual = distancia[atual[0]][atual[1]];

            if (distanciaAtual >= 2 && distanciaAtual <= 8) {
                candidatas.add(centroDoTile(atual[0], atual[1]));
            }

            if (distanciaAtual >= 8) {
                continue;
            }

            for (int[] direcao : direcoes) {
                int proximaLinha = atual[0] + direcao[0];
                int proximaColuna = atual[1] + direcao[1];

                if (posicaoDaGradeBloqueada(proximaLinha, proximaColuna)
                        || distancia[proximaLinha][proximaColuna] != -1) {
                    continue;
                }

                distancia[proximaLinha][proximaColuna] = distanciaAtual + 1;
                fila.addLast(new int[]{proximaLinha, proximaColuna});
            }
        }

        if (candidatas.isEmpty()) {
            return List.of(centroDoTile(linhaCentral, colunaCentral));
        }

        // Ordena em volta do jogador para distribuir os guardas pelos lados.
        candidatas.sort((primeira, segunda) -> Double.compare(
                Math.atan2(
                        primeira.getY() - centro.getY(),
                        primeira.getX() - centro.getX()
                ),
                Math.atan2(
                        segunda.getY() - centro.getY(),
                        segunda.getX() - centro.getX()
                )
        ));

        List<Point2D> escolhidas = new ArrayList<>();
        int totalDesejado = Math.min(quantidade, candidatas.size());

        for (int indice = 0; indice < totalDesejado; indice++) {
            int indiceCandidata = (int) Math.floor(
                    indice * candidatas.size() / (double) totalDesejado
            );
            escolhidas.add(candidatas.get(indiceCandidata));
        }

        return escolhidas;
    }

    private Point2D centroDoTile(int linha, int coluna) {
        return new Point2D(
                coluna * larguraTileMapa + larguraTileMapa / 2.0,
                linha * alturaTileMapa + alturaTileMapa / 2.0
        );
    }

    private boolean posicaoDaGradeBloqueada(int linha, int coluna) {
        return linha < 0
                || coluna < 0
                || linha >= blocosComColisao.length
                || coluna >= blocosComColisao[0].length
                || blocosComColisao[linha][coluna];
    }

    /**
     * Carrega o arquivo TSX indicado pelo mapa e a imagem PNG
     * que contém todos os tiles.
     */
    private List<DadosTileset> carregarTilesets(
            URL enderecoMapa,
            Element mapa
    ) {
        NodeList elementosTileset = mapa.getElementsByTagName("tileset");

        if (elementosTileset.getLength() == 0) {
            throw new IllegalStateException("O mapa não possui um tileset.");
        }

        List<DadosTileset> tilesets = new ArrayList<>();

        for (int indice = 0; indice < elementosTileset.getLength(); indice++) {
            Element referenciaTileset =
                    (Element) elementosTileset.item(indice);
            int primeiroGid = lerInteiro(referenciaTileset, "firstgid");
            String caminhoTileset =
                    referenciaTileset.getAttribute("source");

            if (caminhoTileset.isBlank()) {
                throw new IllegalStateException(
                        "Tilesets incorporados ao TMX ainda não são suportados."
                );
            }

            URL enderecoTileset =
                    resolverEndereco(enderecoMapa, caminhoTileset);
            Document documentoTileset = lerDocumentoXml(enderecoTileset);
            Element tileset = documentoTileset.getDocumentElement();

            int larguraTile = lerInteiro(tileset, "tilewidth");
            int alturaTile = lerInteiro(tileset, "tileheight");
            int quantidadeColunas = lerInteiro(tileset, "columns");

            if (quantidadeColunas <= 0) {
                throw new IllegalStateException(
                        "O tileset não possui uma grade de imagem: "
                                + caminhoTileset
                );
            }

            NodeList elementosImagem =
                    tileset.getElementsByTagName("image");

            if (elementosImagem.getLength() == 0) {
                throw new IllegalStateException(
                        "O tileset não possui uma imagem: " + caminhoTileset
                );
            }

            Element elementoImagem = (Element) elementosImagem.item(0);
            URL enderecoImagem = resolverEndereco(
                    enderecoTileset,
                    elementoImagem.getAttribute("source")
            );
            Image imagem = new Image(enderecoImagem.toExternalForm(), false);

            if (imagem.isError()) {
                throw new IllegalStateException(
                        "Não foi possível carregar a imagem do tileset: "
                                + enderecoImagem,
                        imagem.getException()
                );
            }

            tilesets.add(new DadosTileset(
                    imagem,
                    primeiroGid,
                    quantidadeColunas,
                    larguraTile,
                    alturaTile
            ));
        }

        return List.copyOf(tilesets);
    }

    /**
     * Localiza o tileset responsável por um GID do mapa.
     */
    private DadosTileset encontrarTileset(
            List<DadosTileset> tilesets,
            int gid
    ) {
        DadosTileset encontrado = null;

        for (DadosTileset candidato : tilesets) {
            if (gid >= candidato.primeiroGid
                    && (encontrado == null
                    || candidato.primeiroGid > encontrado.primeiroGid)) {
                encontrado = candidato;
            }
        }

        return encontrado;
    }

    /**
     * Desenha uma camada que utiliza dados no formato CSV.
     */
    private void desenharCamada(
            GraphicsContext pincel,
            Element camada,
            List<DadosTileset> tilesets,
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

            DadosTileset tileset = encontrarTileset(tilesets, gid);

            if (tileset == null) {
                throw new IllegalStateException(
                        "Nenhum tileset foi encontrado para o GID " + gid
                );
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
        double margem = Math.min(
                5.0,
                Math.min(largura, altura) / 4.0
        );

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
     * Objeto retangular criado na camada ItensInterativos do Tiled.
     */
    public record ObjetoInterativo(
            String nome,
            double x,
            double y,
            double largura,
            double altura
    ) {
        public Point2D centro() {
            return new Point2D(
                    x + largura / 2.0,
                    y + altura / 2.0
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
