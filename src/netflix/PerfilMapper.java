package netflix;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

/**
 * Para cada registro do csv que passa nos filtros do recorte (tipo, gêneros e país) transforma:
 *   Cada informação que queremos contar em: ("dimensão\tvalor", 1)
 *   Dimensões: tipo, tipo_por_ano, diretor_informado, genero, combinacao_generos, pais, diretor,
 *              elenco, classificacao, duracao_min, temporadas, mes_adicao, palavra
 */
public class PerfilMapper extends Mapper<LongWritable, Text, Text, IntWritable> {

    static final String FILTRO_TIPO = "netflix.filtro.tipo";
    static final String FILTRO_GENEROS = "netflix.filtro.generos";
    static final String FILTRO_PAIS = "netflix.filtro.pais";

    enum Contadores { TITULOS_NO_RECORTE, FORA_DO_RECORTE, REGISTROS_INVALIDOS }

    private static final IntWritable UM = new IntWritable(1);
    private final Text chave = new Text();

    private String filtroTipo;
    private List<String> filtroGeneros;
    private String filtroPais;
    private Set<String> stopwords;
    private final Map<String, Integer> colunas = new HashMap<>();

    // roda uma vez por task: lê os filtros do recorte (-D) e carrega as stopwords
    @Override
    protected void setup(Context context) throws IOException {
        Configuration conf = context.getConfiguration();
        filtroTipo = conf.getTrimmed(FILTRO_TIPO, "");
        filtroGeneros = Arrays.asList(conf.getTrimmedStrings(FILTRO_GENEROS));
        filtroPais = conf.getTrimmed(FILTRO_PAIS, "");
        stopwords = Utils.carregarStopwords();
    }

    // para cada registro, separa as colunas, confere se o título está no recorte
    // e emite ("dimensão\tvalor", 1) para cada informação que queremos contar
    @Override
    protected void map(LongWritable posicao, Text registro, Context context)
            throws IOException, InterruptedException {
        List<String> campos = Utils.lerCampos(registro.toString());

        if (posicao.get() == 0) {
            lerCabecalho(campos);
            return;
        }
        if (campos == null || campos.size() < colunas.size()) {
            context.getCounter(Contadores.REGISTROS_INVALIDOS).increment(1);
            return;
        }

        String tipo = campo(campos, "type");
        List<String> generos = Utils.separarLista(campo(campos, "listed_in"));
        List<String> paises = Utils.separarLista(campo(campos, "country"));

        // filtro vazio deixa passar tudo; os gêneros do filtro precisam estar todos no título
        boolean noRecorte = (filtroTipo.isEmpty() || filtroTipo.equals(tipo))
                && generos.containsAll(filtroGeneros)
                && (filtroPais.isEmpty() || paises.contains(filtroPais));
        if (!noRecorte) {
            context.getCounter(Contadores.FORA_DO_RECORTE).increment(1);
            return;
        }
        context.getCounter(Contadores.TITULOS_NO_RECORTE).increment(1);

        // date_added vem como "September 25, 2021"
        String dataAdicao = campo(campos, "date_added");
        String mes = dataAdicao.contains(" ") ? dataAdicao.substring(0, dataAdicao.indexOf(' ')) : "";
        String ano = dataAdicao.length() >= 4 ? dataAdicao.substring(dataAdicao.length() - 4) : "";
        List<String> diretores = Utils.separarLista(campo(campos, "director"));

        emitir(context, "tipo", tipo);
        if (!ano.isEmpty()) {
            emitir(context, "tipo_por_ano", ano + " " + tipo);
        }
        emitir(context, "diretor_informado", tipo + ": " + (diretores.isEmpty() ? "nao" : "sim"));
        emitir(context, "combinacao_generos", String.join(", ", generos));
        emitir(context, "classificacao", campo(campos, "rating"));
        emitir(context, "mes_adicao", mes);
        for (String genero : generos) {
            emitir(context, "genero", genero);
        }
        for (String pais : paises) {
            emitir(context, "pais", pais);
        }
        for (String diretor : diretores) {
            emitir(context, "diretor", diretor);
        }
        for (String ator : Utils.separarLista(campo(campos, "cast"))) {
            emitir(context, "elenco", ator);
        }

        // duration vem como "98 min" (filmes) ou "2 Seasons" (series)
        String[] duracao = campo(campos, "duration").split(" ");
        if (duracao.length == 2) {
            emitir(context, duracao[1].equals("min") ? "duracao_min" : "temporadas", duracao[0]);
        }

        for (String palavra : Utils.normalizar(campo(campos, "description")).split(" ")) {
            if (!palavra.isEmpty() && !stopwords.contains(palavra)) {
                emitir(context, "palavra", palavra);
            }
        }
    }

    // monta a chave "dimensão\tvalor" e entrega para o Hadoop com o valor 1
    private void emitir(Context context, String dimensao, String valor)
            throws IOException, InterruptedException {
        if (!valor.isEmpty()) {
            chave.set(dimensao + "\t" + valor);
            context.write(chave, UM);
        }
    }

    // pega o valor de uma coluna pelo nome dela no cabeçalho
    private String campo(List<String> campos, String nome) {
        return campos.get(colunas.get(nome)).trim();
    }

    /** Descobre a posicao das colunas pelo nome, entao a ordem delas no CSV nao importa. */
    private void lerCabecalho(List<String> campos) throws IOException {
        if (campos != null) {
            for (int i = 0; i < campos.size(); i++) {
                colunas.put(campos.get(i).replace("\uFEFF", "").trim().toLowerCase(Locale.ROOT), i);
            }
        }
        for (String nome : new String[] {"type", "director", "cast", "country", "date_added",
                "rating", "duration", "listed_in", "description"}) {
            if (!colunas.containsKey(nome)) {
                throw new IOException("Cabecalho sem a coluna " + nome + ": " + colunas.keySet());
            }
        }
    }
}
